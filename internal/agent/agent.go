package agent

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/hedleyluna/analyzer-agent/internal/email"
	"github.com/hedleyluna/analyzer-agent/internal/models"
	"github.com/hedleyluna/analyzer-agent/internal/websocket"
	"github.com/hedleyluna/analyzer-agent/pkg/config"
)

type Agent struct {
	config       *config.Config
	logger       *log.Logger
	wsClient     *websocket.Client
	analyzer     *PriceAnalyzer
	emailService *email.Service
	status       *models.AgentStatus
	ticker       *time.Ticker
	ctx          context.Context
	cancel       context.CancelFunc
	mu           sync.RWMutex
	alertsSent   int
}

func NewAgent(cfg *config.Config, logger *log.Logger) *Agent {
	wsClient := websocket.NewClient(&cfg.Scraper, &cfg.WebSocket, logger)
	analyzer := NewPriceAnalyzer(&cfg.Analysis, logger)
	emailService := email.NewService(&cfg.Email, logger)

	ctx, cancel := context.WithCancel(context.Background())

	return &Agent{
		config:       cfg,
		logger:       logger,
		wsClient:     wsClient,
		analyzer:     analyzer,
		emailService: emailService,
		ctx:          ctx,
		cancel:       cancel,
		status: &models.AgentStatus{
			Status: "stopped",
			Errors: make([]string, 0),
		},
	}
}

func (agent *Agent) Start() error {
	agent.mu.Lock()
	defer agent.mu.Unlock()

	if agent.status.Status == "running" {
		return fmt.Errorf("agent is already running")
	}

	agent.logger.Println("Starting analyzer agent...")

	if err := agent.emailService.TestConnection(); err != nil {
		agent.logger.Printf("Warning: Email service test failed: %v", err)
	}

	if err := agent.wsClient.ConnectWithRetry(agent.ctx); err != nil {
		return fmt.Errorf("failed to connect to scraper agent: %w", err)
	}

	agent.status.Status = "running"
	agent.status.LastCheck = time.Now()

	agent.ticker = time.NewTicker(agent.config.Analysis.CheckInterval)

	go agent.run()

	agent.logger.Printf("Analyzer agent started. Checking for deals every %v", agent.config.Analysis.CheckInterval)
	return nil
}

// Stop stops the analyzer agent
func (agent *Agent) Stop() error {
	agent.mu.Lock()
	defer agent.mu.Unlock()

	if agent.status.Status == "stopped" {
		return nil
	}

	agent.logger.Println("Stopping analyzer agent...")

	// Cancel context
	agent.cancel()

	// Stop ticker
	if agent.ticker != nil {
		agent.ticker.Stop()
	}

	// Disconnect WebSocket
	if err := agent.wsClient.Disconnect(); err != nil {
		agent.logger.Printf("Error disconnecting WebSocket: %v", err)
	}

	// Update status
	agent.status.Status = "stopped"

	agent.logger.Println("Analyzer agent stopped")
	return nil
}

func (agent *Agent) run() {
	// funny way go does to recover from panics :)
	defer func() {
		if r := recover(); r != nil {
			agent.logger.Printf("Agent panic: %v", r)
			agent.addError(fmt.Sprintf("Agent panic: %v", r))
		}
	}()

	if err := agent.performAnalysis(); err != nil {
		agent.logger.Printf("Initial analysis failed: %v", err)
		agent.addError(fmt.Sprintf("Initial analysis failed: %v", err))
	}

	for {
		select {
		case <-agent.ctx.Done():
			agent.logger.Println("Agent context cancelled, stopping...")
			return

		case <-agent.ticker.C:
			if err := agent.performAnalysis(); err != nil {
				agent.logger.Printf("Analysis failed: %v", err)
				agent.addError(fmt.Sprintf("Analysis failed: %v", err))
			}
		}
	}
}

