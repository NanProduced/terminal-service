package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineTimePort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Consumer;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

/**
 * 设备在线状态Redis存储服务
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOnlineStatusRedisService implements DeviceOnlineStatusPort {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceOnlineTimePort deviceOnlineTimePort;
    private final DeviceConfigPort deviceConfigPort;
    
    /**
     * 获取状态TTL配置
     */
    private Duration getStatusTtl() {
        return Duration.ofSeconds(deviceConfigPort.getRedisStatusTtl());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void saveDeviceStatus(DeviceOnlineStatus status) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, status.getDeviceId());
        
        try {
            // 使用Redis事务保证原子性
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    
                    // 保存设备状态详情
                    Map<String, Object> statusMap = convertToRedisMap(status);
                    operations.opsForHash().putAll(statusKey, statusMap);
                    operations.expire(statusKey, getStatusTtl());
                    
                    // 添加到设备索引
                    operations.opsForSet().add(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY, status.getDeviceId());
                    
                    // 更新在线设备计数（如果是在线状态）
                    if (status.getStatus() == OnlineStatus.ONLINE) {
                        operations.opsForValue().increment(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);
                    }
                    
                    return operations.exec();
                }
            });
            
            // 分离存储：如果是在线状态且有上线时间，记录到独立的在线时间存储
            if (status.getStatus() == OnlineStatus.ONLINE && status.getOnlineStartTime() != null) {
                deviceOnlineTimePort.recordOnlineStartTime(status.getDeviceId(), status.getOnlineStartTime());
            }
            
            log.debug("DeviceOnlineStatus - 保存设备状态成功: deviceId={}, status={}", status.getDeviceId(), status.getStatus());
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 保存设备状态失败: deviceId={}", status.getDeviceId(), e);
            throw e;
        }
    }
    
    @Override
    public Optional<DeviceOnlineStatus> getDeviceStatus(Long deviceId) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);
        
        try {
            Map<Object, Object> statusMap = redisTemplate.opsForHash().entries(statusKey);
            
            if (statusMap.isEmpty()) {
                return Optional.empty();
            }
            
            DeviceOnlineStatus status = convertFromRedisMap(deviceId, statusMap);
            return Optional.of(status);
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 获取设备状态失败: deviceId={}", deviceId, e);
            return Optional.empty();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, DeviceOnlineStatus> batchGetDeviceStatus(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        try {
            log.debug("DeviceOnlineStatus - 批量获取设备状态: count={}", deviceIds.size());
            
            // 使用Pipeline优化批量查询
            List<Object> results = redisTemplate.executePipelined(
                new SessionCallback<Object>() {
                    @Override
                    public Object execute(RedisOperations operations) throws DataAccessException {
                        for (Long deviceId : deviceIds) {
                            String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);
                            operations.opsForHash().entries(statusKey);
                        }
                        return null;
                    }
                }
            );

            
            Map<Long, DeviceOnlineStatus> statusMap = new HashMap<>();
            
            for (int i = 0; i < deviceIds.size() && i < results.size(); i++) {
                Long deviceId = deviceIds.get(i);
                @SuppressWarnings("unchecked")
                Map<Object, Object> redisMap = (Map<Object, Object>) results.get(i);
                
                if (redisMap != null && !redisMap.isEmpty()) {
                    DeviceOnlineStatus status = convertFromRedisMap(deviceId, redisMap);
                    statusMap.put(deviceId, status);
                }
            }
            
            log.debug("DeviceOnlineStatus - 批量获取设备状态完成: 请求={}, 成功={}", deviceIds.size(), statusMap.size());
            return statusMap;
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 批量获取设备状态失败: deviceIds.size={}", deviceIds.size(), e);
            return Collections.emptyMap();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void removeDeviceStatus(Long deviceId) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);
        
        try {
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    
                    // 删除状态详情
                    operations.delete(statusKey);
                    
                    // 从索引中移除
                    operations.opsForSet().remove(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY, deviceId);
                    
                    // 减少在线设备计数
                    operations.opsForValue().decrement(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);
                    
                    return operations.exec();
                }
            });
            
            log.debug("DeviceOnlineStatus - 删除设备状态成功: deviceId={}", deviceId);
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 删除设备状态失败: deviceId={}", deviceId, e);
        }
    }
    
    @Override
    public Set<Long> getAllDeviceIds() {
        // 根据配置选择查询方式
        if (deviceConfigPort.isStreamQueryEnabled()) {
            return getAllDeviceIdsWithStream();
        } else {
            return getAllDeviceIdsTraditional();
        }
    }
    
    /**
     * 传统方式获取所有设备ID - 一次性加载
     * 适用于设备数量较少的场景
     */
    private Set<Long> getAllDeviceIdsTraditional() {
        try {
            Set<Object> deviceIdObjs = redisTemplate.opsForSet().members(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY);
            
            if (deviceIdObjs == null) {
                return Collections.emptySet();
            }
            
            return deviceIdObjs.stream()
                    .map(obj -> Long.valueOf(obj.toString()))
                    .collect(Collectors.toSet());
                    
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 获取所有设备ID失败", e);
            return Collections.emptySet();
        }
    }
    
    /**
     * 流式方式获取所有设备ID - 分页迭代
     * 适用于设备数量很大的场景，避免OOM
     */
    private Set<Long> getAllDeviceIdsWithStream() {
        Set<Long> deviceIds = new HashSet<>();
        
        try {
            streamAllDeviceIds(deviceId -> deviceIds.add(deviceId));
            log.debug("DeviceOnlineStatus - 流式获取设备ID完成: count={}", deviceIds.size());
            return deviceIds;
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 流式获取所有设备ID失败", e);
            // 降级到传统方式
            log.warn("DeviceOnlineStatus - 降级使用传统方式获取设备ID");
            return getAllDeviceIdsTraditional();
        }
    }
    
    /**
     * 流式迭代所有设备ID
     * 使用SCAN命令分页获取SET成员，避免阻塞Redis
     * 
     * @param consumer 设备ID消费者
     */
    public void streamAllDeviceIds(Consumer<Long> consumer) {
        if (consumer == null) {
            return;
        }
        
        try {
            int pageSize = deviceConfigPort.getStreamQueryPageSize();
            int maxIterations = deviceConfigPort.getStreamQueryMaxIterations();
            long timeoutMs = deviceConfigPort.getStreamQueryTimeoutMs();
            
            long startTime = System.currentTimeMillis();
            int iterationCount = 0;
            
            // 使用SCAN命令分页迭代SET成员
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .count(pageSize)
                    .build();
            
            try (Cursor<Object> cursor = redisTemplate.opsForSet().scan(
                    RedisKeyConstant.DEVICE_STATUS_INDEX_KEY, scanOptions)) {
                
                while (cursor.hasNext() && iterationCount < maxIterations) {
                    // 检查超时
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        log.warn("DeviceOnlineStatus - 流式查询超时: timeoutMs={}, iterations={}", 
                                timeoutMs, iterationCount);
                        break;
                    }
                    
                    Object deviceIdObj = cursor.next();
                    if (deviceIdObj != null) {
                        try {
                            Long deviceId = Long.valueOf(deviceIdObj.toString());
                            consumer.accept(deviceId);
                        } catch (NumberFormatException e) {
                            log.warn("DeviceOnlineStatus - 无效设备ID格式: {}", deviceIdObj);
                        }
                    }
                    
                    iterationCount++;
                }
                
                log.debug("DeviceOnlineStatus - 流式查询完成: iterations={}, duration={}ms", 
                        iterationCount, System.currentTimeMillis() - startTime);
            }
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 流式迭代设备ID失败", e);
            throw e;
        }
    }
    
    @Override
    public List<Long> findExpiredDevices(long expireThreshold) {
        try {
            Set<Long> allDeviceIds = getAllDeviceIds();
            
            if (allDeviceIds.isEmpty()) {
                return Collections.emptyList();
            }
            
            // 使用Lua脚本批量检查过期设备
            String luaScript = """
                local expireThreshold = ARGV[1]
                local expiredDevices = {}
                
                for i, deviceId in ipairs(KEYS) do
                    local statusKey = 'device:status:' .. deviceId
                    local lastReportTime = redis.call('HGET', statusKey, 'lastReportTime')
                    
                    if lastReportTime and tonumber(lastReportTime) < tonumber(expireThreshold) then
                        table.insert(expiredDevices, deviceId)
                    end
                end
                
                return expiredDevices
                """;
            
            List<String> keys = allDeviceIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            
            @SuppressWarnings("unchecked")
            List<String> expiredDeviceStrs = (List<String>) redisTemplate.execute(
                    new DefaultRedisScript<>(luaScript, List.class),
                    keys,
                    String.valueOf(expireThreshold)
            );
            
            return expiredDeviceStrs.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 查找过期设备失败: expireThreshold={}", expireThreshold, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void batchMarkOffline(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        
        try {
            log.debug("DeviceOnlineStatus - 批量标记设备离线: count={}", deviceIds.size());
            
            // 1. 先批量计算在线时长
            Map<Long, Long> onlineDurations = deviceOnlineTimePort.batchCalculateOnlineDuration(deviceIds);
            
            // 2. 批量更新Redis状态
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    
                    for (Long deviceId : deviceIds) {
                        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);
                        
                        // 更新状态为离线，清除上线开始时间
                        operations.opsForHash().put(statusKey, "status", OnlineStatus.OFFLINE.name());
                        operations.opsForHash().put(statusKey, "statusChangeTime", System.currentTimeMillis());
                        operations.opsForHash().delete(statusKey, "onlineStartTime");
                        
                        // 重新设置TTL
                        operations.expire(statusKey, getStatusTtl());
                    }
                    
                    // 减少在线设备计数
                    operations.opsForValue().decrement(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY, deviceIds.size());
                    
                    return operations.exec();
                }
            });
            
            // 3. 清理在线时间记录
            for (Long deviceId : deviceIds) {
                try {
                    Long onlineDuration = onlineDurations.get(deviceId);
                    if (onlineDuration != null && onlineDuration > 0) {
                        log.debug("DeviceOnlineStatus - 设备离线，在线时长: deviceId={}, duration={}ms", deviceId, onlineDuration);
                        // 这里可以记录在线时长统计或发布事件
                    }
                    
                    // 清理在线时间记录
                    deviceOnlineTimePort.removeOnlineStartTime(deviceId);
                    
                } catch (Exception e) {
                    log.warn("DeviceOnlineStatus - 处理设备离线后续操作失败: deviceId={}", deviceId, e);
                }
            }
            
            log.info("DeviceOnlineStatus - 批量标记设备离线完成: count={}, 计算在线时长数量={}",
                    deviceIds.size(), onlineDurations.size());
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 批量标记设备离线失败: deviceIds.size={}", deviceIds.size(), e);
        }
    }
    
    @Override
    public int getOnlineDeviceCount() {
        try {
            String countStr = (String) redisTemplate.opsForValue().get(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);
            return countStr != null ? Integer.parseInt(countStr) : 0;
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 获取在线设备数量失败", e);
            return 0;
        }
    }
    
    /**
     * 将DeviceOnlineStatus转换为Redis存储格式
     */
    private Map<String, Object> convertToRedisMap(DeviceOnlineStatus status) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("deviceId", status.getDeviceId());
        map.put("lastReportTime", status.getLastReportTime());
        map.put("lastReportSource", status.getLastReportSource() != null ? status.getLastReportSource().name() : null);
        map.put("status", status.getStatus().name());
        map.put("statusChangeTime", status.getStatusChangeTime());
        map.put("onlineStartTime", status.getOnlineStartTime());
        map.put("clientIp", status.getClientIp());
        
        return map;
    }
    
    /**
     * 从Redis存储格式转换为DeviceOnlineStatus
     */
    private DeviceOnlineStatus convertFromRedisMap(Long deviceId, Map<Object, Object> map) {
        DeviceOnlineStatus status = new DeviceOnlineStatus();
        
        status.setDeviceId(deviceId);
        status.setLastReportTime(getLongValue(map.get("lastReportTime")));
        
        String sourceStr = (String) map.get("lastReportSource");
        status.setLastReportSource(sourceStr != null ? ReportSource.valueOf(sourceStr) : null);
        
        String statusStr = (String) map.get("status");
        status.setStatus(statusStr != null ? OnlineStatus.valueOf(statusStr) : OnlineStatus.OFFLINE);
        
        status.setStatusChangeTime(getLongValue(map.get("statusChangeTime")));
        status.setOnlineStartTime(getLongValue(map.get("onlineStartTime")));
        status.setClientIp((String) map.get("clientIp"));
        
        return status;
    }
    
    /**
     * 安全地获取Long值
     */
    private Long getLongValue(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Long) {
            return (Long) value;
        }
        
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}