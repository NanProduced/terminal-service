package com.colorlight.terminal.application.domain.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备状态变更事件基类
 * 
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatusEvent {
    
    /**
     * 设备ID
     */
    private Long deviceId;
    
    /**
     * 事件时间戳
     */
    private Long eventTime;
    
    /**
     * 事件类型
     */
    private EventType eventType;
    
    /**
     * 上报来源
     */
    private ReportSource reportSource;
    
    /**
     * 客户端IP
     */
    private String clientIp;
    
    /**
     * 在线时长(仅离线事件有效，毫秒)
     */
    private Long onlineDuration;
    
    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 设备上线
         */
        DEVICE_ONLINE,
        
        /**
         * 设备离线
         */
        DEVICE_OFFLINE,
        
        /**
         * 状态更新(心跳刷新)
         */
        STATUS_UPDATE
    }
    
    /**
     * 创建设备上线事件
     */
    public static DeviceStatusEvent createOnlineEvent(Long deviceId, ReportSource source, String clientIp) {
        return DeviceStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_ONLINE)
                .reportSource(source)
                .clientIp(clientIp)
                .build();
    }
    
    /**
     * 创建设备离线事件
     */
    public static DeviceStatusEvent createOfflineEvent(Long deviceId, long onlineDuration) {
        return DeviceStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_OFFLINE)
                .onlineDuration(onlineDuration)
                .build();
    }
    
    /**
     * 创建状态更新事件
     */
    public static DeviceStatusEvent createUpdateEvent(Long deviceId, ReportSource source) {
        return DeviceStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.STATUS_UPDATE)
                .reportSource(source)
                .build();
    }
}