package com.colorlight.terminal.infrastructure.scheduler;

import com.colorlight.terminal.infrastructure.config.properties.WebSocketConfigProperties;
import com.colorlight.terminal.infrastructure.websocket.connection.ShardedConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * WebSocketConnectionStatsScheduler 单元测试
 *
 * 覆盖范围：
 * 1. 统计任务的主流程与异常兜底
 * 2. 应急统计兜底行为
 * 3. 私有工具方法的边界值
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket 连接统计调度")
class WebSocketConnectionStatsSchedulerTest {

    @Mock
    private ShardedConnectionManager shardedConnectionManager;

    private WebSocketConfigProperties webSocketConfig;

    private WebSocketConnectionStatsScheduler scheduler;

    @BeforeEach
    void setUp() {
        webSocketConfig = new WebSocketConfigProperties();
        scheduler = new WebSocketConnectionStatsScheduler(shardedConnectionManager, webSocketConfig);
    }

    @Test
    @DisplayName("全功能开启时应完成统计与清理")
    void should_collect_full_statistics_when_features_enabled() {
        // Given
        webSocketConfig.getStats().setDetailEnabled(true);
        webSocketConfig.getStats().setVisualBarEnabled(true);
        webSocketConfig.getStats().setLoadBalanceAnalysisEnabled(true);
        webSocketConfig.getStats().setLoadBalanceWarningThreshold(1.8);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 4);
        stats.put("totalConnections", 120);
        stats.put("running", true);
        stats.put("versionCount", Map.of("1.0", 80, "1.1", 40));

        Map<Integer, Integer> shardSizes = new HashMap<>();
        shardSizes.put(0, 0);          // 覆盖空连接分支
        shardSizes.put(1, 15);         // 正常展示
        shardSizes.put(2, 620);        // 覆盖上限截断
        stats.put("shardSizes", shardSizes);

        stats.put("maxShardSize", 320);
        stats.put("minShardSize", 0);
        stats.put("loadBalance", 2.4d);

        when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
        when(shardedConnectionManager.cleanupInvalidConnections()).thenReturn(5);

        // When & Then
        assertThatCode(() -> scheduler.logWebSocketConnectionStatistics())
                .doesNotThrowAnyException();

        verify(shardedConnectionManager).getShardStatistics();
        verify(shardedConnectionManager).cleanupInvalidConnections();
    }

    @Test
    @DisplayName("特性关闭时应只输出基础统计")
    void should_skip_optional_sections_when_features_disabled() {
        // Given
        webSocketConfig.getStats().setDetailEnabled(false);
        webSocketConfig.getStats().setVisualBarEnabled(false);
        webSocketConfig.getStats().setLoadBalanceAnalysisEnabled(false);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 2);
        stats.put("totalConnections", 30);
        stats.put("running", false);
        stats.put("versionCount", Map.of());

        when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
        when(shardedConnectionManager.cleanupInvalidConnections()).thenReturn(0);

        // When & Then
        scheduler.logWebSocketConnectionStatistics();

        verify(shardedConnectionManager).getShardStatistics();
        verify(shardedConnectionManager).cleanupInvalidConnections();
        verifyNoMoreInteractions(shardedConnectionManager);
    }

    @Test
    @DisplayName("统计流程出现异常时应被兜底")
    void should_catch_exception_during_statistics_logging() {
        when(shardedConnectionManager.getShardStatistics()).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> scheduler.logWebSocketConnectionStatistics())
                .doesNotThrowAnyException();

        verify(shardedConnectionManager).getShardStatistics();
        verify(shardedConnectionManager, never()).cleanupInvalidConnections();
    }

    @Test
    @DisplayName("应急统计需包含清理逻辑")
    void should_log_emergency_statistics_and_cleanup() {
        Map<String, Object> stats = Map.of(
                "totalShards", 1,
                "totalConnections", 6,
                "running", true,
                "versionCount", Map.of("1.0", 6)
        );

        when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
        when(shardedConnectionManager.cleanupInvalidConnections()).thenReturn(2);

        scheduler.logEmergencyStatistics();

        verify(shardedConnectionManager).getShardStatistics();
        verify(shardedConnectionManager).cleanupInvalidConnections();
    }

    @Test
    @DisplayName("应急统计异常同样需要兜底")
    void should_swallow_exception_in_emergency_statistics() {
        when(shardedConnectionManager.getShardStatistics()).thenThrow(new RuntimeException("unexpected"));

        assertThatCode(() -> scheduler.logEmergencyStatistics())
                .doesNotThrowAnyException();

        verify(shardedConnectionManager).getShardStatistics();
        verify(shardedConnectionManager, never()).cleanupInvalidConnections();
    }

    @Nested
    @DisplayName("私有工具方法")
    class PrivateMethodTests {

        @Test
        @DisplayName("连接条图生成应处理边界")
        void should_generate_connection_bar_by_connections() throws Exception {
            Method method = WebSocketConnectionStatsScheduler.class
                    .getDeclaredMethod("generateConnectionBar", int.class);
            method.setAccessible(true);

            String zero = (String) method.invoke(scheduler, 0);
            String small = (String) method.invoke(scheduler, 8);
            String capped = (String) method.invoke(scheduler, 800);

            assertThat(zero).isEmpty();
            assertThat(small).hasSizeGreaterThanOrEqualTo(1);
            assertThat(capped).hasSize(50);
        }

        @Test
        @DisplayName("负载均衡状态应按阈值分类")
        void should_classify_load_balance_status() throws Exception {
            Method method = WebSocketConnectionStatsScheduler.class
                    .getDeclaredMethod("getLoadBalanceStatus", double.class);
            method.setAccessible(true);

            List<Double> samples = List.of(1.0, 1.3, 1.7, 2.5, 3.5);
            Set<String> statuses = new LinkedHashSet<>();

            for (Double sample : samples) {
                String status = (String) method.invoke(scheduler, sample);
                assertThat(status).isNotBlank();
                statuses.add(status);
            }

            assertThat(statuses).hasSize(5);
        }
    }
}
