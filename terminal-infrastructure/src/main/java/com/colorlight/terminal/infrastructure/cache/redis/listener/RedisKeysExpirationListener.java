package com.colorlight.terminal.infrastructure.cache.redis.listener;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

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
public class RedisKeysExpirationListener implements MessageListener {
    
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final DeviceStatusEventPort deviceStatusEventPort;
    private final RedisMessageListenerContainer listenerContainer;
    private final PatternTopic keyExpirationTopic;
    private final MainServerRpcPort mainServerRpcPort;
    
    /**
     * 设备状态键模式：device:status:123
     */
    private static final Pattern DEVICE_STATUS_PATTERN = Pattern.compile("^device:status:(\\d+)$");

    /**
     * 设备指令键模式：terminal:command:detail:123432467132434:113
     */
    private static final Pattern DEVICE_COMMAND_PATTERN = Pattern.compile("^terminal:command:detail:(\\d+):(\\d+)$");
    /**
     * 构造函数，注入必要依赖
     */
    public RedisKeysExpirationListener(RedisMessageListenerContainer listenerContainer,
                                       DeviceOnlineStatusPort deviceOnlineStatusPort,
                                       DeviceStatusEventPort deviceStatusEventPort,
                                       PatternTopic keyExpirationTopic,
                                       MainServerRpcPort mainServerRpcPort) {
        this.listenerContainer = listenerContainer;
        this.deviceOnlineStatusPort = deviceOnlineStatusPort;
        this.deviceStatusEventPort = deviceStatusEventPort;
        this.keyExpirationTopic = keyExpirationTopic;
        this.mainServerRpcPort = mainServerRpcPort;
    }
    
    /**
     * 启动时注册监听器到当前数据库的过期事件主题
     */
    @PostConstruct
    public void init() {
        listenerContainer.addMessageListener(this, keyExpirationTopic);
        log.info("RedisTTL监听 - 已注册到特定数据库的键过期事件监听: {}", keyExpirationTopic.getTopic());
    }
    
    @Override
    public void onMessage(@NotNull Message message, byte[] pattern) {
        try {
            String expiredKey = message.toString();
            log.debug("RedisTTL监听 - 检测到Redis键过期: {}", expiredKey);
            
            // 匹配设备状态键
            Matcher matcher = DEVICE_STATUS_PATTERN.matcher(expiredKey);
            if (matcher.matches()) {
                Long deviceId = Long.valueOf(matcher.group(1));
                log.info("RedisTTL监听 -deviceStatus- 设备状态键过期: deviceId={}, key={}", deviceId, expiredKey);
                
                // 处理设备状态过期
                handleDeviceStatusExpiration(deviceId);
                return;
            }

            Matcher matcher1 = DEVICE_COMMAND_PATTERN.matcher(expiredKey);
            if (matcher1.matches()) {
                Long deviceId = Long.valueOf(matcher1.group(1));
                Integer commandId = Integer.valueOf(matcher1.group(2));
                log.info("RedisTTL监听 -command- 指令详情键过期: deviceId={}, key={}", deviceId, expiredKey);
                handleDeviceCommandExpiration(deviceId, commandId);
            }
            
        } catch (Exception e) {
            log.error("RedisTTL监听 -deviceStatus- 处理Redis键过期事件失败: key={}", message, e);
        }
    }

    /*========================= 设备状态键过期处理 =========================*/

    /**
     * 处理设备状态过期
     */
    private void handleDeviceStatusExpiration(Long deviceId) {
        try {
            // 移除设备状态索引
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

    /*========================= 设备指令键过期处理 =========================*/

    /**
     * 处理设备指令过期
     * @param deviceId 设备ID
     * @param commandId 指令ID
     */
    private void handleDeviceCommandExpiration(Long deviceId, Integer commandId) {
        mainServerRpcPort.notifyCommandExpiration(deviceId, commandId);
    }
}