package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 设备状态事件处理器
 * 示例实现，展示如何监听设备状态变更事件
 * 
 * @author Nan
 */
@Slf4j
@Component
public class DeviceStatusEventHandler {
    
    /**
     * 处理设备上线事件
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleDeviceOnline(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_GO_LIVE) {
            log.info("DeviceStatusEvent - 设备上线事件: deviceId={}, source={}, clientIp={}",
                    event.getDeviceId(), event.getReportSource(), event.getClientIp());
        }
    }

    /**
     * 处理设备重连事件
     * @param event
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handlerDeviceReconnect(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_RECONNECT) {
            log.info("DeviceStatusEvent - 设备短时间重连事件: deviceId={}, source={}, clientIp={}",
                    event.getDeviceId(), event.getReportSource(), event.getClientIp());
        }
    }
    
    /**
     * 处理设备离线事件（定时任务检测）
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleDetectedDeviceOffline(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_DETECTED_OFFLINE) {
            log.info("DeviceStatusEvent - 标记设备离线事件: deviceId={}, 在线时长={}ms",
                    event.getDeviceId(), event.getOnlineDuration());

        }
    }
    @Async("deviceEventExecutor")
    @EventListener
    public void handleConfirmDeviceOffline(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_CONFIRMED_OFFLINE) {
            log.info("DeviceStatusEvent - 确认设备离线事件: deviceId={}, 在线时长={}ms",
                    event.getDeviceId(), event.getOnlineDuration());

        }
    }
    
    /**
     * 处理设备状态更新事件
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleStatusUpdate(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_HEARTBEAT) {
            log.debug("DeviceStatusEvent - 设备在线状态刷新事件: deviceId={}, source={}",
                    event.getDeviceId(), event.getReportSource());

        }
    }
    
    /**
     * 统一事件处理入口（可选）
     * 如果需要对所有事件进行统一处理
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleAllDeviceStatusEvents(DeviceStatusEvent event) {
        // 统一的事件记录、指标更新等
        log.debug("DeviceStatusEvent - 设备状态事件: deviceId={}, eventType={}, eventTime={}",
                event.getDeviceId(), event.getEventType(), event.getEventTime());

    }
}