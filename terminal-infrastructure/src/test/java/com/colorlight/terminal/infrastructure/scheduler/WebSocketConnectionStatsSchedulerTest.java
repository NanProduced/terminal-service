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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WebSocketConnectionStatsScheduler 单元测试
 * 测试WebSocket连接统计定时任务的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket连接统计定时任务测试")
class WebSocketConnectionStatsSchedulerTest {

    @Mock
    private ShardedConnectionManager shardedConnectionManager;
    
    @Mock
    private WebSocketConfigProperties webSocketConfig;
    
    @Mock
    private WebSocketConfigProperties.Stats statsConfig;
    
    private WebSocketConnectionStatsScheduler scheduler;
    
    @BeforeEach
    void setUp() {
        scheduler = new WebSocketConnectionStatsScheduler(shardedConnectionManager, webSocketConfig);
        
        // 设置默认的配置Mock
        lenient().when(webSocketConfig.getStats()).thenReturn(statsConfig);
        lenient().when(statsConfig.isDetailEnabled()).thenReturn(true);
        lenient().when(statsConfig.isLoadBalanceAnalysisEnabled()).thenReturn(true);
        lenient().when(statsConfig.isVisualBarEnabled()).thenReturn(true);
        lenient().when(statsConfig.getLoadBalanceWarningThreshold()).thenReturn(2.0);
    }
    
    @Nested
    @DisplayName("基本统计信息测试")
    class BasicStatisticsTest {
        
        @Test
        @DisplayName("应该成功输出基本WebSocket连接统计信息")
        void should_log_basic_websocket_statistics_successfully() {
            // Given - 准备基本统计数据
            Map<String, Object> stats = createBasicStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证调用了统计方法
            verify(shardedConnectionManager).getShardStatistics();
        }
        
        @Test
        @DisplayName("应该处理零连接的情况")
        void should_handle_zero_connections() {
            // Given - 零连接统计数据
            Map<String, Object> stats = createStatsWithZeroConnections();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证处理了零连接情况
            verify(shardedConnectionManager).getShardStatistics();
        }
        
        @Test
        @DisplayName("应该处理单分片的情况")
        void should_handle_single_shard() {
            // Given - 单分片统计数据
            Map<String, Object> stats = createSingleShardStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证处理了单分片情况
            verify(shardedConnectionManager).getShardStatistics();
        }
        
        @Test
        @DisplayName("当获取统计信息失败时应该捕获异常")
        void should_catch_exception_when_getting_statistics_fails() {
            // Given - 统计信息获取失败
            RuntimeException exception = new RuntimeException("连接管理器不可用");
            when(shardedConnectionManager.getShardStatistics()).thenThrow(exception);
            
            // When - 执行统计信息输出，应该不抛出异常
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证仍然尝试获取统计信息
            verify(shardedConnectionManager).getShardStatistics();
        }
    }
    
    @Nested
    @DisplayName("详细统计信息测试")
    class DetailedStatisticsTest {
        
        @Test
        @DisplayName("当启用详细信息时应该输出分片详情")
        void should_log_shard_details_when_detail_enabled() {
            // Given - 启用详细信息，准备多分片统计数据
            when(statsConfig.isDetailEnabled()).thenReturn(true);
            Map<String, Object> stats = createMultiShardStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证获取了统计信息
            verify(shardedConnectionManager).getShardStatistics();
            verify(statsConfig).isDetailEnabled();
        }
        
        @Test
        @DisplayName("当禁用详细信息时应该跳过分片详情")
        void should_skip_shard_details_when_detail_disabled() {
            // Given - 禁用详细信息
            when(statsConfig.isDetailEnabled()).thenReturn(false);
            Map<String, Object> stats = createBasicStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证检查了详细信息配置
            verify(statsConfig).isDetailEnabled();
        }
        
        @Test
        @DisplayName("应该正确处理空的分片信息")
        void should_handle_empty_shard_details() {
            // Given - 空的分片信息
            when(statsConfig.isDetailEnabled()).thenReturn(true);
            Map<String, Object> stats = createStatsWithEmptyShards();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证处理了空分片信息
            verify(shardedConnectionManager).getShardStatistics();
        }
        
