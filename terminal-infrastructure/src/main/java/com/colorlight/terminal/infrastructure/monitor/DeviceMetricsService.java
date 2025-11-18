package com.colorlight.terminal.infrastructure.monitor;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.infrastructure.websocket.connection.ShardedConnectionManager;
import com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopAlertEvent;
import com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopHealthMonitor;
import com.colorlight.terminal.application.handler.WebsocketMsgMetricsHelper;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(
    name = "terminal.metrics.enabled",
    havingValue = "true",
    matchIfMissing = false
)
@Slf4j
public class DeviceMetricsService {

    private final MeterRegistry meterRegistry;
    private final ConnectionManagerPort connectionManagerPort;
    private final EventLoopHealthMonitor eventLoopHealthMonitor;
    private final ThreadPoolTaskExecutor[] taskExecutors;
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final ThreadPoolTaskScheduler taskScheduler;

    public DeviceMetricsService(
            MeterRegistry meterRegistry,
            ConnectionManagerPort connectionManagerPort,
            EventLoopHealthMonitor eventLoopHealthMonitor,
            ThreadPoolTaskExecutor[] taskExecutors,
            DeviceOnlineStatusPort deviceOnlineStatusPort,
            @Qualifier("deviceTaskSchedulerForMetrics") ThreadPoolTaskScheduler taskScheduler) {
        this.meterRegistry = meterRegistry;
        this.connectionManagerPort = connectionManagerPort;
        this.eventLoopHealthMonitor = eventLoopHealthMonitor;
        this.taskExecutors = taskExecutors;
        this.deviceOnlineStatusPort = deviceOnlineStatusPort;
        this.taskScheduler = taskScheduler;
    }

    // EventLoop告警计数器
    private Counter eventLoopWarnings;
    private Counter eventLoopCriticals;

    @PostConstruct
    public void initOptimizedMetrics() {
        try {
            // 1. 系统核心指标合并
            registerSystemMetrics();

            // 2. 线程池指标合并
            registerThreadPoolMetrics();

            // 3. 分片指标合并
            registerShardMetrics();

            // 4. 协议版本指标合并
            registerProtocolMetrics();

            // 5. EventLoop指标合并
            registerEventLoopMetrics();

            // 6. WebSocket消息计数
            registerWebsocketMsgCount();

            log.info("{} {}", MetricsConstant.LogTag.OPTIMIZED, MetricsConstant.SuccessMessage.OPTIMIZED_METRICS_INIT_SUCCESS);

        } catch (Exception e) {
            log.error("{} 优化指标初始化失败", MetricsConstant.LogTag.OPTIMIZED, e);
            throw new TechnicalException(TechErrorCode.METRICS_ERROR, MetricsConstant.ErrorMessage.OPTIMIZED_METRICS_INIT_FAILED, e);
        }
    }

    /**
     * 1. 系统核心指标合并
     */
    private void registerSystemMetrics() {
        // WebSocket连接数
        Gauge.builder(MetricsConstant.TERMINAL_SYSTEM_METRICS, this, DeviceMetricsService::getCurrentConnectionCount)
                .description(MetricsConstant.SYSTEM_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.TYPE, MetricsConstant.SystemType.WEBSOCKET_CONNECTIONS))
                .register(meterRegistry);

