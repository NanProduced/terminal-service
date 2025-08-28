package com.colorlight.terminal.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * WebSocket配置属性
 * Infrastructure层Spring Boot配置属性，支持Nacos动态刷新
 * 
 * @author Nan
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "terminal.websocket")
public class WebSocketConfigProperties {
    
    /**
     * 统计信息配置
     */
    private Stats stats = new Stats();
    
    /**
     * 连接管理配置
     */
    private Connection connection = new Connection();
    
    /**
     * 统计信息配置
     */
    @Data
    public static class Stats {
        
        /**
         * 是否启用WebSocket统计信息定时输出
         */
        private boolean enabled = true;
        
        /**
         * 统计信息输出间隔(毫秒)
         * 默认5分钟
         */
        private long interval = 300_000;
        
        /**
         * 统计任务初始延迟(毫秒)
         * 默认2分钟，避免启动时资源竞争
         */
        private long initialDelay = 120_000;
        
        /**
         * 是否启用详细的分片信息输出
         */
        private boolean detailEnabled = true;
        
        /**
         * 是否启用负载均衡分析
         */
        private boolean loadBalanceAnalysisEnabled = true;
        
        /**
         * 是否启用可视化条形图输出
         */
        private boolean visualBarEnabled = true;
        
        /**
         * 负载均衡警告阈值
         * 当最大分片/最小分片的比率超过此值时发出警告
         */
        private double loadBalanceWarningThreshold = 2.0;
    }
    
    /**
     * 连接管理配置
     */
    @Data
    public static class Connection {
        
        /**
         * 是否启用连接清理
         */
        private boolean cleanupEnabled = true;
        
        /**
         * 连接清理间隔(毫秒)
         * 默认10分钟
         */
        private long cleanupInterval = 600_000;
        
        /**
         * 心跳超时阈值(毫秒)
         * 默认65秒，比WebSocket心跳间隔(55秒)多10秒容错
         */
        private long heartbeatTimeout = 65_000;
        
        /**
         * 是否启用紧急统计信息输出
         * 在连接数异常时输出详细调试信息
         */
        private boolean emergencyStatsEnabled = true;
    }
}