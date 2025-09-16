package com.colorlight.terminal.boot.config.actuator;

/**
 * Actuator监控端点常量类
 * 收集复用的字段名和标准化值，避免魔法字符串
 *
 * @author Nan
 */
public final class ActuatorConstant {

    private ActuatorConstant() {
        // 工具类不允许实例化
    }

    /**
     * 通用字段名常量
     */
    public static final class FieldNames {
        private FieldNames() {
            // 工具类不允许实例化
        }
        
        /** 时间戳字段名 */
        public static final String TIMESTAMP = "timestamp";
        /** 端点字段名 */
        public static final String ENDPOINT = "endpoint";
        /** 错误字段名 */
        public static final String ERROR = "error";
        /** 消息字段名 */
        public static final String MESSAGE = "message";
        /** 摘要字段名 */
        public static final String SUMMARY = "summary";
        /** 健康状态字段名 */
        public static final String HEALTH = "health";
        /** 状态字段名 */
        public static final String STATUS = "status";
        /** 描述字段名 */
        public static final String DESCRIPTION = "description";
        /** 警告字段名 */
        public static final String WARNING = "warning";
        /** 建议字段名 */
        public static final String RECOMMENDATION = "recommendation";
        /** 备注字段名 */
        public static final String NOTE = "note";
    }

    /**
     * 设备状态相关字段名
     */
    public static final class DeviceFields {
        private DeviceFields() {
            // 工具类不允许实例化
        }
        
        /** 在线状态字段名 */
        public static final String ONLINE = "online";
        /** TTL字段名 */
        public static final String TTL = "ttl";
        /** 总在线设备数字段名 */
        public static final String TOTAL_ONLINE_DEVICES = "totalOnlineDevices";
        /** 已索引设备数字段名 */
        public static final String INDEXED_DEVICES = "indexedDevices";
        /** 数据一致性字段名 */
        public static final String DATA_CONSISTENCY = "dataConsistency";
        /** TTL信息字段名 */
        public static final String TTL_INFO = "ttlInfo";
        /** 活跃TTL设备数字段名 */
        public static final String ACTIVE_TTL_DEVICES = "activeTtlDevices";
        /** TTL机制字段名 */
        public static final String TTL_MECHANISM = "ttlMechanism";
        /** 初始TTL字段名 */
        public static final String INITIAL_TTL = "initialTtl";
        /** 重连窗口字段名 */
        public static final String RECONNECT_WINDOW = "reconnectWindow";
        /** 一致性警告字段名 */
        public static final String CONSISTENCY_WARNING = "consistencyWarning";
        /** TTL描述字段名 */
        public static final String TTL_DESCRIPTION = "ttlDescription";
        /** 性能建议字段名 */
        public static final String PERFORMANCE_RECOMMENDATION = "performanceRecommendation";
    }

    /**
     * 线程池相关字段名
     */
    public static final class ThreadPoolFields {
        private ThreadPoolFields() {
            // 工具类不允许实例化
        }
        