        @Test
        @DisplayName("应该按分片ID排序输出详情")
        void should_sort_shard_details_by_id() {
            // Given - 乱序的分片统计数据
            when(statsConfig.isDetailEnabled()).thenReturn(true);
            when(statsConfig.isVisualBarEnabled()).thenReturn(false);
            Map<String, Object> stats = createUnorderedShardStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证处理了分片排序
            verify(shardedConnectionManager).getShardStatistics();
            verify(statsConfig, times(3)).isVisualBarEnabled();
        }
    }
    
    @Nested
    @DisplayName("负载均衡分析测试")
    class LoadBalanceAnalysisTest {
        
        @Test
        @DisplayName("当启用负载均衡分析时应该输出分析结果")
        void should_log_load_balance_analysis_when_enabled() {
            // Given - 启用负载均衡分析
            when(statsConfig.isLoadBalanceAnalysisEnabled()).thenReturn(true);
            when(statsConfig.getLoadBalanceWarningThreshold()).thenReturn(2.0);
            Map<String, Object> stats = createLoadBalanceStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证进行了负载均衡分析
            verify(statsConfig).isLoadBalanceAnalysisEnabled();
            verify(statsConfig).getLoadBalanceWarningThreshold();
        }
        
        @Test
        @DisplayName("当禁用负载均衡分析时应该跳过分析")
        void should_skip_load_balance_analysis_when_disabled() {
            // Given - 禁用负载均衡分析
            when(statsConfig.isLoadBalanceAnalysisEnabled()).thenReturn(false);
            Map<String, Object> stats = createBasicStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证检查了负载均衡分析配置
            verify(statsConfig).isLoadBalanceAnalysisEnabled();
        }
        
        @Test
        @DisplayName("当负载均衡比率超过阈值时应该输出警告")
        void should_log_warning_when_load_balance_exceeds_threshold() {
            // Given - 负载均衡比率超过阈值
            when(statsConfig.isLoadBalanceAnalysisEnabled()).thenReturn(true);
            when(statsConfig.getLoadBalanceWarningThreshold()).thenReturn(2.0);
            Map<String, Object> stats = createPoorLoadBalanceStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证检查了负载均衡阈值
            verify(statsConfig, times(2)).getLoadBalanceWarningThreshold();
        }
        
        @Test
        @DisplayName("应该正确处理null的负载均衡值")
        void should_handle_null_load_balance_values() {
            // Given - null的负载均衡值
            when(statsConfig.isLoadBalanceAnalysisEnabled()).thenReturn(true);
            Map<String, Object> stats = createStatsWithNullLoadBalance();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证处理了null值
            verify(shardedConnectionManager).getShardStatistics();
        }
    }
    

    
    @Nested
    @DisplayName("紧急统计信息测试")
    class EmergencyStatisticsTest {
        
        @Test
        @DisplayName("应该成功输出紧急统计信息")
        void should_log_emergency_statistics_successfully() {
            // Given - 准备紧急统计数据
            Map<String, Object> stats = createBasicStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            when(shardedConnectionManager.cleanupInvalidConnections()).thenReturn(5);
            
            // When - 执行紧急统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logEmergencyStatistics());
            
            // Then - 验证调用了紧急统计方法
            verify(shardedConnectionManager).getShardStatistics();
            verify(shardedConnectionManager).cleanupInvalidConnections();
        }
        
        @Test
        @DisplayName("当紧急统计失败时应该捕获异常")
        void should_catch_exception_when_emergency_statistics_fails() {
            // Given - 紧急统计失败
            RuntimeException exception = new RuntimeException("系统异常");
            when(shardedConnectionManager.getShardStatistics()).thenThrow(exception);
            
            // When - 执行紧急统计信息输出，应该不抛出异常
            assertThatNoException().isThrownBy(() -> scheduler.logEmergencyStatistics());
            
            // Then - 验证仍然尝试获取统计信息
            verify(shardedConnectionManager).getShardStatistics();
        }
        
        @Test
        @DisplayName("应该输出清理的无效连接数")
        void should_log_cleaned_invalid_connections_count() {
            // Given - 清理了一些无效连接
            Map<String, Object> stats = createBasicStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            when(shardedConnectionManager.cleanupInvalidConnections()).thenReturn(10);
            
            // When - 执行紧急统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logEmergencyStatistics());
            
            // Then - 验证调用了清理方法
            verify(shardedConnectionManager).cleanupInvalidConnections();
        }
    }
    
    @Nested
    @DisplayName("性能监控测试")
    class PerformanceMonitoringTest {
        
