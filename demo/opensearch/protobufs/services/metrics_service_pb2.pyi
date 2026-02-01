from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class MetricCategory(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    THREAD_POOL: _ClassVar[MetricCategory]
    CIRCUIT_BREAKER: _ClassVar[MetricCategory]
    INDEXING_PRESSURE: _ClassVar[MetricCategory]
    SEARCH_BACKPRESSURE: _ClassVar[MetricCategory]
    JVM: _ClassVar[MetricCategory]
THREAD_POOL: MetricCategory
CIRCUIT_BREAKER: MetricCategory
INDEXING_PRESSURE: MetricCategory
SEARCH_BACKPRESSURE: MetricCategory
JVM: MetricCategory

class StreamMetricsRequest(_message.Message):
    __slots__ = ("categories", "interval_seconds", "delta_mode", "change_threshold")
    CATEGORIES_FIELD_NUMBER: _ClassVar[int]
    INTERVAL_SECONDS_FIELD_NUMBER: _ClassVar[int]
    DELTA_MODE_FIELD_NUMBER: _ClassVar[int]
    CHANGE_THRESHOLD_FIELD_NUMBER: _ClassVar[int]
    categories: _containers.RepeatedScalarFieldContainer[MetricCategory]
    interval_seconds: int
    delta_mode: bool
    change_threshold: float
    def __init__(self, categories: _Optional[_Iterable[_Union[MetricCategory, str]]] = ..., interval_seconds: _Optional[int] = ..., delta_mode: bool = ..., change_threshold: _Optional[float] = ...) -> None: ...

class NodeMetricsSnapshot(_message.Message):
    __slots__ = ("timestamp_millis", "node_id", "thread_pool", "circuit_breaker", "indexing_pressure", "search_backpressure", "jvm")
    TIMESTAMP_MILLIS_FIELD_NUMBER: _ClassVar[int]
    NODE_ID_FIELD_NUMBER: _ClassVar[int]
    THREAD_POOL_FIELD_NUMBER: _ClassVar[int]
    CIRCUIT_BREAKER_FIELD_NUMBER: _ClassVar[int]
    INDEXING_PRESSURE_FIELD_NUMBER: _ClassVar[int]
    SEARCH_BACKPRESSURE_FIELD_NUMBER: _ClassVar[int]
    JVM_FIELD_NUMBER: _ClassVar[int]
    timestamp_millis: int
    node_id: str
    thread_pool: ThreadPoolMetrics
    circuit_breaker: CircuitBreakerMetrics
    indexing_pressure: IndexingPressureMetrics
    search_backpressure: SearchBackpressureMetrics
    jvm: JvmMetrics
    def __init__(self, timestamp_millis: _Optional[int] = ..., node_id: _Optional[str] = ..., thread_pool: _Optional[_Union[ThreadPoolMetrics, _Mapping]] = ..., circuit_breaker: _Optional[_Union[CircuitBreakerMetrics, _Mapping]] = ..., indexing_pressure: _Optional[_Union[IndexingPressureMetrics, _Mapping]] = ..., search_backpressure: _Optional[_Union[SearchBackpressureMetrics, _Mapping]] = ..., jvm: _Optional[_Union[JvmMetrics, _Mapping]] = ...) -> None: ...

class ThreadPoolMetrics(_message.Message):
    __slots__ = ("pools",)
    POOLS_FIELD_NUMBER: _ClassVar[int]
    pools: _containers.RepeatedCompositeFieldContainer[ThreadPoolStats]
    def __init__(self, pools: _Optional[_Iterable[_Union[ThreadPoolStats, _Mapping]]] = ...) -> None: ...

class ThreadPoolStats(_message.Message):
    __slots__ = ("name", "threads", "queue", "active", "rejected", "largest", "completed", "wait_time_nanos")
    NAME_FIELD_NUMBER: _ClassVar[int]
    THREADS_FIELD_NUMBER: _ClassVar[int]
    QUEUE_FIELD_NUMBER: _ClassVar[int]
    ACTIVE_FIELD_NUMBER: _ClassVar[int]
    REJECTED_FIELD_NUMBER: _ClassVar[int]
    LARGEST_FIELD_NUMBER: _ClassVar[int]
    COMPLETED_FIELD_NUMBER: _ClassVar[int]
    WAIT_TIME_NANOS_FIELD_NUMBER: _ClassVar[int]
    name: str
    threads: int
    queue: int
    active: int
    rejected: int
    largest: int
    completed: int
    wait_time_nanos: int
    def __init__(self, name: _Optional[str] = ..., threads: _Optional[int] = ..., queue: _Optional[int] = ..., active: _Optional[int] = ..., rejected: _Optional[int] = ..., largest: _Optional[int] = ..., completed: _Optional[int] = ..., wait_time_nanos: _Optional[int] = ...) -> None: ...

class CircuitBreakerMetrics(_message.Message):
    __slots__ = ("breakers",)
    BREAKERS_FIELD_NUMBER: _ClassVar[int]
    breakers: _containers.RepeatedCompositeFieldContainer[CircuitBreakerStats]
    def __init__(self, breakers: _Optional[_Iterable[_Union[CircuitBreakerStats, _Mapping]]] = ...) -> None: ...

class CircuitBreakerStats(_message.Message):
    __slots__ = ("name", "limit", "estimated", "overhead", "tripped")
    NAME_FIELD_NUMBER: _ClassVar[int]
    LIMIT_FIELD_NUMBER: _ClassVar[int]
    ESTIMATED_FIELD_NUMBER: _ClassVar[int]
    OVERHEAD_FIELD_NUMBER: _ClassVar[int]
    TRIPPED_FIELD_NUMBER: _ClassVar[int]
    name: str
    limit: int
    estimated: int
    overhead: float
    tripped: int
    def __init__(self, name: _Optional[str] = ..., limit: _Optional[int] = ..., estimated: _Optional[int] = ..., overhead: _Optional[float] = ..., tripped: _Optional[int] = ...) -> None: ...

class JvmMetrics(_message.Message):
    __slots__ = ("heap_used_bytes", "heap_max_bytes", "non_heap_used_bytes", "gc_count", "gc_time_millis")
    HEAP_USED_BYTES_FIELD_NUMBER: _ClassVar[int]
    HEAP_MAX_BYTES_FIELD_NUMBER: _ClassVar[int]
    NON_HEAP_USED_BYTES_FIELD_NUMBER: _ClassVar[int]
    GC_COUNT_FIELD_NUMBER: _ClassVar[int]
    GC_TIME_MILLIS_FIELD_NUMBER: _ClassVar[int]
    heap_used_bytes: int
    heap_max_bytes: int
    non_heap_used_bytes: int
    gc_count: int
    gc_time_millis: int
    def __init__(self, heap_used_bytes: _Optional[int] = ..., heap_max_bytes: _Optional[int] = ..., non_heap_used_bytes: _Optional[int] = ..., gc_count: _Optional[int] = ..., gc_time_millis: _Optional[int] = ...) -> None: ...

class IndexingPressureMetrics(_message.Message):
    __slots__ = ("current_coordinating_bytes", "current_primary_bytes", "current_replica_bytes", "total_coordinating_rejected", "total_primary_rejected", "total_replica_rejected", "memory_limit")
    CURRENT_COORDINATING_BYTES_FIELD_NUMBER: _ClassVar[int]
    CURRENT_PRIMARY_BYTES_FIELD_NUMBER: _ClassVar[int]
    CURRENT_REPLICA_BYTES_FIELD_NUMBER: _ClassVar[int]
    TOTAL_COORDINATING_REJECTED_FIELD_NUMBER: _ClassVar[int]
    TOTAL_PRIMARY_REJECTED_FIELD_NUMBER: _ClassVar[int]
    TOTAL_REPLICA_REJECTED_FIELD_NUMBER: _ClassVar[int]
    MEMORY_LIMIT_FIELD_NUMBER: _ClassVar[int]
    current_coordinating_bytes: int
    current_primary_bytes: int
    current_replica_bytes: int
    total_coordinating_rejected: int
    total_primary_rejected: int
    total_replica_rejected: int
    memory_limit: int
    def __init__(self, current_coordinating_bytes: _Optional[int] = ..., current_primary_bytes: _Optional[int] = ..., current_replica_bytes: _Optional[int] = ..., total_coordinating_rejected: _Optional[int] = ..., total_primary_rejected: _Optional[int] = ..., total_replica_rejected: _Optional[int] = ..., memory_limit: _Optional[int] = ...) -> None: ...

class SearchBackpressureMetrics(_message.Message):
    __slots__ = ("search_task_count", "search_shard_task_count", "cancellation_count", "limited_count")
    SEARCH_TASK_COUNT_FIELD_NUMBER: _ClassVar[int]
    SEARCH_SHARD_TASK_COUNT_FIELD_NUMBER: _ClassVar[int]
    CANCELLATION_COUNT_FIELD_NUMBER: _ClassVar[int]
    LIMITED_COUNT_FIELD_NUMBER: _ClassVar[int]
    search_task_count: int
    search_shard_task_count: int
    cancellation_count: int
    limited_count: int
    def __init__(self, search_task_count: _Optional[int] = ..., search_shard_task_count: _Optional[int] = ..., cancellation_count: _Optional[int] = ..., limited_count: _Optional[int] = ...) -> None: ...