        /** 线程池列表字段名 */
        public static final String THREAD_POOLS = "threadPools";
        /** 总线程池数字段名 */
        public static final String TOTAL_THREAD_POOLS = "totalThreadPools";
        /** 活跃线程池数字段名 */
        public static final String ACTIVE_THREAD_POOLS = "activeThreadPools";
        /** 总活跃线程数字段名 */
        public static final String TOTAL_ACTIVE_THREADS = "totalActiveThreads";
        /** 总最大线程数字段名 */
        public static final String TOTAL_MAX_THREADS = "totalMaxThreads";
        /** 平均利用率字段名 */
        public static final String AVERAGE_UTILIZATION = "averageUtilization";
        /** 整体容量字段名 */
        public static final String OVERALL_CAPACITY = "overallCapacity";
        /** Bean名称字段名 */
        public static final String BEAN_NAME = "beanName";
        /** 类型字段名 */
        public static final String TYPE = "type";
        /** 核心池大小字段名 */
        public static final String CORE_POOL_SIZE = "corePoolSize";
        /** 最大池大小字段名 */
        public static final String MAX_POOL_SIZE = "maxPoolSize";
        /** 活跃线程数字段名 */
        public static final String ACTIVE_THREADS = "activeThreads";
        /** 池大小字段名 */
        public static final String POOL_SIZE = "poolSize";
        /** 队列大小字段名 */
        public static final String QUEUE_SIZE = "queueSize";
        /** 队列容量字段名 */
        public static final String QUEUE_CAPACITY = "queueCapacity";
        /** 已完成任务数字段名 */
        public static final String COMPLETED_TASKS = "completedTasks";
        /** 总任务数字段名 */
        public static final String TOTAL_TASKS = "totalTasks";
        /** 保持活跃秒数字段名 */
        public static final String KEEP_ALIVE_SECONDS = "keepAliveSeconds";
        /** 线程名称前缀字段名 */
        public static final String THREAD_NAME_PREFIX = "threadNamePrefix";
        /** 是否关闭字段名 */
        public static final String IS_SHUTDOWN = "isShutdown";
        /** 是否终止字段名 */
        public static final String IS_TERMINATED = "isTerminated";
        /** 是否活跃字段名 */
        public static final String IS_ACTIVE = "isActive";
        /** 利用率字段名 */
        public static final String UTILIZATION = "utilization";
        /** 队列利用率字段名 */
        public static final String QUEUE_UTILIZATION = "queueUtilization";
        /** 高利用率池字段名 */
        public static final String HIGH_UTILIZATION_POOLS = "highUtilizationPools";
        /** 高队列利用率池字段名 */
        public static final String HIGH_QUEUE_UTILIZATION_POOLS = "highQueueUtilizationPools";
        /** 利用率警告字段名 */
        public static final String UTILIZATION_WARNING = "utilizationWarning";
        /** 队列警告字段名 */
        public static final String QUEUE_WARNING = "queueWarning";
        /** 容量警告字段名 */
        public static final String CAPACITY_WARNING = "capacityWarning";
    }

    /**
     * WebSocket连接相关字段名
     */
    public static final class WebSocketFields {
        private WebSocketFields() {
            // 工具类不允许实例化
        }
        
        /** 连接信息字段名 */
        public static final String CONNECTIONS = "connections";
        /** 总连接数字段名 */
        public static final String TOTAL_CONNECTIONS = "totalConnections";
        /** 活跃分片数字段名 */
        public static final String ACTIVE_SHARDS = "activeShards";
        /** 平均分片大小字段名 */
        public static final String AVERAGE_SHARD_SIZE = "averageShardSize";
        /** 负载均衡字段名 */
        public static final String LOAD_BALANCE = "loadBalance";
        /** 连接状态字段名 */
        public static final String CONNECTION_STATUS = "connectionStatus";
        /** 连接警告字段名 */
        public static final String CONNECTION_WARNING = "connectionWarning";
        /** 平衡状态字段名 */
        public static final String BALANCE_STATUS = "balanceStatus";
        /** 平衡警告字段名 */
        public static final String BALANCE_WARNING = "balanceWarning";
    }

    /**
     * EventLoop监控相关字段名
     */
    public static final class EventLoopFields {
        private EventLoopFields() {
            // 工具类不允许实例化
        }

        /** 统计信息字段名 */
        public static final String STATISTICS = "statistics";
        /** 阈值字段名 */
        public static final String THRESHOLDS = "thresholds";
        /** 性能字段名 */
        public static final String PERFORMANCE = "performance";
        /** 当前总待处理任务数字段名 */
        public static final String TOTAL_PENDING_TASKS = "totalPendingTasks";
        /** 最大待处理任务数字段名 */
        public static final String MAX_PENDING_TASKS = "maxPendingTasks";
        /** 告警次数字段名 */
        public static final String WARNING_COUNT = "warningCount";
        /** 严重告警次数字段名 */
        public static final String CRITICAL_COUNT = "criticalCount";
        /** 告警阈值字段名 */
        public static final String WARNING_THRESHOLD = "warningThreshold";
        /** 严重告警阈值字段名 */
        public static final String CRITICAL_THRESHOLD = "criticalThreshold";
        /** 任务积压比率字段名 */
        public static final String TASK_BACKLOG_RATIO = "taskBacklogRatio";
        /** 告警频率字段名 */
        public static final String ALERT_FREQUENCY = "alertFrequency";
        /** 系统稳定性字段名 */
        public static final String SYSTEM_STABILITY = "systemStability";
        /** 成功字段名 */
        public static final String SUCCESS = "success";
    }

