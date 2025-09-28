package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

import static com.colorlight.terminal.application.domain.CommonConstant.Device.*;
import static com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant.DEVICE_STATUS_INDEX_KEY;

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
    private final DeviceConfigPort deviceConfigPort;
    
    /**
     * 获取状态TTL配置 - 用于正常在线状态
     */
    private Duration getStatusTtl() {
        return Duration.ofSeconds(deviceConfigPort.getRedisStatusTtl());
    }
    
    /**
     * 获取重连窗口TTL配置 - 用于离线后等待重连
     */
    private Duration getReconnectTtl() {
        return Duration.ofSeconds(deviceConfigPort.getReconnectTtl());
    }

    @Override
    public void smartDetermined(DeviceOnlineStatus status) {
        // 检查 status.getStatus() 是否为 null
        if (status.getStatus() == null) {
            updateDeviceStatus(status);
        } else {
            switch (status.getStatus()) {
                case GO_LIVE, RECONNECT:
                    saveDeviceStatus(status);
                    break;
                case ONLINE:
                    updateDeviceStatus(status);
                    break;
                case OFFLINE:
                    // OFFLINE 不做任何处理
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 上线和重连两个状态使用这个方法
     * <p>全量保存+同步处理</p>
     * @param status 完整的设备状态
     */
    @Override
    @SuppressWarnings("unchecked")
    public void saveDeviceStatus(DeviceOnlineStatus status) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, status.getDeviceId());
        
        try {
            // 在应用层分布式锁保护下，直接执行保存操作
            // 使用Redis事务保证原子性
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    
                    // 保存设备状态详情
                    Map<String, Object> statusMap = convertToRedisMap(status);
                    operations.opsForHash().putAll(statusKey, statusMap);
                    operations.expire(statusKey, getStatusTtl());

                    // 添加到设备索引
                    operations.opsForSet().add(DEVICE_STATUS_INDEX_KEY, status.getDeviceId());

                    // 增加在线设备计数
                    operations.opsForValue().increment(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);
                    
                    return operations.exec();
                }
            });
            
            log.debug("DeviceOnlineStatus - 保存设备状态成功: deviceId={}, status={}", status.getDeviceId(), status.getStatus());
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 保存设备状态失败: deviceId={}", status.getDeviceId(), e);
            throw e;
        }
    }
    

    /**
     * 在线状态更新走这个方法，可异步
     * @param status 部分设备状态字段
     */
    @Override
    @SuppressWarnings("unchecked")
    public void updateDeviceStatus(DeviceOnlineStatus status) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, status.getDeviceId());

        try {
            // 1. 构建更新字段映射
            Map<String, Object> updateFields = convertToRedisMap(status);

            // 4. 执行原子更新
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 更新状态字段
                    operations.opsForHash().putAll(statusKey, updateFields);

                    // 重新设置TTL
                    operations.expire(statusKey, getStatusTtl());

                    return operations.exec();
                }
            });

            log.debug("DeviceOnlineStatus - 设备状态部分更新成功: deviceId={}, 更新字段={}",
                    status.getDeviceId(), updateFields.keySet());

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 更新设备状态失败: deviceId={}", status.getDeviceId(), e);
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
                    public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
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
    public Long getDeviceLastReportTime(Long deviceId) {
        Optional<DeviceOnlineStatus> deviceStatus = getDeviceStatus(deviceId);
        if (deviceStatus.isPresent()) {
            DeviceOnlineStatus status = deviceStatus.get();
            Long lastReportTime = status.getLastReportTime();
            if (lastReportTime != null && lastReportTime > 0) {
                return lastReportTime;
            }
            return status.getOnlineStartTime();
        }
        return null; // 返回null而不是0，更明确表示设备不存在
    }

    @Override
    @SuppressWarnings("unchecked")
    public void removeDeviceStatus(Long deviceId) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);
        
        try {
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    
                    // 删除状态详情
                    operations.delete(statusKey);
                    
                    // 从索引中移除
                    operations.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);
                    
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
    public void removeDeviceIndex(Long deviceId) {
        try {
            redisTemplate.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);
            log.debug("DeviceOnlineStatus - 删除设备状态索引成功: deviceId={}", deviceId);
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 删除设备状态索引失败: deviceId={}", deviceId, e);
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
            Set<Object> deviceIdObjs = redisTemplate.opsForSet().members(DEVICE_STATUS_INDEX_KEY);
            
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
            streamAllDeviceIds(deviceIds::add);
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
    public void streamAllDeviceIds(LongConsumer consumer) {
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
                    DEVICE_STATUS_INDEX_KEY, scanOptions)) {
                
                while (cursor.hasNext() && iterationCount < maxIterations) {
                    // 检查超时
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        log.warn("DeviceOnlineStatus - 流式查询超时: timeoutMs={}, iterations={}", 
                                timeoutMs, iterationCount);
                        break;
                    }
                    
                    Object deviceIdObj = cursor.next();
                    if (deviceIdObj != null) {
                        processDeviceIdObject(deviceIdObj, consumer);
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
    
    /**
     * 处理设备ID对象
     * @param deviceIdObj 设备ID对象
     * @param consumer 设备ID消费者
     */
    private void processDeviceIdObject(Object deviceIdObj, LongConsumer consumer) {
        try {
            long deviceId = Long.parseLong(deviceIdObj.toString());
            consumer.accept(deviceId);
        } catch (NumberFormatException e) {
            log.warn("DeviceOnlineStatus - 无效设备ID格式: {}", deviceIdObj);
        }
    }
    
    @Override
    public List<Long> findExpiredDevices(long expireThreshold) {
        // 流式查询，后期再考虑LUA脚本
        return findExpiredDevicesWithStream(expireThreshold);
    }
    
    /**
     * 使用流式查询和分批Pipeline的方式查找过期设备
     * 替代Lua脚本，避免序列化问题，更好的可调试性
     */
    public List<Long> findExpiredDevicesWithStream(long expireThreshold) {
        try {
            long startTime = System.currentTimeMillis();
            log.debug("DeviceOnlineStatus - 流式查找过期设备开始: expireThreshold={}, 当前时间={}", 
                    expireThreshold, startTime);
            
            List<Long> expiredDevices = new ArrayList<>();
            List<Long> batchDeviceIds = new ArrayList<>();
            
            // 配置批量大小，避免单次Pipeline过大
            int batchSize = Math.min(deviceConfigPort.getStreamQueryPageSize(), 100);

            // 使用流式查询处理所有设备
            streamAllDeviceIds(deviceId -> {
                batchDeviceIds.add(deviceId);
                
                // 达到批量大小时进行处理
                if (batchDeviceIds.size() >= batchSize) {
                    List<Long> batchExpired = processBatchDevices(new ArrayList<>(batchDeviceIds), expireThreshold);
                    expiredDevices.addAll(batchExpired);
                    
                    log.debug("DeviceOnlineStatus - 处理批次: 设备数={}, 过期数={}, 累计过期={}", 
                            batchDeviceIds.size(), batchExpired.size(), expiredDevices.size());
                    
                    batchDeviceIds.clear();
                }
            });
            
            // 处理剩余的设备
            if (!batchDeviceIds.isEmpty()) {
                List<Long> batchExpired = processBatchDevices(batchDeviceIds, expireThreshold);
                expiredDevices.addAll(batchExpired);
                
                log.debug("DeviceOnlineStatus - 处理最后批次: 设备数={}, 过期数={}, 最终过期总数={}", 
                        batchDeviceIds.size(), batchExpired.size(), expiredDevices.size());
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("DeviceOnlineStatus - 流式查找过期设备完成: 过期设备数={}, 耗时={}ms", 
                    expiredDevices.size(), elapsed);
            
            return expiredDevices;
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 流式查找过期设备失败: expireThreshold={}", expireThreshold, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 处理一批设备，找出其中过期的设备
     */
    private List<Long> processBatchDevices(List<Long> deviceIds, long expireThreshold) {
        if (deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            // 使用现有的批量查询方法
            Map<Long, DeviceOnlineStatus> statusMap = batchGetDeviceStatus(deviceIds);
            List<Long> expiredDevices = new ArrayList<>();
            
            for (Long deviceId : deviceIds) {
                DeviceOnlineStatus status = statusMap.get(deviceId);
                
                if (status == null) {
                    // 状态不存在，视为离线（索引存在但数据缺失）
                    expiredDevices.add(deviceId);
                    log.debug("DeviceOnlineStatus - 设备状态缺失，标记离线: deviceId={}", deviceId);
                    
                } else if (status.getLastReportTime() == null) {
                    // lastReportTime为空，视为离线
                    expiredDevices.add(deviceId);
                    log.debug("DeviceOnlineStatus - 设备lastReportTime为空，标记离线: deviceId={}", deviceId);
                    
                } else if (status.getLastReportTime() < expireThreshold) {
                    // 超过阈值，视为离线
                    expiredDevices.add(deviceId);
                    log.debug("DeviceOnlineStatus - 设备超时离线: deviceId={}, lastReportTime={}, expireThreshold={}, 超时={}ms", 
                            deviceId, status.getLastReportTime(), expireThreshold, 
                            expireThreshold - status.getLastReportTime());
                            
                } else {
                    // 设备在线
                    log.debug("DeviceOnlineStatus - 设备在线: deviceId={}, lastReportTime={}, 距离超时还有={}ms", 
                            deviceId, status.getLastReportTime(), 
                            status.getLastReportTime() - expireThreshold);
                }
            }
            
            return expiredDevices;
            
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 处理设备批次失败: deviceIds.size={}", deviceIds.size(), e);
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void removeDeviceStatusForStartupCleanup(Long deviceId) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);

        try {
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 删除状态详情（不影响计数器）
                    operations.delete(statusKey);

                    // 从索引中移除
                    operations.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);

                    // 启动清理时不修改计数器，因为已经在启动时重置

                    return operations.exec();
                }
            });

            log.debug("DeviceOnlineStatus - 启动清理设备状态成功: deviceId={}", deviceId);

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 启动清理设备状态失败: deviceId={}", deviceId, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DeviceOnlineStatus markOfflineAndResetTtl(Long deviceId) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);

        try {
            // 先获取当前状态信息
            Optional<DeviceOnlineStatus> currentStatusOpt = getDeviceStatus(deviceId);
            if (currentStatusOpt.isEmpty()) {
                log.debug("DeviceOnlineStatus -single- 设备状态不存在，无法标记离线: deviceId={}", deviceId);
                return null;
            }

            DeviceOnlineStatus currentStatus = currentStatusOpt.get();
            OnlineStatus status = currentStatus.getStatus();

            // 检查是否为在线相关状态
            if (status != OnlineStatus.ONLINE && status != OnlineStatus.GO_LIVE && status != OnlineStatus.RECONNECT) {
                log.debug("DeviceOnlineStatus -single- 设备非在线状态，跳过离线处理: deviceId={}, status={}", deviceId, status);
                return null;
            }

            // 验证时间信息完整性
            if (currentStatus.getOnlineStartTime() == null || currentStatus.getLastReportTime() == null) {
                log.warn("DeviceOnlineStatus -single- 设备时间信息不完整，跳过离线处理: deviceId={}, onlineStartTime={}, lastReportTime={}",
                        deviceId, currentStatus.getOnlineStartTime(), currentStatus.getLastReportTime());
                return null;
            }

            // 使用Redis事务原子化执行离线操作
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 更新状态为离线
                    operations.opsForHash().put(statusKey, STATUS, OnlineStatus.OFFLINE.name());
                    operations.opsForHash().put(statusKey, STATUS_CHANGE_TIME, System.currentTimeMillis());

                    // 重置TTL为重连窗口时间
                    operations.expire(statusKey, getReconnectTtl());

                    // 从在线设备索引中移除
                    operations.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);

                    // 减少在线设备计数
                    operations.opsForValue().decrement(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);

                    return operations.exec();
                }
            });

            // 标记当前状态对象为离线（返回给调用方用于保存在线时长记录）
            currentStatus.markOffline();

            log.info("DeviceOnlineStatus - 设备标记离线并重置TTL成功: deviceId={}, reconnectTtl={}s",
                    deviceId, getReconnectTtl().getSeconds());

            return currentStatus;

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 标记设备离线失败: deviceId={}", deviceId, e);
            return null;
        }
    }
    
    @Override
    public List<DeviceOnlineStatus> batchMarkOfflineAndResetTtl(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            long startTime = System.currentTimeMillis();

            // 第一步：批量获取当前状态
            Map<Long, DeviceOnlineStatus> currentStatuses = batchGetDeviceStatus(deviceIds);

            // 过滤出需要处理的设备（在线状态且时间信息完整）
            List<Long> validDeviceIds = new ArrayList<>();
            List<DeviceOnlineStatus> validStatuses = new ArrayList<>();

            for (Long deviceId : deviceIds) {
                DeviceOnlineStatus status = currentStatuses.get(deviceId);
                if (isValidForOfflineProcessing(deviceId, status)) {
                    validDeviceIds.add(deviceId);
                    validStatuses.add(status);
                }
            }

            if (validDeviceIds.isEmpty()) {
                log.debug("DeviceOnlineStatus - 批量标记离线: 无有效设备需要处理");
                return new ArrayList<>();
            }

            // 第二步：使用Pipeline批量执行离线操作
            long currentTime = System.currentTimeMillis();
            Duration reconnectTtl = getReconnectTtl();

            redisTemplate.executePipelined(new RedisCallback<Object>() {
                @Override
                public Object doInRedis(@NotNull RedisConnection connection) throws DataAccessException {
                    StringRedisConnection stringConnection = (StringRedisConnection) connection;

                    for (Long deviceId : validDeviceIds) {
                        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);

                        // 更新状态为离线
                        stringConnection.hSet(statusKey, STATUS, OnlineStatus.OFFLINE.name());
                        stringConnection.hSet(statusKey, STATUS_CHANGE_TIME, String.valueOf(currentTime));

                        // 重置TTL为重连窗口时间
                        stringConnection.expire(statusKey, reconnectTtl.getSeconds());

                        // 从在线设备索引中移除
                        stringConnection.sRem(DEVICE_STATUS_INDEX_KEY, deviceId.toString());
                    }

                    // 批量减少在线设备计数
                    if (validDeviceIds.size() > 1) {
                        stringConnection.decrBy(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY, validDeviceIds.size());
                    } else {
                        stringConnection.decr(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);
                    }

                    return null;
                }
            });

            // 第三步：标记返回的状态对象为离线
            for (DeviceOnlineStatus status : validStatuses) {
                status.markOffline();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("DeviceOnlineStatus - 批量标记设备离线完成: total={}, valid={}, duration={}ms, reconnectTtl={}s",
                    deviceIds.size(), validDeviceIds.size(), duration, reconnectTtl.getSeconds());

            return validStatuses;

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 批量标记设备离线失败: deviceCount={}", deviceIds.size(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 检查设备状态是否适合离线处理
     */
    private boolean isValidForOfflineProcessing(Long deviceId, DeviceOnlineStatus status) {
        if (status == null) {
            log.debug("DeviceOnlineStatus -batch- 设备状态不存在，跳过离线处理: deviceId={}", deviceId);
            return false;
        }

        OnlineStatus currentStatus = status.getStatus();
        if (currentStatus != OnlineStatus.ONLINE &&
            currentStatus != OnlineStatus.GO_LIVE &&
            currentStatus != OnlineStatus.RECONNECT) {
            log.debug("DeviceOnlineStatus -batch- 设备非在线状态，跳过离线处理: deviceId={}, status={}",
                     deviceId, currentStatus);
            return false;
        }

        if (status.getOnlineStartTime() == null || status.getLastReportTime() == null) {
            log.warn("DeviceOnlineStatus -batch- 设备时间信息不完整，跳过离线处理: deviceId={}, onlineStartTime={}, lastReportTime={}",
                    deviceId, status.getOnlineStartTime(), status.getLastReportTime());
            return false;
        }

        return true;
    }

    @Override
    public int getOnlineDeviceCount() {
        try {
            Integer count = (Integer) redisTemplate.opsForValue().get(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 获取在线设备数量失败", e);
            return 0;
        }
    }

    @Override
    public void setOnlineDeviceCount(int onlineDeviceCount) {
        try {
            redisTemplate.opsForValue().set(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY, onlineDeviceCount);
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 重置在线设备数量失败", e);
        }
    }

    /**
     * 将DeviceOnlineStatus转换为Redis存储格式
     */
    private Map<String, Object> convertToRedisMap(DeviceOnlineStatus status) {
        Map<String, Object> map = new HashMap<>();

        /* --------------------- 必更新字段 ---------------------*/
        map.put(LAST_REPORT_TIME, status.getLastReportTime());
        map.put(LAST_REPORT_SOURCE, status.getLastReportSource() != null ? status.getLastReportSource().name() : null);
        map.put(CLIENT_IP, status.getClientIp());

        /* --------------------- 动态更新字段 ---------------------*/
        if (status.getDeviceId() != null) {
            map.put(DEVICE_ID, status.getDeviceId());
        }
        if (status.getStatus() != null) {
            map.put(STATUS, status.getStatus().name());
        }
        if (status.getVersion() != null) {
            map.put(VERSION, status.getVersion());
        }
        if (status.getStatusChangeTime() != null) {
            map.put(STATUS_CHANGE_TIME, status.getStatusChangeTime());
        }
        if (status.getOnlineStartTime() != null) {
            map.put(ONLINE_START_TIME, status.getOnlineStartTime());
        }
        
        return map;
    }
    
    /**
     * 从Redis存储格式转换为DeviceOnlineStatus
     */
    private DeviceOnlineStatus convertFromRedisMap(Long deviceId, Map<Object, Object> map) {
        DeviceOnlineStatus status = new DeviceOnlineStatus();
        
        status.setDeviceId(deviceId);
        status.setLastReportTime(getLongValue(map.get(LAST_REPORT_TIME)));
        
        String sourceStr = (String) map.get(LAST_REPORT_SOURCE);
        status.setLastReportSource(sourceStr != null ? ReportSource.valueOf(sourceStr) : null);
        
        String statusStr = (String) map.get(STATUS);
        status.setStatus(statusStr != null ? OnlineStatus.valueOf(statusStr) : OnlineStatus.OFFLINE);
        
        status.setStatusChangeTime(getLongValue(map.get(STATUS_CHANGE_TIME)));
        status.setOnlineStartTime(getLongValue(map.get(ONLINE_START_TIME)));
        status.setClientIp((String) map.get(CLIENT_IP));
        
        return status;
    }
    
    /**
     * 安全地获取Long值
     */
    private Long getLongValue(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Long longValue) {
            return longValue;
        }
        
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 并发控制实现 ====================

    @Override
    public Boolean tryAcquireDeviceUpdateLock(Long deviceId, Long timeoutMs) {
        String lockKey = String.format(RedisKeyConstant.DEVICE_STATUS_UPDATE_LOCK_KEY, deviceId);
        try {
            // 使用SET NX PX命令实现分布式锁
            Duration timeout = Duration.ofMillis(timeoutMs);
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", timeout);
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("DeviceOnlineStatus - 获取分布式锁成功: deviceId={}, timeout={}ms", deviceId, timeoutMs);
            } else {
                log.debug("DeviceOnlineStatus - 获取分布式锁失败，锁已存在: deviceId={}", deviceId);
            }
            
            return acquired;
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 获取分布式锁异常: deviceId={}", deviceId, e);
            return false;
        }
    }

    @Override
    public void releaseDeviceUpdateLock(Long deviceId) {
        String lockKey = String.format(RedisKeyConstant.DEVICE_STATUS_UPDATE_LOCK_KEY, deviceId);
        try {
            boolean deleted = redisTemplate.delete(lockKey);
            if (deleted) {
                log.debug("DeviceOnlineStatus - 释放分布式锁成功: deviceId={}", deviceId);
            } else {
                log.debug("DeviceOnlineStatus - 分布式锁不存在或已过期: deviceId={}", deviceId);
            }
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 释放分布式锁异常: deviceId={}", deviceId, e);
        }
    }


}