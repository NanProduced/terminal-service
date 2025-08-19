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
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_ONLINE) {
            log.info("DeviceStatusEvent - 设备上线事件: deviceId={}, source={}, clientIp={}",
                    event.getDeviceId(), event.getReportSource(), event.getClientIp());
            
            // TODO: 业务逻辑扩展点
            // 1. 发送上线通知给主服务
            // 2. 记录设备上线日志
            // 3. 触发设备状态同步
            // 4. 更新设备统计信息
        }
    }
    
    /**
     * 处理设备离线事件
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleDeviceOffline(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_OFFLINE) {
            log.info("DeviceStatusEvent - 设备离线事件: deviceId={}, 在线时长={}ms",
                    event.getDeviceId(), event.getOnlineDuration());
            
            // TODO: 业务逻辑扩展点
            // 1. 发送离线通知给主服务
            // 2. 清理设备相关资源
            // 3. 记录在线时长统计
            // 4. 触发告警（如果需要）
        }
    }
    
    /**
     * 处理设备状态更新事件
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleStatusUpdate(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.STATUS_UPDATE) {
            log.debug("DeviceStatusEvent - 设备状态更新事件: deviceId={}, source={}",
                    event.getDeviceId(), event.getReportSource());
            
            // TODO: 业务逻辑扩展点
            // 1. 更新设备活跃度统计
            // 2. 刷新监控指标
            // 3. 检查设备健康状态
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
        
        // TODO: 扩展点
        // 1. 事件持久化到MongoDB
        // 2. 指标数据更新
        // 3. 实时监控数据推送
    }
}