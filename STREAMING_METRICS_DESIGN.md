# Real-Time Node Metrics via gRPC

## Overview
This feature adds a way to stream internal OpenSearch node metrics in real-time using gRPC. Instead of constantly polling the REST API (which can be heavy), you open a single connection and the server pushes updates to you.

## What Metrics?
We currently support:
*   **Thread Pools**: Active threads, queue depth, and rejection counts.
*   **Circuit Breakers**: Memory usage and trip counts.
*   **JVM**: Heap usage, GC counts, and execution time.

## How it Works
1.  **Efficient Streaming**: You ask for the metrics you want. The server checks them every few seconds (configurable).
2.  **Smart Updates**: To save bandwidth, the server can be configured to only send data when something significant changes (like a 10% jump in queue depth or a new rejection).
3.  **Standard Protocol**: It uses Protocol Buffers and gRPC, making it easy to consume from any language (Python, Go, Java, etc.).

## Running the Demo
We've included a ready-to-run demo to see this in action.

### Prerequisites
1.  Build and start OpenSearch with the `transport-grpc` plugin.
    ```bash
    ./gradlew run
    ```

### Starting the Client
1.  Open a new terminal.
2.  Go to the demo directory:
    ```bash
    cd OpenSearch/demo
    ```
3.  Run the demo script:
    ```bash
    ./demo.sh
    ```

This script will set up a Python environment and start printing live metrics from your local OpenSearch node.

## Configuration
You can tweak the behavior in `opensearch.yml`:
*   `grpc.metrics.streaming.enabled`: Turn it on/off (default: true).
*   `grpc.metrics.max_streams`: Limit concurrent listeners (default: 50).
*   `grpc.metrics.default_interval`: How often to check metrics in seconds (default: 5).