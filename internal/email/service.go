package email

import (
	"bytes"
	"fmt"
	"html/template"
	"log"
	"net/smtp"

	"github.com/hedleyluna/analyzer-agent/internal/models"
	"github.com/hedleyluna/analyzer-agent/pkg/config"
)

// Service handles email notifications
type Service struct {
	config *config.EmailConfig
	logger *log.Logger
	auth   smtp.Auth
}

// NewService creates a new email service
func NewService(config *config.EmailConfig, logger *log.Logger) *Service {
	var auth smtp.Auth
	if config.Username != "" && config.Password != "" {
		auth = smtp.PlainAuth("", config.Username, config.Password, config.SMTPHost)
	}

	return &Service{
		config: config,
		logger: logger,
		auth:   auth,
	}
}

// SendPriceAlert sends an email alert for a good deal
func (s *Service) SendPriceAlert(analysis *models.PriceAnalysis) error {
	if len(s.config.ToEmails) == 0 {
		return fmt.Errorf("no recipient email addresses configured")
	}

	subject := s.generateSubject(analysis)
	body, err := s.generateBody(analysis)
	if err != nil {
		return fmt.Errorf("failed to generate email body: %w", err)
	}

	for _, toEmail := range s.config.ToEmails {
		if err := s.sendEmail(toEmail, subject, body); err != nil {
			s.logger.Printf("Failed to send email to %s: %v", toEmail, err)
			return err
		}
		s.logger.Printf("Price alert sent to %s for product: %s", toEmail, analysis.Product.Name)
	}

	return nil
}

// SendMultiplePriceAlerts sends email alerts for multiple good deals
func (s *Service) SendMultiplePriceAlerts(analyses []*models.PriceAnalysis) error {
	if len(analyses) == 0 {
		return nil
	}

	if len(s.config.ToEmails) == 0 {
		return fmt.Errorf("no recipient email addresses configured")
	}

	subject := s.generateMultipleAlertsSubject(len(analyses))
	body, err := s.generateMultipleAlertsBody(analyses)
	if err != nil {
		return fmt.Errorf("failed to generate email body: %w", err)
	}

	for _, toEmail := range s.config.ToEmails {
		if err := s.sendEmail(toEmail, subject, body); err != nil {
			s.logger.Printf("Failed to send multiple alerts email to %s: %v", toEmail, err)
			return err
		}
		s.logger.Printf("Multiple price alerts sent to %s for %d products", toEmail, len(analyses))
	}

	return nil
}

// sendEmail sends an email using SMTP
func (s *Service) sendEmail(to, subject, body string) error {
	msg := s.buildMessage(s.config.FromEmail, to, subject, body)

	addr := fmt.Sprintf("%s:%d", s.config.SMTPHost, s.config.SMTPPort)
	
	if s.auth != nil {
		return smtp.SendMail(addr, s.auth, s.config.FromEmail, []string{to}, []byte(msg))
	}
	
	// For testing or local SMTP servers without auth
	return smtp.SendMail(addr, nil, s.config.FromEmail, []string{to}, []byte(msg))
}

// buildMessage builds the email message with headers
func (s *Service) buildMessage(from, to, subject, body string) string {
	var msg bytes.Buffer
	
	msg.WriteString(fmt.Sprintf("From: %s\r\n", from))
	msg.WriteString(fmt.Sprintf("To: %s\r\n", to))
	msg.WriteString(fmt.Sprintf("Subject: %s\r\n", subject))
	msg.WriteString("MIME-Version: 1.0\r\n")
	msg.WriteString("Content-Type: text/html; charset=UTF-8\r\n")
	msg.WriteString("\r\n")
	msg.WriteString(body)
	
	return msg.String()
}

