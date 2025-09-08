package config

import (
	"encoding/json"
	"os"
	"time"
)

// Config holds all configuration for the analyzer agent
type Config struct {
	Server     ServerConfig     `json:"server"`
	Email      EmailConfig      `json:"email"`
	Scraper    ScraperConfig    `json:"scraper"`
	Analysis   AnalysisConfig   `json:"analysis"`
	WebSocket  WebSocketConfig  `json:"websocket"`
}

// ServerConfig contains server-related configuration
type ServerConfig struct {
	Host string `json:"host"`
	Port int    `json:"port"`
}

// EmailConfig contains email notification settings
type EmailConfig struct {
	SMTPHost     string `json:"smtp_host"`
	SMTPPort     int    `json:"smtp_port"`
	Username     string `json:"username"`
	Password     string `json:"password"`
	FromEmail    string `json:"from_email"`
	ToEmails     []string `json:"to_emails"`
	Subject      string `json:"subject"`
}

// ScraperConfig contains scraper agent connection settings
type ScraperConfig struct {
	WebSocketURL string        `json:"websocket_url"`
	Timeout      time.Duration `json:"timeout"`
	RetryDelay   time.Duration `json:"retry_delay"`
	MaxRetries   int           `json:"max_retries"`
}

// AnalysisConfig contains price analysis settings
type AnalysisConfig struct {
	DiscountThreshold float64       `json:"discount_threshold"` // 10% = 0.1
	AnalysisPeriod    time.Duration `json:"analysis_period"`    // 30 days
	CheckInterval     time.Duration `json:"check_interval"`     // How often to check for deals
}

// WebSocketConfig contains WebSocket client settings
type WebSocketConfig struct {
	HandshakeTimeout time.Duration `json:"handshake_timeout"`
	ReadBufferSize   int           `json:"read_buffer_size"`
	WriteBufferSize  int           `json:"write_buffer_size"`
}

// LoadConfig loads configuration from a JSON file
func LoadConfig(filename string) (*Config, error) {
	data, err := os.ReadFile(filename)
	if err != nil {
		return nil, err
	}

	var config Config
	if err := json.Unmarshal(data, &config); err != nil {
		return nil, err
	}

	// Set defaults if not provided
	setDefaults(&config)
	
	return &config, nil
}

// setDefaults sets default values for configuration
func setDefaults(config *Config) {
	if config.Server.Host == "" {
		config.Server.Host = "localhost"
	}
	if config.Server.Port == 0 {
		config.Server.Port = 8080
	}
	if config.Scraper.Timeout == 0 {
		config.Scraper.Timeout = 30 * time.Second
	}
	if config.Scraper.RetryDelay == 0 {
		config.Scraper.RetryDelay = 5 * time.Second
	}
	if config.Scraper.MaxRetries == 0 {
		config.Scraper.MaxRetries = 3
	}
	if config.Analysis.DiscountThreshold == 0 {
		config.Analysis.DiscountThreshold = 0.1 // 10%
	}
	if config.Analysis.AnalysisPeriod == 0 {
		config.Analysis.AnalysisPeriod = 30 * 24 * time.Hour // 30 days
	}
	if config.Analysis.CheckInterval == 0 {
		config.Analysis.CheckInterval = 1 * time.Hour // Check every hour
	}
	if config.WebSocket.HandshakeTimeout == 0 {
		config.WebSocket.HandshakeTimeout = 10 * time.Second
	}
	if config.WebSocket.ReadBufferSize == 0 {
		config.WebSocket.ReadBufferSize = 1024
	}
	if config.WebSocket.WriteBufferSize == 0 {
		config.WebSocket.WriteBufferSize = 1024
	}
	if config.Email.Subject == "" {
		config.Email.Subject = "Price Alert: Great Deal Found!"
	}
}
