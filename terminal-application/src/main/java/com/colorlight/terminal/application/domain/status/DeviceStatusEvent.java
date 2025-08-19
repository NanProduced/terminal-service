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
         * 设备首次上线（缓存不存在的首次上报）
         */
        DEVICE_GO_LIVE,

        /**
         * 设备重连（短时间离线后重新连接）
         */
        DEVICE_RECONNECT,

        /**
         * 设备持续在线心跳更新
         */
        DEVICE_HEARTBEAT,

        /**
         * 设备被检测为离线（定时任务检测）
         */
        DEVICE_DETECTED_OFFLINE,

        /**
         * 设备正式下线（TTL过期确认）
         */
        DEVICE_CONFIRMED_OFFLINE

    }
    
    /**
     * 创建设备上线事件
     */
    public static DeviceStatusEvent createGoLiveEvent(Long deviceId, ReportSource source, String clientIp) {
        return DeviceStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_GO_LIVE)
                .reportSource(source)
                .clientIp(clientIp)
                .build();
    }

    /**
     * 设备重连
     * @param deviceId 设备ID
     * @param source 上报源
     * @param clientIp IP
     * @return
     */
    public static DeviceStatusEvent createReconnectEvent(Long deviceId, ReportSource source, String clientIp) {
        return DeviceStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_RECONNECT)
                .reportSource(source)
                .clientIp(clientIp)
                .build();
    }
    
    /**
     * 创建设备离线事件（定时任务标记）
     */
    public static DeviceStatusEvent createDetectedOfflineEvent(Long deviceId, long onlineDuration) {
        return DeviceStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_DETECTED_OFFLINE)
                .onlineDuration(onlineDuration)
                .build();
    }

    /**
     * 创建确认设备离线事件（状态键过期）
     * @param deviceId 设备Id
     * @return
     */
    public static DeviceStatusEvent createConfirmOfflineEvent(Long deviceId) {
        return DeviceStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_CONFIRMED_OFFLINE)
                .build();
    }
    
    /**
     * 创建刷新事件-心跳
     */
    public static DeviceStatusEvent createHeartbeatEvent(Long deviceId, ReportSource source) {
        return DeviceStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_HEARTBEAT)
                .reportSource(source)
                .build();
    }
}