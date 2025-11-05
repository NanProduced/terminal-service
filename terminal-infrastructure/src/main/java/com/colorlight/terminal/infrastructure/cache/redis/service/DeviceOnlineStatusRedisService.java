package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.commons.exception.technical.RedisTransactionFailedException;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    // ==================== Lua脚本定义 ====================

    /**
     * 原子递减在线设备计数器的Lua脚本
     * 功能：获取当前值，递减后保证>=0，然后返回新值
     * <p>
     * 脚本参数：
     *   KEYS[1]: 计数器key
     *   ARGV[1]: 递减数量
     * </p>
     * 返回值：递减后的计数器值（保证>=0）
     */
    private static final DefaultRedisScript<Long> DECREMENT_ONLINE_COUNT_SCRIPT;

    static {
        DECREMENT_ONLINE_COUNT_SCRIPT = new DefaultRedisScript<>();
        DECREMENT_ONLINE_COUNT_SCRIPT.setScriptText(
            "local current = redis.call('GET', KEYS[1]) or '0' " +
            "local newVal = math.max(0, tonumber(current) - tonumber(ARGV[1])) " +
            "redis.call('SET', KEYS[1], newVal) " +
            "return newVal"
        );
        DECREMENT_ONLINE_COUNT_SCRIPT.setResultType(Long.class);
    }

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

    /**
     * 获取设备状态Redis Key
     */
    private String getStatusKey(Long deviceId) {
        return String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);
    }

    @Override
    public void smartDetermined(DeviceOnlineStatus status) {
        // 检查 status.getStatus() 是否为 null
        if (status.getStatus() == null) {
            updateDeviceStatus(status);
        }
        else {
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
     * 批量智能判定设备状态
     *
     * <p>使用Redis Pipeline优化，将多个状态操作打包成一个网络往返</p>
     *
     * <p>执行逻辑：只处理异步缓冲接收的状态（ONLINE/null），跳过其他状态
     * <ul>
     *   <li>ONLINE/null状态：执行更新操作（心跳更新）</li>
     *   <li>GO_LIVE/RECONNECT/OFFLINE状态：跳过处理（不进入异步缓冲）</li>
     * </ul>
     *
     * <p>注：GO_LIVE和RECONNECT状态在应用层被强制同步处理，不会进入异步批处理。
     * 参见 DeviceOnlineStatusApplicationService#updateDeviceStatusWithMode
     *
     * @param statusList 设备状态列表
     */
    @Override
    public void batchSmartDetermined(List<DeviceOnlineStatus> statusList) {
        if (statusList == null || statusList.isEmpty()) {
            return;
        }

        try {
            // 使用单一Pipeline执行所有操作，最大化网络往返优化
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    for (DeviceOnlineStatus status : statusList) {
                        if (status == null) {
                            continue;
                        }

                        // 只处理ONLINE和null状态（异步缓冲接收的状态）
                        OnlineStatus currentStatus = status.getStatus();
                        if (currentStatus == null || currentStatus == OnlineStatus.ONLINE) {
                            performUpdateInPipeline(operations, getStatusKey(status.getDeviceId()), status);
                        }
                        // GO_LIVE/RECONNECT不进入异步缓冲，OFFLINE不处理
                    }

                    return null;
                }
            });

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 批量智能判定设备状态失败: statusList.size={}", statusList.size(), e);
        }
    }

    /**
     * 在Pipeline中执行更新操作（对应updateDeviceStatus的逻辑）
     *
     * <p>操作序列：
     * <ol>
     *   <li>HSET: 更新设备状态字段</li>
     *   <li>EXPIRE: 重新设置TTL</li>
     * </ol>
     *
     * @param operations Redis操作对象
     * @param statusKey 设备状态key
     * @param status 设备在线状态
     */
    @SuppressWarnings("unchecked")
    private void performUpdateInPipeline(RedisOperations operations, String statusKey, DeviceOnlineStatus status) {
        // 更新状态字段
        Map<String, Object> updateFields = convertToRedisMap(status);
        operations.opsForHash().putAll(statusKey, updateFields);

        // 重新设置TTL
        operations.expire(statusKey, getStatusTtl());
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
        String statusKey = getStatusKey(status.getDeviceId());

        try {
            // 在应用层分布式锁保护下，直接执行保存操作
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

            // Redis EXEC 失败时返回空列表，成功时返回非空列表
            if (results.isEmpty()) {
                log.error("DeviceOnlineStatus - 设备上线事务失败: deviceId={}, statusKey={}",
                    status.getDeviceId(), statusKey);
                throw new RedisTransactionFailedException(
                    String.format("设备上线事务失败: deviceId=%d", status.getDeviceId()));
            }

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
    public void updateDeviceStatus(DeviceOnlineStatus status) {
        String statusKey = getStatusKey(status.getDeviceId());

        try {
            Map<String, Object> updateFields = convertToRedisMap(status);

            // 执行管道批量更新
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    // 更新状态字段
                    operations.opsForHash().putAll(statusKey, updateFields);

                    // 重新设置TTL
                    operations.expire(statusKey, getStatusTtl());

                    return null;
                }
            });
            // Pipeline 已执行，无需检查结果（高频操作，失败不阻塞）

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
    private void streamAllDeviceIds(LongConsumer consumer) {
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
    private List<Long> findExpiredDevicesWithStream(long expireThreshold) {
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
    public void removeDeviceStatusForStartupCleanup(Long deviceId) {
        String statusKey = getStatusKey(deviceId);

        try {
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    // 删除状态详情（不影响计数器）
                    operations.delete(statusKey);

                    // 从索引中移除
                    operations.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, deviceId);

                    // 启动清理时不修改计数器，因为已经在启动时重置

                    return null;
                }
            });

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 启动清理设备状态失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 离线处理结果 - 包含统计信息和成功的设备状态
     */
    @Getter
    private static class OfflineProcessResult {
        int actualRemovedCount = 0;  // 实际从索引中移除的设备数
        int notFoundCount = 0;       // 设备不在索引中的数量
        int failedCount = 0;         // 处理失败的设备数
        List<DeviceOnlineStatus> successfulStatuses = new ArrayList<>();  // 成功标记离线的设备状态
    }

    /**
     * 批量标记设备离线并重置TTL
     *
     * <p>Spring Data Redis Pipeline 返回值行为说明：
     * <ul>
     *   <li>Pipeline 执行4个操作/设备：2x HSET(STATUS + STATUS_CHANGE_TIME) + EXPIRE + SREM</li>
     *   <li>HSET/EXPIRE 返回 null，SREM 返回 Long(移除成员数)</li>
     *   <li>保守校验策略：验证 SREM 操作，用于统计和监控</li>
     * </ul>
     *
     * @param deviceIds 设备ID列表
     * @return 成功标记离线的设备状态列表
     */
    @Override
    public List<DeviceOnlineStatus> batchMarkOfflineAndResetTtl(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            long startTime = System.currentTimeMillis();

            // 批量获取当前状态并过滤
            List<DeviceOnlineStatus> offlineDevices = filterValidDevicesForOffline(deviceIds);

            if (offlineDevices.isEmpty()) {
                return new ArrayList<>();
            }

            // 使用Pipeline批量执行离线操作
            long currentTime = System.currentTimeMillis();
            Duration reconnectTtl = getReconnectTtl();

            List<Object> pipelineResults = executeBatchOfflineOperations(offlineDevices, currentTime, reconnectTtl);

            // 处理Pipeline结果并统计
            OfflineProcessResult processResult = processBatchOfflineResults(offlineDevices, pipelineResults);

            // 根据实际移除的设备数递减计数器
            if (processResult.actualRemovedCount > 0) {
                decrementOnlineCountSafely(processResult.actualRemovedCount);
            }

            // 记录日志
            logBatchOfflineCompletion(deviceIds.size(), offlineDevices.size(), processResult,
                                     System.currentTimeMillis() - startTime, reconnectTtl);

            return processResult.successfulStatuses;

        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 批量标记设备离线失败: deviceCount={}", deviceIds.size(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 过滤出需要离线处理的设备
     */
    private List<DeviceOnlineStatus> filterValidDevicesForOffline(List<Long> deviceIds) {
        Map<Long, DeviceOnlineStatus> currentStatuses = batchGetDeviceStatus(deviceIds);

        List<DeviceOnlineStatus> offlineDevices = new ArrayList<>();
        for (Long deviceId : deviceIds) {
            DeviceOnlineStatus status = currentStatuses.get(deviceId);
            if (isValidForOfflineProcessing(status)) {
                offlineDevices.add(status);
            }
        }

        return offlineDevices;
    }

    /**
     * 执行批量离线操作（Pipeline）
     * @param offlineDevices 需要离线的设备状态列表
     * @param currentTime 当前时间戳
     * @param reconnectTtl 重连窗口时间
     */
    private List<Object> executeBatchOfflineOperations(List<DeviceOnlineStatus> offlineDevices,
                                                        long currentTime, Duration reconnectTtl) {
        return redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                for (DeviceOnlineStatus status : offlineDevices) {
                    String statusKey = getStatusKey(status.getDeviceId());

                    // 操作1: 更新状态为离线
                    operations.opsForHash().put(statusKey, STATUS, OnlineStatus.OFFLINE.name());
                    // 操作2: 更新状态变更时间
                    operations.opsForHash().put(statusKey, STATUS_CHANGE_TIME, currentTime);
                    // 操作3: 重置TTL为重连窗口时间
                    operations.expire(statusKey, reconnectTtl);
                    // 操作4: 从在线设备索引中移除
                    operations.opsForSet().remove(DEVICE_STATUS_INDEX_KEY, status.getDeviceId());
                }

                return null;
            }
        });
    }

    /**
     * 处理Pipeline批量离线操作的结果
     */
    private OfflineProcessResult processBatchOfflineResults(List<DeviceOnlineStatus> offlineDevices,
                                                            List<Object> pipelineResults) {
        OfflineProcessResult result = new OfflineProcessResult();

        for (int i = 0; i < offlineDevices.size(); i++) {
            DeviceOnlineStatus status = offlineDevices.get(i);
            int baseIndex = i * 4;  // 每个设备4个操作

            // 只检查 SREM 的返回值（第4个操作，索引 baseIndex + 3）
            Object sremResult = pipelineResults.get(baseIndex + 3);

            if (sremResult != null) {
                // SREM 返回移除的成员数：1 表示成功移除，0 表示成员不存在
                if (sremResult.equals(1L)) {
                    result.actualRemovedCount++;
                } else if (sremResult.equals(0L)) {
                    result.notFoundCount++;
                }

                // SREM 返回 1 或 0 都视为成功（设备可能已不在索引中）
                status.markOffline();
                result.successfulStatuses.add(status);
            } else {
                result.failedCount++;
                log.warn("DeviceOnlineStatus - 设备索引移除异常: deviceId={}, sremResult=null", status.getDeviceId());
            }
        }

        return result;
    }

    /**
     * 记录批量离线操作完成日志
     */
    private void logBatchOfflineCompletion(int totalCount, int validCount, OfflineProcessResult result,
                                          long duration, Duration reconnectTtl) {
        double successRate = validCount == 0 ? 0.0 :
            (double) result.successfulStatuses.size() / validCount * 100;

        log.info("DeviceOnlineStatus - 批量标记设备离线完成: total={}, valid={}, successful={}, " +
                "actualRemoved={}, notFound={}, failed={}, successRate={}%, duration={}ms, reconnectTtl={}s",
                totalCount, validCount, result.successfulStatuses.size(),
                result.actualRemovedCount, result.notFoundCount, result.failedCount,
                successRate, duration, reconnectTtl.getSeconds());
    }

    /**
     * 检查设备状态是否适合离线处理
     *
     * <p>校验策略说明：
     * <ul>
     *   <li>只验证设备是否为在线相关状态（ONLINE/GO_LIVE/RECONNECT）</li>
     *   <li>不检查时间字段的完整性，脏数据在监听器处理时会被验证并跳过记录</li>
     *   <li>这样做确保过期设备能被正常标记为离线，避免永久孤立</li>
     * </ul>
     */
    private boolean isValidForOfflineProcessing(DeviceOnlineStatus status) {
        if (status == null) {
            return false;
        }

        OnlineStatus currentStatus = status.getStatus();
        return currentStatus == OnlineStatus.ONLINE ||
            currentStatus == OnlineStatus.GO_LIVE ||
            currentStatus == OnlineStatus.RECONNECT;
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
            // Spring Data Redis 3.3.x 推荐方式：使用 RedisScript + redisTemplate.execute()
            redisTemplate.execute(
                DECREMENT_ONLINE_COUNT_SCRIPT,
                List.of(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY),
                String.valueOf(amount)
            );
        } catch (Exception e) {
            log.error("DeviceOnlineStatus - 递减在线设备数量失败: amount={}", amount, e);
        }
    }

    /**
     * 将DeviceOnlineStatus转换为Redis存储格式
     * @param status 设备在线状态
     * @return Redis存储格式
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
     * @param deviceId 设备ID
     * @param map Redis存储格式
     * @return DeviceOnlineStatus
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