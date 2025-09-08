package agent

import (
	"context"
	"fmt"
	"log"
	"math"
	"time"

	"github.com/hedleyluna/analyzer-agent/internal/models"
	"github.com/hedleyluna/analyzer-agent/pkg/config"
)

// PriceAnalyzer analyzes product prices and detects good deals
type PriceAnalyzer struct {
	config *config.AnalysisConfig
	logger *log.Logger
}

// NewPriceAnalyzer creates a new price analyzer
func NewPriceAnalyzer(config *config.AnalysisConfig, logger *log.Logger) *PriceAnalyzer {
	return &PriceAnalyzer{
		config: config,
		logger: logger,
	}
}

// AnalyzeProduct analyzes a single product's price trend
func (pa *PriceAnalyzer) AnalyzeProduct(product *models.Product) *models.PriceAnalysis {
	if len(product.PriceHistory) == 0 {
		pa.logger.Printf("No price history for product %s", product.ID)
		return &models.PriceAnalysis{
			Product:      product,
			CurrentPrice: product.CurrentPrice,
			IsGoodDeal:   false,
			AnalyzedAt:   time.Now(),
		}
	}

	// Filter price history for the analysis period
	cutoffTime := time.Now().Add(-pa.config.AnalysisPeriod)
	relevantPrices := pa.filterPriceHistory(product.PriceHistory, cutoffTime)

	if len(relevantPrices) == 0 {
		pa.logger.Printf("No recent price history for product %s", product.ID)
		return &models.PriceAnalysis{
			Product:      product,
			CurrentPrice: product.CurrentPrice,
			IsGoodDeal:   false,
			AnalyzedAt:   time.Now(),
		}
	}

	// Calculate statistics
	stats := pa.calculatePriceStatistics(relevantPrices)
	
	// Calculate discount percentage from average
	discountPercent := (stats.Average - product.CurrentPrice) / stats.Average

	// Determine if it's a good deal
	isGoodDeal := discountPercent >= pa.config.DiscountThreshold

	analysis := &models.PriceAnalysis{
		Product:         product,
		AveragePrice:    stats.Average,
		MinPrice:        stats.Min,
		MaxPrice:        stats.Max,
		CurrentPrice:    product.CurrentPrice,
		DiscountPercent: discountPercent,
		IsGoodDeal:      isGoodDeal,
		AnalyzedAt:      time.Now(),
	}

	if isGoodDeal {
		pa.logger.Printf("Good deal found for %s: %.2f%% discount (current: %.2f, avg: %.2f)", 
			product.Name, discountPercent*100, product.CurrentPrice, stats.Average)
	}

	return analysis
}

// AnalyzeProducts analyzes multiple products and returns those with good deals
func (pa *PriceAnalyzer) AnalyzeProducts(ctx context.Context, products []models.Product) []*models.PriceAnalysis {
	var goodDeals []*models.PriceAnalysis

	for i, product := range products {
		select {
		case <-ctx.Done():
			pa.logger.Printf("Analysis cancelled after processing %d/%d products", i, len(products))
			return goodDeals
		default:
			analysis := pa.AnalyzeProduct(&product)
			if analysis.IsGoodDeal {
				goodDeals = append(goodDeals, analysis)
			}
		}
	}

	pa.logger.Printf("Analysis complete: found %d good deals out of %d products", len(goodDeals), len(products))
	return goodDeals
}

// filterPriceHistory filters price history to only include prices after the cutoff time
func (pa *PriceAnalyzer) filterPriceHistory(priceHistory []models.PricePoint, cutoffTime time.Time) []models.PricePoint {
	var filtered []models.PricePoint
	for _, point := range priceHistory {
		if point.Timestamp.After(cutoffTime) {
			filtered = append(filtered, point)
		}
	}
	return filtered
}

// PriceStatistics holds calculated price statistics
type PriceStatistics struct {
	Average float64
	Min     float64
	Max     float64
	Count   int
}

// calculatePriceStatistics calculates basic statistics for a set of price points
func (pa *PriceAnalyzer) calculatePriceStatistics(prices []models.PricePoint) PriceStatistics {
	if len(prices) == 0 {
		return PriceStatistics{}
	}

	var sum, min, max float64
	min = math.MaxFloat64
	max = -math.MaxFloat64

	for _, point := range prices {
		sum += point.Price
		if point.Price < min {
			min = point.Price
		}
		if point.Price > max {
			max = point.Price
		}
	}

	return PriceStatistics{
		Average: sum / float64(len(prices)),
		Min:     min,
		Max:     max,
		Count:   len(prices),
	}
}

// ValidateProduct checks if a product has sufficient data for analysis
func (pa *PriceAnalyzer) ValidateProduct(product *models.Product) error {
	if product == nil {
		return fmt.Errorf("product is nil")
	}

	if product.ID == "" {
		return fmt.Errorf("product ID is empty")
	}

	if product.Name == "" {
		return fmt.Errorf("product name is empty")
	}

	if product.CurrentPrice <= 0 {
		return fmt.Errorf("invalid current price: %.2f", product.CurrentPrice)
	}

	if len(product.PriceHistory) == 0 {
		return fmt.Errorf("no price history available")
	}

	return nil
}

// GetAnalysisReport generates a summary report of the analysis
func (pa *PriceAnalyzer) GetAnalysisReport(analyses []*models.PriceAnalysis) string {
	if len(analyses) == 0 {
		return "No good deals found in the current analysis."
	}

	report := fmt.Sprintf("Analysis Report - %d Good Deals Found:\n\n", len(analyses))
	
	for i, analysis := range analyses {
		report += fmt.Sprintf("%d. %s\n", i+1, analysis.Product.Name)
		report += fmt.Sprintf("   Store: %s\n", analysis.Product.Store)
		report += fmt.Sprintf("   Current Price: %.2f %s\n", analysis.CurrentPrice, analysis.Product.Currency)
		report += fmt.Sprintf("   Average Price: %.2f %s\n", analysis.AveragePrice, analysis.Product.Currency)
		report += fmt.Sprintf("   Discount: %.1f%%\n", analysis.DiscountPercent*100)
		report += fmt.Sprintf("   Savings: %.2f %s\n", analysis.AveragePrice-analysis.CurrentPrice, analysis.Product.Currency)
		if analysis.Product.URL != "" {
			report += fmt.Sprintf("   URL: %s\n", analysis.Product.URL)
		}
		report += "\n"
	}

	return report
}
