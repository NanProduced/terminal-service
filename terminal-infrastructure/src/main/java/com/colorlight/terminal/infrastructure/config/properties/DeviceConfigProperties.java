package com.colorlight.terminal.infrastructure.config.properties;

import com.colorlight.terminal.application.properties.DeviceProperties;
import com.colorlight.terminal.rpc.dto.enums.CleanupMode;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.EnumSet;
import java.util.Set;

/**
 * 设备配置属性
 * Infrastructure层Spring Boot配置属性，支持Nacos动态刷新
 * 
 * @author Nan
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "terminal.device")
public class DeviceConfigProperties {
    
    /**
     * 离线检测配置
     */
    private OfflineCheck offlineCheck = new OfflineCheck();
    
    /**
     * 状态更新配置
     */
    private StatusUpdate statusUpdate = new StatusUpdate();
    
    /**
     * Spring事件配置
     */
    private SpringEvent springEvent = new SpringEvent();
    
    /**
     * 过期监听器配置
     */
    private ExpirationListener expirationListener = new ExpirationListener();

    /**
     * 启动缓存清理配置
     */
    private StartupCleanup startupCleanup = new StartupCleanup();
    
    /**
     * 定时任务启动配置
     */
    private TaskStartup taskStartup = new TaskStartup();
    
    /**
     * 设备数据清理配置
     */
    private DataCleanup cleanup = new DataCleanup();
    
    /**
     * 离线检测配置
     */
    @Data
    public static class OfflineCheck {
        
        /**
         * 是否启用离线检测定时任务
         */
        private boolean enabled = true;
        
        /**
         * 检查间隔(毫秒)
         */
        private long interval = 30_000;
        
        /**
         * 初始延迟(毫秒)
         */
        private long initialDelay = 60_000;
        
        /**
         * 离线超时阈值(毫秒)
         * 默认70秒 = 60秒业务超时 + 10秒容错
         */
        private long timeoutThreshold = 70_000;
        
        /**
         * 统计信息输出间隔(毫秒)
         */
        private long statisticsInterval = 300_000; // 5分钟
    }
    
    /**
     * 状态更新配置
     */
    @Data
    public static class StatusUpdate {
        
        /**
         * Redis状态初始TTL(秒) - 设备上线和心跳时使用
         */
        private long redisTtl = 3600; // 1小时
        
        /**
         * 重连窗口TTL(秒) - 设备离线后等待重连的时间
         */
        private long reconnectTtl = 120;
        
        /**
         * 是否启用HTTP请求状态更新
         */
        private boolean httpEnabled = true;
        
        /**
         * 是否启用WebSocket状态更新
         */
        private boolean websocketEnabled = true;
        
        /**
         * 是否启用异步状态更新
         */
        private boolean asyncEnabled = true;
        
        /**
         * 缓冲池配置
         */
        private BufferPool bufferPool = new BufferPool();
        
        /**
         * 流式查询配置
         */
        private StreamQuery streamQuery = new StreamQuery();
    }
    
    /**
     * 缓冲池配置
     */
    @Data
    public static class BufferPool {
        
        /**
         * 缓冲窗口时间(毫秒)
         */
        private long windowMs = 2000;
        
        /**
         * 最大缓冲数量
         */
        private int maxSize = 10000;
        
        /**
         * 批处理大小
         */
        private int batchSize = 100;
        
        /**
         * 是否启用紧急刷新
         */
        private boolean emergencyFlushEnabled = true;
        
        /**
         * 紧急刷新阈值(百分比)
         */
        private double emergencyFlushThreshold = 0.8;
        
        /**
         * 统计信息输出间隔(毫秒)
         */
        private long statisticsInterval = 300000; // 5分钟
    }
    
    /**
     * 流式查询配置
     */
    @Data
    public static class StreamQuery {
        
        /**
         * 是否启用Redis Stream查询
         */
        private boolean enabled = true;
        
        /**
         * 分页大小
         */
        private int pageSize = 1000;
        
        /**
         * 最大迭代次数
         */
        private int maxIterations = 1000;
        
        /**
         * 查询超时时间(毫秒)
         */
        private long timeoutMs = 5000;
    }
    
    /**
     * Spring事件配置
     */
    @Data
    public static class SpringEvent {
        
        /**
         * 是否启用Spring事件发布
         */
        private boolean enabled = true;
        
        /**
         * 是否异步处理事件
         */
        private boolean async = true;
    }
    
    
    
    /**
     * 过期监听器配置
     */
    @Data
    public static class ExpirationListener {
        
        /**
         * 是否启用过期监听器
         */
        private boolean enabled = true;
        
        /**
         * 是否启用自动清理
         */
        private boolean autoFreshEnabled = true;
        
        /**
         * 流式查询阈值
         * 超过此数量的设备将使用流式查询以避免内存问题
         */
        private int streamQueryThreshold = 2000;
    }

    /**
     * 启动缓存清理配置
     */
    @Data
    public static class StartupCleanup {

        /**
         * 是否启用启动时缓存清理
         */
        private boolean enabled = true;
    }
    
    /**
     * 定时任务启动配置
     */
    @Data
    public static class TaskStartup {
        
        /**
         * 是否启用交错启动
         * 避免多个定时任务同时启动造成资源竞争
         */
        private boolean staggeredEnabled = true;
        
        /**
         * 基础延迟时间(毫秒)
         * 应用启动后的基础等待时间
         */
        private long baseDelayMs = 60_000; // 1分钟
        
        /**
         * 缓冲池任务延迟(毫秒)
         * 状态更新和登录更新缓冲池任务的延迟
         */
        private long bufferPoolDelayMs = 15_000; // 15秒
        
        /**
         * 统计任务延迟(毫秒)
         * 统计和监控任务的延迟
         */
        private long statisticsDelayMs = 120_000; // 2分钟
    }
    
    /**
     * 设备数据清理配置
     */
    @Data
    public static class DataCleanup {
        
        /**
         * 是否启用设备数据清理
         */
        private boolean enabled = true;
        
        /**
         * 默认清理模式: ALL/INCLUDE/EXCLUDE
         */
        private CleanupMode mode = CleanupMode.EXCLUDE;
        
        /**
         * 数据类型配置
         * - 当mode=INCLUDE时，仅清理列表中的数据类型
         * - 当mode=EXCLUDE时，清理除列表外的所有数据类型  
         * - 当mode=ALL时，忽略此配置，清理所有数据类型
         * 注意: DEVICE_ACCOUNT(设备账号)始终会被删除，不受此配置影响
         */
        private Set<DataType> dataTypes = EnumSet.of(DataType.TERMINAL_LOG);
    }
}