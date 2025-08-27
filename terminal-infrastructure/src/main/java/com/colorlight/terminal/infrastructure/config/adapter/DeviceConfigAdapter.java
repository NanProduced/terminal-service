package com.colorlight.terminal.infrastructure.config.adapter;

import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.properties.DeviceProperties;
import com.colorlight.terminal.infrastructure.config.properties.DeviceConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备配置适配器
 * Infrastructure层实现，将Spring Boot配置转换为Application层需要的格式
 * 支持Nacos动态刷新
 * 
 * @author Nan
 */
@Slf4j
@Component("deviceConfigPort")
@RequiredArgsConstructor
public class DeviceConfigAdapter implements DeviceConfigPort {
    
    private final DeviceConfigProperties configProperties;
    
    @Override
    public DeviceProperties getDeviceConfig() {
        
        DeviceProperties domainConfig = new DeviceProperties();
        
        // 复制离线检测配置
        copyOfflineCheckConfig(configProperties.getOfflineCheck(), domainConfig.getOfflineCheck());
        
        // 复制状态更新配置
        copyStatusUpdateConfig(configProperties.getStatusUpdate(), domainConfig.getStatusUpdate());
        
        // 复制Spring事件配置
        copySpringEventConfig(configProperties.getSpringEvent(), domainConfig.getSpringEvent());
        
        // 复制过期监听器配置
        copyExpirationListenerConfig(configProperties.getExpirationListener(),
                                    domainConfig.getExpirationListener());

        // 复制缓存清理配置
        copyStartupCleanup(configProperties.getStartupCleanup(), domainConfig.getStartupCleanup());
        
        // 复制定时任务启动配置
        copyTaskStartupConfig(configProperties.getTaskStartup(), domainConfig.getTaskStartup());
        
        return domainConfig;
    }
    
    /**
     * 复制离线检测配置
     */
    private void copyOfflineCheckConfig(DeviceConfigProperties.OfflineCheck source, 
                                       DeviceProperties.OfflineCheck target) {
        target.setEnabled(source.isEnabled());
        target.setInterval(source.getInterval());
        target.setInitialDelay(source.getInitialDelay());
        target.setTimeoutThreshold(source.getTimeoutThreshold());
        target.setStatisticsInterval(source.getStatisticsInterval());
    }
    
    /**
     * 复制状态更新配置
     */
    private void copyStatusUpdateConfig(DeviceConfigProperties.StatusUpdate source,
                                       DeviceProperties.StatusUpdate target) {
        target.setRedisTtl(source.getRedisTtl());
        target.setReconnectTtl(source.getReconnectTtl());
        target.setHttpEnabled(source.isHttpEnabled());
        target.setWebsocketEnabled(source.isWebsocketEnabled());
        target.setAsyncEnabled(source.isAsyncEnabled());
        
        // 复制缓冲池配置
        copyBufferPoolConfig(source.getBufferPool(), target.getBufferPool());
        
        // 复制流式查询配置
        copyStreamQueryConfig(source.getStreamQuery(), target.getStreamQuery());
    }
    
    /**
     * 复制缓冲池配置
     */
    private void copyBufferPoolConfig(DeviceConfigProperties.BufferPool source,
                                     DeviceProperties.BufferPool target) {
        target.setWindowMs(source.getWindowMs());
        target.setMaxSize(source.getMaxSize());
        target.setBatchSize(source.getBatchSize());
        target.setEmergencyFlushEnabled(source.isEmergencyFlushEnabled());
        target.setEmergencyFlushThreshold(source.getEmergencyFlushThreshold());
        target.setStatisticsInterval(source.getStatisticsInterval());
    }
    
    /**
     * 复制流式查询配置
     */
    private void copyStreamQueryConfig(DeviceConfigProperties.StreamQuery source,
                                      DeviceProperties.StreamQuery target) {
        target.setEnabled(source.isEnabled());
        target.setPageSize(source.getPageSize());
        target.setMaxIterations(source.getMaxIterations());
        target.setTimeoutMs(source.getTimeoutMs());
    }
    
    /**
     * 复制Spring事件配置
     */
    private void copySpringEventConfig(DeviceConfigProperties.SpringEvent source,
                                      DeviceProperties.SpringEvent target) {
        target.setEnabled(source.isEnabled());
        target.setAsync(source.isAsync());
    }
    
    /**
     * 复制过期监听器配置
     */
    private void copyExpirationListenerConfig(DeviceConfigProperties.ExpirationListener source,
                                             DeviceProperties.ExpirationListener target) {
        target.setEnabled(source.isEnabled());
        target.setAutoFreshEnabled(source.isAutoFreshEnabled());
        target.setStreamQueryThreshold(source.getStreamQueryThreshold());
    }

    /**
     * 复制缓存清理配置
     */
    private void copyStartupCleanup(DeviceConfigProperties.StartupCleanup source,
                                    DeviceProperties.StartupCleanup target) {
        target.setEnabled(source.isEnabled());
    }
    
    /**
     * 复制定时任务启动配置
     */
    private void copyTaskStartupConfig(DeviceConfigProperties.TaskStartup source,
                                       DeviceProperties.TaskStartup target) {
        target.setStaggeredEnabled(source.isStaggeredEnabled());
        target.setBaseDelayMs(source.getBaseDelayMs());
        target.setBufferPoolDelayMs(source.getBufferPoolDelayMs());
        target.setStatisticsDelayMs(source.getStatisticsDelayMs());
    }
}