    /**
     * 应用信息相关字段名
     */
    public static final class ApplicationFields {
        private ApplicationFields() {
            // 工具类不允许实例化
        }
        
        /** 应用信息字段名 */
        public static final String APPLICATION = "application";
        /** 名称字段名 */
        public static final String NAME = "name";
        /** 版本字段名 */
        public static final String VERSION = "version";
        /** 启动时间字段名 */
        public static final String STARTUP_TIME = "startup-time";
        /** 技术栈字段名 */
        public static final String TECHNOLOGY = "technology";
        /** Java版本字段名 */
        public static final String JAVA_VERSION = "java-version";
        /** Spring Boot版本字段名 */
        public static final String SPRING_BOOT_VERSION = "spring-boot-version";
        /** 框架字段名 */
        public static final String FRAMEWORK = "framework";
        /** 架构字段名 */
        public static final String ARCHITECTURE = "architecture";
        /** 运行时字段名 */
        public static final String RUNTIME = "runtime";
        /** 激活配置字段名 */
        public static final String ACTIVE_PROFILES = "active-profiles";
        /** JVM名称字段名 */
        public static final String JVM_NAME = "jvm-name";
        /** 操作系统名称字段名 */
        public static final String OS_NAME = "os-name";
        /** 处理器数字段名 */
        public static final String PROCESSORS = "processors";
        /** 端口信息字段名 */
        public static final String PORTS = "ports";
        /** HTTP端口字段名 */
        public static final String HTTP_PORT = "http-port";
        /** WebSocket端口字段名 */
        public static final String WEBSOCKET_PORT = "websocket-port";
        /** 管理端口字段名 */
        public static final String MANAGEMENT_PORT = "management-port";
        /** 构建信息字段名 */
        public static final String BUILD = "build";
        /** 时间字段名 */
        public static final String TIME = "time";
        /** 构件字段名 */
        public static final String ARTIFACT = "artifact";
        /** 组字段名 */
        public static final String GROUP = "group";
        /** Git信息字段名 */
        public static final String GIT = "git";
        /** 分支字段名 */
        public static final String BRANCH = "branch";
        /** 提交ID字段名 */
        public static final String COMMIT_ID = "commit-id";
        /** 提交时间字段名 */
        public static final String COMMIT_TIME = "commit-time";
        /** 功能特性字段名 */
        public static final String FEATURES = "features";
        /** WebSocket支持字段名 */
        public static final String WEBSOCKET_SUPPORT = "websocket-support";
        /** 设备管理字段名 */
        public static final String DEVICE_MANAGEMENT = "device-management";
        /** 实时监控字段名 */
        public static final String REAL_TIME_MONITORING = "real-time-monitoring";
        /** 事件驱动架构字段名 */
        public static final String EVENT_DRIVEN_ARCHITECTURE = "event-driven-architecture";
        /** 连接池字段名 */
        public static final String CONNECTION_POOLING = "connection-pooling";
        /** 健康监控字段名 */
        public static final String HEALTH_MONITORING = "health-monitoring";
        /** 性能指标字段名 */
        public static final String PERFORMANCE = "performance";
        /** 最大并发连接数字段名 */
        public static final String MAX_CONCURRENT_CONNECTIONS = "max-concurrent-connections";
        /** 目标响应时间字段名 */
        public static final String TARGET_RESPONSE_TIME = "target-response-time";
        /** 心跳间隔字段名 */
        public static final String HEARTBEAT_INTERVAL = "heartbeat-interval";
        /** 离线检查间隔字段名 */
        public static final String OFFLINE_CHECK_INTERVAL = "offline-check-interval";
    }

    /**
     * 状态值常量
     */
    public static final class StatusValues {
        private StatusValues() {
            // 工具类不允许实例化
        }
        
        // 通用状态
        /** 健康状态值 */
        public static final String HEALTHY = "HEALTHY";
        /** 正常状态值 */
        public static final String NORMAL = "NORMAL";
        /** 未知状态值 */
        public static final String UNKNOWN = "Unknown";

        // 负载状态
        /** 高负载状态值 */
        public static final String HIGH_LOAD = "HIGH_LOAD";
        /** 中等负载状态值 */
        public static final String MEDIUM_LOAD = "MEDIUM_LOAD";
        /** 高利用率状态值 */
        public static final String HIGH_UTILIZATION = "HIGH_UTILIZATION";
        /** 中等利用率状态值 */
        public static final String MEDIUM_UTILIZATION = "MEDIUM_UTILIZATION";