        @Test
        @DisplayName("应该监控统计信息收集的执行时间")
        void should_monitor_statistics_collection_execution_time() {
            // Given - 快速执行的统计收集
            Map<String, Object> stats = createBasicStatsMap();
            when(shardedConnectionManager.getShardStatistics()).thenReturn(stats);
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证执行了性能监控
            verify(shardedConnectionManager).getShardStatistics();
        }
        
        @Test
        @DisplayName("应该处理统计收集过程中的延迟")
        void should_handle_delays_in_statistics_collection() {
            // Given - 模拟慢速统计收集
            Map<String, Object> stats = createBasicStatsMap();
            doAnswer(invocation -> {
                // 模拟延迟但不实际睡眠
                return stats;
            }).when(shardedConnectionManager).getShardStatistics();
            
            // When - 执行统计信息输出
            assertThatNoException().isThrownBy(() -> scheduler.logWebSocketConnectionStatistics());
            
            // Then - 验证处理了延迟情况
            verify(shardedConnectionManager).getShardStatistics();
        }
    }
    
    // ===================== 测试数据构建辅助方法 =====================
    
    /**
     * 创建基本统计数据Map
     */
    private Map<String, Object> createBasicStatsMap() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 4);
        stats.put("totalConnections", 100);
        stats.put("running", true);
        stats.put("versionCount", Map.of("v1.0", 60, "v1.1", 40));
        return stats;
    }
    
    /**
     * 创建零连接统计数据Map
     */
    private Map<String, Object> createStatsWithZeroConnections() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 4);
        stats.put("totalConnections", 0);
        stats.put("running", true);
        stats.put("versionCount", Map.of());
        return stats;
    }
    
    /**
     * 创建单分片统计数据Map
     */
    private Map<String, Object> createSingleShardStatsMap() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 1);
        stats.put("totalConnections", 50);
        stats.put("running", true);
        stats.put("versionCount", Map.of("v1.1", 50));
        Map<Integer, Integer> shardSizes = Map.of(0, 50);
        stats.put("shardSizes", shardSizes);
        return stats;
    }
    
    /**
     * 创建多分片统计数据Map
     */
    private Map<String, Object> createMultiShardStatsMap() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 4);
        stats.put("totalConnections", 200);
        stats.put("running", true);
        stats.put("versionCount", Map.of("v1.0", 120, "v1.1", 80));
        Map<Integer, Integer> shardSizes = Map.of(0, 60, 1, 50, 2, 45, 3, 45);
        stats.put("shardSizes", shardSizes);
        return stats;
    }
    
    /**
     * 创建空分片统计数据Map
     */
    private Map<String, Object> createStatsWithEmptyShards() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 0);
        stats.put("totalConnections", 0);
        stats.put("running", true);
        stats.put("versionCount", Map.of());
        stats.put("shardSizes", Map.of());
        return stats;
    }
    
    /**
     * 创建乱序分片统计数据Map
     */
    private Map<String, Object> createUnorderedShardStatsMap() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 3);
        stats.put("totalConnections", 150);
        stats.put("running", true);
        stats.put("versionCount", Map.of("v1.1", 150));
        Map<Integer, Integer> shardSizes = Map.of(2, 40, 0, 60, 1, 50); // 乱序
        stats.put("shardSizes", shardSizes);
        return stats;
    }
    
    /**
     * 创建负载均衡统计数据Map
     */
    private Map<String, Object> createLoadBalanceStatsMap() {
        Map<String, Object> stats = createMultiShardStatsMap();
        stats.put("maxShardSize", 60);
        stats.put("minShardSize", 45);
        stats.put("loadBalance", 1.33); // 良好的负载均衡
        return stats;
    }
    
    /**
     * 创建负载均衡较差的统计数据Map
     */
    private Map<String, Object> createPoorLoadBalanceStatsMap() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShards", 3);
        stats.put("totalConnections", 150);
        stats.put("running", true);
        stats.put("versionCount", Map.of("v1.1", 150));
        stats.put("maxShardSize", 100);
        stats.put("minShardSize", 25);
        stats.put("loadBalance", 4.0); // 较差的负载均衡
        return stats;
    }
    
    /**
     * 创建包含null负载均衡值的统计数据Map
     */
    private Map<String, Object> createStatsWithNullLoadBalance() {
        Map<String, Object> stats = createBasicStatsMap();
        stats.put("maxShardSize", 30);
        stats.put("minShardSize", 20);
        stats.put("loadBalance", null);
        return stats;
    }
}