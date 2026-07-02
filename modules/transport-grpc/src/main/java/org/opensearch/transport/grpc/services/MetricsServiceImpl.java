/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.transport.grpc.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.common.breaker.CircuitBreaker;
import org.opensearch.core.common.breaker.CircuitBreakingException;
import org.opensearch.core.indices.breaker.CircuitBreakerService;
import org.opensearch.monitor.jvm.JvmStats;
import org.opensearch.protobufs.services.MetricCategory;
import org.opensearch.protobufs.services.MetricsServiceGrpc;
import org.opensearch.protobufs.services.NodeMetricsSnapshot;
import org.opensearch.protobufs.services.StreamMetricsRequest;
import org.opensearch.threadpool.Scheduler.Cancellable;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.grpc.util.GrpcErrorHandler;

import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import static org.opensearch.transport.grpc.Netty4GrpcServerTransport.SETTING_GRPC_METRICS_DEFAULT_INTERVAL;
import static org.opensearch.transport.grpc.Netty4GrpcServerTransport.SETTING_GRPC_METRICS_MAX_STREAMS;

/**
 * Implementation of the gRPC MetricsService for streaming node metrics.
 * Provides server-side streaming of thread pool stats, circuit breaker stats, etc.
 */
public class MetricsServiceImpl extends MetricsServiceGrpc.MetricsServiceImplBase {
    private static final Logger logger = LogManager.getLogger(MetricsServiceImpl.class);

    private final ThreadPool threadPool;
    private final CircuitBreakerService circuitBreakerService;
    private final ClusterService clusterService;
    private final AtomicInteger activeStreams = new AtomicInteger(0);
    private final int maxConcurrentStreams;
    private final int defaultIntervalSeconds;

    /**
     * Creates a new MetricsServiceImpl.
     *
     * @param threadPool Thread pool for scheduling periodic updates and accessing thread pool stats
     * @param circuitBreakerService Circuit breaker service for memory protection and stats
     * @param clusterService Cluster service for node identity
     * @param settings Node settings for configuration
     */
    public MetricsServiceImpl(
        ThreadPool threadPool,
        CircuitBreakerService circuitBreakerService,
        ClusterService clusterService,
        Settings settings
    ) {
        if (threadPool == null) {
            throw new IllegalArgumentException("ThreadPool cannot be null");
        }
        if (circuitBreakerService == null) {
            throw new IllegalArgumentException("Circuit breaker service cannot be null");
        }
        if (clusterService == null) {
            throw new IllegalArgumentException("ClusterService cannot be null");
        }

        this.threadPool = threadPool;
        this.circuitBreakerService = circuitBreakerService;
        this.clusterService = clusterService;
        this.maxConcurrentStreams = SETTING_GRPC_METRICS_MAX_STREAMS.get(settings);
        this.defaultIntervalSeconds = SETTING_GRPC_METRICS_DEFAULT_INTERVAL.get(settings);
    }

    @Override
    public void streamNodeMetrics(StreamMetricsRequest request, StreamObserver<NodeMetricsSnapshot> responseObserver) {
        // Check concurrent stream limit
        int currentStreams = activeStreams.incrementAndGet();
        if (currentStreams > maxConcurrentStreams) {
            activeStreams.decrementAndGet();
            logger.warn("Max concurrent metric streams exceeded: {}/{}", currentStreams, maxConcurrentStreams);
            StatusRuntimeException error = GrpcErrorHandler.convertToGrpcError(
                new IllegalStateException("Max concurrent streams exceeded")
            );
            responseObserver.onError(error);
            return;
        }

        logger.info("Starting metrics stream (active streams: {})", currentStreams);

        try {
            // Validate and normalize request parameters
            int interval = request.getIntervalSeconds();
            if (interval <= 0) {
                interval = defaultIntervalSeconds;
            }
            int intervalSeconds = Math.max(1, Math.min(60, interval));

            // Create metrics collector
            StreamingMetricsCollector collector = new StreamingMetricsCollector(
                request,
                responseObserver,
                threadPool,
                circuitBreakerService,
                clusterService
            );

            // Schedule periodic collection
            Cancellable scheduledTask = threadPool.scheduleWithFixedDelay(
                collector,
                TimeValue.timeValueSeconds(intervalSeconds),
                ThreadPool.Names.GENERIC
            );

            // Handle client disconnect
            if (responseObserver instanceof ServerCallStreamObserver) {
                ((ServerCallStreamObserver<?>) responseObserver).setOnCancelHandler(() -> {
                    logger.info("Client cancelled metrics stream");
                    scheduledTask.cancel();
                    collector.cleanup();
                    activeStreams.decrementAndGet();
                });
            }

        } catch (CircuitBreakingException e) {
            activeStreams.decrementAndGet();
            logger.debug("Circuit breaker tripped for metrics stream: {}", e.getMessage());
            StatusRuntimeException grpcError = GrpcErrorHandler.convertToGrpcError(e);
            responseObserver.onError(grpcError);
        } catch (Exception e) {
            activeStreams.decrementAndGet();
            logger.error("Failed to start metrics stream", e);
            StatusRuntimeException grpcError = GrpcErrorHandler.convertToGrpcError(e);
            responseObserver.onError(grpcError);
        }
    }

