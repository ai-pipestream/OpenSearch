#!/usr/bin/env python3
import sys
import os
import time
import grpc

# Add current directory to path to find generated protos
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from opensearch.protobufs.services.metrics_service_pb2 import StreamMetricsRequest, MetricCategory
from opensearch.protobufs.services.metrics_service_pb2_grpc import MetricsServiceStub

def run_demo(host='localhost', port=9400):
    print(f"Connecting to OpenSearch gRPC at {host}:{port}...")
    channel = grpc.insecure_channel(f'{host}:{port}')
    stub = MetricsServiceStub(channel)

    # Request all metrics
    request = StreamMetricsRequest(
        categories=[
            MetricCategory.THREAD_POOL,
            MetricCategory.CIRCUIT_BREAKER,
            MetricCategory.JVM
        ],
        interval_seconds=2,
        delta_mode=True,
        change_threshold=0.01  # 1% change
    )

    print("Starting metrics stream (Ctrl+C to stop)...")
    print("-" * 60)
    print(f"{ 'Time':<10} | {'Type':<15} | {'Metric':<20} | {'Value':<10}")
    print("-" * 60)

    try:
        for snapshot in stub.StreamNodeMetrics(request):
            timestamp = time.strftime('%H:%M:%S', time.localtime(snapshot.timestamp_millis / 1000))
            
            # Print Thread Pool Stats
            if snapshot.HasField('thread_pool'):
                for pool in snapshot.thread_pool.pools:
                    if pool.queue > 0 or pool.rejected > 0 or pool.active > 0:
                        print(f"{timestamp:<10} | {'ThreadPool':<15} | {pool.name:<20} | A:{pool.active} Q:{pool.queue} R:{pool.rejected}")

            # Print Circuit Breaker Stats
            if snapshot.HasField('circuit_breaker'):
                for breaker in snapshot.circuit_breaker.breakers:
                     if breaker.estimated > 0:
                        print(f"{timestamp:<10} | {'Breaker':<15} | {breaker.name:<20} | {breaker.estimated // 1024}KB")

            # Print JVM Stats
            if snapshot.HasField('jvm'):
                jvm = snapshot.jvm
                heap_pct = int(jvm.heap_used_bytes / jvm.heap_max_bytes * 100) if jvm.heap_max_bytes > 0 else 0
                print(f"{timestamp:<10} | {'JVM':<15} | {'Heap Usage':<20} | {heap_pct}% ({jvm.heap_used_bytes // 1024 // 1024}MB)")
                print(f"{timestamp:<10} | {'JVM':<15} | {'GC Count':<20} | {jvm.gc_count}")

    except grpc.RpcError as e:
        print(f"RPC Error: {e.code()} - {e.details()}")
    except KeyboardInterrupt:
        print("\nStopping demo...")

if __name__ == '__main__':
    run_demo()
