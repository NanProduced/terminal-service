package com.colorlight.terminal.infrastructure.monitor;

/**
 * Metrics常量定义类
 * 统一管理所有监控指标的字符串常量，避免魔法值分散在代码中
 *
 * @author Nan
 */
public final class MetricsConstant {

    // ============== 指标名称常量 ==============

    /**
     * 系统核心指标名称
     */
    public static final String TERMINAL_SYSTEM_METRICS = "terminal_system_metrics";

    /**
     * 线程池详细指标名称
     */
    public static final String TERMINAL_THREADPOOL_METRICS = "terminal_threadpool_metrics";

    /**
     * 分片详细指标名称
     */
    public static final String TERMINAL_SHARD_METRICS = "terminal_shard_metrics";

    /**
     * 协议版本指标名称
     */
    public static final String TERMINAL_PROTOCOL_METRICS = "terminal_protocol_metrics";

    /**
     * EventLoop性能指标名称
     */
    public static final String TERMINAL_EVENTLOOP_METRICS = "terminal_eventloop_metrics";

    /**
     * WebSocket消息计数器名称
     */
    public static final String WEBSOCKET_MSG_COUNT_METRICS = "terminal_websocket_msg_count_metrics";

    /**
     * EventLoop告警计数器名称
     */
    public static final String TERMINAL_EVENTLOOP_WARNINGS_TOTAL = "terminal_eventloop_warnings_total";

    /**
     * EventLoop严重告警计数器名称
     */
    public static final String TERMINAL_EVENTLOOP_CRITICALS_TOTAL = "terminal_eventloop_criticals_total";

    // ============== 指标描述常量 ==============

    /**
     * 系统核心指标描述
     */
    public static final String SYSTEM_METRICS_DESC = "系统核心指标";

    /**
     * 线程池详细指标描述
     */
    public static final String THREADPOOL_METRICS_DESC = "线程池详细指标";

    /**
     * 分片详细指标描述
     */
    public static final String SHARD_METRICS_DESC = "分片详细指标";

    /**
     * 协议版本连接数描述
     */
    public static final String PROTOCOL_METRICS_DESC = "协议版本连接数";

    /**
     * EventLoop性能指标描述
     */
    public static final String EVENTLOOP_METRICS_DESC = "EventLoop性能指标";

    /**
     * EventLoop告警总数描述
     */
    public static final String EVENTLOOP_WARNINGS_DESC = "EventLoop告警总数";

    /**
     * EventLoop严重告警总数描述
     */
    public static final String EVENTLOOP_CRITICALS_DESC = "EventLoop严重告警总数";

    /**
     * WebSocket消息统计描述
     */
    public static final String WEBSOCKET_MSG_DESC = "Websocket消息统计";

    // ============== 标签类型常量 ==============

    /**
     * 系统指标类型
     */
    public static final class SystemType {
        public static final String WEBSOCKET_CONNECTIONS = "websocket_connections";
        public static final String ONLINE_DEVICES = "online_devices";
        public static final String WEBSOCKET_RATIO = "websocket_ratio";

        private SystemType() {
        }
    }

    /**
     * 线程池指标类型
     */
    public static final class ThreadPoolType {
        public static final String UTILIZATION = "utilization";
        public static final String ACTIVE_THREADS = "active_threads";
        public static final String QUEUE_SIZE = "queue_size";
        public static final String CORE_THREADS = "core_threads";
        public static final String MAX_THREADS = "max_threads";
        public static final String CURRENT_THREADS = "current_threads";
        public static final String TOTAL_TASKS = "total_tasks";
        public static final String COMPLETED_TASKS = "completed_tasks";
        public static final String QUEUE_CAPACITY = "queue_capacity";
        public static final String QUEUE_UTILIZATION = "queue_utilization";

        private ThreadPoolType() {
        }
    }

    /**
     * 分片指标类型
     */
    public static final class ShardType {
        public static final String CONNECTIONS = "connections";
        public static final String BALANCE = "balance";

        private ShardType() {
        }
    }

    /**
     * 协议版本指标类型
     */
    public static final class ProtocolType {
        public static final String CONNECTIONS = "connections";

        private ProtocolType() {
        }
    }

    /**
     * EventLoop指标类型
     */
    public static final class EventLoopType {
        public static final String PENDING_TASKS = "pending_tasks";

        private EventLoopType() {
        }
    }

    /**
     * WebSocket消息计数器类型
     */
    public static final class WebsocketMsgCountType {
        public static final String WEBSOCKET_MSG_SENT = "websocket_msg_sent";
        public static final String WEBSOCKET_MSG_RECEIVED = "websocket_msg_received";
        public static final String WEBSOCKET_MSG_ERROR = "websocket_msg_error";

        private WebsocketMsgCountType() {
        }
    }

    /**
     * 告警级别
     */
    public static final class AlertLevel {
        public static final String WARNING = "warning";
        public static final String CRITICAL = "critical";

        private AlertLevel() {
        }
    }

    // ============== 线程池名称常量 ==============

    /**
     * 调度器线程池名称
     */
    public static final String DEVICE_SCHEDULER_POOL = "devicescheduler";

    // ============== 协议版本常量 ==============

    /**
     * 支持的协议版本
     */
    public static final class ProtocolVersions {
        public static final String V1_0 = "v1.0";
        public static final String V1_1 = "v1.1";
        static final String[] ACTUAL_VERSIONS = {V1_0, V1_1};

        private ProtocolVersions() {
        }
    }

    // ============== 日志标识常量 ==============

    /**
     * 日志标识前缀
     */
    public static final class LogTag {
        public static final String OPTIMIZED = "[优化]";
        public static final String CONNECTION = "[连接]";
        public static final String DEVICE = "[设备]";
        public static final String WEBSOCKET = "[WebSocket]";
        public static final String THREADPOOL = "[线程池]";
        public static final String SCHEDULER = "[调度器]";
        public static final String EVENTLOOP = "[EventLoop]";
        public static final String SUMMARY = "[摘要]";

        private LogTag() {
        }
    }

    // ============== 标签Key常量 ==============

    /**
     * 通用标签键
     */
    public static final class TagKey {
        public static final String TYPE = "type";
        public static final String POOL = "pool";
        public static final String SHARD_ID = "shard_id";
        public static final String VERSION = "version";
        public static final String LEVEL = "level";

        private TagKey() {
        }
    }

    // ============== 默认值常量 ==============

    /**
     * 默认线程池名称前缀
     */
    public static final String DEFAULT_POOL_PREFIX = "pool-";

    /**
     * 线程名前缀分隔符
     */
    public static final String THREAD_NAME_SEPARATOR = "-";

    /**
     * 协议版本前缀
     */
    public static final String PROTOCOL_VERSION_PREFIX = "v";

    /**
     * 分片数量
     */
    public static final int SHARD_COUNT = 16;

    // ============== 错误消息常量 ==============

    /**
     * 初始化相关错误消息
     */
    public static final class ErrorMessage {
        public static final String OPTIMIZED_METRICS_INIT_FAILED = "优化Metrics初始化失败";

        private ErrorMessage() {
        }
    }

    // ============== 成功消息常量 ==============

    /**
     * 成功消息
     */
    public static final class SuccessMessage {
        public static final String OPTIMIZED_METRICS_INIT_SUCCESS = "优化指标初始化成功, 总指标数: 5个多维度Gauge";

        private SuccessMessage() {
        }
    }

    // 私有构造函数，防止实例化
    /**
     * 私有构造函数，防止实例化
     */
    private MetricsConstant() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}