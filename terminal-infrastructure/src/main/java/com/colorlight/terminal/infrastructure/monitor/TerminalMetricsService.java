package com.colorlight.terminal.infrastructure.monitor;

import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
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
public class TerminalMetricsService {

    private final MeterRegistry meterRegistry;
    private final ConnectionManagerPort connectionManagerPort;
    private final EventLoopHealthMonitor eventLoopHealthMonitor;
    private final ThreadPoolTaskExecutor[] taskExecutors;
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;

    /*============== 需要后续访问的指标字段 ==============*/
    /** EventLoop告警计数器 - 需要在事件处理中increment() */
    private Counter eventLoopWarnings;
    private Counter eventLoopCriticals;

    /*============== 其他指标在initMetrics中注册后无需保留引用 ==============*/
    // WebSocket连接指标、EventLoop待处理任务、线程池指标、设备指标
    // 这些Gauge通过方法引用自动获取值，无需保留字段引用

    /*============== 缓存机制 ==============*/
    /** 分片统计缓存 - 避免频繁计算 */
    private final AtomicReference<Map<String, Object>> cachedShardStats = new AtomicReference<>();
    /** 缓存时间戳 */
    private final AtomicLong cacheTimestamp = new AtomicLong(0);
    /** 缓存有效期(毫秒) - 5秒 */
    private static final long CACHE_TTL_MS = 5000;

    @PostConstruct
    public void initMetrics() {
        try {
            // WebSocket连接指标
            Gauge.builder("terminal.websocket.connections", this, TerminalMetricsService::getCurrentConnectionCount)
                    .description("当前WebSocket连接数")
                    .tags(Tags.of("type", "websocket"))
                    .register(meterRegistry);

            Gauge.builder("terminal.websocket.balance_ratio", this, TerminalMetricsService::getConnectionBalance)
                    .description("分片负载均衡比例")
                    .tags(Tags.of("type", "balance"))
                    .register(meterRegistry);

            // EventLoop性能指标
            Gauge.builder("terminal.eventloop.pending_tasks", this, TerminalMetricsService::getEventLoopPendingTasks)
                    .description("EventLoop待处理任务数")
                    .tags(Tags.of("type", "pending"))
                    .register(meterRegistry);

            eventLoopWarnings = Counter.builder("terminal.eventloop.warnings_total")
                    .description("EventLoop告警总数")
                    .tags(Tags.of("level", "warning"))
                    .register(meterRegistry);

            eventLoopCriticals = Counter.builder("terminal.eventloop.criticals_total")
                    .description("EventLoop严重告警总数")
                    .tags(Tags.of("level", "critical"))
                    .register(meterRegistry);

            // 线程池指标
            Gauge.builder("terminal.threadpool.utilization_percent", this, TerminalMetricsService::getThreadPoolUtilization)
                    .description("线程池平均利用率")
                    .tags(Tags.of("type", "utilization"))
                    .register(meterRegistry);

            Gauge.builder("terminal.threadpool.queue_size", this, TerminalMetricsService::getThreadPoolQueueSize)
                    .description("线程池队列大小")
                    .tags(Tags.of("type", "queue"))
                    .register(meterRegistry);

            Gauge.builder("terminal.threadpool.active_threads", this, TerminalMetricsService::getActiveThreads)
                    .description("线程池活跃线程数")
                    .tags(Tags.of("type", "active"))
                    .register(meterRegistry);

            // 设备指标
            Gauge.builder("terminal.devices.online_count", this, TerminalMetricsService::getOnlineDeviceCount)
                    .description("在线设备数量")
                    .tags(Tags.of("type", "online"))
                    .register(meterRegistry);

            Gauge.builder("terminal.devices.websocket_connection_ratio", this, TerminalMetricsService::getWebsocketConnectionRatio)
                    .description("在线设备WebSocket连接率")
                    .tags(Tags.of("type", "websocket_ratio"))
                    .register(meterRegistry);

            log.info("TerminalMetrics -init- 指标初始化成功");
        } catch (Exception e) {
            log.error("TerminalMetrics -init- 指标初始化失败", e);
            throw new TechnicalException(TechErrorCode.METRICS_ERROR, "Metrics初始化失败", e);
        }
    }

