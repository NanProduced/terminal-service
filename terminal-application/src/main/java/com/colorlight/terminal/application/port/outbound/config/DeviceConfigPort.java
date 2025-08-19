package com.colorlight.terminal.application.port.outbound.config;

import com.colorlight.terminal.application.properties.DeviceProperties;

/**
 * 设备配置端口
 * 定义Application层获取配置的接口，遵循依赖倒置原则
 * 
 * @author Nan
 */
public interface DeviceConfigPort {
    
    /**
     * 获取设备配置
     * @return 设备配置属性
     */
    DeviceProperties getDeviceConfig();
    
    /**
     * 获取离线超时阈值(毫秒)
     * @return 超时阈值
     */
    default long getOfflineTimeoutThreshold() {
        return getDeviceConfig().getOfflineCheck().getTimeoutThreshold();
    }
    
    /**
     * 获取离线检测间隔(毫秒)
     * @return 检测间隔
     */
    default long getOfflineCheckInterval() {
        return getDeviceConfig().getOfflineCheck().getInterval();
    }
    
    /**
     * 是否启用离线检测
     * @return 是否启用
     */
    default boolean isOfflineCheckEnabled() {
        return getDeviceConfig().getOfflineCheck().isEnabled();
    }
    
    /**
     * 获取Redis状态TTL(秒)
     * @return TTL时间
     */
    default long getRedisStatusTtl() {
        return getDeviceConfig().getStatusUpdate().getRedisTtl();
    }
    
    /**
     * 是否启用HTTP状态更新
     * @return 是否启用
     */
    default boolean isHttpStatusUpdateEnabled() {
        return getDeviceConfig().getStatusUpdate().isHttpEnabled();
    }
    
    /**
     * 是否启用WebSocket状态更新
     * @return 是否启用
     */
    default boolean isWebSocketStatusUpdateEnabled() {
        return getDeviceConfig().getStatusUpdate().isWebsocketEnabled();
    }
    
    /**
     * 是否启用Spring事件
     * @return 是否启用
     */
    default boolean isSpringEventEnabled() {
        return getDeviceConfig().getSpringEvent().isEnabled();
    }
    
    /**
     * Spring事件是否异步处理
     * @return 是否异步
     */
    default boolean isSpringEventAsync() {
        return getDeviceConfig().getSpringEvent().isAsync();
    }

    /**
     * 获取上线时间TTL(小时)
     * @return TTL小时数
     */
    default long getOnlineTimeTtlHours() {
        return getDeviceConfig().getExpirationListener().getOnlineTimeTtlHours();
    }
    
    /**
     * 是否启用自动清理
     * @return 是否启用
     */
    default boolean isAutoCleanupEnabled() {
        return getDeviceConfig().getExpirationListener().isAutoFreshEnabled();
    }
    
    // ==================== 异步状态更新配置 ====================
    
    /**
     * 是否启用异步状态更新
     * @return 是否启用
     */
    default boolean isAsyncStatusUpdateEnabled() {
        return getDeviceConfig().getStatusUpdate().isAsyncEnabled();
    }
    
    /**
     * 获取缓冲池窗口时间(毫秒)
     * @return 窗口时间
     */
    default long getBufferPoolWindowMs() {
        return getDeviceConfig().getStatusUpdate().getBufferPool().getWindowMs();
    }
    
    /**
     * 获取缓冲池最大数量
     * @return 最大数量
     */
    default int getBufferPoolMaxSize() {
        return getDeviceConfig().getStatusUpdate().getBufferPool().getMaxSize();
    }
    
    /**
     * 获取缓冲池批处理大小
     * @return 批处理大小
     */
    default int getBufferPoolBatchSize() {
        return getDeviceConfig().getStatusUpdate().getBufferPool().getBatchSize();
    }
    
    /**
     * 是否启用紧急刷新
     * @return 是否启用
     */
    default boolean isEmergencyFlushEnabled() {
        return getDeviceConfig().getStatusUpdate().getBufferPool().isEmergencyFlushEnabled();
    }
    
    /**
     * 获取紧急刷新阈值
     * @return 阈值(百分比)
     */
    default double getEmergencyFlushThreshold() {
        return getDeviceConfig().getStatusUpdate().getBufferPool().getEmergencyFlushThreshold();
    }
    
    /**
     * 获取缓冲池统计信息输出间隔(毫秒)
     * @return 统计间隔
     */
    default long getBufferPoolStatisticsInterval() {
        return getDeviceConfig().getStatusUpdate().getBufferPool().getStatisticsInterval();
    }
    
    // ==================== 流式查询配置 ====================
    
    /**
     * 是否启用Redis Stream查询
     * @return 是否启用
     */
    default boolean isStreamQueryEnabled() {
        return getDeviceConfig().getStatusUpdate().getStreamQuery().isEnabled();
    }
    
    /**
     * 获取流式查询分页大小
     * @return 分页大小
     */
    default int getStreamQueryPageSize() {
        return getDeviceConfig().getStatusUpdate().getStreamQuery().getPageSize();
    }
    
    /**
     * 获取流式查询最大迭代次数
     * @return 最大迭代次数
     */
    default int getStreamQueryMaxIterations() {
        return getDeviceConfig().getStatusUpdate().getStreamQuery().getMaxIterations();
    }
    
    /**
     * 获取流式查询超时时间(毫秒)
     * @return 超时时间
     */
    default long getStreamQueryTimeoutMs() {
        return getDeviceConfig().getStatusUpdate().getStreamQuery().getTimeoutMs();
    }
    
    // ==================== TTL刷新配置 ====================
    
    /**
     * 获取TTL刷新间隔(小时)
     * @return 刷新间隔小时数
     */
    default long getTtlRefreshIntervalHours() {
        return getDeviceConfig().getExpirationListener().getTtlRefreshIntervalHours();
    }
    
    /**
     * 获取流式查询阈值
     * @return 设备数量阈值
     */
    default int getStreamQueryThreshold() {
        return getDeviceConfig().getExpirationListener().getStreamQueryThreshold();
    }
}