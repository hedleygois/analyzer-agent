package models

import (
	"time"
)

// Product represents a scraped product with price history
type Product struct {
	ID          string        `json:"id"`
	Name        string        `json:"name"`
	URL         string        `json:"url"`
	Store       string        `json:"store"`
	Category    string        `json:"category"`
	CurrentPrice float64      `json:"current_price"`
	Currency    string        `json:"currency"`
	PriceHistory []PricePoint `json:"price_history"`
	LastUpdated time.Time     `json:"last_updated"`
	ImageURL    string        `json:"image_url,omitempty"`
	Description string        `json:"description,omitempty"`
}

// PricePoint represents a single price observation
type PricePoint struct {
	Price     float64   `json:"price"`
	Timestamp time.Time `json:"timestamp"`
}

// PriceAnalysis contains the result of price trend analysis
type PriceAnalysis struct {
	Product         *Product  `json:"product"`
	AveragePrice    float64   `json:"average_price"`
	MinPrice        float64   `json:"min_price"`
	MaxPrice        float64   `json:"max_price"`
	CurrentPrice    float64   `json:"current_price"`
	DiscountPercent float64   `json:"discount_percent"`
	IsGoodDeal      bool      `json:"is_good_deal"`
	AnalyzedAt      time.Time `json:"analyzed_at"`
}

// JSONRPCRequest represents a JSON-RPC 2.0 request
type JSONRPCRequest struct {
	JSONRPC string      `json:"jsonrpc"`
	Method  string      `json:"method"`
	Params  interface{} `json:"params,omitempty"`
	ID      interface{} `json:"id"`
}

// JSONRPCResponse represents a JSON-RPC 2.0 response
type JSONRPCResponse struct {
	JSONRPC string      `json:"jsonrpc"`
	Result  interface{} `json:"result,omitempty"`
	Error   *JSONRPCError `json:"error,omitempty"`
	ID      interface{} `json:"id"`
}

// JSONRPCError represents a JSON-RPC error
type JSONRPCError struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

// GetProductsRequest represents the request to get products from scraper
type GetProductsRequest struct {
	DaysBack int      `json:"days_back"`
	Stores   []string `json:"stores,omitempty"`
	Category string   `json:"category,omitempty"`
}

// GetProductsResponse represents the response from scraper
type GetProductsResponse struct {
	Products []Product `json:"products"`
	Count    int       `json:"count"`
}

// EmailAlert represents an email notification about a good deal
type EmailAlert struct {
	Product   *Product       `json:"product"`
	Analysis  *PriceAnalysis `json:"analysis"`
	Subject   string         `json:"subject"`
	Body      string         `json:"body"`
	SentAt    time.Time      `json:"sent_at"`
}

// AgentStatus represents the current status of the analyzer agent
type AgentStatus struct {
	Status          string    `json:"status"`
	LastCheck       time.Time `json:"last_check"`
	ProductsChecked int       `json:"products_checked"`
	AlertsSent      int       `json:"alerts_sent"`
	Errors          []string  `json:"errors,omitempty"`
}
