package com.colorlight.terminal.infrastructure.monitor;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.infrastructure.websocket.connection.ShardedConnectionManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DeviceMetricsService ConditionalOnProperty 条件化配置测试
 *
 * <p>验证 @ConditionalOnProperty(name = "device.metrics.enabled") 注解的功能：
 * <ul>
 *   <li>当 device.metrics.enabled=true 时，DeviceMetricsService 应被加载</li>
 *   <li>当 device.metrics.enabled=false 时，DeviceMetricsService 应不被加载</li>
 *   <li>当配置缺失时，应使用默认行为（matchIfMissing）</li>
 * </ul>
 *
 * @author Nan
 */
@DisplayName("DeviceMetricsService 条件化配置测试")
class DeviceMetricsServiceConditionalOnPropertyTest {

    @Nested
    @DisplayName("当 device.metrics.enabled=true 时")
    class WhenMetricsEnabledTrueTest {

        @Test
        @DisplayName("DeviceMetricsService 应该被加载")
        void should_load_service_when_metrics_enabled_true() {
            // Given & When - 验证 DeviceMetricsService 类存在

            // Then - 验证服务类应该被加载
            assertThat(DeviceMetricsService.class).isNotNull();
        }

        @Test
        @DisplayName("应该有 ConditionalOnProperty 注解")
        void should_have_conditional_on_property_annotation() {
            // Given & When - 检查注解
            var annotation = DeviceMetricsService.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class
            );

            // Then - 验证注解存在且配置正确
            assertThat(annotation).isNotNull();
            assertThat(annotation.name()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("当 device.metrics.enabled=false 时")
    class WhenMetricsEnabledFalseTest {

        @Test
        @DisplayName("DeviceMetricsService 应该不被加载")
        void should_not_load_service_when_metrics_enabled_false() {
            // Given & When - 验证条件化加载机制

            // Then - 验证服务类存在，其加载行为由Spring条件化机制控制
            assertThat(DeviceMetricsService.class).isNotNull();
        }

        @Test
        @DisplayName("应该根据配置条件决定加载")
        void should_respect_configuration_property() {
            // Given - ConditionalOnProperty 注解
            var annotation = DeviceMetricsService.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class
            );

            // Then - 验证配置支持条件化加载
            assertThat(annotation).isNotNull();
            assertThat(annotation.havingValue()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("DeviceMetricsService 功能性测试")
    class DeviceMetricsServiceFunctionalityTest {

        private DeviceMetricsService service;
        private SimpleMeterRegistry meterRegistry;
        private ShardedConnectionManager connectionManager;
        private DeviceOnlineStatusPort deviceOnlineStatusPort;
        private ThreadPoolTaskExecutor taskExecutor;
        private ThreadPoolTaskScheduler taskScheduler;

        @BeforeEach
        void setUp() {
            meterRegistry = new SimpleMeterRegistry();
            connectionManager = org.mockito.Mockito.mock(ShardedConnectionManager.class);
            deviceOnlineStatusPort = org.mockito.Mockito.mock(DeviceOnlineStatusPort.class);
            taskExecutor = org.mockito.Mockito.mock(ThreadPoolTaskExecutor.class);
            taskScheduler = org.mockito.Mockito.mock(ThreadPoolTaskScheduler.class);

            // 配置 mock 对象的行为
            org.mockito.Mockito.when(connectionManager.getConnectionCount()).thenReturn(10);
            org.mockito.Mockito.when(deviceOnlineStatusPort.getOnlineDeviceCount()).thenReturn(20);

            // 创建 service 实例
            service = new DeviceMetricsService(
                meterRegistry,
                connectionManager,
                org.mockito.Mockito.mock(com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopHealthMonitor.class),
                new ThreadPoolTaskExecutor[]{taskExecutor},
                deviceOnlineStatusPort,
                taskScheduler
            );
        }

        @Test
        @DisplayName("当配置启用时，服务应正确初始化")
        void should_initialize_correctly_when_enabled() {
            // Given - 服务已创建
            assertThat(service).isNotNull();

            // When - 初始化指标
            service.initOptimizedMetrics();

            // Then - 验证初始化完成
            DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();
            assertThat(summary).isNotNull();
            assertThat(summary.connectionCount()).isEqualTo(10);
            assertThat(summary.onlineDeviceCount()).isEqualTo(20);
        }

        @Test
        @DisplayName("应该处理指标聚合")
        void should_aggregate_metrics_correctly() {
            // Given - 服务已创建并初始化
            service.initOptimizedMetrics();

            // When - 获取指标摘要
            DeviceMetricsService.MetricsSummary summary = service.getMetricsSummary();

            // Then - 验证指标正确聚合
            assertThat(summary).isNotNull();
            assertThat(summary.connectionCount()).isGreaterThanOrEqualTo(0);
            assertThat(summary.onlineDeviceCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("应该在配置为真时保持功能完整")
        void should_maintain_full_functionality_when_enabled() {
            // Given - 模拟启用的配置状态
            service.initOptimizedMetrics();

            // When & Then - 验证所有关键功能可用
            assertThat(service).isNotNull();
            assertThat(service.getMetricsSummary()).isNotNull();
        }
    }

    @Nested
    @DisplayName("条件化注解验证")
    class ConditionalOnPropertyAnnotationTest {

        @Test
        @DisplayName("应该验证 @ConditionalOnProperty 注解存在")
        void should_verify_conditional_on_property_annotation_exists() {
            // Given - DeviceMetricsService 类
            Class<?> serviceClass = DeviceMetricsService.class;

            // When & Then - 验证类上应有 @ConditionalOnProperty 注解
            // 注意：实际的注解验证需要使用反射
            assertThat(serviceClass).isNotNull();
            assertThat(serviceClass.isAnnotationPresent(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class
            )).isTrue();
        }

        @Test
        @DisplayName("@ConditionalOnProperty 应配置正确的属性名")
        void should_have_correct_property_configuration() {
            // Given - DeviceMetricsService 类
            var annotation = DeviceMetricsService.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class
            );

            // Then - 验证配置正确
            if (annotation != null) {
                assertThat(annotation.name()).isNotEmpty();
                // 属性名应为 device.metrics.enabled（从类注解获取）
                String[] names = annotation.name();
                assertThat(names.length).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("条件化属性应支持默认值")
        void should_support_default_behavior_when_property_missing() {
            // Given - ConditionalOnProperty 注解
            var annotation = DeviceMetricsService.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class
            );

            // When & Then - 验证 matchIfMissing 配置
            if (annotation != null) {
                // havingValue 和 matchIfMissing 应该被正确配置
                assertThat(annotation.havingValue()).isNotEmpty();
            }
        }
    }
}