        // 一致性状态
        /** 一致状态值 */
        public static final String CONSISTENT = "CONSISTENT";
        /** 不一致状态值 */
        public static final String INCONSISTENT = "INCONSISTENT";

        // TTL机制状态
        /** 已配置状态值 */
        public static final String CONFIGURED = "CONFIGURED";

        // 负载均衡状态
        /** 平衡状态值 */
        public static final String BALANCED = "BALANCED";
        /** 轻微不平衡状态值 */
        public static final String SLIGHTLY_UNBALANCED = "SLIGHTLY_UNBALANCED";
        /** 不平衡状态值 */
        public static final String UNBALANCED = "UNBALANCED";

        // EventLoop健康状态
        /** 严重状态值 */
        public static final String CRITICAL = "CRITICAL";
        /** 警告状态值 */
        public static final String WARNING = "WARNING";
        /** 注意状态值 */
        public static final String CAUTION = "CAUTION";

        // EventLoop系统稳定性状态
        /** 稳定状态值 */
        public static final String STABLE = "稳定";
        /** 基本稳定状态值 */
        public static final String BASICALLY_STABLE = "基本稳定";
        /** 轻微不稳定状态值 */
        public static final String SLIGHTLY_UNSTABLE = "轻微不稳定";
        /** 不稳定状态值 */
        public static final String UNSTABLE = "不稳定";

        // EventLoop告警频率状态
        /** 无告警状态值 */
        public static final String NO_ALERTS = "无告警";
        /** 偶尔告警状态值 */
        public static final String OCCASIONAL_ALERTS = "偶尔告警";
        /** 频繁告警状态值 */
        public static final String FREQUENT_ALERTS = "频繁告警";
        /** 持续告警状态值 */
        public static final String CONTINUOUS_ALERTS = "持续告警";
    }

    /**
     * 端点名称常量
     */
    public static final class EndpointNames {
        private EndpointNames() {
            // 工具类不允许实例化
        }
        
        /** 设备状态统计端点名称 */
        public static final String DEVICE_STATUS_STATISTICS = "Device Status Statistics";
        /** 线程池统计端点名称 */
        public static final String THREAD_POOL_STATISTICS = "Thread Pool Statistics";
        /** WebSocket连接统计端点名称 */
        public static final String WEBSOCKET_CONNECTION_STATISTICS = "WebSocket Connection Statistics";
        /** EventLoop统计端点名称 */
        public static final String EVENTLOOP_STATISTICS = "eventloop-statistics";
    }

    /**
     * 错误消息常量
     */
    public static final class ErrorMessages {
        private ErrorMessages() {
            // 工具类不允许实例化
        }
        
        /** 获取设备统计失败错误消息 */
        public static final String FAILED_TO_RETRIEVE_DEVICE_STATISTICS = "Failed to retrieve device statistics";
        /** 获取线程池统计失败错误消息 */
        public static final String FAILED_TO_RETRIEVE_THREAD_POOL_STATISTICS = "Failed to retrieve thread pool statistics";
        /** 获取WebSocket统计失败错误消息 */
        public static final String FAILED_TO_RETRIEVE_WEBSOCKET_STATISTICS = "Failed to retrieve WebSocket statistics";
        /** 获取在线设备统计失败错误消息 */
        public static final String FAILED_TO_GET_ONLINE_DEVICE_STATS = "Failed to get online device stats";
        /** 获取TTL统计失败错误消息 */
        public static final String FAILED_TO_GET_TTL_STATS = "Failed to get TTL stats";
        /** 获取线程池信息失败错误消息 */
        public static final String FAILED_TO_GET_THREAD_POOL_INFO = "Failed to get thread pool info: ";
        /** 获取EventLoop统计失败错误消息 */
        public static final String FAILED_TO_RETRIEVE_EVENTLOOP_STATISTICS = "Failed to retrieve EventLoop statistics";
        /** 重置EventLoop统计失败错误消息 */
        public static final String FAILED_TO_RESET_EVENTLOOP_STATISTICS = "Failed to reset EventLoop statistics";
    }