        // 在线设备数
        Gauge.builder(MetricsConstant.TERMINAL_SYSTEM_METRICS, this, DeviceMetricsService::getOnlineDeviceCount)
                .description(MetricsConstant.SYSTEM_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.TYPE, MetricsConstant.SystemType.ONLINE_DEVICES))
                .register(meterRegistry);

        // WebSocket连接率
        Gauge.builder(MetricsConstant.TERMINAL_SYSTEM_METRICS, this, DeviceMetricsService::getWebsocketConnectionRatio)
                .description(MetricsConstant.SYSTEM_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.TYPE, MetricsConstant.SystemType.WEBSOCKET_RATIO))
                .register(meterRegistry);
    }

    /**
     * 2. 线程池指标合并 - 详细按线程池分组
     */
    private void registerThreadPoolMetrics() {
        // 1. 注册调度器线程池指标
        registerSchedulerMetrics();

        // 2. 注册普通线程池指标
        if (taskExecutors == null || taskExecutors.length == 0) {
            return;
        }

        for (int i = 0; i < taskExecutors.length; i++) {
            final int poolIndex = i;
            final String poolName = getThreadPoolName(taskExecutors[i], poolIndex);

            // 利用率
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolUtilization(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.UTILIZATION))
                    .register(meterRegistry);

            // 活跃线程数
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolActiveThreads(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.ACTIVE_THREADS))
                    .register(meterRegistry);

            // 队列大小
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolQueueSize(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.QUEUE_SIZE))
                    .register(meterRegistry);

            // 核心线程数
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolCoreThreads(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.CORE_THREADS))
                    .register(meterRegistry);

            // 最大线程数
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolMaxThreads(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.MAX_THREADS))
                    .register(meterRegistry);

            // 当前线程数
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolCurrentThreads(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.CURRENT_THREADS))
                    .register(meterRegistry);

            // 总任务数
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolTotalTasks(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.TOTAL_TASKS))
                    .register(meterRegistry);

            // 已完成任务数
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolCompletedTasks(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.COMPLETED_TASKS))
                    .register(meterRegistry);

            // 最大队列容量
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolQueueCapacity(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.QUEUE_CAPACITY))
                    .register(meterRegistry);

            // 队列使用率
            Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, service -> service.getThreadPoolQueueUtilization(poolIndex))
                    .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.QUEUE_UTILIZATION))
                    .register(meterRegistry);
        }
    }

    /**
     * 注册调度器线程池指标
     */
    private void registerSchedulerMetrics() {
        if (taskScheduler == null) {
            return;
        }

        final String poolName = MetricsConstant.DEVICE_SCHEDULER_POOL;

        // 利用率
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerUtilization)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.UTILIZATION))
                .register(meterRegistry);

        // 活跃线程数
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerActiveThreads)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.ACTIVE_THREADS))
                .register(meterRegistry);

        // 队列大小
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerQueueSize)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.QUEUE_SIZE))
                .register(meterRegistry);

        // 核心线程数（调度器的poolSize）
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerCoreThreads)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.CORE_THREADS))
                .register(meterRegistry);

        // 最大线程数（调度器的poolSize）
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerMaxThreads)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.MAX_THREADS))
                .register(meterRegistry);

        // 当前线程数
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerCurrentThreads)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.CURRENT_THREADS))
                .register(meterRegistry);

        // 总任务数
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerTotalTasks)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.TOTAL_TASKS))
                .register(meterRegistry);

        // 已完成任务数
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerCompletedTasks)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.COMPLETED_TASKS))
                .register(meterRegistry);

        // 队列容量（调度器通常为无界队列）
        Gauge.builder(MetricsConstant.TERMINAL_THREADPOOL_METRICS, this, DeviceMetricsService::getSchedulerQueueCapacity)
                .description(MetricsConstant.THREADPOOL_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.POOL, poolName, MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.QUEUE_CAPACITY))
                .register(meterRegistry);
    }

    /**
     * 3. 分片指标合并 - 按分片ID分组
     */
    private void registerShardMetrics() {
        if (!(connectionManagerPort instanceof ShardedConnectionManager)) {
            return;
        }

        // 注册16个分片的连接数指标
        for (int shardId = 0; shardId < MetricsConstant.SHARD_COUNT; shardId++) {
            final int finalShardId = shardId;

            Gauge.builder(MetricsConstant.TERMINAL_SHARD_METRICS, this, service -> service.getShardConnectionCount(finalShardId))
                    .description(MetricsConstant.SHARD_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.SHARD_ID, String.valueOf(shardId), MetricsConstant.TagKey.TYPE, MetricsConstant.ShardType.CONNECTIONS))
                    .register(meterRegistry);
        }
    }

    /**
     * 4. 协议版本指标合并 - 按版本分组
     * 动态注册协议版本指标，基于ShardedConnectionManager的versionCounter
     */
    private void registerProtocolMetrics() {
        if (!(connectionManagerPort instanceof ShardedConnectionManager)) {
            return;
        }

        // 只注册协议版本分布指标，不重复注册总连接数
        // 动态注册实际存在的协议版本
        registerActualProtocolVersions();
    }

    /**
     * 动态注册实际存在的协议版本指标
     * 基于ShardedConnectionManager的versionCounter真实数据
     */
    private void registerActualProtocolVersions() {
        // 只注册项目中实际使用的协议版本 v1.0 和 v1.1
        String[] actualVersions = MetricsConstant.ProtocolVersions.ACTUAL_VERSIONS;

        for (String version : actualVersions) {
            Gauge.builder(MetricsConstant.TERMINAL_PROTOCOL_METRICS, this, service -> service.getProtocolVersionConnections(version))
                    .description(MetricsConstant.PROTOCOL_METRICS_DESC)
                    .tags(Tags.of(MetricsConstant.TagKey.VERSION, version, MetricsConstant.TagKey.TYPE, MetricsConstant.ProtocolType.CONNECTIONS))
                    .register(meterRegistry);
        }
    }

    /**
     * 5. EventLoop指标合并
     */
    private void registerEventLoopMetrics() {
        // 待处理任务数
        Gauge.builder(MetricsConstant.TERMINAL_EVENTLOOP_METRICS, this, DeviceMetricsService::getEventLoopPendingTasks)
                .description(MetricsConstant.EVENTLOOP_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.TYPE, MetricsConstant.EventLoopType.PENDING_TASKS))
                .register(meterRegistry);

        // 告警计数器(用于Grafana rate计算)
        eventLoopWarnings = Counter.builder(MetricsConstant.TERMINAL_EVENTLOOP_WARNINGS_TOTAL)
                .description(MetricsConstant.EVENTLOOP_WARNINGS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.LEVEL, MetricsConstant.AlertLevel.WARNING))
                .register(meterRegistry);

        eventLoopCriticals = Counter.builder(MetricsConstant.TERMINAL_EVENTLOOP_CRITICALS_TOTAL)
                .description(MetricsConstant.EVENTLOOP_CRITICALS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.LEVEL, MetricsConstant.AlertLevel.CRITICAL))
                .register(meterRegistry);
    }

    /**
     * 6. WebSocket消息数指标
     */
    private void registerWebsocketMsgCount() {

        // WebSocket消息发送数
        FunctionCounter.builder(MetricsConstant.WEBSOCKET_MSG_COUNT_METRICS,
                        WebsocketMsgMetricsHelper.class,
                        helper -> (double) WebsocketMsgMetricsHelper.getTotalSentMessage())
                .description(MetricsConstant.SYSTEM_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.TYPE, MetricsConstant.WebsocketMsgCountType.WEBSOCKET_MSG_SENT))
                .register(meterRegistry);

        // WebSocket消息接收数
        FunctionCounter.builder(MetricsConstant.WEBSOCKET_MSG_COUNT_METRICS,
                        WebsocketMsgMetricsHelper.class,
                        helper -> (double) WebsocketMsgMetricsHelper.getTotalReceivedMessage())
                .description(MetricsConstant.SYSTEM_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.TYPE, MetricsConstant.WebsocketMsgCountType.WEBSOCKET_MSG_RECEIVED))
                .register(meterRegistry);

        // WebSocket错误消息数
        FunctionCounter.builder(MetricsConstant.WEBSOCKET_MSG_COUNT_METRICS,
                        WebsocketMsgMetricsHelper.class,
                        helper -> (double) WebsocketMsgMetricsHelper.getTotalErrorMessage())
                .description(MetricsConstant.SYSTEM_METRICS_DESC)
                .tags(Tags.of(MetricsConstant.TagKey.TYPE, MetricsConstant.WebsocketMsgCountType.WEBSOCKET_MSG_ERROR))
                .register(meterRegistry);
    }

    private double getCurrentConnectionCount() {
        try {
            return connectionManagerPort.getConnectionCount();
        } catch (Exception e) {
            log.warn("{} 获取连接数失败", MetricsConstant.LogTag.CONNECTION, e);
            return 0.0;
        }
    }

    private double getOnlineDeviceCount() {
        try {
            return deviceOnlineStatusPort.getOnlineDeviceCount();
        } catch (Exception e) {
            log.warn("{} 获取在线设备数失败", MetricsConstant.LogTag.DEVICE, e);
            return 0.0;
        }
    }

    private double getWebsocketConnectionRatio() {
        try {
            // 获取WebSocket连接数
            int websocketConnectionCount = connectionManagerPort.getConnectionCount();

            if (websocketConnectionCount == 0) {
                return 0.0; // 没有WebSocket连接时，连接率为0
            }

            // 获取总在线设备数
            int totalOnlineDevices = deviceOnlineStatusPort.getOnlineDeviceCount();

            if (totalOnlineDevices == 0) {
                log.error("{} websocket连接率计算错误: websocket连接数 > 总在线数（0）", MetricsConstant.LogTag.WEBSOCKET);
            }

            // 计算WebSocket连接率
            double ratio = (double) websocketConnectionCount / totalOnlineDevices;
            return Math.min(ratio, 1.0); // 确保不超过100%

        } catch (Exception e) {
            log.warn("{} 获取websocket连接率失败", MetricsConstant.LogTag.WEBSOCKET, e);
            return 0.0; // 出错时返回0，表示无法确定连接率
        }
    }

    // 线程池相关指标
    private double getThreadPoolUtilization(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            if (executor == null) return 0.0;

            int maxPoolSize = executor.getMaximumPoolSize();
            return maxPoolSize > 0 ? (double) executor.getActiveCount() / maxPoolSize * 100 : 0.0;

        } catch (Exception e) {
            log.warn("{} 获取线程池利用率失败 poolIndex={}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolActiveThreads(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getActiveCount() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取线程池活跃线程数异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolQueueSize(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getQueue().size() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取线程池队列大小异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolCoreThreads(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getCorePoolSize() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取线程池核心线程数异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolMaxThreads(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getMaximumPoolSize() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取线程池最大线程数异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolCurrentThreads(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getPoolSize() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取线程池当前线程数异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolTotalTasks(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getTaskCount() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取线程池总任务数异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolCompletedTasks(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getCompletedTaskCount() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取线程池已完成任务数异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolQueueCapacity(int poolIndex) {
        try {
            if (taskExecutors == null || poolIndex >= taskExecutors.length) {
                return 0.0;
            }
            ThreadPoolTaskExecutor executor = taskExecutors[poolIndex];
            return executor != null ? executor.getQueueCapacity() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取线程池队列容量异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolQueueUtilization(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            if (executor == null) {
                return 0.0;
            }

            if (taskExecutors == null || poolIndex >= taskExecutors.length) {
                return 0.0;
            }

            ThreadPoolTaskExecutor taskExecutor = taskExecutors[poolIndex];
            int queueCapacity = taskExecutor.getQueueCapacity();

            if (queueCapacity <= 0) {
                return 0.0; // 无界队列或容量为0
            }

            int currentQueueSize = executor.getQueue().size();
            return (double) currentQueueSize / queueCapacity * 100;

        } catch (Exception e) {
            log.debug("{} 获取线程池队列使用率异常, poolIndex: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e);
            return 0.0;
        }
    }

    // 分片相关指标
    private double getShardConnectionCount(int shardId) {
        try {
            if (!(connectionManagerPort instanceof ShardedConnectionManager shardedManager)) {
                return 0.0;
            }

            Map<String, Object> shardStats = shardedManager.getShardStatistics();
            @SuppressWarnings("unchecked")
            Map<Integer, Integer> shardSizes = (Map<Integer, Integer>) shardStats.get("shardSizes");

            return shardSizes != null ? shardSizes.getOrDefault(shardId, 0) : 0.0;

        } catch (Exception e) {
            log.debug("{} 获取分片连接数异常, shardId: {}", MetricsConstant.LogTag.THREADPOOL, shardId, e);
            return 0.0;
        }
    }


    /**
     * 获取指定协议版本的连接数
     * 通过ShardedConnectionManager的公共接口获取真实数据
     *
     * @param version 协议版本字符串（如："v1.0", "v1.1"）
     * @return 该协议版本的连接数，异常时返回0.0
     */
    private double getProtocolVersionConnections(String version) {
        try {
            if (!(connectionManagerPort instanceof ShardedConnectionManager shardedManager)) {
                return 0.0;
            }

            // 将字符串版本转换为ProtocolVersion枚举
            ProtocolVersion protocolVersion = parseProtocolVersion(version);
            if (protocolVersion == null) {
                return 0.0;
            }

            // 直接获取指定协议版本的连接数
            return shardedManager.getProtocolVersionConnectionCount(protocolVersion);

        } catch (Exception e) {
            log.error("{} 获取协议版本连接数异常, version: {}", MetricsConstant.LogTag.THREADPOOL, version, e);
            return 0.0;
        }
    }

    /**
     * 解析协议版本字符串为ProtocolVersion枚举
     *
     * @param version 版本字符串（如："v1.0", "v1.1"）
     * @return ProtocolVersion枚举，未知版本返回null
     */
    private ProtocolVersion parseProtocolVersion(String version) {
        if (version == null) {
            return null;
        }

        // 移除前缀 "v" 并匹配枚举
        String cleanVersion = version.startsWith(MetricsConstant.PROTOCOL_VERSION_PREFIX) ? 
            version.substring(1) : version;

        return switch (cleanVersion) {
            case "1.0" -> ProtocolVersion.V1_0;
            case "1.1" -> ProtocolVersion.V1_1;
            default -> null;
        };
    }

    private double getEventLoopPendingTasks() {
        try {
            EventLoopHealthMonitor.EventLoopStatistics statistics = eventLoopHealthMonitor.getStatistics();
            return statistics.totalPendingTasks();
        } catch (Exception e) {
            log.warn("{} 获取EventLoop待处理任务失败", MetricsConstant.LogTag.EVENTLOOP, e);
            return 0.0;
        }
    }

    // ============== 调度器指标方法 ==============

    private double getSchedulerUtilization() {
        try {
            if (taskScheduler == null) return 0.0;

            ThreadPoolExecutor executor = taskScheduler.getScheduledThreadPoolExecutor();

            int poolSize = taskScheduler.getPoolSize();
            return poolSize > 0 ? (double) executor.getActiveCount() / poolSize * 100 : 0.0;

        } catch (Exception e) {
            log.debug("{} 获取调度器利用率异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    private double getSchedulerActiveThreads() {
        try {
            if (taskScheduler == null) return 0.0;

            ThreadPoolExecutor executor = taskScheduler.getScheduledThreadPoolExecutor();
            return executor.getActiveCount();
        } catch (Exception e) {
            log.debug("{} 获取调度器活跃线程数异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    private double getSchedulerQueueSize() {
        try {
            if (taskScheduler == null) return 0.0;

            ThreadPoolExecutor executor = taskScheduler.getScheduledThreadPoolExecutor();
            return executor.getQueue().size();
        } catch (Exception e) {
            log.debug("{} 获取调度器队列大小异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    private double getSchedulerCoreThreads() {
        try {
            return taskScheduler != null ? taskScheduler.getPoolSize() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取调度器核心线程数异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    private double getSchedulerMaxThreads() {
        try {
            return taskScheduler != null ? taskScheduler.getPoolSize() : 0.0;
        } catch (Exception e) {
            log.debug("{} 获取调度器最大线程数异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    private double getSchedulerCurrentThreads() {
        try {
            if (taskScheduler == null) return 0.0;

            ThreadPoolExecutor executor = taskScheduler.getScheduledThreadPoolExecutor();
            return executor.getPoolSize();
        } catch (Exception e) {
            log.debug("{} 获取调度器当前线程数异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    private double getSchedulerTotalTasks() {
        try {
            if (taskScheduler == null) return 0.0;

            ThreadPoolExecutor executor = taskScheduler.getScheduledThreadPoolExecutor();
            return executor.getTaskCount();
        } catch (Exception e) {
            log.debug("{} 获取调度器总任务数异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    private double getSchedulerCompletedTasks() {
        try {
            if (taskScheduler == null) return 0.0;

            ThreadPoolExecutor executor = taskScheduler.getScheduledThreadPoolExecutor();
            return executor.getCompletedTaskCount();
        } catch (Exception e) {
            log.debug("{} 获取调度器已完成任务数异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    private double getSchedulerQueueCapacity() {
        try {
            // ThreadPoolTaskScheduler通常使用无界队列，返回Integer.MAX_VALUE或-1表示无界
            return -1.0; // 表示无界队列
        } catch (Exception e) {
            log.debug("{} 获取调度器队列容量异常", MetricsConstant.LogTag.SCHEDULER, e);
            return 0.0;
        }
    }

    // ============== 辅助方法 ==============

    /**
     * 获取有效的线程池执行器流
     * 过滤掉null或已关闭的线程池
     */
    private Stream<ThreadPoolExecutor> getValidThreadPoolExecutors() {
        if (taskExecutors == null || taskExecutors.length == 0) {
            return Stream.empty();
        }

        return Arrays.stream(taskExecutors)
                .filter(executor -> {
                    if (executor == null) return false;
                    try {
                        // 检查线程池是否可用
                        ThreadPoolExecutor poolExecutor = executor.getThreadPoolExecutor();
                        return !poolExecutor.isShutdown();
                    } catch (Exception e) {
                        log.debug("{} 线程池执行器不可用: {}", MetricsConstant.LogTag.THREADPOOL, e.getMessage());
                        return false;
                    }
                })
                .map(ThreadPoolTaskExecutor::getThreadPoolExecutor);
    }

    private ThreadPoolExecutor getThreadPoolExecutor(int poolIndex) {
        if (taskExecutors == null || poolIndex >= taskExecutors.length) {
            return null;
        }

        ThreadPoolTaskExecutor executor = taskExecutors[poolIndex];
        if (executor == null) {
            return null;
        }

        try {
            ThreadPoolExecutor poolExecutor = executor.getThreadPoolExecutor();
            return !poolExecutor.isShutdown() ? poolExecutor : null;
        } catch (Exception e) {
            log.debug("{} 线程池执行器不可用 poolIndex={}: {}", MetricsConstant.LogTag.THREADPOOL, poolIndex, e.getMessage());
            return null;
        }
    }

    private String getThreadPoolName(ThreadPoolTaskExecutor executor, int index) {
        if (executor == null) {
            return MetricsConstant.DEFAULT_POOL_PREFIX + index;
        }

        try {
            String threadNamePrefix = executor.getThreadNamePrefix();
            return threadNamePrefix.replace(MetricsConstant.THREAD_NAME_SEPARATOR, "");
        } catch (Exception e) {
            return MetricsConstant.DEFAULT_POOL_PREFIX + index;
        }
    }

    /**
     * 处理EventLoop告警事件
     * 监听EventLoopAlertEvent，根据事件级别更新相应的告警计数器
     */
    @EventListener
    @Async("deviceEventExecutor")
    public void handleEventLoopAlert(EventLoopAlertEvent event) {
        try {
            if (EventLoopAlertEvent.AlertLevel.WARNING.equals(event.getAlertLevel())) {
                eventLoopWarnings.increment();
                log.debug("{} 收到EventLoop告警事件: {}", MetricsConstant.LogTag.EVENTLOOP, event.getMessage());
            } else if (EventLoopAlertEvent.AlertLevel.CRITICAL.equals(event.getAlertLevel())) {
                eventLoopCriticals.increment();
                log.warn("{} 收到EventLoop严重告警事件: {}", MetricsConstant.LogTag.EVENTLOOP, event.getMessage());
            }
        } catch (Exception e) {
            log.error("{} 处理EventLoop告警事件失败", MetricsConstant.LogTag.EVENTLOOP, e);
        }
    }

    /**
     * 获取所有指标的摘要信息（用于调试和监控）
     */
    public MetricsSummary getMetricsSummary() {
        try {
            return new MetricsSummary(
                getCurrentConnectionCount(),
                getEventLoopPendingTasks(),
                eventLoopWarnings.count(),
                eventLoopCriticals.count(),
                getAverageThreadPoolUtilization(),
                getTotalThreadPoolQueueSize(),
                getTotalActiveThreads(),
                getOnlineDeviceCount(),
                getWebsocketConnectionRatio()
            );
        } catch (Exception e) {
            log.error("{} 获取指标摘要失败", MetricsConstant.LogTag.SUMMARY, e);
            return MetricsSummary.empty();
        }
    }

    /**
     * 获取平均线程池利用率
     */
    private double getAverageThreadPoolUtilization() {
        try {
            if (taskExecutors == null || taskExecutors.length == 0) {
                return 0.0;
            }

            return getValidThreadPoolExecutors()
                    .mapToDouble(executor -> {
                        int maxPoolSize = executor.getMaximumPoolSize();
                        if (maxPoolSize > 0) {
                            return (double) executor.getActiveCount() / maxPoolSize * 100;
                        }
                        return 0.0;
                    })
                    .average()
                    .orElse(0.0);
        } catch (Exception e) {
            log.warn("{} 获取平均线程池利用率失败", MetricsConstant.LogTag.THREADPOOL, e);
            return 0.0;
        }
    }

    /**
     * 获取线程池队列总大小
     */
    private double getTotalThreadPoolQueueSize() {
        try {
            if (taskExecutors == null || taskExecutors.length == 0) {
                return 0.0;
            }

            return getValidThreadPoolExecutors()
                    .mapToInt(executor -> executor.getQueue().size())
                    .sum();
        } catch (Exception e) {
            log.warn("{} 获取线程池队列总大小失败", MetricsConstant.LogTag.THREADPOOL, e);
            return 0.0;
        }
    }

    /**
     * 获取活跃线程总数
     */
    private double getTotalActiveThreads() {
        try {
            if (taskExecutors == null || taskExecutors.length == 0) {
                return 0.0;
            }

            return getValidThreadPoolExecutors()
                    .mapToInt(ThreadPoolExecutor::getActiveCount)
                    .sum();
        } catch (Exception e) {
            log.warn("{} 获取活跃线程总数失败", MetricsConstant.LogTag.THREADPOOL, e);
            return 0.0;
        }
    }

    /**
     * 指标摘要数据
     */
    public record MetricsSummary(
        double connectionCount,
        double eventLoopPendingTasks,
        double eventLoopWarnings,
        double eventLoopCriticals,
        double threadPoolUtilization,
        double threadPoolQueueSize,
        double activeThreads,
        double onlineDeviceCount,
        double websocketConnectionRatio
    ) {
        public static MetricsSummary empty() {
            return new MetricsSummary(0, 1.0, 0, 0, 0, 0, 0, 0, 0.0);
        }
    }
}