// generateSubject generates a subject line for a single product alert
func (s *Service) generateSubject(analysis *models.PriceAnalysis) string {
	discountPercent := analysis.DiscountPercent * 100
	return fmt.Sprintf("🔥 %.0f%% OFF - %s - Now %.2f %s!", 
		discountPercent, 
		analysis.Product.Name, 
		analysis.CurrentPrice,
		analysis.Product.Currency)
}

// generateMultipleAlertsSubject generates a subject line for multiple alerts
func (s *Service) generateMultipleAlertsSubject(count int) string {
	return fmt.Sprintf("🛍️ %d Great Deals Found - Price Alerts", count)
}

// generateBody generates HTML email body for a single product alert
func (s *Service) generateBody(analysis *models.PriceAnalysis) (string, error) {
	tmpl := `
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }
        .container { max-width: 600px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 10px; }
        .header { text-align: center; color: #2c3e50; border-bottom: 2px solid #e74c3c; padding-bottom: 20px; margin-bottom: 20px; }
        .deal-badge { background-color: #e74c3c; color: white; padding: 10px 20px; border-radius: 25px; font-size: 18px; font-weight: bold; }
        .product-info { margin: 20px 0; }
        .product-name { font-size: 24px; font-weight: bold; color: #2c3e50; margin-bottom: 10px; }
        .store { color: #7f8c8d; font-size: 16px; margin-bottom: 20px; }
        .price-info { background-color: #ecf0f1; padding: 20px; border-radius: 10px; margin: 20px 0; }
        .current-price { font-size: 28px; color: #27ae60; font-weight: bold; }
        .original-price { font-size: 18px; color: #7f8c8d; text-decoration: line-through; }
        .savings { font-size: 20px; color: #e74c3c; font-weight: bold; }
        .button { background-color: #3498db; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 20px 0; }
        .footer { text-align: center; color: #7f8c8d; font-size: 12px; margin-top: 30px; border-top: 1px solid #bdc3c7; padding-top: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="deal-badge">{{printf "%.0f%%" .DiscountPercent}} OFF!</div>
            <h1>Great Deal Alert</h1>
        </div>
        
        <div class="product-info">
            <div class="product-name">{{.Product.Name}}</div>
            <div class="store">Available at {{.Product.Store}}</div>
            
            <div class="price-info">
                <div class="current-price">{{printf "%.2f %s" .CurrentPrice .Product.Currency}}</div>
                <div class="original-price">Regular: {{printf "%.2f %s" .AveragePrice .Product.Currency}}</div>
                <div class="savings">You save: {{printf "%.2f %s" .Savings .Product.Currency}}</div>
            </div>
            
            {{if .Product.URL}}
            <a href="{{.Product.URL}}" class="button">View Product</a>
            {{end}}
        </div>
        
        <div class="footer">
            <p>This alert was generated on {{.AnalyzedAt.Format "January 2, 2006 at 3:04 PM"}}</p>
            <p>Price analysis based on {{.AnalysisPeriod}} of price history</p>
        </div>
    </div>
</body>
</html>`

	data := struct {
		*models.PriceAnalysis
		DiscountPercent  float64
		Savings          float64
		AnalysisPeriod   string
	}{
		PriceAnalysis:   analysis,
		DiscountPercent: analysis.DiscountPercent * 100,
		Savings:         analysis.AveragePrice - analysis.CurrentPrice,
		AnalysisPeriod:  "30 days", // You might want to make this configurable
	}

	t, err := template.New("email").Parse(tmpl)
	if err != nil {
		return "", err
	}

	var buf bytes.Buffer
	if err := t.Execute(&buf, data); err != nil {
		return "", err
	}

	return buf.String(), nil
}

