# Streaming Metrics API - Design

Low-risk POC for gRPC server-side streaming using operational metrics.

## Proto Definition

**Location**: `krickert/opensearch-protobufs` fork, branch `feature/streaming-metrics-api`
**File**: `protos/services/metrics_service.proto`

```protobuf
service MetricsService {
  rpc StreamNodeMetrics(StreamMetricsRequest) returns (stream NodeMetricsSnapshot) {}
}
```

## Implementation Design

### 1. Service Implementation

**File**: `modules/transport-grpc/src/main/java/org/opensearch/transport/grpc/services/MetricsServiceImpl.java`

```java
public class MetricsServiceImpl extends MetricsServiceGrpc.MetricsServiceImplBase {

    @Override
    public void streamNodeMetrics(StreamMetricsRequest request,
                                   StreamObserver<NodeMetricsSnapshot> responseObserver) {
        // Validate request
        int interval = Math.max(1, Math.min(60, request.getIntervalSeconds()));

        // Start background collector
        StreamingMetricsCollector collector = new StreamingMetricsCollector(
            request, responseObserver, nodeService
        );

        scheduledExecutor.scheduleAtFixedRate(
            collector, 0, interval, TimeUnit.SECONDS
        );

        // Handle client disconnect
        ((ServerCallStreamObserver) responseObserver).setOnCancelHandler(() -> {
            collector.stop();
        });
    }
}
```

### 2. Metrics Collector

**File**: `modules/transport-grpc/src/main/java/org/opensearch/transport/grpc/services/StreamingMetricsCollector.java`

```java
class StreamingMetricsCollector implements Runnable {
    private final StreamMetricsRequest request;
    private final StreamObserver<NodeMetricsSnapshot> observer;
    private final NodeService nodeService;
    private NodeMetricsSnapshot previousSnapshot;

    @Override
    public void run() {
        try {
            // Collect current stats
            NodeMetricsSnapshot current = buildSnapshot();

            // Delta detection
            if (shouldEmit(current, previousSnapshot)) {
                observer.onNext(current);
                previousSnapshot = current;
            }
        } catch (Exception e) {
            observer.onError(e);
        }
    }

    private NodeMetricsSnapshot buildSnapshot() {
        NodeStats nodeStats = nodeService.stats(...);

        return NodeMetricsSnapshot.newBuilder()
            .setTimestampMillis(System.currentTimeMillis())
            .setNodeId(nodeStats.getNode().getId())
            .setThreadPool(convertThreadPoolStats(nodeStats))
            .setCircuitBreaker(convertBreakerStats(nodeStats))
            .build();
    }

    private boolean shouldEmit(NodeMetricsSnapshot current, NodeMetricsSnapshot prev) {
        if (prev == null) return true;  // First snapshot
        if (!request.getDeltaMode()) return true;  // Always emit

        // Check for significant changes
        for (ThreadPoolStats pool : current.getThreadPool().getPoolsList()) {
            ThreadPoolStats prevPool = findPool(prev, pool.getName());

            // Always emit if rejections increased
            if (pool.getRejected() > prevPool.getRejected()) return true;

            // Check queue depth change
            double queueDelta = Math.abs(pool.getQueue() - prevPool.getQueue());
            double threshold = request.getChangeThreshold();
            if (prevPool.getQueue() > 0 && queueDelta / prevPool.getQueue() > threshold) {
                return true;
            }
        }

        return false;
    }
}
```

### 3. Stats Conversion

**File**: `modules/transport-grpc/src/main/java/org/opensearch/transport/grpc/services/MetricsConverter.java`

