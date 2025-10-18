package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 设备状态事件发布器
 * 支持配置化的Spring事件发布和异步处理
 * 
 * @author Nan
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "terminal.device.spring-event.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceStatusEventPublisher implements DeviceStatusEventPort {
    
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DeviceConfigPort deviceConfigPort;
    private final Executor deviceEventExecutor;
    
    // 手动构造函数以支持@Qualifier注解
    public DeviceStatusEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            DeviceConfigPort deviceConfigPort,
            @Qualifier("deviceEventExecutor") Executor deviceEventExecutor) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.deviceConfigPort = deviceConfigPort;
        this.deviceEventExecutor = deviceEventExecutor;
    }
    
    @Override
    public void publishStatusEvent(DeviceStatusEvent event) {
        try {
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
    
    private void publishEventAsync(DeviceStatusEvent event) {
        deviceEventExecutor.execute(() -> {
            try {
                applicationEventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.error("DeviceStatusEvent - 异步发布事件失败: deviceId={}, eventType={}", 
                    event.getDeviceId(), event.getEventType(), e);
            }
        });
    }
    
    @Override
    public void batchPublishStatusEvents(List<DeviceStatusEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        try {
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
    
    private void batchPublishEventsAsync(List<DeviceStatusEvent> events) {
        deviceEventExecutor.execute(() -> {
            try {
                for (DeviceStatusEvent event : events) {
                    applicationEventPublisher.publishEvent(event);
                }
            } catch (Exception e) {
                log.error("DeviceStatusEvent - 异步批量发布事件失败: count={}", events.size(), e);
            }
        });
    }
}