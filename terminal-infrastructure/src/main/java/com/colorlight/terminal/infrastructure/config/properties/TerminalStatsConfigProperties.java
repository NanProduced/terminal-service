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
     * GPS数据处理配置
     */
    private Gps gps = new Gps();
    
    /**
     * 时区时间校准配置
     */
    @Data
    public static class TimeCalibration {
        
        /**
         * 时间偏差阈值(秒)
         * 与标准时间相差超过此值视为有偏差，需要校准
         * 默认5秒
         */
        private long offsetThresholdSeconds = 5L;
        
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

    /**
     * gps处理配置
     */
    @Data
    public static class Gps {

        /**
         * 缓存队列大小
         */
        private int maxQueueSize = 10000;

        /**
         * 批处理量
         */
        private int batchSize = 5000;

        /**
         * 默认的google s2 geometry cell精度
         */
        private int defaultS2CellLevel = 16;

        /**
         * 刷新间隔（s）
         */
        private int flushInterval = 5000;

        /**
         * 里程计算配置
         */
        private Mileage mileage = new Mileage();

    }

    @Data
    public static class Mileage {

        /**
         * 最小有效距离阈值（公里）
         * 两点间距离小于此值时视为GPS漂移，不计入里程
         * 默认0.020km（20米），可过滤绝大多数GPS漂移（通常5~15米级别）
         */
        private double minDistanceThresholdKm = 0.020;

        /**
         * 静止速度阈值（米/秒）
         * 速度低于此值时认为设备处于静止状态
         * 默认0.1m/s（约0.36km/h），正常步行速度约1.2~1.5m/s
         */
        private double staticSpeedThreshold = 0.1;

        /**
         * 是否启用速度过滤
         * 启用后，当连续两个GPS点的速度均低于静止阈值时，该段距离不计入里程
         */
        private boolean speedFilterEnabled = true;

        /**
         * 是否启用精度过滤
         * 启用后，当两点间距离小于两者定位精度之和时，视为GPS噪声，不计入里程
         */
        private boolean accuracyFilterEnabled = false;

    }
}