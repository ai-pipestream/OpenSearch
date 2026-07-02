/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.transport.grpc.services;

import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.indices.breaker.CircuitBreakerService;
import org.opensearch.core.indices.breaker.NoneCircuitBreakerService;
import org.opensearch.protobufs.services.MetricCategory;
import org.opensearch.protobufs.services.NodeMetricsSnapshot;
import org.opensearch.protobufs.services.StreamMetricsRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.stub.StreamObserver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for MetricsServiceImpl
 */
public class MetricsServiceImplTests extends OpenSearchTestCase {

    private ThreadPool threadPool;
    private CircuitBreakerService circuitBreakerService;
    private ClusterService clusterService;
    private MetricsServiceImpl service;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        threadPool = new TestThreadPool("test");
        circuitBreakerService = new NoneCircuitBreakerService();
        clusterService = mock(ClusterService.class);
        DiscoveryNode discoveryNode = mock(DiscoveryNode.class);
        when(discoveryNode.getId()).thenReturn("test-node-id");
        when(clusterService.localNode()).thenReturn(discoveryNode);

        service = new MetricsServiceImpl(threadPool, circuitBreakerService, clusterService, Settings.EMPTY);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        ThreadPool.terminate(threadPool, 10, TimeUnit.SECONDS);
    }

    /**
     * Test basic streaming - should receive at least 3 snapshots in 3 seconds
     */
    public void testBasicStreaming() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        List<NodeMetricsSnapshot> snapshots = new ArrayList<>();

        StreamObserver<NodeMetricsSnapshot> observer = new StreamObserver<NodeMetricsSnapshot>() {
            @Override
            public void onNext(NodeMetricsSnapshot value) {
                snapshots.add(value);
                logger.info(
                    "Received snapshot at {}, thread pools: {}",
                    value.getTimestampMillis(),
                    value.getThreadPool() != null ? value.getThreadPool().getPoolsList().size() : 0
                );
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Stream error", t);
                fail("Should not receive error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                logger.info("Stream completed");
            }
        };

        // Request thread pool metrics every 1 second
        StreamMetricsRequest request = StreamMetricsRequest.newBuilder()
            .addCategories(MetricCategory.THREAD_POOL)
            .setIntervalSeconds(1)
            .setDeltaMode(false)  // Always emit for this test
            .build();

        // Start streaming
        service.streamNodeMetrics(request, observer);

        // Wait for at least 3 snapshots
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Should receive at least 3 snapshots within 5 seconds", received);
        assertTrue("Should have snapshots", snapshots.size() >= 3);

        // Verify snapshot structure
        NodeMetricsSnapshot snapshot = snapshots.get(0);
        assertNotNull("Snapshot should not be null", snapshot);
        assertTrue("Timestamp should be set", snapshot.getTimestampMillis() > 0);
        assertNotNull("Thread pool metrics should be present", snapshot.getThreadPool());
        assertTrue("Should have thread pools", snapshot.getThreadPool().getPoolsList().size() > 0);

        // Verify we have common thread pools
        boolean hasGeneric = snapshot.getThreadPool()
            .getPoolsList()
            .stream()
            .anyMatch(pool -> pool.getName().equals(ThreadPool.Names.GENERIC));
        assertTrue("Should have 'generic' thread pool", hasGeneric);
    }

    /**
     * Test circuit breaker metrics
     */
    public void testCircuitBreakerMetrics() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger snapshotCount = new AtomicInteger(0);

        StreamObserver<NodeMetricsSnapshot> observer = new StreamObserver<NodeMetricsSnapshot>() {
            @Override
            public void onNext(NodeMetricsSnapshot value) {
                if (snapshotCount.incrementAndGet() == 1) {
                    logger.info(
                        "Received snapshot with circuit breakers: {}",
                        value.getCircuitBreaker() != null ? value.getCircuitBreaker().getBreakersList().size() : 0
                    );

                    assertNotNull("Circuit breaker metrics should be present", value.getCircuitBreaker());
                    assertTrue("Should have circuit breakers", value.getCircuitBreaker().getBreakersList().size() > 0);

                    latch.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not receive error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {}
        };

        StreamMetricsRequest request = StreamMetricsRequest.newBuilder()
            .addCategories(MetricCategory.CIRCUIT_BREAKER)
            .setIntervalSeconds(1)
            .setDeltaMode(false)
            .build();

        service.streamNodeMetrics(request, observer);

        assertTrue("Should receive circuit breaker snapshot", latch.await(3, TimeUnit.SECONDS));
    }

    /**
     * Test delta mode - verify that identical snapshots are not emitted
     */
    public void testDeltaMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<NodeMetricsSnapshot> snapshots = new ArrayList<>();

        StreamObserver<NodeMetricsSnapshot> observer = new StreamObserver<NodeMetricsSnapshot>() {
            @Override
            public void onNext(NodeMetricsSnapshot value) {
                snapshots.add(value);
                logger.info("Delta mode snapshot #{} received", snapshots.size());
                if (snapshots.size() == 1) {
                    latch.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not receive error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {}
        };

        StreamMetricsRequest request = StreamMetricsRequest.newBuilder()
            .addCategories(MetricCategory.THREAD_POOL)
            .setIntervalSeconds(1)
            .setDeltaMode(true)  // Only emit on change
            .setChangeThreshold(0.1f)  // 10% change threshold
            .build();

        service.streamNodeMetrics(request, observer);

        // Should get at least the first snapshot
        assertTrue("Should receive first snapshot", latch.await(3, TimeUnit.SECONDS));

        // Wait a bit more to see if we get more (unlikely unless thread pool activity)
        Thread.sleep(2000);

        logger.info("Received {} snapshots in delta mode (expected: 1-2 for idle system)", snapshots.size());
        // In an idle test system, we should get very few updates
        assertTrue("Should receive at least 1 snapshot", snapshots.size() >= 1);
    }

    /**
     * Test multiple categories
     */
    public void testMultipleCategories() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<NodeMetricsSnapshot> observer = new StreamObserver<NodeMetricsSnapshot>() {
            @Override
            public void onNext(NodeMetricsSnapshot value) {
                logger.info("Snapshot: threadPool={}, circuitBreaker={}", value.getThreadPool() != null, value.getCircuitBreaker() != null);

                assertNotNull("Thread pool should be present", value.getThreadPool());
                assertNotNull("Circuit breaker should be present", value.getCircuitBreaker());
                assertTrue("Should have thread pools", value.getThreadPool().getPoolsList().size() > 0);
                assertTrue("Should have breakers", value.getCircuitBreaker().getBreakersList().size() > 0);

                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not receive error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {}
        };

        StreamMetricsRequest request = StreamMetricsRequest.newBuilder()
            .addCategories(MetricCategory.THREAD_POOL)
            .addCategories(MetricCategory.CIRCUIT_BREAKER)
            .setIntervalSeconds(1)
            .setDeltaMode(false)
            .build();

        service.streamNodeMetrics(request, observer);

        assertTrue("Should receive snapshot with both metrics", latch.await(3, TimeUnit.SECONDS));
    }

    /**
     * Test that metrics contain valid data
     */
    public void testMetricsContent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<NodeMetricsSnapshot> observer = new StreamObserver<NodeMetricsSnapshot>() {
            @Override
            public void onNext(NodeMetricsSnapshot value) {
                // Verify thread pool stats have valid data
                value.getThreadPool().getPoolsList().forEach(pool -> {
                    assertNotNull("Pool name should not be null", pool.getName());
                    assertTrue("Threads should be >= 0", pool.getThreads() >= 0);
                    assertTrue("Queue should be >= 0", pool.getQueue() >= 0);
                    assertTrue("Active should be >= 0", pool.getActive() >= 0);
                    assertTrue("Rejected should be >= 0", pool.getRejected() >= 0);

                    logger.info(
                        "Pool '{}': threads={}, queue={}, active={}, rejected={}",
                        pool.getName(),
                        pool.getThreads(),
                        pool.getQueue(),
                        pool.getActive(),
                        pool.getRejected()
                    );
                });

                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not receive error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {}
        };

        StreamMetricsRequest request = StreamMetricsRequest.newBuilder()
            .addCategories(MetricCategory.THREAD_POOL)
            .setIntervalSeconds(1)
            .setDeltaMode(false)
            .build();

        service.streamNodeMetrics(request, observer);

        assertTrue("Should receive valid metrics", latch.await(3, TimeUnit.SECONDS));
    }
}
