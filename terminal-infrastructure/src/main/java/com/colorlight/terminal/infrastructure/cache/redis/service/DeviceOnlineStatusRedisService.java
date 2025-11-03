package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.commons.exception.technical.RedisTransactionFailedException;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
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
 * <h3>Spring Data Redis Transaction/Pipeline 返回值行为说明</h3>
 * <p>根据 Spring Data Redis 官方文档（版本 2.2.4 - 3.5.4），需要注意以下行为：
 * <ul>
 *   <li><b>Transaction (MULTI/EXEC)</b>:
 *     <ul>
 *       <li>void 方法（如 {@code putAll}）不在结果列表中产生条目</li>
 *       <li>{@code exec()} 成功返回非空列表，失败返回空列表或抛出异常</li>
 *       <li>部分操作返回有意义的值（如 SADD, INCR），部分返回 Boolean（如 expire）</li>
 *     </ul>
 *   </li>
 *   <li><b>Pipeline</b>:
 *     <ul>
 *       <li>put/expire 等操作返回 null，即使成功（这是正常行为）</li>
 *       <li>只有部分操作（如 SREM, INCR）返回有意义的值</li>
 *       <li>{@code executePipelined} 失败时抛出 {@code DataAccessException}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>校验策略</h3>
 * <ul>
 *   <li><b>Transaction</b>: 只验证 {@code exec()} 返回值是否为空，依赖异常机制</li>
 *   <li><b>Pipeline</b>: 验证返回值非 null，可选地检查 SREM 等有意义的操作用于监控</li>
 * </ul>
 *
 * @author Nan
 * @see <a href="https://docs.spring.io/spring-data/redis/reference/redis/transactions.html">Spring Data Redis Transactions</a>
 * @see <a href="https://docs.spring.io/spring-data/redis/reference/redis/pipelining.html">Spring Data Redis Pipelining</a>
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
     *
     * <p>Spring Data Redis Transaction 返回值行为说明：
     * <ul>
     *   <li>void 方法（如 putAll）不在结果列表中产生条目</li>
     *   <li>exec() 成功返回非空列表，失败返回空列表或抛出异常</li>
     *   <li>简化校验策略：只验证事务整体成功，依赖异常机制处理失败</li>
     * </ul>
     *
     * @param status 完整的设备状态
     */
    @Override
    @SuppressWarnings("unchecked")
    public void saveDeviceStatus(DeviceOnlineStatus status) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, status.getDeviceId());

        try {
            // 在应用层分布式锁保护下，直接执行保存操作
            // 使用Redis事务保证原子性
            List<Object> results = (List<Object>) redisTemplate.execute(new SessionCallback<Object>() {
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

            // 验证事务是否执行成功
            // Redis EXEC 失败时返回空列表，成功时返回非空列表
            if (results.isEmpty()) {
                log.error("DeviceOnlineStatus - 设备上线事务失败: deviceId={}, statusKey={}",
                    status.getDeviceId(), statusKey);
                throw new RedisTransactionFailedException(
                    String.format("设备上线事务失败: deviceId=%d", status.getDeviceId()));
            }

            // 事务成功，记录日志
            log.debug("DeviceOnlineStatus - 设备上线成功: deviceId={}, operations=4",
                status.getDeviceId());

        } catch (RedisTransactionFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 保存设备状态失败: deviceId={}", status.getDeviceId(), e);
            throw e;
        }
    }
    

    /**
     * 在线状态更新走这个方法，可异步
     *
     * <p>心跳场景采用最小校验策略，优先保证性能：
     * <ul>
     *   <li>只验证事务整体成功，不进行细粒度校验</li>
     *   <li>失败时记录日志但不抛异常，避免阻塞心跳处理</li>
     * </ul>
     *
     * @param status 部分设备状态字段
     */
    @Override
    @SuppressWarnings("unchecked")
    public void updateDeviceStatus(DeviceOnlineStatus status) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, status.getDeviceId());

        try {
            // 1. 构建更新字段映射
            Map<String, Object> updateFields = convertToRedisMap(status);

            // 2. 执行原子更新
            List<Object> results = (List<Object>) redisTemplate.execute(new SessionCallback<Object>() {
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

            // 验证事务是否执行成功
            if (results.isEmpty()) {
                log.error("DeviceOnlineStatus - 心跳更新事务失败: deviceId={}, statusKey={}",
                    status.getDeviceId(), statusKey);
                // 心跳更新失败不抛异常，避免阻塞心跳处理
            }

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 更新设备状态失败: deviceId={}", status.getDeviceId(), e);
            // 心跳更新异常不抛出，避免影响正常业务流程
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
            List<Object> results = (List<Object>) redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 删除状态详情
                    operations.delete(statusKey);

                    // 从索引中移除（返回移除的成员数）
                    operations.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);

                    return operations.exec();
                }
            });

            // 验证事务结果（exec失败返回空列表）
            if (results == null || results.isEmpty()) {
                log.warn("DeviceOnlineStatus - 删除设备状态事务失败: deviceId={}", deviceId);
                return;
            }

            // 验证结果集大小（应该有2个操作的结果: 1个DELETE + 1个SREM）
            if (results.size() >= 2) {
                // 第2个操作是 SREM，检查是否实际移除了设备
                Object sremResult = results.get(1);
                if (sremResult != null && sremResult.equals(1L)) {
                    // 仅当实际移除时才减少计数器
                    decrementOnlineCountSafely(1);
                } else {
                    log.debug("DeviceOnlineStatus - 设备不在索引中: deviceId={}, sremResult={}",
                        deviceId, sremResult);
                }
            }

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 删除设备状态失败: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void removeDeviceIndex(Long deviceId) {
        try {
            redisTemplate.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);
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
                    batchDeviceIds.clear();
                }
            });

            // 处理剩余的设备
            if (!batchDeviceIds.isEmpty()) {
                List<Long> batchExpired = processBatchDevices(batchDeviceIds, expireThreshold);
                expiredDevices.addAll(batchExpired);
            }

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
                
                if (status == null || status.getLastReportTime() == null || status.getLastReportTime() < expireThreshold) {
                    // 1.状态不存在，视为离线（索引存在但数据缺失）
                    // 2.lastReportTime为空，视为离线
                    // 3.超过阈值，视为离线
                    expiredDevices.add(deviceId);
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
                return null;
            }

            DeviceOnlineStatus currentStatus = currentStatusOpt.get();
            OnlineStatus status = currentStatus.getStatus();

            // 检查是否为在线相关状态
            if (status != OnlineStatus.ONLINE && status != OnlineStatus.GO_LIVE && status != OnlineStatus.RECONNECT) {
                return null;
            }

            // 验证时间信息完整性
            if (currentStatus.getOnlineStartTime() == null || currentStatus.getLastReportTime() == null) {
                log.warn("DeviceOnlineStatus -single- 设备时间信息不完整，跳过离线处理: deviceId={}, onlineStartTime={}, lastReportTime={}",
                        deviceId, currentStatus.getOnlineStartTime(), currentStatus.getLastReportTime());
                return null;
            }

            // 使用Redis事务原子化执行离线操作
            List<Object> results = (List<Object>) redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 更新状态为离线
                    operations.opsForHash().put(statusKey, STATUS, OnlineStatus.OFFLINE.name());
                    operations.opsForHash().put(statusKey, STATUS_CHANGE_TIME, System.currentTimeMillis());

                    // 重置TTL为重连窗口时间
                    operations.expire(statusKey, getReconnectTtl());

                    // 从在线设备索引中移除（返回移除的成员数）
                    operations.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);

                    return operations.exec();
                }
            });

            // 验证事务结果
            if (results.isEmpty()) {
                log.error("DeviceOnlineStatus - 离线标记事务失败(exec返回null): deviceId={}, statusKey={}",
                    deviceId, statusKey);
                // 事务失败时返回null，防止发布离线事件导致数据不一致
                return null;
            }

            // 验证结果集大小（应该有4个操作的结果: 2个HSET + 1个EXPIRE + 1个SREM）
            if (results.size() != 4) {
                log.error("DeviceOnlineStatus - 离线标记事务结果不完整: deviceId={}, expected=4, actual={}",
                    deviceId, results.size());
                // 结果不完整，返回null防止数据不一致
                return null;
            }

            // 验证索引移除结果（第4个操作，SREM应该返回1表示成功移除）
            Object sremResult = results.get(3);
            if (sremResult != null && sremResult.equals(1L)) {
                // 仅当实际移除时才减少计数器
                decrementOnlineCountSafely(1);
            } else {
                log.warn("DeviceOnlineStatus - 设备索引移除失败或设备不在索引中: deviceId={}, sremResult={}",
                    deviceId, sremResult);
            }

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
    
    /**
     * 批量标记设备离线并重置TTL
     *
     * <p>Spring Data Redis Pipeline 返回值行为说明：
     * <ul>
     *   <li>Pipeline 中 put/expire 等操作返回 null，但不代表失败</li>
     *   <li>只有部分操作（如 SREM）返回有意义的值</li>
     *   <li>保守校验策略：验证 SREM 操作，用于统计和监控</li>
     * </ul>
     *
     * @param deviceIds 设备ID列表
     * @return 成功标记离线的设备状态列表
     */
    @Override
    @SuppressWarnings("unchecked")
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
                return new ArrayList<>();
            }

            // 第二步：使用Pipeline批量执行离线操作
            long currentTime = System.currentTimeMillis();
            Duration reconnectTtl = getReconnectTtl();

            List<Object> pipelineResults = redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    for (Long deviceId : validDeviceIds) {
                        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);

                        // 更新状态为离线
                        operations.opsForHash().put(statusKey, STATUS, OnlineStatus.OFFLINE.name());
                        operations.opsForHash().put(statusKey, STATUS_CHANGE_TIME, currentTime);

                        // 重置TTL为重连窗口时间
                        operations.expire(statusKey, reconnectTtl);

                        // 从在线设备索引中移除（返回移除的成员数）
                        operations.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);
                    }

                    return null;
                }
            });

            // 统计 SREM 操作的实际移除情况
            int actualRemovedCount = 0;  // 实际移除的设备数（SREM 返回1）
            int notFoundCount = 0;       // 设备不在索引中的数量（SREM 返回0）
            int failedCount = 0;         // SREM 操作返回异常的数量

            List<DeviceOnlineStatus> successfulStatuses = new ArrayList<>();
            for (int i = 0; i < validDeviceIds.size(); i++) {
                Long deviceId = validDeviceIds.get(i);
                int baseIndex = i * 4;

                // 只检查 SREM 的返回值（第4个操作，索引 baseIndex + 3）
                // put 和 expire 在 Pipeline 中返回 null 是正常行为
                Object sremResult = pipelineResults.get(baseIndex + 3);

                if (sremResult != null) {
                    // SREM 返回移除的成员数：1 表示成功移除，0 表示成员不存在
                    if (sremResult.equals(1L)) {
                        actualRemovedCount++;  // 实际移除计数
                    } else if (sremResult.equals(0L)) {
                        notFoundCount++;
                    }

                    // SREM 返回 1 或 0 都视为成功（设备可能已不在索引中）
                    DeviceOnlineStatus status = validStatuses.get(i);
                    status.markOffline();
                    successfulStatuses.add(status);
                } else {
                    // SREM 返回 null 表示操作异常
                    failedCount++;
                    log.warn("DeviceOnlineStatus - 设备索引移除异常: deviceId={}, sremResult=null", deviceId);
                }
            }

            // 根据实际移除的设备数递减计数器
            if (actualRemovedCount > 0) {
                decrementOnlineCountSafely(actualRemovedCount);
            }

            long duration = System.currentTimeMillis() - startTime;
            double successRate = validDeviceIds.isEmpty() ? 0.0 :
                (double) successfulStatuses.size() / validDeviceIds.size() * 100;

            log.info("DeviceOnlineStatus - 批量标记设备离线完成: total={}, valid={}, successful={}, " +
                    "actualRemoved={}, notFound={}, failed={}, successRate={}%, duration={}ms, reconnectTtl={}s",
                    deviceIds.size(), validDeviceIds.size(), successfulStatuses.size(),
                    actualRemovedCount, notFoundCount, failedCount, successRate, duration, reconnectTtl.getSeconds());

            return successfulStatuses;

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
            return false;
        }

        OnlineStatus currentStatus = status.getStatus();
        if (currentStatus != OnlineStatus.ONLINE &&
            currentStatus != OnlineStatus.GO_LIVE &&
            currentStatus != OnlineStatus.RECONNECT) {
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
     * 安全地递减在线设备计数，确保不会变为负数
     *
     * @param amount 递减数量
     */
    private void decrementOnlineCountSafely(long amount) {
        if (amount <= 0) {
            return;
        }

        try {
            // 使用 Lua 脚本确保原子性和下限保护
            String luaScript =
                "local current = redis.call('GET', KEYS[1]) or '0' " +
                "local newVal = math.max(0, tonumber(current) - tonumber(ARGV[1])) " +
                "redis.call('SET', KEYS[1], newVal) " +
                "return newVal";

            redisTemplate.execute(
                (RedisCallback<Long>) connection -> connection.eval(
                    luaScript.getBytes(),
                    org.springframework.data.redis.connection.ReturnType.INTEGER,
                    1,
                    RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY.getBytes(),
                    String.valueOf(amount).getBytes()
                )
            );
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 递减在线设备数量失败: amount={}", amount, e);
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

            // 处理setIfAbsent可能返回null的情况
            if (acquired == null) {
                log.warn("DeviceOnlineStatus - Redis setIfAbsent返回null: deviceId={}, lockKey={}", deviceId, lockKey);
                return false;
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
            if (!deleted) {
                log.error("DeviceOnlineStatus - 分布式锁释放失败: deviceId={}, lockKey={}, 锁可能不存在或已过期",
                    deviceId, lockKey);
            } else {
                log.debug("DeviceOnlineStatus - 分布式锁释放成功: deviceId={}", deviceId);
            }
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 释放分布式锁异常: deviceId={}, lockKey={}, 锁将在TTL过期后自动释放",
                deviceId, lockKey, e);
        }
    }


}