    /**
     * 应用信息默认值常量
     */
    public static final class DefaultValues {
        private DefaultValues() {
            // 工具类不允许实例化
        }
        
        /** 应用名称默认值 */
        public static final String APP_NAME = "Colorlight Terminal Service";
        /** 应用描述默认值 */
        public static final String APP_DESCRIPTION = "高性能终端设备管理服务";
        /** 未知版本默认值 */
        public static final String UNKNOWN_VERSION = "unknown";
        /** 默认Spring Boot版本值 */
        public static final String DEFAULT_SPRING_BOOT_VERSION = "3.3.11";
        /** 默认框架值 */
        public static final String DEFAULT_FRAMEWORK = "Spring Boot + Netty WebSocket + Dubbo RPC";
        /** 默认架构值 */
        public static final String DEFAULT_ARCHITECTURE = "Hexagonal Architecture (Clean Architecture)";
        /** 默认HTTP端口值 */
        public static final String DEFAULT_HTTP_PORT = "8088";
        /** 默认WebSocket端口值 */
        public static final String DEFAULT_WEBSOCKET_PORT = "8443";
        /** 默认管理端口值 */
        public static final String DEFAULT_MANAGEMENT_PORT = "same as http";
        /** 默认最大并发连接数 */
        public static final String DEFAULT_MAX_CONCURRENT_CONNECTIONS = "20K+";
        /** 默认目标响应时间值 */
        public static final String DEFAULT_TARGET_RESPONSE_TIME = "<100ms";
        /** 默认心跳间隔值 */
        public static final String DEFAULT_HEARTBEAT_INTERVAL = "55s";
        /** 默认离线检查间隔值 */
        public static final String DEFAULT_OFFLINE_CHECK_INTERVAL = "60s";
    }

    /**
     * TTL相关常量值
     */
    public static final class TtlValues {
        private TtlValues() {
            // 工具类不允许实例化
        }
        
        /** 双TTL机制描述值 */
        public static final String DUAL_TTL_MECHANISM = "双TTL机制";
        /** 初始TTL 1小时值 */
        public static final String INITIAL_TTL_1_HOUR = "1小时";
        /** 重连窗口2分钟值 */
        public static final String RECONNECT_WINDOW_2_MINUTES = "2分钟";
        /** TTL描述文本值 */
        public static final String TTL_DESCRIPTION_TEXT = "设备首次连接1小时TTL，重连时2分钟窗口期";
        /** TTL计算备注值 */
        public static final String TTL_CALCULATION_NOTE = "基于设备状态Hash计算";
        /** TTL统计备注值 */
        public static final String TTL_STATISTICS_NOTE = "详细TTL统计需要遍历所有设备状态，可按需实现";
        /** TTL已配置描述值 */
        public static final String TTL_CONFIGURED_DESCRIPTION = "双TTL机制已配置";
        /** Redis基础描述值 */
        public static final String REDIS_BASED_DESCRIPTION = "基于Redis计数器和设备状态索引的在线统计";
    }

    /**
     * EventLoop相关常量值
     */
    public static final class EventLoopValues {
        private EventLoopValues() {
            // 工具类不允许实例化
        }

        /** 告警阈值 */
        public static final long WARNING_THRESHOLD_VALUE = 1000L;
        /** 严重告警阈值 */
        public static final long CRITICAL_THRESHOLD_VALUE = 5000L;
        /** 默认健康消息 */
        public static final String DEFAULT_HEALTHY_MESSAGE = "EventLoop运行正常";
        /** EventLoop运行良好建议 */
        public static final String GOOD_PERFORMANCE_RECOMMENDATION = "EventLoop运行良好，无需特别优化";
        /** 严重阻塞建议 */
        public static final String CRITICAL_BLOCKING_RECOMMENDATION = "建议检查业务逻辑是否存在阻塞操作，考虑异步处理或增加EventLoop线程数";
        /** 频繁告警建议 */
        public static final String FREQUENT_WARNING_RECOMMENDATION = "建议优化业务处理逻辑，减少EventLoop中的耗时操作";
        /** 任务积压建议 */
        public static final String BACKLOG_RECOMMENDATION = "当前任务积压较多，建议监控业务处理性能";
        /** 重置成功消息 */
        public static final String RESET_SUCCESS_MESSAGE = "EventLoop统计信息已重置";
    }
}