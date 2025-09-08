# Makefile for Price Analyzer Agent

# Variables
BINARY_NAME=analyzer
BINARY_UNIX=$(BINARY_NAME)_unix
BINARY_WINDOWS=$(BINARY_NAME).exe
MAIN_PATH=./cmd/analyzer
CONFIG_FILE=config.json

.PHONY: build clean test run deps fmt vet lint help

# Default target
all: build

# Build the application
build:
	@echo "Building $(BINARY_NAME)..."
	go build -o $(BINARY_NAME) $(MAIN_PATH)

# Build for Linux
build-linux:
	@echo "Building for Linux..."
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o $(BINARY_UNIX) $(MAIN_PATH)

# Build for Windows
build-windows:
	@echo "Building for Windows..."
	CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -o $(BINARY_WINDOWS) $(MAIN_PATH)

# Build for all platforms
build-all: build build-linux build-windows

# Install dependencies
deps:
	@echo "Installing dependencies..."
	go mod download
	go mod tidy

# Run the application
run:
	@echo "Running $(BINARY_NAME)..."
	go run $(MAIN_PATH) -config $(CONFIG_FILE)

# Run with custom config
run-config:
	@echo "Running with custom config..."
	@if [ -z "$(CONFIG)" ]; then \
		echo "Usage: make run-config CONFIG=path/to/config.json"; \
		exit 1; \
	fi
	go run $(MAIN_PATH) -config $(CONFIG)

# Run with custom port
run-port:
	@echo "Running with custom port..."
	@if [ -z "$(PORT)" ]; then \
		echo "Usage: make run-port PORT=8090"; \
		exit 1; \
	fi
	go run $(MAIN_PATH) -port $(PORT)

# Run tests
test:
	@echo "Running tests..."
	go test -v ./...

# Run tests with coverage
test-coverage:
	@echo "Running tests with coverage..."
	go test -cover ./...
	go test -coverprofile=coverage.out ./...
	go tool cover -html=coverage.out -o coverage.html

# Format code
fmt:
	@echo "Formatting code..."
	go fmt ./...

# Vet code
vet:
	@echo "Vetting code..."
	go vet ./...

# Lint code (requires golangci-lint)
lint:
	@echo "Linting code..."
	@which golangci-lint > /dev/null || (echo "golangci-lint not installed. Install with: go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest"; exit 1)
	golangci-lint run

# Clean build artifacts
clean:
	@echo "Cleaning..."
	go clean
	rm -f $(BINARY_NAME) $(BINARY_UNIX) $(BINARY_WINDOWS)
	rm -f coverage.out coverage.html

# Check code quality
check: fmt vet test
	@echo "Code quality check completed"

# Install the binary
install:
	@echo "Installing $(BINARY_NAME)..."
	go install $(MAIN_PATH)

# Generate sample config
sample-config:
	@echo "Sample configuration is already in config.json"
	@echo "Please edit the following fields:"
	@echo "  - email.username and email.password"
	@echo "  - email.to_emails"
	@echo "  - scraper.websocket_url"

# Docker build
docker-build:
	@echo "Building Docker image..."
	docker build -t analyzer-agent .

# Docker run
docker-run:
	@echo "Running Docker container..."
	docker run -p 8080:8080 -v $(PWD)/config.json:/app/config.json analyzer-agent

# Show help
help:
	@echo "Available targets:"
	@echo "  build          - Build the application"
	@echo "  build-linux    - Build for Linux"
	@echo "  build-windows  - Build for Windows"
	@echo "  build-all      - Build for all platforms"
	@echo "  deps           - Install dependencies"
	@echo "  run            - Run the application"
	@echo "  run-config     - Run with custom config (make run-config CONFIG=file.json)"
	@echo "  run-port       - Run with custom port (make run-port PORT=8090)"
	@echo "  test           - Run tests"
	@echo "  test-coverage  - Run tests with coverage"
	@echo "  fmt            - Format code"
	@echo "  vet            - Vet code"
	@echo "  lint           - Lint code (requires golangci-lint)"
	@echo "  clean          - Clean build artifacts"
	@echo "  check          - Run fmt, vet, and test"
	@echo "  install        - Install the binary"
	@echo "  sample-config  - Show sample configuration info"
	@echo "  docker-build   - Build Docker image"
	@echo "  docker-run     - Run Docker container"
	@echo "  help           - Show this help"

# Development targets
dev-setup: deps
	@echo "Setting up development environment..."
	@which golangci-lint > /dev/null || go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
	@echo "Development environment ready!"

dev-run: fmt vet
	@echo "Running in development mode..."
	go run $(MAIN_PATH) -config $(CONFIG_FILE)

# Quick build and run
quick: build
	./$(BINARY_NAME) -config $(CONFIG_FILE)
