package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备状态事件发布器
 * 支持配置化的Spring事件发布和异步处理
 * 
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "terminal.device.spring-event.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceStatusEventPublisher implements DeviceStatusEventPort {
    
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DeviceConfigPort deviceConfigPort;
    
    @Override
    public void publishStatusEvent(DeviceStatusEvent event) {
        try {
            log.debug("DeviceStatusEvent - 发布设备状态事件: deviceId={}, eventType={}, async={}",
                    event.getDeviceId(), event.getEventType(), deviceConfigPort.isSpringEventAsync());
            
            if (deviceConfigPort.isSpringEventAsync()) {
                // 异步发布事件
                publishEventAsync(event);
            } else {
                // 同步发布事件
                applicationEventPublisher.publishEvent(event);
            }
            
        } catch (Exception e) {
            log.error("DeviceStatusEvent - 发布设备状态事件失败: deviceId={}, eventType={}",
                    event.getDeviceId(), event.getEventType(), e);
        }
    }
    
    @Async("deviceEventExecutor")
    protected void publishEventAsync(DeviceStatusEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
    
    @Override
    public void batchPublishStatusEvents(List<DeviceStatusEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        try {
            log.debug("DeviceStatusEvent - 批量发布设备状态事件: count={}, async={}",
                    events.size(), deviceConfigPort.isSpringEventAsync());
            
            if (deviceConfigPort.isSpringEventAsync()) {
                // 异步批量发布
                batchPublishEventsAsync(events);
            } else {
                // 同步批量发布
                for (DeviceStatusEvent event : events) {
                    applicationEventPublisher.publishEvent(event);
                }
            }
        } catch (Exception e) {
            log.error("DeviceStatusEvent - 批量发布设备状态事件失败: count={}", events.size(), e);
        }
    }
    
    @Async("deviceEventExecutor")
    protected void batchPublishEventsAsync(List<DeviceStatusEvent> events) {
        for (DeviceStatusEvent event : events) {
            applicationEventPublisher.publishEvent(event);
        }
    }
}