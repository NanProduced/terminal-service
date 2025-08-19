package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineTimePort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备在线时间管理服务
 * 独立存储设备上线时间，用于准确计算在线时长
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOnlineTimeRedisService implements DeviceOnlineTimePort {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private final DeviceConfigPort deviceConfigPort;
    
    /**
     * 获取在线时间TTL配置
     */
    private Duration getOnlineTimeTtl() {
        return Duration.ofHours(deviceConfigPort.getOnlineTimeTtlHours());
    }
    
    /**
     * 记录设备上线时间
     * 
     * @param deviceId 设备ID
     * @param onlineStartTime 上线开始时间（毫秒时间戳）
     */
    @Override
    public void recordOnlineStartTime(Long deviceId, Long onlineStartTime) {
        try {
            String onlineTimeKey = String.format(RedisKeyConstant.DEVICE_ONLINE_TIME_KEY, deviceId);
            
            // 存储上线时间，设置配置化TTL
            redisTemplate.opsForValue().set(onlineTimeKey, onlineStartTime, getOnlineTimeTtl());
            
            log.debug("DeviceOnlineTime - 记录设备上线时间: deviceId={}, onlineStartTime={}", deviceId, onlineStartTime);
            
        } catch (Exception e) {
            log.error("DeviceOnlineTime - 记录设备上线时间失败: deviceId={}, onlineStartTime={}", deviceId, onlineStartTime, e);
        }
    }
    
    /**
     * 获取设备上线时间
     * 
     * @param deviceId 设备ID
     * @return 上线开始时间，如果不存在返回null
     */
    @Override
    public Long getOnlineStartTime(Long deviceId) {
        try {
            String onlineTimeKey = String.format(RedisKeyConstant.DEVICE_ONLINE_TIME_KEY, deviceId);
            Object value = redisTemplate.opsForValue().get(onlineTimeKey);
            
            if (value != null) {
                return Long.valueOf(value.toString());
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("DeviceOnlineTime - 获取设备上线时间失败: deviceId={}", deviceId, e);
            return null;
        }
    }
    
    /**
     * 计算设备在线时长
     * 
     * @param deviceId 设备ID
     * @return 在线时长（毫秒），如果无法计算返回0
     */
    @Override
    public long calculateOnlineDuration(Long deviceId) {
        try {
            Long onlineStartTime = getOnlineStartTime(deviceId);
            
            if (onlineStartTime != null) {
                long currentTime = System.currentTimeMillis();
                return Math.max(0, currentTime - onlineStartTime);
            }
            
            log.warn("DeviceOnlineTime - 无法计算在线时长，设备上线时间不存在: deviceId={}", deviceId);
            return 0L;
            
        } catch (Exception e) {
            log.error("DeviceOnlineTime - 计算设备在线时长失败: deviceId={}", deviceId, e);
            return 0L;
        }
    }
    
    /**
     * 移除设备上线时间记录
     * 
     * @param deviceId 设备ID
     */
    @Override
    public void removeOnlineStartTime(Long deviceId) {
        try {
            String onlineTimeKey = String.format(RedisKeyConstant.DEVICE_ONLINE_TIME_KEY, deviceId);
            redisTemplate.delete(onlineTimeKey);
            
            log.debug("DeviceOnlineTime - 移除设备上线时间记录: deviceId={}", deviceId);
            
        } catch (Exception e) {
            log.error("DeviceOnlineTime - 移除设备上线时间记录失败: deviceId={}", deviceId, e);
        }
    }
    
    /**
     * 批量获取设备在线时长
     * 
     * @param deviceIds 设备ID列表
     * @return 设备ID -> 在线时长映射
     */
    @Override
    public Map<Long, Long> batchCalculateOnlineDuration(List<Long> deviceIds) {
        Map<Long, Long> result = new HashMap<>();
        
        try {
            if (deviceIds == null || deviceIds.isEmpty()) {
                return result;
            }
            
            // 批量获取上线时间
            List<String> keys = deviceIds.stream()
                    .map(id -> String.format(RedisKeyConstant.DEVICE_ONLINE_TIME_KEY, id))
                    .collect(Collectors.toList());
            
            List<Object> values = redisTemplate.opsForValue().multiGet(keys);
            long currentTime = System.currentTimeMillis();
            
            for (int i = 0; i < deviceIds.size() && i < values.size(); i++) {
                Long deviceId = deviceIds.get(i);
                Object value = values.get(i);
                
                if (value != null) {
                    try {
                        Long onlineStartTime = Long.valueOf(value.toString());
                        long duration = Math.max(0, currentTime - onlineStartTime);
                        result.put(deviceId, duration);
                    } catch (Exception e) {
                        log.warn("DeviceOnlineTime - 解析设备上线时间失败: deviceId={}, value={}", deviceId, value);
                    }
                }
            }
            
            log.debug("DeviceOnlineTime - 批量计算在线时长: 请求数量={}, 成功数量={}", deviceIds.size(), result.size());
            
        } catch (Exception e) {
            log.error("DeviceOnlineTime - 批量计算设备在线时长失败: deviceIds.size={}", deviceIds.size(), e);
        }
        
        return result;
    }
    
    /**
     * 检查设备是否有上线时间记录
     * 
     * @param deviceId 设备ID
     * @return 是否存在上线时间记录
     */
    @Override
    public boolean hasOnlineTimeRecord(Long deviceId) {
        try {
            String onlineTimeKey = String.format(RedisKeyConstant.DEVICE_ONLINE_TIME_KEY, deviceId);
            return redisTemplate.hasKey(onlineTimeKey);
        } catch (Exception e) {
            log.error("DeviceOnlineTime - 检查设备上线时间记录失败: deviceId={}", deviceId, e);
            return false;
        }
    }
}