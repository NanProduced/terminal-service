package com.colorlight.terminal.infrastructure.monitor;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.infrastructure.websocket.connection.ShardedConnectionManager;
import com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopAlertEvent;
import com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopHealthMonitor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DeviceMetricsService 的指标聚合逻辑测试
 */
@DisplayName("DeviceMetricsService 指标聚合逻辑测试")
class DeviceMetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private ShardedConnectionManager connectionManager;
    private EventLoopHealthMonitor eventLoopHealthMonitor;
    private DeviceOnlineStatusPort deviceOnlineStatusPort;
    private ThreadPoolTaskExecutor taskExecutor;
    private ThreadPoolTaskScheduler taskScheduler;
    private ThreadPoolExecutor threadPoolExecutor;
    private BlockingQueue<Runnable> workerQueue;
    private ScheduledThreadPoolExecutor schedulerExecutor;
    private BlockingQueue<Runnable> schedulerQueue;
    private DeviceMetricsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        connectionManager = mock(ShardedConnectionManager.class);
        eventLoopHealthMonitor = mock(EventLoopHealthMonitor.class);
        deviceOnlineStatusPort = mock(DeviceOnlineStatusPort.class);

        taskExecutor = mock(ThreadPoolTaskExecutor.class);
        threadPoolExecutor = mock(ThreadPoolExecutor.class);
        workerQueue = mock(BlockingQueue.class);
        when(threadPoolExecutor.isShutdown()).thenReturn(false);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(8);
        when(threadPoolExecutor.getActiveCount()).thenReturn(4);
        when(threadPoolExecutor.getQueue()).thenReturn(workerQueue);
        when(workerQueue.size()).thenReturn(3);
        when(threadPoolExecutor.getPoolSize()).thenReturn(5);
        when(threadPoolExecutor.getTaskCount()).thenReturn(10L);
        when(threadPoolExecutor.getCompletedTaskCount()).thenReturn(6L);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(3);

        when(taskExecutor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        when(taskExecutor.getThreadNamePrefix()).thenReturn("broadcast-");
        when(taskExecutor.getQueueCapacity()).thenReturn(20);

        taskScheduler = mock(ThreadPoolTaskScheduler.class);
        schedulerExecutor = mock(ScheduledThreadPoolExecutor.class);
        schedulerQueue = mock(BlockingQueue.class);
        when(taskScheduler.getScheduledThreadPoolExecutor()).thenReturn(schedulerExecutor);
        when(taskScheduler.getPoolSize()).thenReturn(2);
        when(schedulerExecutor.getActiveCount()).thenReturn(1);
        when(schedulerExecutor.getQueue()).thenReturn(schedulerQueue);
        when(schedulerQueue.size()).thenReturn(2);
        when(schedulerExecutor.getPoolSize()).thenReturn(2);
        when(schedulerExecutor.getTaskCount()).thenReturn(14L);
        when(schedulerExecutor.getCompletedTaskCount()).thenReturn(12L);

        Map<Integer, Integer> shardSizes = new HashMap<>();
        shardSizes.put(0, 3);
        shardSizes.put(1, 0);
        Map<String, Object> shardStats = new HashMap<>();
        shardStats.put("shardSizes", shardSizes);
        when(connectionManager.getShardStatistics()).thenReturn(shardStats);

        when(connectionManager.getConnectionCount()).thenReturn(12);
        when(connectionManager.getProtocolVersionConnectionCount(ProtocolVersion.V1_0)).thenReturn(7);
        when(connectionManager.getProtocolVersionConnectionCount(ProtocolVersion.V1_1)).thenReturn(5);
        when(deviceOnlineStatusPort.getOnlineDeviceCount()).thenReturn(24);
        when(eventLoopHealthMonitor.getStatistics())
                .thenReturn(new EventLoopHealthMonitor.EventLoopStatistics(15, 20, 2, 1));

        service = new DeviceMetricsService(
                meterRegistry,
                connectionManager,
                eventLoopHealthMonitor,
                new ThreadPoolTaskExecutor[]{taskExecutor},
                deviceOnlineStatusPort,
                taskScheduler
        );

        service.initOptimizedMetrics();
    }

    @Test
    @DisplayName("应根据依赖统计生成指标摘要")
    void should_calculate_metrics_summary_based_on_dependencies() {
        assertThat(connectionManager.getConnectionCount()).isEqualTo(12);
        assertThat(deviceOnlineStatusPort.getOnlineDeviceCount()).isEqualTo(24);

        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        assertThat(summary.connectionCount()).isEqualTo(12);
        assertThat(summary.eventLoopPendingTasks()).isEqualTo(15);
        assertThat(summary.eventLoopWarnings()).isZero();
        assertThat(summary.eventLoopCriticals()).isZero();
        assertThat(summary.threadPoolUtilization()).isEqualTo(50.0);
        assertThat(summary.threadPoolQueueSize()).isEqualTo(3.0);
        assertThat(summary.activeThreads()).isEqualTo(4.0);
        assertThat(summary.onlineDeviceCount()).isEqualTo(24);
        assertThat(summary.websocketConnectionRatio()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("应完成指标注册并响应 EventLoop 告警")
    void should_register_metrics_and_handle_event_loop_alerts() {
        Map<Integer, Integer> shardSizes = new HashMap<>();
        shardSizes.put(0, 3);
        shardSizes.put(1, 4);
        Map<String, Object> shardStats = new HashMap<>();
        shardStats.put("shardSizes", shardSizes);
        when(connectionManager.getShardStatistics()).thenReturn(shardStats);
        when(connectionManager.getProtocolVersionConnectionCount(ProtocolVersion.V1_0)).thenReturn(7);
        when(connectionManager.getProtocolVersionConnectionCount(ProtocolVersion.V1_1)).thenReturn(5);

        service.initOptimizedMetrics();

        double onlineDevices = meterRegistry.get(MetricsConstant.TERMINAL_SYSTEM_METRICS)
                .tag(MetricsConstant.TagKey.TYPE, MetricsConstant.SystemType.ONLINE_DEVICES)
                .gauge().value();
        assertThat(onlineDevices).isEqualTo(24);

        double shard0 = meterRegistry.get(MetricsConstant.TERMINAL_SHARD_METRICS)
                .tag(MetricsConstant.TagKey.SHARD_ID, "0")
                .tag(MetricsConstant.TagKey.TYPE, MetricsConstant.ShardType.CONNECTIONS)
                .gauge().value();
        assertThat(shard0).isEqualTo(3);

        double protocolV10 = meterRegistry.get(MetricsConstant.TERMINAL_PROTOCOL_METRICS)
                .tag(MetricsConstant.TagKey.VERSION, MetricsConstant.ProtocolVersions.V1_0)
                .tag(MetricsConstant.TagKey.TYPE, MetricsConstant.ProtocolType.CONNECTIONS)
                .gauge().value();
        assertThat(protocolV10).isEqualTo(7);

        double utilization = meterRegistry.get(MetricsConstant.TERMINAL_THREADPOOL_METRICS)
                .tag(MetricsConstant.TagKey.POOL, "broadcast")
                .tag(MetricsConstant.TagKey.TYPE, MetricsConstant.ThreadPoolType.UTILIZATION)
                .gauge().value();
        assertThat(utilization).isEqualTo(50.0);

        service.handleEventLoopAlert(EventLoopAlertEvent.warning(1200, "loop-1", 1000));
        service.handleEventLoopAlert(EventLoopAlertEvent.critical(6000, "loop-1", 5000));

        double warningCount = meterRegistry.get(MetricsConstant.TERMINAL_EVENTLOOP_WARNINGS_TOTAL)
                .tag(MetricsConstant.TagKey.LEVEL, MetricsConstant.AlertLevel.WARNING)
                .counter().count();
        double criticalCount = meterRegistry.get(MetricsConstant.TERMINAL_EVENTLOOP_CRITICALS_TOTAL)
                .tag(MetricsConstant.TagKey.LEVEL, MetricsConstant.AlertLevel.CRITICAL)
                .counter().count();

        assertThat(warningCount).isEqualTo(1);
        assertThat(criticalCount).isEqualTo(1);
    }

    @Test
    @DisplayName("EventLoop 统计异常时应返回安全默认值")
    void should_use_safe_defaults_when_monitor_throws() {
        when(eventLoopHealthMonitor.getStatistics())
                .thenThrow(new RuntimeException("monitor failed"));

        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        assertThat(summary.eventLoopPendingTasks()).isZero();
        assertThat(summary.eventLoopWarnings()).isZero();
        assertThat(summary.eventLoopCriticals()).isZero();
    }

    @Test
    @DisplayName("应处理线程池利用率为100%的边界情况")
    void should_handle_thread_pool_utilization_at_100_percent() {
        // Given - 活跃线程等于最大线程数
        when(threadPoolExecutor.getActiveCount()).thenReturn(8);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(8);

        // When
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        // Then
        assertThat(summary.threadPoolUtilization()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("应处理线程池利用率为0%的边界情况")
    void should_handle_thread_pool_utilization_at_0_percent() {
        // Given - 活跃线程为0
        when(threadPoolExecutor.getActiveCount()).thenReturn(0);

        // When
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        // Then
        assertThat(summary.threadPoolUtilization()).isZero();
    }

    @Test
    @DisplayName("应处理WebSocket连接率为1.0的情况")
    void should_handle_websocket_connection_ratio_at_1_dot_0() {
        // Given - WebSocket连接数等于在线设备数
        when(connectionManager.getConnectionCount()).thenReturn(24);
        when(deviceOnlineStatusPort.getOnlineDeviceCount()).thenReturn(24);

        // When
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        // Then
        assertThat(summary.websocketConnectionRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("应处理WebSocket连接率为0.0的情况")
    void should_handle_websocket_connection_ratio_at_0_dot_0() {
        // Given - 没有WebSocket连接
        when(connectionManager.getConnectionCount()).thenReturn(0);

        // When
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        // Then
        assertThat(summary.websocketConnectionRatio()).isZero();
    }

    @Test
    @DisplayName("应处理零在线设备的情况")
    void should_handle_zero_online_devices() {
        // Given - 重新配置所有依赖以确保零设备和零连接
        when(deviceOnlineStatusPort.getOnlineDeviceCount()).thenReturn(0);
        when(connectionManager.getConnectionCount()).thenReturn(0);

        // When
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        // Then
        assertThat(summary.onlineDeviceCount()).isZero();
        assertThat(summary.websocketConnectionRatio()).isZero();
    }

    @Test
    @DisplayName("应正确处理多个告警事件并累计")
    void should_correctly_handle_multiple_alert_events_and_accumulate() {
        // When - 发送多个告警事件
        service.handleEventLoopAlert(EventLoopAlertEvent.warning(1000, "loop-1", 500));
        service.handleEventLoopAlert(EventLoopAlertEvent.warning(1200, "loop-2", 600));
        service.handleEventLoopAlert(EventLoopAlertEvent.critical(5000, "loop-1", 4000));
        service.handleEventLoopAlert(EventLoopAlertEvent.critical(6000, "loop-2", 5000));

        // Then - 验证计数器累计
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();
        assertThat(summary.eventLoopWarnings()).isEqualTo(2);
        assertThat(summary.eventLoopCriticals()).isEqualTo(2);
    }

    @Test
    @DisplayName("应处理分片配置为空的情况")
    void should_handle_empty_shard_configuration() {
        // Given - 空的分片统计，但总连接数还是从setUp()的12
        Map<Integer, Integer> shardSizes = new HashMap<>();
        Map<String, Object> shardStats = new HashMap<>();
        shardStats.put("shardSizes", shardSizes);
        when(connectionManager.getShardStatistics()).thenReturn(shardStats);

        // When & Then - 应该不抛出异常，能够处理空分片配置
        service.initOptimizedMetrics();

        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();
        // 总连接数仍然是12（从setUp()），即使分片为空
        assertThat(summary.connectionCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("应处理协议版本没有连接的情况")
    void should_handle_protocol_versions_with_no_connections() {
        // Given - 两个协议版本都没有连接，同时设置总连接数为0
        when(connectionManager.getProtocolVersionConnectionCount(ProtocolVersion.V1_0)).thenReturn(0);
        when(connectionManager.getProtocolVersionConnectionCount(ProtocolVersion.V1_1)).thenReturn(0);
        when(connectionManager.getConnectionCount()).thenReturn(0);

        // When
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        // Then
        assertThat(summary.connectionCount()).isZero();
    }

    @Test
    @DisplayName("应正确报告v1.0协议版本的连接数")
    void should_correctly_report_v1_0_protocol_connections() {
        // Given - setUp()配置中v1.0有7个连接
        // When
        service.initOptimizedMetrics();

        // Then
        double v10Count = meterRegistry.get(MetricsConstant.TERMINAL_PROTOCOL_METRICS)
                .tag(MetricsConstant.TagKey.VERSION, MetricsConstant.ProtocolVersions.V1_0)
                .tag(MetricsConstant.TagKey.TYPE, MetricsConstant.ProtocolType.CONNECTIONS)
                .gauge().value();
        assertThat(v10Count).isEqualTo(7);
    }

    @Test
    @DisplayName("应处理最大线程数为0的极端情况")
    void should_handle_maximum_pool_size_of_zero() {
        // Given - 设置最大线程数为0
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(0);
        when(threadPoolExecutor.getActiveCount()).thenReturn(0);

        // When & Then - 应该不抛出异常
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();
        assertThat(summary.threadPoolUtilization()).isZero();
    }

    @Test
    @DisplayName("应处理队列大小与容量的关系")
    void should_correctly_report_queue_utilization() {
        // When - 获取当前队列大小（从setUp()返回的是3）
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

        // Then - 验证队列大小正确反映（队列中有3个任务）
        assertThat(summary.threadPoolQueueSize()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("应正确处理多个分片的连接数分布")
    void should_correctly_handle_multiple_shards_connection_distribution() {
        // Given - 配置多个分片的连接数
        Map<Integer, Integer> shardSizes = new HashMap<>();
        shardSizes.put(0, 10);
        shardSizes.put(1, 8);
        shardSizes.put(2, 6);
        shardSizes.put(3, 0);  // 空分片
        Map<String, Object> shardStats = new HashMap<>();
        shardStats.put("shardSizes", shardSizes);
        when(connectionManager.getShardStatistics()).thenReturn(shardStats);

        // When
        service.initOptimizedMetrics();

        // Then
        double shard0 = meterRegistry.get(MetricsConstant.TERMINAL_SHARD_METRICS)
                .tag(MetricsConstant.TagKey.SHARD_ID, "0")
                .tag(MetricsConstant.TagKey.TYPE, MetricsConstant.ShardType.CONNECTIONS)
                .gauge().value();
        double shard3 = meterRegistry.get(MetricsConstant.TERMINAL_SHARD_METRICS)
                .tag(MetricsConstant.TagKey.SHARD_ID, "3")
                .tag(MetricsConstant.TagKey.TYPE, MetricsConstant.ShardType.CONNECTIONS)
                .gauge().value();

        assertThat(shard0).isEqualTo(10);
        assertThat(shard3).isZero();
    }

    @Test
    @DisplayName("应处理连续告警事件的计数器准确性")
    void should_accurately_count_consecutive_alert_events() {
        // When - 发送相同类型的连续告警
        for (int i = 0; i < 5; i++) {
            service.handleEventLoopAlert(EventLoopAlertEvent.warning(1000 + i * 100, "loop-" + i, 500));
        }

        // Then
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();
        assertThat(summary.eventLoopWarnings()).isEqualTo(5);
    }

    @Test
    @DisplayName("应分别计数警告和严重告警事件")
    void should_separately_count_warning_and_critical_alerts() {
        // When - 交替发送警告和严重告警
        service.handleEventLoopAlert(EventLoopAlertEvent.warning(1000, "loop-1", 500));
        service.handleEventLoopAlert(EventLoopAlertEvent.critical(5000, "loop-1", 4000));
        service.handleEventLoopAlert(EventLoopAlertEvent.warning(1100, "loop-2", 600));
        service.handleEventLoopAlert(EventLoopAlertEvent.critical(5100, "loop-2", 4100));

        // Then
        DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();
        assertThat(summary.eventLoopWarnings()).isEqualTo(2);
        assertThat(summary.eventLoopCriticals()).isEqualTo(2);
    }
}