    /**
     * Background collector that periodically collects and emits metrics.
     */
    private static class StreamingMetricsCollector implements Runnable {
        private final StreamMetricsRequest request;
        private final StreamObserver<NodeMetricsSnapshot> responseObserver;
        private final ThreadPool threadPool;
        private final CircuitBreakerService circuitBreakerService;
        private final ClusterService clusterService;
        private volatile NodeMetricsSnapshot previousSnapshot;
        private volatile boolean stopped = false;

        StreamingMetricsCollector(
            StreamMetricsRequest request,
            StreamObserver<NodeMetricsSnapshot> responseObserver,
            ThreadPool threadPool,
            CircuitBreakerService circuitBreakerService,
            ClusterService clusterService
        ) {
            this.request = request;
            this.responseObserver = responseObserver;
            this.threadPool = threadPool;
            this.circuitBreakerService = circuitBreakerService;
            this.clusterService = clusterService;
        }

        @Override
        public void run() {
            if (stopped) {
                return;
            }

            try {
                // Collect current stats
                NodeMetricsSnapshot current = buildSnapshot();

                // Delta detection
                if (shouldEmit(current)) {
                    CircuitBreaker breaker = circuitBreakerService.getBreaker(CircuitBreaker.IN_FLIGHT_REQUESTS);
                    int snapshotSize = current.getSerializedSize();

                    try {
                        breaker.addEstimateBytesAndMaybeBreak(snapshotSize, "<grpc_metrics_stream>");
                        responseObserver.onNext(current);
                        previousSnapshot = current;
                    } finally {
                        breaker.addWithoutBreaking(-snapshotSize);
                    }
                }
            } catch (Exception e) {
                logger.error("Error collecting metrics", e);
                responseObserver.onError(GrpcErrorHandler.convertToGrpcError(e));
                stopped = true;
            }
        }

        private NodeMetricsSnapshot buildSnapshot() {
            // Determine which stats to collect based on requested categories
            boolean includeThreadPool = request.getCategoriesList().contains(MetricCategory.THREAD_POOL);
            boolean includeCircuitBreaker = request.getCategoriesList().contains(MetricCategory.CIRCUIT_BREAKER);
            boolean includeJvm = request.getCategoriesList().contains(MetricCategory.JVM);

            // Build proto message
            NodeMetricsSnapshot.Builder builder = NodeMetricsSnapshot.newBuilder()
                .setTimestampMillis(System.currentTimeMillis())
                .setNodeId(clusterService.localNode().getId());

            // Collect thread pool stats directly
            if (includeThreadPool) {
                org.opensearch.threadpool.ThreadPoolStats threadPoolStats = threadPool.stats();
                builder.setThreadPool(MetricsConverter.convertThreadPoolStats(threadPoolStats));
            }

            // Collect circuit breaker stats directly
            if (includeCircuitBreaker) {
                builder.setCircuitBreaker(MetricsConverter.convertCircuitBreakerStats(circuitBreakerService.stats()));
            }

            // Collect JVM stats
            if (includeJvm) {
                builder.setJvm(MetricsConverter.convertJvmStats(JvmStats.jvmStats()));
            }

            return builder.build();
        }

        private boolean shouldEmit(NodeMetricsSnapshot current) {
            // Always emit first snapshot
            if (previousSnapshot == null) {
                return true;
            }

            // If delta mode is off, always emit
            if (!request.getDeltaMode()) {
                return true;
            }

            // Check if thread pool stats changed significantly
            if (current.getThreadPool() != null && previousSnapshot.getThreadPool() != null) {
                if (MetricsConverter.hasSignificantThreadPoolChange(
                    current.getThreadPool(),
                    previousSnapshot.getThreadPool(),
                    request.getChangeThreshold()
                )) {
                    return true;
                }
            }

            // Check if circuit breaker stats changed
            if (current.getCircuitBreaker() != null && previousSnapshot.getCircuitBreaker() != null) {
                if (MetricsConverter.hasCircuitBreakerTripped(current.getCircuitBreaker(), previousSnapshot.getCircuitBreaker())) {
                    return true;
                }
            }

            return false;
        }

        void cleanup() {
            stopped = true;
        }
    }
}
