package com.colorlight.terminal.infrastructure.monitor;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.infrastructure.websocket.connection.ShardedConnectionManager;
import com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopAlertEvent;
import com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopHealthMonitor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceMetricsService {

    private final MeterRegistry meterRegistry;
    private final ConnectionManagerPort connectionManagerPort;
    private final EventLoopHealthMonitor eventLoopHealthMonitor;
    private final ThreadPoolTaskExecutor[] taskExecutors;
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;

    // EventLoop告警计数器
    private Counter eventLoopWarnings;
    private Counter eventLoopCriticals;

    // 分片统计缓存
    private final AtomicReference<Map<String, Object>> cachedShardStats = new AtomicReference<>();
    private final AtomicLong cacheTimestamp = new AtomicLong(0);
    private static final long CACHE_TTL_MS = 5000;

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

            log.info("TerminalMetrics -optimized- 优化指标初始化成功, 总指标数: 5个多维度Gauge");

        } catch (Exception e) {
            log.error("TerminalMetrics -optimized- 优化指标初始化失败", e);
            throw new TechnicalException(TechErrorCode.METRICS_ERROR, "优化Metrics初始化失败", e);
        }
    }

    /**
     * 1. 系统核心指标合并 - 4合1
     */
    private void registerSystemMetrics() {
        // WebSocket连接数
        Gauge.builder("terminal_system_metrics", this, DeviceMetricsService::getCurrentConnectionCount)
                .description("系统核心指标")
                .tags(Tags.of("type", "websocket_connections"))
                .register(meterRegistry);

        // 在线设备数
        Gauge.builder("terminal_system_metrics", this, DeviceMetricsService::getOnlineDeviceCount)
                .description("系统核心指标")
                .tags(Tags.of("type", "online_devices"))
                .register(meterRegistry);

        // WebSocket连接率
        Gauge.builder("terminal_system_metrics", this, DeviceMetricsService::getWebsocketConnectionRatio)
                .description("系统核心指标")
                .tags(Tags.of("type", "websocket_ratio"))
                .register(meterRegistry);

        // 负载均衡比例
        Gauge.builder("terminal_system_metrics", this, DeviceMetricsService::getConnectionBalance)
                .description("系统核心指标")
                .tags(Tags.of("type", "balance_ratio"))
                .register(meterRegistry);
    }

    /**
     * 2. 线程池指标合并 - 详细按线程池分组
     */
    private void registerThreadPoolMetrics() {
        if (taskExecutors == null || taskExecutors.length == 0) {
            return;
        }

        for (int i = 0; i < taskExecutors.length; i++) {
            final int poolIndex = i;
            final String poolName = getThreadPoolName(taskExecutors[i], poolIndex);

            // 利用率
            Gauge.builder("terminal_threadpool_metrics", this, service -> service.getThreadPoolUtilization(poolIndex))
                    .description("线程池详细指标")
                    .tags(Tags.of("pool", poolName, "type", "utilization"))
                    .register(meterRegistry);

            // 活跃线程数
            Gauge.builder("terminal_threadpool_metrics", this, service -> service.getThreadPoolActiveThreads(poolIndex))
                    .description("线程池详细指标")
                    .tags(Tags.of("pool", poolName, "type", "active_threads"))
                    .register(meterRegistry);

            // 队列大小
            Gauge.builder("terminal_threadpool_metrics", this, service -> service.getThreadPoolQueueSize(poolIndex))
                    .description("线程池详细指标")
                    .tags(Tags.of("pool", poolName, "type", "queue_size"))
                    .register(meterRegistry);

            // 核心线程数
            Gauge.builder("terminal_threadpool_metrics", this, service -> service.getThreadPoolCoreThreads(poolIndex))
                    .description("线程池详细指标")
                    .tags(Tags.of("pool", poolName, "type", "core_threads"))
                    .register(meterRegistry);

            // 最大线程数
            Gauge.builder("terminal_threadpool_metrics", this, service -> service.getThreadPoolMaxThreads(poolIndex))
                    .description("线程池详细指标")
                    .tags(Tags.of("pool", poolName, "type", "max_threads"))
                    .register(meterRegistry);
        }
    }

    /**
     * 3. 分片指标合并 - 按分片ID分组
     */
    private void registerShardMetrics() {
        if (!(connectionManagerPort instanceof ShardedConnectionManager)) {
            return;
        }

        // 注册16个分片的连接数指标
        for (int shardId = 0; shardId < 16; shardId++) {
            final int finalShardId = shardId;

            Gauge.builder("terminal_shard_metrics", this, service -> service.getShardConnectionCount(finalShardId))
                    .description("分片详细指标")
                    .tags(Tags.of("shard_id", String.valueOf(shardId), "type", "connections"))
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
        String[] actualVersions = {"v1.0", "v1.1"};

        for (String version : actualVersions) {
            Gauge.builder("terminal_protocol_metrics", this, service -> service.getProtocolVersionConnections(version))
                    .description("协议版本连接数")
                    .tags(Tags.of("version", version, "type", "connections"))
                    .register(meterRegistry);
        }
    }

    /**
     * 5. EventLoop指标合并
     */
    private void registerEventLoopMetrics() {
        // 待处理任务数
        Gauge.builder("terminal_eventloop_metrics", this, DeviceMetricsService::getEventLoopPendingTasks)
                .description("EventLoop性能指标")
                .tags(Tags.of("type", "pending_tasks"))
                .register(meterRegistry);

        // 告警计数器(用于Grafana rate计算)
        eventLoopWarnings = Counter.builder("terminal_eventloop_warnings_total")
                .description("EventLoop告警总数")
                .tags(Tags.of("level", "warning"))
                .register(meterRegistry);

        eventLoopCriticals = Counter.builder("terminal_eventloop_criticals_total")
                .description("EventLoop严重告警总数")
                .tags(Tags.of("level", "critical"))
                .register(meterRegistry);
    }

    // ============== 指标计算方法 ==============

    private double getCurrentConnectionCount() {
        try {
            return connectionManagerPort.getConnectionCount();
        } catch (Exception e) {
            log.warn("TerminalMetrics -connection- 获取连接数失败", e);
            return 0.0;
        }
    }

    private double getOnlineDeviceCount() {
        try {
            return connectionManagerPort.getOnlineDeviceIds().size();
        } catch (Exception e) {
            log.warn("TerminalMetrics -device- 获取在线设备数失败", e);
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
                log.error("TerminalMetrics -websocket- websocket连接率计算错误: websocket连接数 > 总在线数（0）");
            }

            // 计算WebSocket连接率
            double ratio = (double) websocketConnectionCount / totalOnlineDevices;
            return Math.min(ratio, 1.0); // 确保不超过100%

        } catch (Exception e) {
            log.warn("TerminalMetrics -websocket- 获取websocket连接率失败", e);
            return 0.0; // 出错时返回0，表示无法确定连接率
        }
    }

    private double getConnectionBalance() {
        try {
            if (!(connectionManagerPort instanceof ShardedConnectionManager shardedManager)) {
                // 非分片管理器，默认认为完全均衡
                return 1.0;
            }

            // 使用缓存的分片统计数据
            Map<String, Object> shardStats = getCachedShardStatistics(shardedManager);

            // 获取分片大小数据
            @SuppressWarnings("unchecked")
            Map<Integer, Integer> shardSizes = (Map<Integer, Integer>) shardStats.get("shardSizes");

            if (shardSizes == null || shardSizes.isEmpty()) {
                return 1.0; // 无数据时认为完全均衡
            }

            // 计算负载均衡比例
            return calculateLoadBalanceRatio(shardSizes);

        } catch (Exception e) {
            log.warn("TerminalMetrics -balance- 获取负载均衡比例失败", e);
            return 1.0; // 出错时假设均衡
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
            log.warn("TerminalMetrics -threadpool- 获取线程池利用率失败 poolIndex={}", poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolActiveThreads(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getActiveCount() : 0.0;
        } catch (Exception e) {
            log.debug("TerminalMetricsServiceOptimized - 获取线程池活跃线程数异常, poolIndex: {}", poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolQueueSize(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getQueue().size() : 0.0;
        } catch (Exception e) {
            log.debug("TerminalMetricsServiceOptimized - 获取线程池队列大小异常, poolIndex: {}", poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolCoreThreads(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getCorePoolSize() : 0.0;
        } catch (Exception e) {
            log.debug("TerminalMetricsServiceOptimized - 获取线程池核心线程数异常, poolIndex: {}", poolIndex, e);
            return 0.0;
        }
    }

    private double getThreadPoolMaxThreads(int poolIndex) {
        try {
            ThreadPoolExecutor executor = getThreadPoolExecutor(poolIndex);
            return executor != null ? executor.getMaximumPoolSize() : 0.0;
        } catch (Exception e) {
            log.debug("TerminalMetricsServiceOptimized - 获取线程池最大线程数异常, poolIndex: {}", poolIndex, e);
            return 0.0;
        }
    }

    // 分片相关指标
    private double getShardConnectionCount(int shardId) {
        try {
            if (!(connectionManagerPort instanceof ShardedConnectionManager shardedManager)) {
                return 0.0;
            }

            Map<String, Object> shardStats = getCachedShardStatistics(shardedManager);
            @SuppressWarnings("unchecked")
            Map<Integer, Integer> shardSizes = (Map<Integer, Integer>) shardStats.get("shardSizes");

            return shardSizes != null ? shardSizes.getOrDefault(shardId, 0) : 0.0;

        } catch (Exception e) {
            log.debug("TerminalMetricsServiceOptimized - 获取分片连接数异常, shardId: {}", shardId, e);
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
            log.error("TerminalMetricsServiceOptimized - 获取协议版本连接数异常, version: {}", version, e);
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
        String cleanVersion = version.startsWith("v") ? version.substring(1) : version;

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
            log.warn("TerminalMetrics -eventloop- 获取EventLoop待处理任务失败", e);
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
                        log.debug("TerminalMetrics -threadpool- 线程池执行器不可用: {}", e.getMessage());
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
            log.debug("TerminalMetrics -threadpool- 线程池执行器不可用 poolIndex={}: {}", poolIndex, e.getMessage());
            return null;
        }
    }

    private String getThreadPoolName(ThreadPoolTaskExecutor executor, int index) {
        if (executor == null) {
            return "pool-" + index;
        }

        try {
            String threadNamePrefix = executor.getThreadNamePrefix();
            return threadNamePrefix.replace("-", "");
        } catch (Exception e) {
            return "pool-" + index;
        }
    }

    private Map<String, Object> getCachedShardStatistics(ShardedConnectionManager shardedManager) {
        long currentTime = System.currentTimeMillis();

        Map<String, Object> currentCache = cachedShardStats.get();
        if (currentCache != null && (currentTime - cacheTimestamp.get()) < CACHE_TTL_MS) {
            return currentCache;
        }

        try {
            Map<String, Object> newStats = shardedManager.getShardStatistics();
            cachedShardStats.set(newStats);
            cacheTimestamp.set(currentTime);
            return newStats;
        } catch (Exception e) {
            log.warn("TerminalMetrics -cache- 获取分片统计失败，使用默认值", e);
            // 返回默认的空统计数据
            Map<String, Object> defaultStats = Map.of(
                "totalShards", 0,
                "totalConnections", 0,
                "shardSizes", Map.of()
            );
            cachedShardStats.set(defaultStats);
            cacheTimestamp.set(currentTime);
            return defaultStats;
        }
    }

    /**
     * 计算负载均衡比例
     * 使用变异系数(CV)算法：CV = 标准差/均值
     * 负载均衡比例 = 1.0 - min(CV, 1.0)
     *
     * @param shardSizes 各分片的连接数分布
     * @return 负载均衡比例，范围[0.0, 1.0]，1.0表示完全均衡
     */
    private double calculateLoadBalanceRatio(Map<Integer, Integer> shardSizes) {
        Collection<Integer> sizes = shardSizes.values();
        int shardCount = sizes.size();

        if (shardCount <= 1) {
            return 1.0; // 只有一个分片，认为完全均衡
        }

        // 计算总连接数和平均值
        double totalConnections = sizes.stream().mapToInt(Integer::intValue).sum();
        if (totalConnections == 0) {
            return 1.0; // 无连接时认为完全均衡
        }

        double mean = totalConnections / shardCount;

        // 计算方差
        double variance = sizes.stream()
                .mapToDouble(size -> Math.pow(size - mean, 2))
                .sum() / shardCount;

        // 计算标准差
        double standardDeviation = Math.sqrt(variance);

        // 计算变异系数 (Coefficient of Variation)
        double coefficientOfVariation = mean > 0 ? standardDeviation / mean : 0.0;

        // 负载均衡比例 = 1.0 - min(CV, 1.0)
        // CV越小表示分布越均匀，均衡比例越高
        return Math.max(0.0, 1.0 - Math.min(coefficientOfVariation, 1.0));
    }

    /**
     * 处理EventLoop告警事件
     * 监听EventLoopAlertEvent，根据事件级别更新相应的告警计数器
     */
    @EventListener
    @Async
    public void handleEventLoopAlert(EventLoopAlertEvent event) {
        try {
            if (EventLoopAlertEvent.AlertLevel.WARNING.equals(event.getAlertLevel())) {
                eventLoopWarnings.increment();
                log.debug("TerminalMetrics -eventloop- 收到EventLoop告警事件: {}", event.getMessage());
            } else if (EventLoopAlertEvent.AlertLevel.CRITICAL.equals(event.getAlertLevel())) {
                eventLoopCriticals.increment();
                log.warn("TerminalMetrics -eventloop- 收到EventLoop严重告警事件: {}", event.getMessage());
            }
        } catch (Exception e) {
            log.error("TerminalMetrics -eventloop- 处理EventLoop告警事件失败", e);
        }
    }

    /**
     * 获取所有指标的摘要信息（用于调试和监控）
     */
    public MetricsSummary getMetricsSummary() {
        try {
            return new MetricsSummary(
                getCurrentConnectionCount(),
                getConnectionBalance(),
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
            log.error("TerminalMetrics -summary- 获取指标摘要失败", e);
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
            log.warn("TerminalMetrics -threadpool- 获取平均线程池利用率失败", e);
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
            log.warn("TerminalMetrics -threadpool- 获取线程池队列总大小失败", e);
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
            log.warn("TerminalMetrics -threadpool- 获取活跃线程总数失败", e);
            return 0.0;
        }
    }

    /**
     * 指标摘要数据
     */
    public record MetricsSummary(
        double connectionCount,
        double connectionBalance,
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
            return new MetricsSummary(0, 1.0, 0, 0, 0, 0, 0, 0, 0, 0.0);
        }

        public boolean isHealthy() {
            return eventLoopCriticals == 0
                && threadPoolUtilization < 90.0
                && websocketConnectionRatio >= 0.0; // WebSocket连接率≥0即为正常
        }
    }
}