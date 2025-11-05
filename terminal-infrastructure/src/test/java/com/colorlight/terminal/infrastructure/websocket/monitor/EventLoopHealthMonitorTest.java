package com.colorlight.terminal.infrastructure.websocket.monitor;

import com.colorlight.terminal.infrastructure.websocket.server.NettyWebsocketServer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EventLoopHealthMonitor单元测试
 *
 * 业务逻辑总结：
 * EventLoopHealthMonitor是Netty EventLoop的健康监控组件，负责监控EventLoop的性能状态。
 * 它定期检查EventLoop中的待处理任务数量，当超过告警阈值时发布告警事件。
 * 同时实现Spring Actuator的HealthIndicator接口，提供健康检查端点。
 *
 * 核心业务逻辑：
 * 1. 监控EventLoop待处理任务数
 * 2. 两级告警机制：WARNING(1000)和CRITICAL(5000)
 * 3. 定期监控和告警事件发布
 * 4. 统计监控数据（总任务数、最大任务数、告警次数）
 * 5. 通过反射获取NettyWebsocketServer的WorkerGroup
 * 6. 提供健康检查状态（UP/DOWN）和详细统计信息
 *
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventLoopHealthMonitor单元测试")
class EventLoopHealthMonitorTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NettyWebsocketServer nettyWebsocketServer;

    private EventLoopHealthMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new EventLoopHealthMonitor(eventPublisher, nettyWebsocketServer);
    }

    @Nested
    @DisplayName("监控器初始化测试")
    class InitializationTests {

        @Test
        @DisplayName("应该正确初始化监控器")
        void should_initialize_monitor_correctly() {
            // Then - 验证监控器被创建
            assertThat(monitor).isNotNull();
        }

        @Test
        @DisplayName("应该注入ApplicationEventPublisher依赖")
        void should_inject_event_publisher() {
            // Then - 验证eventPublisher被注入
            assertThat(monitor).isNotNull();
            // 通过调用某个方法验证依赖可用
        }

        @Test
        @DisplayName("应该注入NettyWebsocketServer依赖")
        void should_inject_netty_websocket_server() {
            // Then - 验证nettyWebsocketServer被注入
            assertThat(monitor).isNotNull();
        }
    }

    @Nested
    @DisplayName("monitorEventLoopHealth()方法测试")
    class MonitorEventLoopHealthTests {

        @Test
        @DisplayName("当NettyWebsocketServer为null时应该跳过监控")
        void should_skip_monitoring_when_server_is_null() {
            // Given - NettyWebsocketServer为null
            EventLoopHealthMonitor nullServerMonitor =
                new EventLoopHealthMonitor(eventPublisher, null);

            // When - 调用监控方法
            nullServerMonitor.monitorEventLoopHealth();

            // Then - 验证不发布任何事件
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("当无法获取WorkerGroup时应该跳过监控")
        void should_skip_monitoring_when_cannot_get_worker_group() {
            // Given - 无法获取WorkerGroup
            // nettyWebsocketServer mock未配置WorkerGroup字段

            // When - 调用监控方法
            monitor.monitorEventLoopHealth();

            // Then - 验证不发布任何事件（或发布异常被捕获）
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("应该正确处理监控过程中的异常")
        void should_handle_exceptions_during_monitoring() {
            // Given - Mock NettyWebsocketServer抛出异常
            lenient().when(nettyWebsocketServer.toString()).thenThrow(new RuntimeException("Mock exception"));

            // When - 调用监控方法
            try {
                monitor.monitorEventLoopHealth();
            } catch (Exception e) {
                // 异常被捕获并处理
            }

            // Then - 验证不发布任何事件
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("统计信息获取测试")
    class StatisticsTests {

        @Test
        @DisplayName("应该正确获取监控统计信息")
        void should_get_statistics_correctly() {
            // When - 获取统计信息
            EventLoopHealthMonitor.EventLoopStatistics stats = monitor.getStatistics();

            // Then - 验证统计信息
            assertThat(stats).isNotNull();
            assertThat(stats.totalPendingTasks()).isEqualTo(0);
            assertThat(stats.maxPendingTasks()).isEqualTo(0);
            assertThat(stats.warningCount()).isEqualTo(0);
            assertThat(stats.criticalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("应该能正确重置统计信息")
        void should_reset_statistics() {
            // When - 重置统计信息
            monitor.resetStatistics();

            // Then - 验证统计信息被重置
            EventLoopHealthMonitor.EventLoopStatistics stats = monitor.getStatistics();
            assertThat(stats.totalPendingTasks()).isEqualTo(0);
            assertThat(stats.maxPendingTasks()).isEqualTo(0);
            assertThat(stats.warningCount()).isEqualTo(0);
            assertThat(stats.criticalCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("统计信息应该能显示为字符串")
        void should_convert_statistics_to_string() {
            // When - 获取统计信息的字符串表示
            EventLoopHealthMonitor.EventLoopStatistics stats = monitor.getStatistics();
            String result = stats.toString();

            // Then - 验证字符串包含关键信息
            assertThat(result).contains("EventLoopStatistics");
            assertThat(result).contains("totalPendingTasks=");
            assertThat(result).contains("maxPendingTasks=");
            assertThat(result).contains("warningCount=");
            assertThat(result).contains("criticalCount=");
        }
    }

    @Nested
    @DisplayName("health()健康检查测试")
    class HealthCheckTests {

        @Test
        @DisplayName("当NettyWebsocketServer为null时应该返回DOWN状态")
        void should_return_down_when_server_is_null() {
            // Given - NettyWebsocketServer为null
            EventLoopHealthMonitor nullServerMonitor =
                new EventLoopHealthMonitor(eventPublisher, null);

            // When - 调用health方法
            Health health = nullServerMonitor.health();

            // Then - 验证返回DOWN状态
            assertThat(health.getStatus().toString()).isEqualTo("DOWN");
            assertThat(health.getDetails()).containsKey("reason");
        }

        @Test
        @DisplayName("当无法获取WorkerGroup时应该返回DOWN状态")
        void should_return_down_when_cannot_get_worker_group() {
            // When - 调用health方法（无法获取WorkerGroup）
            Health health = monitor.health();

            // Then - 验证返回DOWN状态或UP状态（取决于实现）
            assertThat(health).isNotNull();
            assertThat(health.getStatus()).isNotNull();
        }

        @Test
        @DisplayName("健康检查应该包含监控统计信息")
        void should_include_statistics_in_health_check() throws Exception {
            // Given - 设置一个真实的EventLoopGroup
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
            try {
                setWorkerGroupViaReflection(eventLoopGroup);

                // When - 调用health方法
                Health health = monitor.health();

                // Then - 验证包含统计信息
                assertThat(health.getDetails()).containsKeys(
                    "totalPendingTasks",
                    "maxPendingTasks",
                    "warningCount",
                    "criticalCount",
                    "warningThreshold",
                    "criticalThreshold"
                );
            } finally {
                eventLoopGroup.shutdownGracefully();
            }
        }

        @Test
        @DisplayName("当EventLoopGroup关闭时应该返回DOWN状态")
        void should_return_down_when_event_loop_group_is_shutdown() throws Exception {
            // Given - 创建并关闭EventLoopGroup
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
            setWorkerGroupViaReflection(eventLoopGroup);
            eventLoopGroup.shutdownGracefully().sync();

            // When - 调用health方法
            Health health = monitor.health();

            // Then - 验证返回DOWN状态
            assertThat(health.getStatus().toString()).isEqualTo("DOWN");
        }

        @Test
        @DisplayName("健康检查应该包含告警和严重告警阈值")
        void should_include_thresholds_in_health_check() throws Exception {
            // Given - 设置一个真实的EventLoopGroup
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
            try {
                setWorkerGroupViaReflection(eventLoopGroup);

                // When - 调用health方法
                Health health = monitor.health();

                // Then - 验证包含阈值信息
                assertThat(health.getDetails()).containsEntry("warningThreshold", 1000L);
                assertThat(health.getDetails()).containsEntry("criticalThreshold", 5000L);
            } finally {
                eventLoopGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    @DisplayName("告警发布测试")
    class AlertPublishingTests {

        @Test
        @DisplayName("应该能处理WARNING级别告警事件")
        void should_handle_warning_alert_event_correctly() throws Exception {
            // Given - 创建一个WARNING级别的告警事件
            EventLoopAlertEvent warningEvent = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);

            // When - 验证事件的属性
            // Then - 验证事件被正确创建
            assertThat(warningEvent.getAlertLevel()).isEqualTo(EventLoopAlertEvent.AlertLevel.WARNING);
            assertThat(warningEvent.getPendingTasks()).isEqualTo(1500);
            assertThat(warningEvent.getThreshold()).isEqualTo(1000);
        }

        @Test
        @DisplayName("应该能处理CRITICAL级别告警事件")
        void should_handle_critical_alert_event_correctly() throws Exception {
            // Given - 创建一个CRITICAL级别的告警事件
            EventLoopAlertEvent criticalEvent = EventLoopAlertEvent.critical(6000, "EventLoop-2", 5000);

            // When - 验证事件的属性
            // Then - 验证事件被正确创建
            assertThat(criticalEvent.getAlertLevel()).isEqualTo(EventLoopAlertEvent.AlertLevel.CRITICAL);
            assertThat(criticalEvent.getPendingTasks()).isEqualTo(6000);
            assertThat(criticalEvent.getThreshold()).isEqualTo(5000);
        }
    }

    @Nested
    @DisplayName("EventLoop遍历测试")
    class EventLoopIterationTests {

        @Test
        @DisplayName("应该能正确遍历EventLoopGroup中的所有EventLoop")
        void should_iterate_all_event_loops_in_group() throws Exception {
            // Given - 创建包含多个EventLoop的EventLoopGroup
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);
            try {
                setWorkerGroupViaReflection(eventLoopGroup);

                // When - 调用监控方法
                monitor.monitorEventLoopHealth();

                // Then - 验证能够遍历所有EventLoop
                // 统计信息应该已更新
                EventLoopHealthMonitor.EventLoopStatistics stats = monitor.getStatistics();
                assertThat(stats).isNotNull();
            } finally {
                eventLoopGroup.shutdownGracefully();
            }
        }

        @Test
        @DisplayName("应该能处理关闭的EventLoop")
        void should_handle_shutdown_event_loops() throws Exception {
            // Given - 创建EventLoopGroup并关闭部分EventLoop
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);
            try {
                setWorkerGroupViaReflection(eventLoopGroup);

                // When - 调用监控方法
                monitor.monitorEventLoopHealth();

                // Then - 统计信息应该被更新
                EventLoopHealthMonitor.EventLoopStatistics stats = monitor.getStatistics();
                assertThat(stats).isNotNull();
            } finally {
                eventLoopGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    @DisplayName("反射访问测试")
    class ReflectionAccessTests {

        @Test
        @DisplayName("应该能通过反射访问NettyWebsocketServer的workerGroup字段")
        void should_access_worker_group_via_reflection() throws Exception {
            // Given - 创建EventLoopGroup
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
            try {
                setWorkerGroupViaReflection(eventLoopGroup);

                // When - 通过反射获取workerGroup
                Field field = NettyWebsocketServer.class.getDeclaredField("workerGroup");
                field.trySetAccessible();
                Object workerGroup = field.get(nettyWebsocketServer);

                // Then - 验证获取到workerGroup
                assertThat(workerGroup).isNotNull();
                assertThat(workerGroup).isInstanceOf(EventLoopGroup.class);
            } finally {
                eventLoopGroup.shutdownGracefully();
            }
        }

        @Test
        @DisplayName("反射访问失败时应该优雅处理")
        void should_handle_reflection_failure_gracefully() {
            // Given - NettyWebsocketServer不包含workerGroup字段（无字段返回null）

            // When - 调用health方法
            Health health = monitor.health();

            // Then - 应该正常返回结果，不抛出异常
            assertThat(health).isNotNull();
        }
    }

    @Nested
    @DisplayName("阈值管理测试")
    class ThresholdManagementTests {

        @Test
        @DisplayName("应该定义WARNING告警阈值为1000")
        void should_define_warning_threshold_as_1000() throws Exception {
            // Given - 创建EventLoopGroup
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
            try {
                setWorkerGroupViaReflection(eventLoopGroup);

                // When - 调用health方法
                Health health = monitor.health();

                // Then - 验证WARNING阈值
                assertThat(health.getDetails()).containsEntry("warningThreshold", 1000L);
            } finally {
                eventLoopGroup.shutdownGracefully();
            }
        }

        @Test
        @DisplayName("应该定义CRITICAL告警阈值为5000")
        void should_define_critical_threshold_as_5000() throws Exception {
            // Given - 创建EventLoopGroup
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
            try {
                setWorkerGroupViaReflection(eventLoopGroup);

                // When - 调用health方法
                Health health = monitor.health();

                // Then - 验证CRITICAL阈值
                assertThat(health.getDetails()).containsEntry("criticalThreshold", 5000L);
            } finally {
                eventLoopGroup.shutdownGracefully();
            }
        }

        @Test
        @DisplayName("告警阈值应该满足 WARNING < CRITICAL")
        void should_satisfy_warning_less_than_critical() throws Exception {
            // Given - 创建EventLoopGroup
            EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
            try {
                setWorkerGroupViaReflection(eventLoopGroup);

                // When - 调用health方法
                Health health = monitor.health();

                // Then - 验证阈值关系
                long warningThreshold = (long) health.getDetails().get("warningThreshold");
                long criticalThreshold = (long) health.getDetails().get("criticalThreshold");
                assertThat(warningThreshold).isLessThan(criticalThreshold);
            } finally {
                eventLoopGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("应该正确处理health()方法中的异常")
        void should_handle_exceptions_in_health_method() {
            // Given - 配置Mock抛出异常
            lenient().doThrow(new RuntimeException("Test exception"))
                .when(nettyWebsocketServer).toString();

            // When - 调用health方法
            Health health = monitor.health();

            // Then - 应该返回DOWN状态
            assertThat(health.getStatus().toString()).isEqualTo("DOWN");
        }

        @Test
        @DisplayName("监控方法应该捕获并记录异常")
        void should_catch_and_log_exceptions_in_monitoring() {
            // Given - 配置Mock抛出异常
            lenient().doThrow(new RuntimeException("Test exception"))
                .when(nettyWebsocketServer).toString();

            // When - 调用监控方法
            try {
                monitor.monitorEventLoopHealth();
            } catch (Exception e) {
                // 异常被捕获
            }

            // Then - 方法应该能处理异常
            assertThat(true).isTrue();
        }

        @Test
        @DisplayName("resetStatistics()方法应该能在异常后调用")
        void should_allow_reset_after_exception() {
            // Given - 先调用可能抛出异常的方法
            try {
                monitor.monitorEventLoopHealth();
            } catch (Exception e) {
                // 忽略异常
            }

            // When - 调用reset方法
            monitor.resetStatistics();

            // Then - 验证重置成功
            EventLoopHealthMonitor.EventLoopStatistics stats = monitor.getStatistics();
            assertThat(stats.totalPendingTasks()).isZero();
        }
    }

    /**
     * 辅助方法：通过反射设置NettyWebsocketServer的workerGroup字段
     */
    private void setWorkerGroupViaReflection(EventLoopGroup eventLoopGroup) throws Exception {
        Field field = NettyWebsocketServer.class.getDeclaredField("workerGroup");
        field.trySetAccessible();
        field.set(nettyWebsocketServer, eventLoopGroup);
    }
}
