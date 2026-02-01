/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.transport.grpc.services;

import org.opensearch.core.indices.breaker.AllCircuitBreakerStats;
import org.opensearch.monitor.jvm.JvmStats;
import org.opensearch.threadpool.ThreadPoolStats;
import org.opensearch.protobufs.services.CircuitBreakerMetrics;
import org.opensearch.protobufs.services.JvmMetrics;
import org.opensearch.protobufs.services.ThreadPoolMetrics;

/**
 * Converts OpenSearch stats objects to protobuf metric messages.
 */
class MetricsConverter {

    /**
     * Converts OpenSearch ThreadPoolStats to protobuf ThreadPoolMetrics.
     */
    static ThreadPoolMetrics convertThreadPoolStats(ThreadPoolStats stats) {
        ThreadPoolMetrics.Builder builder = ThreadPoolMetrics.newBuilder();

        for (ThreadPoolStats.Stats pool : stats) {
            builder.addPools(
                org.opensearch.protobufs.services.ThreadPoolStats.newBuilder()
                    .setName(pool.getName())
                    .setThreads(pool.getThreads())
                    .setQueue(pool.getQueue())
                    .setActive(pool.getActive())
                    .setRejected(pool.getRejected())
                    .setLargest(pool.getLargest())
                    .setCompleted(pool.getCompleted())
                    .setWaitTimeNanos(pool.getWaitTimeNanos())
                    .build()
            );
        }

        return builder.build();
    }

    /**
     * Converts OpenSearch AllCircuitBreakerStats to protobuf CircuitBreakerMetrics.
     */
    static CircuitBreakerMetrics convertCircuitBreakerStats(AllCircuitBreakerStats stats) {
        CircuitBreakerMetrics.Builder builder = CircuitBreakerMetrics.newBuilder();

        for (org.opensearch.core.indices.breaker.CircuitBreakerStats breaker : stats.getAllStats()) {
            builder.addBreakers(
                org.opensearch.protobufs.services.CircuitBreakerStats.newBuilder()
                    .setName(breaker.getName())
                    .setLimit(breaker.getLimit())
                    .setEstimated(breaker.getEstimated())
                    .setOverhead(breaker.getOverhead())
                    .setTripped(breaker.getTrippedCount())
                    .build()
            );
        }

        return builder.build();
    }

    /**
     * Converts OpenSearch JvmStats to protobuf JvmMetrics.
     */
    static JvmMetrics convertJvmStats(JvmStats stats) {
        JvmMetrics.Builder builder = JvmMetrics.newBuilder();
        
        if (stats != null) {
            JvmStats.Mem mem = stats.getMem();
            if (mem != null) {
                builder.setHeapUsedBytes(mem.getHeapUsed().getBytes())
                       .setHeapMaxBytes(mem.getHeapMax().getBytes())
                       .setNonHeapUsedBytes(mem.getNonHeapUsed().getBytes());
            }

            JvmStats.GarbageCollectors gc = stats.getGc();
            if (gc != null) {
                long totalCount = 0;
                long totalTime = 0;
                for (JvmStats.GarbageCollector collector : gc.getCollectors()) {
                    totalCount += collector.getCollectionCount();
                    totalTime += collector.getCollectionTime().getMillis();
                }
                builder.setGcCount(totalCount)
                       .setGcTimeMillis(totalTime);
            }
        }
        
        return builder.build();
    }

    /**
     * Checks if thread pool stats have changed significantly.
     *
     * @param current Current thread pool metrics
     * @param previous Previous thread pool metrics
     * @param threshold Change threshold (0.0-1.0)
     * @return true if significant change detected
     */
    static boolean hasSignificantThreadPoolChange(
        ThreadPoolMetrics current,
        ThreadPoolMetrics previous,
        float threshold
    ) {
        for (org.opensearch.protobufs.services.ThreadPoolStats currentPool : current.getPoolsList()) {
            org.opensearch.protobufs.services.ThreadPoolStats previousPool = findPoolByName(previous, currentPool.getName());
            if (previousPool == null) {
                return true;  // New pool appeared
            }

            // Always emit if rejections increased
            if (currentPool.getRejected() > previousPool.getRejected()) {
                return true;
            }

            // Check queue depth change
            if (previousPool.getQueue() > 0) {
                double queueChange = Math.abs(currentPool.getQueue() - previousPool.getQueue());
                if (queueChange / previousPool.getQueue() > threshold) {
                    return true;
                }
            } else if (currentPool.getQueue() > 0) {
                return true;  // Queue went from empty to non-empty
            }

            // Check active threads change
            if (previousPool.getActive() > 0) {
                double activeChange = Math.abs(currentPool.getActive() - previousPool.getActive());
                if (activeChange / previousPool.getActive() > threshold) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if any circuit breaker has tripped since last snapshot.
     */
    static boolean hasCircuitBreakerTripped(
        CircuitBreakerMetrics current,
        CircuitBreakerMetrics previous
    ) {
        for (org.opensearch.protobufs.services.CircuitBreakerStats currentBreaker : current.getBreakersList()) {
            org.opensearch.protobufs.services.CircuitBreakerStats previousBreaker = findBreakerByName(previous, currentBreaker.getName());
            if (previousBreaker == null) {
                continue;
            }

            // Check if trip count increased
            if (currentBreaker.getTripped() > previousBreaker.getTripped()) {
                return true;
            }
        }

        return false;
    }

    private static org.opensearch.protobufs.services.ThreadPoolStats findPoolByName(ThreadPoolMetrics metrics, String name) {
        for (org.opensearch.protobufs.services.ThreadPoolStats pool : metrics.getPoolsList()) {
            if (pool.getName().equals(name)) {
                return pool;
            }
        }
        return null;
    }

    private static org.opensearch.protobufs.services.CircuitBreakerStats findBreakerByName(CircuitBreakerMetrics metrics, String name) {
        for (org.opensearch.protobufs.services.CircuitBreakerStats breaker : metrics.getBreakersList()) {
            if (breaker.getName().equals(name)) {
                return breaker;
            }
        }
        return null;
    }
}