    /**
     * 获取当前连接数
     */
    private double getCurrentConnectionCount() {
        try {
            return connectionManagerPort.getConnectionCount();
        } catch (Exception e) {
            log.warn("TerminalMetrics -connection- 获取连接数失败", e);
            return 0.0;
        }
    }

    /**
     * 获取连接负载均衡比例
     * 计算各分片间连接数的均衡程度，值越接近1.0表示负载越均衡
     * 使用变异系数算法：balance_ratio = 1.0 - coefficient_of_variation
     */
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
     * 获取缓存的分片统计数据
     * 实现5秒缓存TTL，避免频繁计算分片统计
     * 
     * @param shardedManager 分片连接管理器
     * @return 分片统计数据
     */
    private Map<String, Object> getCachedShardStatistics(ShardedConnectionManager shardedManager) {
        long currentTime = System.currentTimeMillis();
        
        // 检查缓存是否有效
        Map<String, Object> currentCache = cachedShardStats.get();
        if (currentCache != null && (currentTime - cacheTimestamp.get()) < CACHE_TTL_MS) {
            return currentCache;
        }
        
        // 缓存过期或不存在，重新获取统计数据
        try {
            Map<String, Object> newStats = shardedManager.getShardStatistics();
            cachedShardStats.set(newStats);
            cacheTimestamp.set(currentTime);
            log.debug("TerminalMetrics -cache- 更新分片统计缓存, 缓存时间: {}", currentTime);
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
     * 获取EventLoop待处理任务数
     */
    private double getEventLoopPendingTasks() {
        try {
            EventLoopHealthMonitor.EventLoopStatistics statistics = eventLoopHealthMonitor.getStatistics();
            return statistics.totalPendingTasks();
        } catch (Exception e) {
            log.warn("TerminalMetrics -eventloop- 获取EventLoop待处理任务失败", e);
            return 0.0;
        }
    }

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

    /**
     * 获取线程池平均利用率
     */
    private double getThreadPoolUtilization() {
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
            log.warn("TerminalMetrics -threadpool- 获取线程池利用率失败", e);
            return 0.0;
        }
    }

    /**
     * 获取线程池队列大小
     */
    private double getThreadPoolQueueSize() {
        try {
            if (taskExecutors == null || taskExecutors.length == 0) {
                return 0.0;
            }

            return getValidThreadPoolExecutors()
                    .mapToInt(executor -> executor.getQueue().size())
                    .sum();
        } catch (Exception e) {
            log.warn("TerminalMetrics -threadpool- 获取线程池队列大小失败", e);
            return 0.0;
        }
    }

    /**
     * 获取活跃线程数
     */
    private double getActiveThreads() {
        try {
            if (taskExecutors == null || taskExecutors.length == 0) {
                return 0.0;
            }

            return getValidThreadPoolExecutors()
                    .mapToInt(ThreadPoolExecutor::getActiveCount)
                    .sum();
        } catch (Exception e) {
            log.warn("TerminalMetrics -threadpool- 获取活跃线程数失败", e);
            return 0.0;
        }
    }

    /**
     * 获取在线设备数量
     */
    private double getOnlineDeviceCount() {
        try {
            return connectionManagerPort.getOnlineDeviceIds().size();
        } catch (Exception e) {
            log.warn("TerminalMetrics -device- 获取在线设备数失败", e);
            return 0.0;
        }
    }

    /**
     * 获取在线设备WebSocket连接率
     * 计算在线设备中有多少比例通过WebSocket连接（省流量模式）
     * 连接率 = WebSocket连接设备数 / 总在线设备数
     */
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
                getThreadPoolUtilization(),
                getThreadPoolQueueSize(),
                getActiveThreads(),
                getOnlineDeviceCount(),
                getWebsocketConnectionRatio()
            );
        } catch (Exception e) {
            log.error("TerminalMetrics -summary- 获取指标摘要失败", e);
            return MetricsSummary.empty();
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
