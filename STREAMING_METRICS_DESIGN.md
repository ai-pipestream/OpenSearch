# Streaming Node Metrics (gRPC)

## What is this?
This is a gRPC service that pushes node metrics to clients over a long-lived connection. It's an alternative to polling the `/_nodes/stats` REST API.

## Available Metrics
*   **Thread Pools**: Threads (active, queue, rejected, completed).
*   **Circuit Breakers**: Current estimated memory usage and trip counts.
*   **JVM**: Heap usage and Garbage Collection stats.

## How it works
1.  **Subscriptions**: You request specific categories of metrics (e.g., just JVM, or everything).
2.  **Push Interval**: You set how often the server checks for updates (default is 5 seconds).
3.  **Change Detection**: The server can be told to only send a message when values actually change. You can set a threshold (like 10%) so minor fluctuations don't trigger a push.

## Running the Demo
We've included a client script to test the service.

### 1. Start OpenSearch
Build and start the node with the gRPC plugin:
```bash
./gradlew run
```

### 2. Run the Client
Open a new terminal and run the demo script:
```bash
cd OpenSearch/demo
./demo.sh
```
This script sets up a Python environment and prints live metrics from the server.

## Configuration
Settings in `opensearch.yml`:
*   `grpc.metrics.streaming.enabled`: Enable the service (default: true).
*   `grpc.metrics.max_streams`: Max concurrent client connections (default: 50).
*   `grpc.metrics.default_interval`: Default check interval in seconds (default: 5).
