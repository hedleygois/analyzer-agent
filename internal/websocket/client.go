package websocket

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/hedleyluna/analyzer-agent/internal/models"
	"github.com/hedleyluna/analyzer-agent/pkg/config"
)

// Client represents a WebSocket client for JSON-RPC communication
type Client struct {
	conn      *websocket.Conn
	config    *config.ScraperConfig
	wsConfig  *config.WebSocketConfig
	mu        sync.Mutex
	requestID int64
	responses map[interface{}]chan *models.JSONRPCResponse
	done      chan struct{}
	logger    *log.Logger
}

// NewClient creates a new WebSocket client
func NewClient(scraperConfig *config.ScraperConfig, wsConfig *config.WebSocketConfig, logger *log.Logger) *Client {
	return &Client{
		config:    scraperConfig,
		wsConfig:  wsConfig,
		responses: make(map[interface{}]chan *models.JSONRPCResponse),
		done:      make(chan struct{}),
		logger:    logger,
	}
}

// Connect establishes a WebSocket connection
func (client *Client) Connect() error {
	client.mu.Lock()
	defer client.mu.Unlock()

	if client.conn != nil {
		return fmt.Errorf("already connected")
	}

	dialer := websocket.Dialer{
		HandshakeTimeout: client.wsConfig.HandshakeTimeout,
		ReadBufferSize:   client.wsConfig.ReadBufferSize,
		WriteBufferSize:  client.wsConfig.WriteBufferSize,
	}

	conn, _, err := dialer.Dial(client.config.WebSocketURL, http.Header{})
	if err != nil {
		return fmt.Errorf("failed to connect to WebSocket: %w", err)
	}

	client.conn = conn
	client.logger.Printf("Connected to WebSocket: %s", client.config.WebSocketURL)

	go client.handleMessages()

	return nil
}

func (client *Client) Disconnect() error {
	client.mu.Lock()
	defer client.mu.Unlock()

	if client.conn == nil {
		return nil
	}

	close(client.done)
	err := client.conn.Close()
	client.conn = nil
	client.logger.Println("Disconnected from WebSocket")

	return err
}

// Call makes a JSON-RPC call and waits for the response
func (client *Client) Call(ctx context.Context, method string, params any) (*models.JSONRPCResponse, error) {
	client.mu.Lock()
	if client.conn == nil {
		client.mu.Unlock()
		return nil, fmt.Errorf("not connected")
	}

	// Generate request ID
	client.requestID++
	requestID := client.requestID

	// Create response channel
	responseChan := make(chan *models.JSONRPCResponse, 1)
	client.responses[requestID] = responseChan
	client.mu.Unlock()

	// Clean up response channel
	defer func() {
		client.mu.Lock()
		delete(client.responses, requestID)
		client.mu.Unlock()
		close(responseChan)
	}()

	// Create JSON-RPC request
	request := &models.JSONRPCRequest{
		JSONRPC: "2.0",
		Method:  method,
		Params:  params,
		ID:      requestID,
	}

	// Send request
	if err := client.sendRequest(request); err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}

	// Wait for response or timeout
	select {
	case response := <-responseChan:
		return response, nil
	case <-ctx.Done():
		return nil, ctx.Err()
	case <-time.After(client.config.Timeout):
		return nil, fmt.Errorf("request timeout")
	}
}

// GetProducts requests products from the scraper agent
func (client *Client) GetProducts(ctx context.Context, daysBack int) (*models.GetProductsResponse, error) {
	params := &models.GetProductsRequest{
		DaysBack: daysBack,
	}

	response, err := client.Call(ctx, "getProducts", params)
	if err != nil {
		return nil, fmt.Errorf("failed to get products: %w", err)
	}

	if response.Error != nil {
		return nil, fmt.Errorf("JSON-RPC error: %s", response.Error.Message)
	}

	// Parse the result
	resultBytes, err := json.Marshal(response.Result)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal result: %w", err)
	}

	var productsResponse models.GetProductsResponse
	if err := json.Unmarshal(resultBytes, &productsResponse); err != nil {
		return nil, fmt.Errorf("failed to unmarshal products response: %w", err)
	}

	return &productsResponse, nil
}

// sendRequest sends a JSON-RPC request over the WebSocket
func (client *Client) sendRequest(request *models.JSONRPCRequest) error {
	client.mu.Lock()
	defer client.mu.Unlock()

	if client.conn == nil {
		return fmt.Errorf("not connected")
	}

	return client.conn.WriteJSON(request)
}

// handleMessages handles incoming messages from the WebSocket
func (client *Client) handleMessages() {
	defer func() {
		if r := recover(); r != nil {
			client.logger.Printf("Message handler panic: %v", r)
		}
	}()

	for {
		select {
		case <-client.done:
			return
		default:
			var response models.JSONRPCResponse
			err := client.conn.ReadJSON(&response)
			if err != nil {
				if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
					client.logger.Printf("WebSocket read error: %v", err)
				}
				return
			}

			client.handleResponse(&response)
		}
	}
}

// handleResponse handles a JSON-RPC response
func (client *Client) handleResponse(response *models.JSONRPCResponse) {
	client.mu.Lock()
	responseChan, exists := client.responses[response.ID]
	client.mu.Unlock()

	if !exists {
		client.logger.Printf("Received response for unknown request ID: %v", response.ID)
		return
	}

	select {
	case responseChan <- response:
	default:
		client.logger.Printf("Response channel full for request ID: %v", response.ID)
	}
}

// ConnectWithRetry connects to WebSocket with retry logic
func (client *Client) ConnectWithRetry(ctx context.Context) error {
	var lastErr error

	for i := 0; i < client.config.MaxRetries; i++ {
		if err := client.Connect(); err != nil {
			lastErr = err
			client.logger.Printf("Connection attempt %d failed: %v", i+1, err)

			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(client.config.RetryDelay):
				continue
			}
		}
		return nil
	}

	return fmt.Errorf("failed to connect after %d attempts: %w", client.config.MaxRetries, lastErr)
}
