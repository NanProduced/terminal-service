package com.colorlight.terminal.application.domain.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备在线状态领域实体
 * 
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceOnlineStatus {
    
    /**
     * 设备ID
     */
    private Long deviceId;
    
    /**
     * 最后上报时间戳(毫秒)
     */
    private Long lastReportTime;
    
    /**
     * 最后上报来源
     */
    private ReportSource lastReportSource;
    
    /**
     * 在线状态
     */
    private OnlineStatus status;
    
    /**
     * 状态变更时间戳(毫秒)
     */
    private Long statusChangeTime;
    
    /**
     * 本次上线开始时间戳(毫秒) - 用于计算在线时长
     */
    private Long onlineStartTime;
    
    /**
     * 客户端IP地址
     */
    private String clientIp;
    
    /**
     * 创建在线状态
     */
    public static DeviceOnlineStatus createOnline(Long deviceId, ReportSource source, String clientIp) {
        long currentTime = System.currentTimeMillis();
        return DeviceOnlineStatus.builder()
                .deviceId(deviceId)
                .lastReportTime(currentTime)
                .lastReportSource(source)
                .status(OnlineStatus.ONLINE)
                .statusChangeTime(currentTime)
                .onlineStartTime(currentTime)
                .clientIp(clientIp)
                .build();
    }
    
    /**
     * 创建离线状态
     */
    public static DeviceOnlineStatus createOffline(Long deviceId) {
        long currentTime = System.currentTimeMillis();
        return DeviceOnlineStatus.builder()
                .deviceId(deviceId)
                .lastReportTime(currentTime)
                .status(OnlineStatus.OFFLINE)
                .statusChangeTime(currentTime)
                .build();
    }
    
    /**
     * 更新上报时间
     */
    public void updateReportTime(ReportSource source) {
        this.lastReportTime = System.currentTimeMillis();
        this.lastReportSource = source;
        
        // 如果从离线变为在线，记录上线开始时间
        if (this.status == OnlineStatus.OFFLINE) {
            this.status = OnlineStatus.ONLINE;
            this.onlineStartTime = this.lastReportTime;
            this.statusChangeTime = this.lastReportTime;
        }
    }
    
    /**
     * 标记为离线
     * @return 本次在线时长(毫秒)，如果之前已离线则返回0
     */
    public long markOffline() {
        if (this.status == OnlineStatus.ONLINE && this.onlineStartTime != null) {
            long onlineDuration = System.currentTimeMillis() - this.onlineStartTime;
            this.status = OnlineStatus.OFFLINE;
            this.statusChangeTime = System.currentTimeMillis();
            this.onlineStartTime = null;
            return onlineDuration;
        }
        return 0L;
    }
    
    /**
     * 检查是否在线（基于70秒超时）
     */
    public boolean isOnline() {
        if (this.lastReportTime == null) {
            return false;
        }
        long expireThreshold = System.currentTimeMillis() - 70_000; // 70秒
        return this.lastReportTime > expireThreshold;
    }
    
    /**
     * 获取当前在线时长(毫秒)
     */
    public long getCurrentOnlineDuration() {
        if (this.status == OnlineStatus.ONLINE && this.onlineStartTime != null) {
            return System.currentTimeMillis() - this.onlineStartTime;
        }
        return 0L;
    }
}