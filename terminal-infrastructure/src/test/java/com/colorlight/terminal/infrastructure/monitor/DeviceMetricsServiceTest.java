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
}
