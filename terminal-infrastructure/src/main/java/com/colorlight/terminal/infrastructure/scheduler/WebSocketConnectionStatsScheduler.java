package com.colorlight.terminal.infrastructure.scheduler;

import com.colorlight.terminal.infrastructure.config.properties.WebSocketConfigProperties;
import com.colorlight.terminal.infrastructure.websocket.connection.ShardedConnectionManager;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.Map;

/**
 * WebSocket连接统计信息定时任务
 * 每5分钟输出系统WebSocket连接的分片统计信息
 * 
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "terminal.websocket.stats.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketConnectionStatsScheduler {
    
    private final ShardedConnectionManager shardedConnectionManager;
    
    private final WebSocketConfigProperties webSocketConfig;
    
    private final DecimalFormat loadBalanceFormat = new DecimalFormat("#.##");
    
    /**
     * 定时输出WebSocket连接分片统计信息
     * 根据配置的间隔输出，用于监控分片连接管理状态
     */
    @Scheduled(fixedRateString = "#{@webSocketConfigProperties.stats.interval}", 
               initialDelayString = "#{@webSocketConfigProperties.stats.initialDelay}")
    public void logWebSocketConnectionStatistics() {
        try {
            long startTime = System.currentTimeMillis();
            
            log.debug("WebSocketStatsScheduler -websocket- 开始收集WebSocket连接统计信息");
            
            // 获取分片统计信息
            Map<String, Object> stats = shardedConnectionManager.getShardStatistics();
            
            // 输出基本统计信息
            logBasicStatistics(stats);
            
            // 输出分片详细信息
            if (webSocketConfig.getStats().isDetailEnabled()) {
                logShardDetails(stats);
            }
            
            // 输出负载均衡分析
            if (webSocketConfig.getStats().isLoadBalanceAnalysisEnabled()) {
                logLoadBalanceAnalysis(stats);
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            log.debug("WebSocketStatsScheduler -websocket- WebSocket连接统计信息收集完成，耗时: {}ms", elapsed);
            
            // 记录性能指标
            if (elapsed > 1000) { // 超过1秒
                log.warn("WebSocketStatsScheduler -websocket- 统计信息收集耗时过长: {}ms, 可能需要优化", elapsed);
            }
            
        } catch (Exception e) {
            log.error("WebSocketStatsScheduler -websocket- WebSocket连接统计信息输出失败", e);
        }
    }
    
    /**
     * 输出基本统计信息
     */
    private void logBasicStatistics(Map<String, Object> stats) {
        Integer totalShards = (Integer) stats.get("totalShards");
        Integer totalConnections = (Integer) stats.get("totalConnections");
        Boolean running = (Boolean) stats.get("running");
        
        log.info("================ WebSocket连接统计 ================");
        log.info("WebSocketStats - 系统状态: {}", running ? "运行中" : "已停止");
        log.info("WebSocketStats - 总连接数: {}", totalConnections);
        log.info("WebSocketStats - 分片数量: {}", totalShards);
        log.info("WebSocketStats - 协议版本: {}", stats.get("versionCount").toString());
        
        if (totalConnections != null && totalShards != null) {
            double avgConnectionsPerShard = (double) totalConnections / totalShards;
            log.info("WebSocketStats - 平均每分片连接数: {}", loadBalanceFormat.format(avgConnectionsPerShard));
        }
    }
    
    /**
     * 输出分片详细信息
     */
    @SuppressWarnings("unchecked")
    private void logShardDetails(Map<String, Object> stats) {
        Map<Integer, Integer> shardSizes = (Map<Integer, Integer>) stats.get("shardSizes");
        
        if (shardSizes != null && !shardSizes.isEmpty()) {
            log.info("WebSocketStats - 分片连接详情:");
            
            // 按分片ID排序输出
            shardSizes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        int shardId = entry.getKey();
                        int connections = entry.getValue();
                        String bar = webSocketConfig.getStats().isVisualBarEnabled() ? 
                            generateConnectionBar(connections) : "";
                        log.info("WebSocketStats -   分片[{}]: {} 连接 {}", 
                                String.format("%2d", shardId), 
                                String.format("%4d", connections), 
                                bar);
                    });
        }
    }
    
    /**
     * 输出负载均衡分析
     */
    private void logLoadBalanceAnalysis(Map<String, Object> stats) {
        Integer maxShardSize = (Integer) stats.get("maxShardSize");
        Integer minShardSize = (Integer) stats.get("minShardSize");
        Double loadBalance = (Double) stats.get("loadBalance");
        
        log.info("WebSocketStats - 负载均衡分析:");
        log.info("WebSocketStats -   最大分片连接数: {}", maxShardSize);
        log.info("WebSocketStats -   最小分片连接数: {}", minShardSize);
        
        if (loadBalance != null) {
            String loadBalanceStr = loadBalanceFormat.format(loadBalance);
            String balanceStatus = getLoadBalanceStatus(loadBalance);
            log.info("WebSocketStats -   负载均衡比率: {} ({})", loadBalanceStr, balanceStatus);
        }
        
        // 建议优化
        if (loadBalance != null && loadBalance > webSocketConfig.getStats().getLoadBalanceWarningThreshold()) {
            log.warn("WebSocketStats - ⚠️ 分片负载不均衡，建议检查设备ID分布或调整哈希策略 (阈值: {})", 
                    webSocketConfig.getStats().getLoadBalanceWarningThreshold());
        }
        
        log.info("==========================================");
    }
    
    /**
     * 生成连接数可视化条形图
     */
    private String generateConnectionBar(int connections) {
        if (connections == 0) {
            return "";
        }
        
        // 每个方块代表10个连接
        int blocks = Math.max(1, connections / 10);
        blocks = Math.min(blocks, 50); // 最多50个方块，避免日志过长
        
        StringBuilder bar = new StringBuilder();
        bar.append("█".repeat(blocks));
        
        return bar.toString();
    }
    
    /**
     * 获取负载均衡状态描述
     */
    private String getLoadBalanceStatus(double loadBalance) {
        if (loadBalance <= 1.2) {
            return "优秀";
        } else if (loadBalance <= 1.5) {
            return "良好";
        } else if (loadBalance <= 2.0) {
            return "一般";
        } else if (loadBalance <= 3.0) {
            return "较差";
        } else {
            return "很差";
        }
    }
    
    /**
     * 紧急情况下输出详细调试信息
     * 仅在连接数异常时调用
     */
    public void logEmergencyStatistics() {
        try {
            Map<String, Object> stats = shardedConnectionManager.getShardStatistics();
            
            log.warn("WebSocketStatsScheduler - 紧急统计信息:");
            log.warn("WebSocketStats - 完整统计数据: {}", JsonUtils.toJsonPretty(stats));
            
            // 清理无效连接
            int cleanedCount = shardedConnectionManager.cleanupInvalidConnections();
            log.warn("WebSocketStats - 清理无效连接数: {}", cleanedCount);
            
        } catch (Exception e) {
            log.error("WebSocketStatsScheduler - 紧急统计信息输出失败", e);
        }
    }
}