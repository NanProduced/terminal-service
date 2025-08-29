package com.colorlight.terminal.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 终端统计配置属性
 * Infrastructure层Spring Boot配置属性，支持Nacos动态刷新
 * 用于上报数据统计处理模块的配置
 * 
 * @author Nan
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "terminal.stats")
public class TerminalStatsConfigProperties {
    
    /**
     * 时区时间校准配置
     */
    private TimeCalibration timeCalibration = new TimeCalibration();
    
    /**
     * 媒体播放记录配置
     */
    private MediaPlayRecord mediaPlayRecord = new MediaPlayRecord();
    
    /**
     * 时区时间校准配置
     */
    @Data
    public static class TimeCalibration {
        
        /**
         * 时间偏差阈值(秒)
         * 与标准时间相差超过此值视为有偏差，需要校准
         * 默认2秒
         */
        private long offsetThresholdSeconds = 2L;
        
        /**
         * 时区缓存TTL(小时)
         * 设备时区信息的缓存时间
         * 默认24小时
         */
        private long timeZoneCacheTtlHours = 24L;
    }
    
    /**
     * 媒体播放记录配置
     */
    @Data
    public static class MediaPlayRecord {
        
        /**
         * 是否启用播放时间自动校准
         * 默认启用，会根据设备时区偏差自动校准播放开始时间
         */
        private boolean timeCalibrationEnabled = true;

    }
}