package com.colorlight.terminal.infrastructure.cache.redis.listener;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import com.colorlight.terminal.infrastructure.cache.redis.service.DeviceOnlineTimeRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redis设备状态过期监听器
 * 监听设备状态键过期事件，计算在线时长
 * 
 * @author Nan
 */
@Slf4j
@Component
public class DeviceStatusExpirationListener extends KeyExpirationEventMessageListener {
    
    private final DeviceOnlineTimeRedisService onlineTimeService;
    private final DeviceStatusEventPort deviceStatusEventPort;
    
    /**
     * 设备状态键模式：device:status:123
     */
    private static final Pattern DEVICE_STATUS_PATTERN = Pattern.compile("^device:status:(\\d+)$");
    
    /**
     * 构造函数，注入必要依赖
     */
    public DeviceStatusExpirationListener(RedisMessageListenerContainer listenerContainer,
                                         DeviceOnlineTimeRedisService onlineTimeService,
                                         DeviceStatusEventPort deviceStatusEventPort) {
        super(listenerContainer);
        this.onlineTimeService = onlineTimeService;
        this.deviceStatusEventPort = deviceStatusEventPort;
    }
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String expiredKey = message.toString();
            log.debug("RedisTTL监听 -deviceStatus- 检测到Redis键过期: {}", expiredKey);
            
            // 匹配设备状态键
            Matcher matcher = DEVICE_STATUS_PATTERN.matcher(expiredKey);
            if (matcher.matches()) {
                Long deviceId = Long.valueOf(matcher.group(1));
                log.info("RedisTTL监听 -deviceStatus- 设备状态键过期: deviceId={}, key={}", deviceId, expiredKey);
                
                // 处理设备状态过期
                handleDeviceStatusExpiration(deviceId);
            }
            
        } catch (Exception e) {
            log.error("RedisTTL监听 -deviceStatus- 处理Redis键过期事件失败: key={}", message, e);
        }
    }
    
    /**
     * 处理设备状态过期
     */
    private void handleDeviceStatusExpiration(Long deviceId) {
        try {
            // 1. 从在线时间存储中获取上线时间
            Long onlineStartTime = onlineTimeService.getOnlineStartTime(deviceId);
            
            if (onlineStartTime != null) {
                // 2. 计算在线时长（从上线时间到当前时间）
                long currentTime = System.currentTimeMillis();
                long onlineDuration = currentTime - onlineStartTime;
                
                log.info("RedisTTL监听 -deviceStatus- 通过过期监听计算在线时长: deviceId={}, duration={}ms", deviceId, onlineDuration);
                
                // 3. 记录在线时长（如果有统计需求的话）
                
                // 4. 清除在线时间记录
                onlineTimeService.removeOnlineStartTime(deviceId);
                
                // 5. 发布设备离线事件
                publishOfflineEvent(deviceId, onlineDuration);
            }
            
        } catch (Exception e) {
            log.error("RedisTTL监听 -deviceStatus- 处理设备状态过期失败: deviceId={}", deviceId, e);
        }
    }
    
    /**
     * 发布离线事件
     */
    private void publishOfflineEvent(Long deviceId, Long onlineDuration) {
        try {
            // 发布设备离线事件
            DeviceStatusEvent event = DeviceStatusEvent.createOfflineEvent(deviceId, onlineDuration);
            deviceStatusEventPort.publishStatusEvent(event);
            
            log.info("RedisTTL监听 -deviceStatus- 设备因TTL过期而离线，已发布事件: deviceId={}, onlineDuration={}ms", deviceId, onlineDuration);
        } catch (Exception e) {
            log.error("RedisTTL监听 -deviceStatus- 发布设备离线事件失败: deviceId={}", deviceId, e);
        }
    }
}