```java
class MetricsConverter {

    static ThreadPoolMetrics convertThreadPoolStats(NodeStats nodeStats) {
        ThreadPoolMetrics.Builder builder = ThreadPoolMetrics.newBuilder();

        for (ThreadPoolStats.Stats pool : nodeStats.getThreadPool()) {
            builder.addPools(ThreadPoolStats.newBuilder()
                .setName(pool.getName())
                .setThreads(pool.getThreads())
                .setQueue(pool.getQueue())
                .setActive(pool.getActive())
                .setRejected(pool.getRejected())
                .setLargest(pool.getLargest())
                .setCompleted(pool.getCompleted())
                .setWaitTimeNanos(pool.getWaitTimeNanos())
                .build());
        }

        return builder.build();
    }

    static CircuitBreakerMetrics convertBreakerStats(NodeStats nodeStats) {
        CircuitBreakerMetrics.Builder builder = CircuitBreakerMetrics.newBuilder();

        for (CircuitBreakerStats breaker : nodeStats.getBreaker().getAllStats()) {
            builder.addBreakers(CircuitBreakerStats.newBuilder()
                .setName(breaker.getName())
                .setLimit(breaker.getLimit())
                .setEstimated(breaker.getEstimated())
                .setOverhead(breaker.getOverhead())
                .setTripped(breaker.getTripped())
                .build());
        }

        return builder.build();
    }
}
```

## Integration Points

### Existing Code (No Changes)

**Stats Collection**:
- `server/src/main/java/org/opensearch/threadpool/ThreadPoolStats.java` - Read only
- `libs/core/src/main/java/org/opensearch/core/indices/breaker/AllCircuitBreakerStats.java` - Read only
- `server/src/main/java/org/opensearch/action/admin/cluster/node/stats/NodeStats.java` - Read only

**Thread Pool**:
- Reuse existing `grpc` thread pool (configured via `grpc.netty.executor_count`)
- No new thread pools needed

**Circuit Breaker**:
- Add limit for max concurrent streams (default: 50)
- Memory tracking via existing CircuitBreakerStreamObserver pattern

### New Code (~1,500 LOC)

1. `MetricsServiceImpl.java` (~200 LOC)
2. `StreamingMetricsCollector.java` (~150 LOC)
3. `MetricsConverter.java` (~100 LOC)
4. Tests (~500 LOC)
5. Proto mappings (generated)

## Configuration

**New Settings** (add to `OpenSearchGrpcServerTransportSettings.java`):

```java
public static final Setting<Boolean> GRPC_METRICS_STREAMING_ENABLED =
    Setting.boolSetting("grpc.metrics.streaming.enabled", true, ...);

public static final Setting<Integer> GRPC_METRICS_MAX_STREAMS =
    Setting.intSetting("grpc.metrics.max_streams", 50, 1, ...);

public static final Setting<Integer> GRPC_METRICS_DEFAULT_INTERVAL =
    Setting.intSetting("grpc.metrics.default_interval", 5, 1, 60, ...);
```

**opensearch.yml**:
```yaml
grpc:
  metrics:
    streaming:
      enabled: true
      max_streams: 50
      default_interval: 5
```

## Testing Strategy

**Unit Tests**:
- Delta detection logic
- Change threshold calculation
- Client cancellation handling

**Integration Tests**:
- Stream thread pool stats
- Trigger circuit breaker, verify in stream
- Concurrent clients (50 streams)

**Performance**:
- Measure CPU/memory overhead per stream
- Target: <1% CPU per stream, <10MB heap per stream

## Why This Won't Break Things

✅ **Read-only metrics** - No core search/index path changes
✅ **Existing infrastructure** - gRPC module already exists
✅ **Opt-in** - Clients explicitly request streaming
✅ **Isolated failure** - If streaming breaks, search/indexing unaffected
✅ **Resource bounded** - Max streams configurable

## Example Client

```python
import grpc
from opensearch_pb2 import StreamMetricsRequest, MetricCategory
from opensearch_pb2_grpc import MetricsServiceStub

channel = grpc.insecure_channel('localhost:9400')
stub = MetricsServiceStub(channel)

request = StreamMetricsRequest(
    categories=[MetricCategory.THREAD_POOL],
    interval_seconds=5,
    delta_mode=True,
    change_threshold=0.1
)

for snapshot in stub.StreamNodeMetrics(request):
    for pool in snapshot.thread_pool.pools:
        print(f"{pool.name}: queue={pool.queue} rejected={pool.rejected}")
```

## Next Steps

1. Build protobufs locally with Bazel
2. Generate Java stubs
3. Implement MetricsServiceImpl
4. Add tests
5. Submit PR to opensearch-protobufs
6. After proto merge, implement in OpenSearch