func (agent *Agent) performAnalysis() error {
	agent.logger.Println("Starting price analysis cycle...")

	agent.mu.Lock()
	agent.status.LastCheck = time.Now()
	agent.mu.Unlock()

	products, err := agent.fetchProducts()
	if err != nil {
		return fmt.Errorf("failed to fetch products: %w", err)
	}

	agent.logger.Printf("Fetched %d products from scraper agent", len(products))

	agent.mu.Lock()
	// make sure we dont have a race condition here. lock the mutex
	agent.status.ProductsChecked = len(products)
	agent.mu.Unlock()

	goodDeals := agent.analyzer.AnalyzeProducts(agent.ctx, products)

	if len(goodDeals) > 0 {
		agent.logger.Printf("Found %d good deals, sending email alerts...", len(goodDeals))

		for _, deal := range goodDeals {
			agent.logger.Printf("Nice deal %w", deal.Product.Name)
			agent.logger.Printf("Nice deal %w", deal.Product.CurrentPrice)
			agent.logger.Printf("Nice deal %w", deal.Product.Store)
			agent.logger.Printf("Nice deal %w", deal.AveragePrice)
		}

		// if err := agent.sendEmailAlerts(goodDeals); err != nil {
		// 	return fmt.Errorf("failed to send email alerts: %w", err)
		// }

		// Update alerts sent count
		agent.mu.Lock()
		agent.alertsSent += len(goodDeals)
		agent.status.AlertsSent = agent.alertsSent
		agent.mu.Unlock()
	} else {
		agent.logger.Println("No good deals found in this analysis cycle")
	}

	agent.logger.Println("Analysis cycle completed successfully")
	return nil
}

func (agent *Agent) fetchProducts() ([]models.Product, error) {
	daysBack := int(agent.config.Analysis.AnalysisPeriod.Hours() / 24)

	ctx, cancel := context.WithTimeout(agent.ctx, agent.config.Scraper.Timeout)
	defer cancel()

	response, err := agent.wsClient.GetProducts(ctx, daysBack)
	if err != nil {
		return nil, err
	}

	return response.Products, nil
}

func (agent *Agent) sendEmailAlerts(goodDeals []*models.PriceAnalysis) error {
	if len(goodDeals) == 0 {
		return nil
	}

	// If there's only one deal, send individual email
	if len(goodDeals) == 1 {
		return agent.emailService.SendPriceAlert(goodDeals[0])
	}

	// If there are multiple deals, send a summary email
	return agent.emailService.SendMultiplePriceAlerts(goodDeals)
}

func (agent *Agent) GetStatus() *models.AgentStatus {
	agent.mu.RLock()
	defer agent.mu.RUnlock()

	// avoid race conditions
	status := *agent.status
	return &status
}

func (agent *Agent) addError(errorMsg string) {
	agent.mu.Lock()
	defer agent.mu.Unlock()

	agent.status.Errors = append(agent.status.Errors, errorMsg)

	if len(agent.status.Errors) > 10 {
		agent.status.Errors = agent.status.Errors[len(agent.status.Errors)-10:]
	}
}

func (agent *Agent) ClearErrors() {
	agent.mu.Lock()
	defer agent.mu.Unlock()

	agent.status.Errors = make([]string, 0)
}

func (agent *Agent) ForceAnalysis() error {
	agent.logger.Println("Forcing immediate analysis cycle...")
	return agent.performAnalysis()
}

func (agent *Agent) GetAnalysisReport() (string, error) {
	products, err := agent.fetchProducts()
	if err != nil {
		return "", fmt.Errorf("failed to fetch products for report: %w", err)
	}

	// Analyze ALL products
	var allAnalyses []*models.PriceAnalysis
	for _, product := range products {
		analysis := agent.analyzer.AnalyzeProduct(&product)
		allAnalyses = append(allAnalyses, analysis)
	}

	var goodDeals []*models.PriceAnalysis
	for _, analysis := range allAnalyses {
		if analysis.IsGoodDeal {
			goodDeals = append(goodDeals, analysis)
		}
	}

	report := fmt.Sprintf("Analysis Report - %s\n", time.Now().Format("2006-01-02 15:04:05"))
	report += fmt.Sprintf("Total Products Analyzed: %d\n", len(allAnalyses))
	report += fmt.Sprintf("Good Deals Found: %d\n\n", len(goodDeals))

	if len(goodDeals) > 0 {
		report += agent.analyzer.GetAnalysisReport(goodDeals)
	} else {
		report += "No good deals found at this time.\n"
	}

	return report, nil
}

func (agent *Agent) Reconnect() error {
	agent.logger.Println("Attempting to reconnect to scraper agent...")

	if err := agent.wsClient.Disconnect(); err != nil {
		agent.logger.Printf("Error during disconnect: %v", err)
	}

	if err := agent.wsClient.ConnectWithRetry(agent.ctx); err != nil {
		return fmt.Errorf("failed to reconnect: %w", err)
	}

	agent.logger.Println("Successfully reconnected to scraper agent")
	return nil
}
