# Price Analyzer Agent (Kotlin rewrite)

Kotlin CLI agent that communicates with a scraper via JSON-RPC over WebSockets to analyze product price trends using OpenAI and send email alerts for significant discounts.

## Features

- **JSON-RPC Communication**: Communicates with scraper agent via WebSockets using JSON-RPC 2.0 protocol
- **Price Trend Analysis**: Analyzes 30-day price history to detect significant discounts
- **Email Notifications**: Sends HTML email alerts for good deals with detailed price information
- **Configurable Thresholds**: Customizable discount threshold (default: 10%)
- **OpenAI-driven Analysis**: Uses LLM to evaluate deal quality
- **Robust Error Handling**: Retry logic, connection recovery, and comprehensive error tracking
- **Real-time Monitoring**: Periodic analysis with configurable intervals

## Architecture

```
┌─────────────────┐    JSON-RPC/WebSocket    ┌─────────────────┐
│                 │◄─────────────────────────┤                 │
│ Analyzer Agent  │                          │ Scraper Agent   │
│                 │        getProducts       │                 │
└─────────────────┘                          └─────────────────┘
         │
         │ SMTP Email
         ▼
┌─────────────────┐
│  Email Service  │
└─────────────────┘
```

## Project Structure

```
analyzer-agent/
├── kotlin/                     # Kotlin rewrite
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/
│       ├── app/Main.kt         # Application entry point
│       ├── config/Config.kt    # Configuration management
│       ├── model/Models.kt     # Data models and JSON-RPC types
│       ├── net/WsClient.kt     # WebSocket client for JSON-RPC
│       ├── analysis/LLM.kt     # OpenAI-driven analyzer
│       ├── email/Email.kt      # Email service
│       └── agent/Agent.kt      # Orchestrator
├── config.json                 # Sample configuration
└── README.md
```

## Installation

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd analyzer-agent
   ```

2. **Install dependencies**:
   ```bash
   cd kotlin && ./gradlew build
   ```

3. **Configure the application**:
   Edit `config.json` with your settings (see Configuration section below).

4. **Build the application**:
   ```bash
   cd kotlin && ./gradlew shadowJar
   ```

## Configuration

Edit the `config.json` file:

```json
{
  "openai": {
    "api_key": "sk-...",
    "model": "gpt-4o-mini"
  },
  "email": {
    "smtp_host": "smtp.gmail.com",
    "smtp_port": 587,
    "username": "your-email@gmail.com",
    "password": "your-app-password",
    "from_email": "your-email@gmail.com",
    "to_emails": ["recipient1@example.com", "recipient2@example.com"],
    "subject": "🔥 Price Alert: Great Deal Found!"
  },
  "scraper": {
    "websocket_url": "ws://localhost:8081/ws",
    "timeout": "30s",
    "retry_delay": "5s",
    "max_retries": 3
  },
  "analysis": {
    "discount_threshold": 0.1,      # 10% discount threshold (used as hint)
    "analysis_period": "720h",      # 30 days
    "check_interval": "1h"          # Check every hour
  },
  "websocket": {
    "handshake_timeout": "10s",
    "read_buffer_size": 1024,
    "write_buffer_size": 1024
  }
}
```

### Email Configuration

For Gmail, you'll need to:
1. Enable 2-factor authentication
2. Generate an app-specific password
3. Use the app password in the `password` field

### Scraper Agent Requirements

The scraper agent must implement the following JSON-RPC method:

```json
{
  "jsonrpc": "2.0",
  "method": "getProducts",
  "params": {
    "days_back": 30,
    "stores": ["optional array of store names"],
    "category": "optional category filter"
  },
  "id": 1
}
```

Expected response:
```json
{
  "jsonrpc": "2.0",
  "result": {
    "products": [
      {
        "id": "product-123",
        "name": "Product Name",
        "url": "https://store.com/product",
        "store": "Store Name",
        "category": "Electronics",
        "current_price": 99.99,
        "currency": "USD",
        "price_history": [
          {
            "price": 109.99,
            "timestamp": "2024-01-01T10:00:00Z"
          }
        ],
        "last_updated": "2024-01-15T10:00:00Z",
        "image_url": "https://example.com/image.jpg",
        "description": "Product description"
      }
    ],
    "count": 1
  },
  "id": 1
}
```

## Usage

### Running the Application

```bash
# Using default config.json
./analyzer

# Using custom configuration file
./analyzer -config /path/to/config.json

# Override HTTP server port
./analyzer -port 9090
```

### HTTP API Endpoints

Removed. The agent now runs as a headless CLI process.

## How It Works

1. **Startup**: The agent connects to the scraper agent via WebSocket and starts periodic analysis
2. **Data Fetching**: Every hour (configurable), it requests products from the last 30 days
3. **Price Analysis**: For each product, it:
   - Filters price history to the last 30 days
   - Calculates average price
   - Compares current price to average
   - Identifies deals with >10% discount
4. **Email Alerts**: If good deals are found, sends HTML email notifications
5. **Monitoring**: Provides HTTP API for status and control

## Algorithm

The price analysis algorithm:

1. **Data Validation**: Ensures product has sufficient price history
2. **Time Filtering**: Only considers prices from the last 30 days
3. **Statistical Analysis**: Calculates average, min, and max prices
4. **Discount Calculation**: `discount_percent = (average_price - current_price) / average_price`
5. **Threshold Check**: If `discount_percent >= 0.1` (10%), it's considered a good deal
6. **Alert Generation**: Creates email with deal details and sends to configured recipients

## Monitoring

The application logs all activities and provides:

- **Status Tracking**: Current state, last check time, products analyzed, alerts sent
- **Error Logging**: Maintains history of the last 10 errors
- **HTTP Metrics**: Server status and endpoint usage
- **Connection Health**: WebSocket connection status and retry attempts

## Development

### Project Dependencies

- `github.com/gorilla/websocket`: WebSocket client implementation
- Standard library packages for HTTP, SMTP, JSON, etc.

### Testing

```bash
# Run tests
go test ./...

# Run tests with coverage
go test -cover ./...

# Build for different platforms
GOOS=linux GOARCH=amd64 go build -o analyzer-linux ./cmd/analyzer
GOOS=windows GOARCH=amd64 go build -o analyzer.exe ./cmd/analyzer
```

### Code Structure

- **Modular Design**: Separate packages for different concerns
- **Interface-based**: Easy to mock and test individual components
- **Configuration-driven**: All behavior configurable via JSON
- **Concurrent-safe**: Proper mutex usage for shared state
- **Context-aware**: Supports graceful shutdown and timeouts

## Troubleshooting

### Common Issues

1. **WebSocket Connection Failed**:
   - Check scraper agent is running
   - Verify WebSocket URL in config
   - Check firewall/network connectivity

2. **Email Not Sending**:
   - Verify SMTP settings
   - Check email credentials
   - Enable "less secure apps" for Gmail (or use app passwords)

3. **No Good Deals Found**:
   - Check if products have sufficient price history
   - Verify discount threshold setting
   - Review product data from scraper agent

### Logs

The application provides detailed logging:
- Connection events
- Analysis results
- Email send status
- Error details with stack traces

### Debug Mode

For troubleshooting, you can:
1. Lower the check interval to test more frequently
2. Use the `/analyze` endpoint to force immediate analysis
3. Check `/status` for current agent state
4. Review `/report` for detailed analysis results

## License

This project is licensed under the MIT License.