// generateMultipleAlertsBody generates HTML email body for multiple product alerts
func (s *Service) generateMultipleAlertsBody(analyses []*models.PriceAnalysis) (string, error) {
	tmpl := `
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }
        .container { max-width: 800px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 10px; }
        .header { text-align: center; color: #2c3e50; border-bottom: 2px solid #e74c3c; padding-bottom: 20px; margin-bottom: 20px; }
        .deal-count { background-color: #e74c3c; color: white; padding: 10px 20px; border-radius: 25px; font-size: 18px; font-weight: bold; }
        .product { border: 1px solid #bdc3c7; border-radius: 10px; margin: 20px 0; padding: 20px; }
        .product-name { font-size: 20px; font-weight: bold; color: #2c3e50; margin-bottom: 5px; }
        .store { color: #7f8c8d; font-size: 14px; margin-bottom: 10px; }
        .price-row { display: flex; justify-content: space-between; align-items: center; }
        .current-price { font-size: 24px; color: #27ae60; font-weight: bold; }
        .discount-badge { background-color: #e74c3c; color: white; padding: 5px 15px; border-radius: 15px; font-size: 14px; }
        .savings { color: #e74c3c; font-weight: bold; }
        .view-button { background-color: #3498db; color: white; padding: 8px 16px; text-decoration: none; border-radius: 3px; font-size: 12px; }
        .footer { text-align: center; color: #7f8c8d; font-size: 12px; margin-top: 30px; border-top: 1px solid #bdc3c7; padding-top: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="deal-count">{{len .}} Great Deals</div>
            <h1>Price Alert Summary</h1>
        </div>
        
        {{range $index, $analysis := .}}
        <div class="product">
            <div class="product-name">{{$analysis.Product.Name}}</div>
            <div class="store">{{$analysis.Product.Store}}</div>
            
            <div class="price-row">
                <div>
                    <div class="current-price">{{printf "%.2f %s" $analysis.CurrentPrice $analysis.Product.Currency}}</div>
                    <div class="savings">Save {{printf "%.2f %s" (printf "%.2f" (printf "%.2f" ($analysis.AveragePrice) | parseFloat) (printf "%.2f" $analysis.CurrentPrice | parseFloat) | subtract) $analysis.Product.Currency}}</div>
                </div>
                <div>
                    <div class="discount-badge">{{printf "%.0f%%" ($analysis.DiscountPercent | multiply 100)}} OFF</div>
                    {{if $analysis.Product.URL}}<br><a href="{{$analysis.Product.URL}}" class="view-button">View Product</a>{{end}}
                </div>
            </div>
        </div>
        {{end}}
        
        <div class="footer">
            <p>These alerts were generated on {{(index . 0).AnalyzedAt.Format "January 2, 2006 at 3:04 PM"}}</p>
            <p>Happy shopping!</p>
        </div>
    </div>
</body>
</html>`

	funcMap := template.FuncMap{
		"multiply": func(a float64, b float64) float64 { return a * b },
		"subtract": func(a, b float64) float64 { return a - b },
		"parseFloat": func(s string) float64 {
			// Simple helper for template
			return 0.0 // This is a simplified version
		},
	}

	t, err := template.New("multipleEmail").Funcs(funcMap).Parse(tmpl)
	if err != nil {
		return "", err
	}

	var buf bytes.Buffer
	if err := t.Execute(&buf, analyses); err != nil {
		return "", err
	}

	return buf.String(), nil
}

// TestConnection tests the email service configuration
func (s *Service) TestConnection() error {
	if s.config.FromEmail == "" {
		return fmt.Errorf("from email not configured")
	}

	if len(s.config.ToEmails) == 0 {
		return fmt.Errorf("no recipient emails configured")
	}

	if s.config.SMTPHost == "" {
		return fmt.Errorf("SMTP host not configured")
	}

	// Try to connect to SMTP server
	addr := fmt.Sprintf("%s:%d", s.config.SMTPHost, s.config.SMTPPort)
	conn, err := smtp.Dial(addr)
	if err != nil {
		return fmt.Errorf("failed to connect to SMTP server: %w", err)
	}
	defer conn.Quit()

	s.logger.Println("Email service connection test successful")
	return nil
}
