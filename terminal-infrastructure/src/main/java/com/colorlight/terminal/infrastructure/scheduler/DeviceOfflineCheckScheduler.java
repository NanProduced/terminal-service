package com.colorlight.terminal.infrastructure.scheduler;

import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import org.springframework.data.util.Pair;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.jetbrains.annotations.NotNull;

/**
 * 设备离线检测定时任务
 * 根据配置动态调整检查间隔和超时阈值
 * 
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "terminal.device.offline-check.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceOfflineCheckScheduler {
    
    private final DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;
    private final DeviceConfigPort deviceConfigPort;
    private final RedisTemplate<String, Object> redisTemplate;

    // 设备缓存维护锁超时时间（秒）
    private static final int MAINTENANCE_LOCK_TIMEOUT = 300; // 5分钟
    private static final int RETRY_ATTEMPTS = 3;             // 离线检测任务重试次数
    private static final int RETRY_DELAY_MS = 100;           // 重试间隔毫秒
    
    /**
     * 定时检查离线设备（带锁保护和重试机制）
     * 根据配置的间隔和延迟执行，使用设备缓存维护锁防止与校准任务冲突
     */
    @Scheduled(fixedDelayString = "#{@deviceConfigPort.getOfflineCheckInterval()}",
               initialDelayString = "#{@deviceConfigPort.getDeviceConfig().getOfflineCheck().getInitialDelay()}")
    public void checkOfflineDevices() {
        // 高优先级任务，带重试机制
        int retryCount = 0;
        boolean lockAcquired = false;

        while (retryCount < RETRY_ATTEMPTS) {
            lockAcquired = acquireMaintenanceLock();
            if (lockAcquired) {
                break; // 获取锁成功
            }

            retryCount++;
            log.debug("DeviceStatusScheduler -offline- 获取维护锁失败，重试 {}/{}", retryCount, RETRY_ATTEMPTS);

            if (retryCount < RETRY_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("DeviceStatusScheduler -offline- 重试等待被中断", e);
                    return;
                }
            }
        }

        if (!lockAcquired) {
            log.warn("DeviceStatusScheduler -offline- 获取维护锁失败，跳过本轮离线检测");
            return;
        }

        try {
            executeOfflineCheck();
        } finally {
            releaseMaintenanceLock();
        }
    }

    /**
     * 执行离线设备检查的核心逻辑
     */
    private void executeOfflineCheck() {
        try {
            long startTime = System.currentTimeMillis();

            log.debug("DeviceStatusSchedule -offline- 开始执行设备离线检查任务");

            // 检查并处理离线设备
            int offlineCount = deviceOnlineStatusUseCase.processOfflineDevices();

            long elapsed = System.currentTimeMillis() - startTime;

            if (offlineCount > 0) {
                log.info("DeviceStatusScheduler -offline- 设备离线检查完成: 处理离线设备={}, 耗时={}ms", offlineCount, elapsed);
            } else {
                log.debug("DeviceStatusScheduler -offline- 设备离线检查完成: 无离线设备, 耗时={}ms", elapsed);
            }

            // 记录性能指标（如果耗时过长需要优化）
            if (elapsed > 5000) { // 超过5秒
                log.warn("DeviceStatusScheduler -offline- 设备离线检查耗时过长: {}ms, 可能需要优化", elapsed);
            }

        } catch (Exception e) {
            log.error("DeviceStatusScheduler -offline- 设备离线检查任务执行失败", e);
        }
    }
    
    /**
     * 定时输出在线设备统计信息并执行校准
     * 根据配置的统计间隔输出统计信息，同时集成校准任务作为兜底机制
     */
    @Scheduled(fixedRateString = "#{@deviceConfigPort.getDeviceConfig().getOfflineCheck().getStatisticsInterval()}",
               initialDelay = 120_000)
    public void calibrationAndStatistic() {
        // 低优先级任务，获取锁失败直接跳过校准
        boolean lockAcquired = acquireMaintenanceLock();
        if (!lockAcquired) {
            log.debug("DeviceStatusScheduler -calibration- 获取维护锁失败，跳过校准，仅执行统计");
            // 锁获取失败时仅执行统计，不影响监控功能
            executeStatisticOnly();
            return;
        }

        try {
            // 执行统计
            int onlineCount = deviceOnlineStatusUseCase.getOnlineDeviceCount();
            log.info("DeviceStatusScheduler -calibration- 设备在线统计: 当前在线设备数={}", onlineCount);

            // 执行校准任务（兜底机制）
            performSimpleCalibration();

        } catch (Exception e) {
            log.error("DeviceStatusScheduler -calibration- 校准和统计任务执行失败", e);
        } finally {
            releaseMaintenanceLock();
        }
    }

    /**
     * 仅执行统计功能（锁获取失败时的降级方案）
     */
    private void executeStatisticOnly() {
        try {
            int onlineCount = deviceOnlineStatusUseCase.getOnlineDeviceCount();
            log.info("DeviceStatusScheduler -statistic- 设备在线统计: 当前在线设备数={}", onlineCount);
        } catch (Exception e) {
            log.error("DeviceStatusScheduler -statistic- 统计任务执行失败", e);
        }
    }
    
    /**
     * 增强的简单校准任务 - 解决孤儿数据问题
     * 一次扫描获取在线和离线设备分类，分别处理
     */
    private void performSimpleCalibration() {
        log.debug("DeviceStatusScheduler - 开始增强简单校准");

        try {
            long startTime = System.currentTimeMillis();

            // Step 1: 一次扫描获取在线和离线设备分类
            Pair<Set<Long>, Set<Long>> deviceClassification = scanAndClassifyDeviceStatus();
            Set<Long> onlineDevices = deviceClassification.getFirst();   // 在线设备
            Set<Long> offlineDevices = deviceClassification.getSecond(); // 离线设备

            // Step 2: 处理离线设备 - 设置OFFLINE状态和重置TTL（关键改进）
            int processedOfflineCount = 0;
            for (Long deviceId : offlineDevices) {
                if (markDeviceOfflineInCalibration(deviceId)) {
                    processedOfflineCount++;
                }
            }

            // Step 3: 校正索引（基于在线设备）
            calibrateIndexSimple(onlineDevices);

            // Step 4: 校正计数器（基于在线设备数量）
            calibrateCounterSimple(onlineDevices.size());

            long elapsed = System.currentTimeMillis() - startTime;

            log.info("DeviceStatusScheduler - 增强校准完成: 在线设备={}, 离线处理={}, 耗时={}ms",
                    onlineDevices.size(), processedOfflineCount, elapsed);

        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 增强校准失败，但不影响系统运行", e);
            // 校准失败不抛出异常，不影响统计任务继续执行
        }
    }

    /**
     * 校准中标记设备离线并重置TTL（避免孤儿数据）
     * @param deviceId 设备ID
     * @return 是否处理成功
     */
    @SuppressWarnings("unchecked")
    private boolean markDeviceOfflineInCalibration(Long deviceId) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, deviceId);

        try {
            // 获取当前状态进行验证
            Map<Object, Object> statusMap = redisTemplate.opsForHash().entries(statusKey);
            if (statusMap.isEmpty()) {
                log.debug("DeviceStatusScheduler - 设备状态不存在，跳过离线处理: deviceId={}", deviceId);
                return false;
            }

            String currentStatus = (String) statusMap.get("status");
            if ("OFFLINE".equals(currentStatus)) {
                log.debug("DeviceStatusScheduler - 设备已为离线状态，跳过处理: deviceId={}", deviceId);
                return false;
            }

            // 原子化操作：设置离线状态 + 重置TTL
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 设置离线状态和时间
                    operations.opsForHash().put(statusKey, "status", "OFFLINE");
                    operations.opsForHash().put(statusKey, "statusChangeTime", System.currentTimeMillis());

                    // 重置TTL为重连窗口时间（关键：避免孤儿数据长期存在）
                    operations.expire(statusKey, Duration.ofSeconds(deviceConfigPort.getReconnectTtl()));

                    return operations.exec();
                }
            });

            log.debug("DeviceStatusScheduler - 校准中设备离线处理成功: deviceId={}, reconnectTtl={}s",
                    deviceId, deviceConfigPort.getReconnectTtl());
            return true;

        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 校准中设备离线处理失败: deviceId={}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 扫描所有设备状态并分类为在线/离线设备（Pair优化版）
     * @return Pair.of(在线设备集合, 离线设备集合)
     */
    private Pair<Set<Long>, Set<Long>> scanAndClassifyDeviceStatus() {
        log.debug("DeviceStatusScheduler - 开始完整扫描设备状态并分类");

        Set<Long> onlineDevices = new HashSet<>();
        Set<Long> offlineDevices = new HashSet<>();

        long currentTime = System.currentTimeMillis();
        long offlineThreshold = deviceConfigPort.getOfflineTimeoutThreshold();

        try {
            // 使用SCAN扫描device:status:*模式
            String pattern = "device:status:*";

            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(200)
                    .build();

            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String statusKey = cursor.next();

                    // 提取deviceId
                    Long deviceId = extractDeviceIdFromStatusKey(statusKey);
                    if (deviceId == null) continue;

                    // 检查设备状态并分类
                    try {
                        Object lastReportTimeObj = redisTemplate.opsForHash().get(statusKey, "lastReportTime");
                        Object statusObj = redisTemplate.opsForHash().get(statusKey, "status");

                        if (lastReportTimeObj != null && statusObj != null) {
                            long lastReportTime = Long.parseLong(lastReportTimeObj.toString());
                            String status = statusObj.toString();

                            // 判断设备是否在线（时间阈值 + 状态检查）
                            boolean isOnline = (currentTime - lastReportTime <= offlineThreshold)
                                             && !"OFFLINE".equals(status);

                            if (isOnline) {
                                onlineDevices.add(deviceId);
                                log.debug("DeviceStatusScheduler - 设备在线: deviceId={}, lastReportTime={}",
                                        deviceId, lastReportTime);
                            } else {
                                offlineDevices.add(deviceId);
                                log.debug("DeviceStatusScheduler - 设备离线: deviceId={}, lastReportTime={}, status={}, 超时={}ms",
                                        deviceId, lastReportTime, status, currentTime - lastReportTime);
                            }
                        } else {
                            // 数据不完整，视为离线
                            offlineDevices.add(deviceId);
                            log.debug("DeviceStatusScheduler - 设备数据不完整，标记离线: deviceId={}", deviceId);
                        }

                    } catch (Exception e) {
                        log.debug("DeviceStatusScheduler - 检查设备状态失败: deviceId={}", deviceId, e);
                        // 单个设备检查失败，视为离线处理
                        offlineDevices.add(deviceId);
                    }
                }
            }

            log.info("DeviceStatusScheduler - 设备状态分类完成: 在线={}, 离线={}",
                    onlineDevices.size(), offlineDevices.size());

            return Pair.of(onlineDevices, offlineDevices);

        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 设备状态分类失败", e);
            // 返回空结果，避免误操作
            return Pair.of(Collections.emptySet(), Collections.emptySet());
        }
    }
    
    /**
     * 从状态key提取设备ID - 排除索引缓存
     */
    private Long extractDeviceIdFromStatusKey(String statusKey) {
        try {
            // device:status:123 -> 123
            if (statusKey.startsWith("device:status:")) {
                String deviceIdStr = statusKey.substring("device:status:".length());
                
                // 排除索引缓存key: device:status:index
                if ("index".equals(deviceIdStr)) {
                    log.debug("DeviceStatusScheduler - 跳过索引缓存key: {}", statusKey);
                    return null;
                }
                
                // 验证是否为有效的数字格式
                if (deviceIdStr.matches("\\d+")) {
                    return Long.valueOf(deviceIdStr);
                } else {
                    log.debug("DeviceStatusScheduler - 跳过非数字设备ID key: {}", statusKey);
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            log.warn("DeviceStatusScheduler - 无效的状态key格式: {}", statusKey);
        }
        return null;
    }
    
    /**
     * 直接重建，无需diff
     */
    private void calibrateIndexSimple(Set<Long> correctOnlineDevices) {
        try {
            
            // 先清空，再重建
            redisTemplate.delete(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY);
            
            if (!correctOnlineDevices.isEmpty()) {
                redisTemplate.opsForSet().add(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY, correctOnlineDevices.toArray());
            }
            
            log.debug("DeviceStatusScheduler - 索引重建完成: 设备数={}", correctOnlineDevices.size());
            
        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 索引校正失败", e);
        }
    }
    
    /**
     * 直接设置正确值
     */
    private void calibrateCounterSimple(int correctCount) {
        try {
            redisTemplate.opsForValue().set(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY, correctCount);
            
            log.debug("DeviceStatusScheduler - 计数器校正完成: 设置为{}", correctCount);
            
        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 计数器校正失败", e);
        }
    }

    // ==================== 设备缓存维护锁机制 ====================

    /**
     * 获取设备缓存维护锁（简化版）
     * @return 是否获取成功
     */
    private boolean acquireMaintenanceLock() {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY,
                    "locked",
                    Duration.ofSeconds(MAINTENANCE_LOCK_TIMEOUT));

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("DeviceStatusScheduler - 获取维护锁成功");
                return true;
            } else {
                log.debug("DeviceStatusScheduler - 获取维护锁失败，锁已存在");
                return false;
            }
        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 获取维护锁异常", e);
            return false;
        }
    }

    /**
     * 释放设备缓存维护锁（简化版）
     */
    private void releaseMaintenanceLock() {
        try {
            boolean deleted = redisTemplate.delete(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY);
            if (deleted) {
                log.debug("DeviceStatusScheduler - 释放维护锁成功");
            } else {
                log.debug("DeviceStatusScheduler - 维护锁不存在或已过期");
            }
        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 释放维护锁异常", e);
        }
    }
}