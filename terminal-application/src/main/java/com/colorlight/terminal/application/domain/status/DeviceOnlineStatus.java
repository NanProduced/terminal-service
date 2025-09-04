package com.colorlight.terminal.application.domain.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 协议版本 - WebSocket连接的版本信息
     * null表示未通过WebSocket连接或使用默认版本
     */
    private String version;
    
    /**
     * 创建在线状态
     * @param deviceId 设备ID
     * @param source 上报来源
     * @param clientIp 客户端IP
     * @param version 协议版本（可为null）
     * @return 在线状态对象
     */
    public static DeviceOnlineStatus createGoLive(Long deviceId, ReportSource source, String clientIp, String version) {
        long currentTime = System.currentTimeMillis();
        return DeviceOnlineStatus.builder()
                .deviceId(deviceId)
                .lastReportTime(currentTime)
                .lastReportSource(source)
                .status(OnlineStatus.GO_LIVE)
                .statusChangeTime(currentTime)
                .onlineStartTime(currentTime)
                .clientIp(clientIp)
                .version(version)
                .build();
    }

    /**
     * 刷新在线状态
     * @param deviceId 设备ID
     * @param source 上报来源
     * @param clientIp 客户端IP
     * @param version 协议版本（WebSocket来源时用于懒更新）
     * @return 在线状态对象
     */
    public static DeviceOnlineStatus refreshOnline(Long deviceId, ReportSource source, String clientIp, String version) {
        long currentTime = System.currentTimeMillis();
        DeviceOnlineStatus.DeviceOnlineStatusBuilder builder = DeviceOnlineStatus.builder()
                .deviceId(deviceId)
                .lastReportTime(currentTime)
                .lastReportSource(source)
                .status(OnlineStatus.ONLINE)
                .clientIp(clientIp);
        
        // 懒版本更新：仅WebSocket来源且提供版本号时设置
        if (source == ReportSource.WEBSOCKET && version != null) {
            builder.version(version);
        }
        
        return builder.build();
    }

    /**
     * 创建重连状态
     * @param currentStatus 当前状态
     * @param source 上报来源
     * @param clientIp 客户端IP
     * @param version 协议版本（可为null）
     * @return 重连状态对象
     */
    public static DeviceOnlineStatus createReconnect(DeviceOnlineStatus currentStatus, ReportSource source, String clientIp, String version) {
        long currentTime = System.currentTimeMillis();
        return DeviceOnlineStatus.builder()
                .deviceId(currentStatus.deviceId)
                .lastReportTime(currentTime)
                .lastReportSource(source)
                .status(OnlineStatus.RECONNECT)
                .statusChangeTime(currentTime)
                .onlineStartTime(currentTime)
                .clientIp(clientIp)
                .version(version)
                .build();
    }

    
    /**
     * 标记为离线
     */
    public void markOffline() {
        if (this.status != OnlineStatus.OFFLINE) {
            this.status = OnlineStatus.OFFLINE;
            this.statusChangeTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 检查是否在线（基于配置的超时阈值）
     * 注意：此方法需要外部传入配置，建议优先使用应用服务层的isDeviceOnline方法
     */
    public boolean isOnline() {
        if (this.lastReportTime == null) {
            return false;
        }
        
        // 首先检查状态语义 - 离线状态直接返回false
        if (this.status == OnlineStatus.OFFLINE) {
            return false;
        }
        
        // 对于在线相关状态，检查时间阈值
        if (isOnlineRelatedStatus(this.status)) {
            // 使用默认70秒，但建议调用方使用带配置的重载方法
            long expireThreshold = System.currentTimeMillis() - 70_000; 
            return this.lastReportTime > expireThreshold;
        }
        
        return false;
    }
    
    /**
     * 检查是否在线（使用配置的超时阈值）
     * @param timeoutThreshold 超时阈值(毫秒)
     * @return 是否在线
     */
    public boolean isOnline(long timeoutThreshold) {
        if (this.lastReportTime == null) {
            return false;
        }
        
        // 首先检查状态语义 - 离线状态直接返回false
        if (this.status == OnlineStatus.OFFLINE) {
            return false;
        }
        
        // 对于在线相关状态，检查时间阈值
        if (isOnlineRelatedStatus(this.status)) {
            long expireThreshold = System.currentTimeMillis() - timeoutThreshold;
            return this.lastReportTime > expireThreshold;
        }
        
        return false;
    }
    
    /**
     * 获取当前在线时长(毫秒)
     */
    public long getCurrentOnlineDuration() {
        // 扩展到所有在线相关状态
        if (isOnlineRelatedStatus(this.status) && this.onlineStartTime != null) {
            return System.currentTimeMillis() - this.onlineStartTime;
        }
        return 0L;
    }
    
    /**
     * 获取有效协议版本（用于业务逻辑）
     * @return 协议版本，null时返回默认V1.0版本号
     */
    public String getEffectiveVersion() {
        return version != null ? version : "1.0";
    }
    
    /**
     * 检查是否为在线相关状态
     * @param status 状态
     * @return 是否为在线相关状态
     */
    private boolean isOnlineRelatedStatus(OnlineStatus status) {
        return status == OnlineStatus.GO_LIVE || 
               status == OnlineStatus.ONLINE || 
               status == OnlineStatus.RECONNECT;
    }
}