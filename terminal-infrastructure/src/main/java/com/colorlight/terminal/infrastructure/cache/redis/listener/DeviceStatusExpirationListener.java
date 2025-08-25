package com.colorlight.terminal.infrastructure.cache.redis.listener;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineTimePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import com.colorlight.terminal.infrastructure.cache.redis.service.DeviceOnlineTimeRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redis设备状态过期监听器
 * 监听当前数据库的设备状态键过期事件，计算在线时长
 * 
 * @author Nan
 */
@Slf4j
@Component
public class DeviceStatusExpirationListener implements MessageListener {
    
    private final DeviceOnlineTimePort deviceOnlineTimePort;
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final DeviceStatusEventPort deviceStatusEventPort;
    private final RedisMessageListenerContainer listenerContainer;
    private final PatternTopic keyExpirationTopic;
    
    /**
     * 设备状态键模式：device:status:123
     */
    private static final Pattern DEVICE_STATUS_PATTERN = Pattern.compile("^device:status:(\\d+)$");
    
    /**
     * 构造函数，注入必要依赖
     */
    public DeviceStatusExpirationListener(RedisMessageListenerContainer listenerContainer,
                                         DeviceOnlineTimeRedisService deviceOnlineTimePort,
                                         DeviceOnlineStatusPort deviceOnlineStatusPort,
                                         DeviceStatusEventPort deviceStatusEventPort,
                                         PatternTopic keyExpirationTopic) {
        this.listenerContainer = listenerContainer;
        this.deviceOnlineTimePort = deviceOnlineTimePort;
        this.deviceOnlineStatusPort = deviceOnlineStatusPort;
        this.deviceStatusEventPort = deviceStatusEventPort;
        this.keyExpirationTopic = keyExpirationTopic;
    }
    
    /**
     * 启动时注册监听器到当前数据库的过期事件主题
     */
    @PostConstruct
    public void init() {
        listenerContainer.addMessageListener(this, keyExpirationTopic);
        log.info("RedisTTL监听 -deviceStatus- 已注册到特定数据库的键过期事件监听: {}", keyExpirationTopic.getTopic());
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
            // 删除上线时间键
            deviceOnlineTimePort.removeOnlineStartTime(deviceId);

            // 移除设备状态索引 这里重复删保底
            deviceOnlineStatusPort.removeDeviceIndex(deviceId);

            // 发布确认终端离线事件
            publishConfirmOfflineEvent(deviceId);

            
        } catch (Exception e) {
            log.error("RedisTTL监听 -deviceStatus- 处理设备状态过期失败: deviceId={}", deviceId, e);
        }
    }
    
    /**
     * 发布确认离线事件（缓存已过期，相关键已删）
     */
    private void publishConfirmOfflineEvent(Long deviceId) {
        try {
            // 发布设备离线事件
            DeviceStatusEvent event = DeviceStatusEvent.createConfirmOfflineEvent(deviceId);
            deviceStatusEventPort.publishStatusEvent(event);
            
            log.info("RedisTTL监听 -deviceStatus- 设备状态TTL过期，已发布事件: deviceId={}", deviceId);
        } catch (Exception e) {
            log.error("RedisTTL监听 -deviceStatus- 发布设备离线事件失败: deviceId={}", deviceId, e);
        }
    }
}