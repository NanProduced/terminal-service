package com.colorlight.terminal.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.Map;

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
     * 协议版本配置
     */
    private Protocol protocol = new Protocol();
    
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

    /**
     * 协议版本配置 - 在配置文件动态配置以覆盖枚举类中的supported
     * <p>这里的配置项要和ProtocolVersion枚举里的版本对应</p>
     * @see com.colorlight.terminal.application.domain.connection.ProtocolVersion
     */
    @Data
    public static class Protocol {

        /**
         * 协议版本支持配置
         * Key: 协议版本字符串 (如"1.0", "1.1")
         * Value: 是否支持该协议版本
         */
        private Map<String, Boolean> versions = Map.of(
                "1.0", true,   // 默认支持V1.0
                "1.1", false   // 默认禁用V1.1（用于渐进式发布）
        );

        /**
         * 检查指定协议版本是否被支持
         *
         * @param versionString 协议版本字符串
         * @param enumSupported 枚举类中的supported
         * @return 是否支持该协议版本，配置项中没有则默认使用枚举类中配置
         */
        public boolean isVersionSupported(String versionString, boolean enumSupported) {
            return versions.getOrDefault(versionString, enumSupported);
        }
    }
}