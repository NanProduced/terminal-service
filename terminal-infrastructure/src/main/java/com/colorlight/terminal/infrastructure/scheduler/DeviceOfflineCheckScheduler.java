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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
    
    /**
     * 定时检查离线设备
     * 根据配置的间隔和延迟执行
     */
    @Scheduled(fixedDelayString = "#{@deviceConfigPort.getOfflineCheckInterval()}", 
               initialDelayString = "#{@deviceConfigPort.getDeviceConfig().getOfflineCheck().getInitialDelay()}")
    public void checkOfflineDevices() {
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
     * 定时输出在线设备统计信息
     * 根据配置的统计间隔输出统计信息，用于监控
     */
    @Scheduled(fixedRateString = "#{@deviceConfigPort.getDeviceConfig().getOfflineCheck().getStatisticsInterval()}", 
               initialDelay = 120_000)
    public void calibrationAndStatistic() {
        try {
            int onlineCount = deviceOnlineStatusUseCase.getOnlineDeviceCount();
            
            log.info("DeviceStatusScheduler -offline- 设备在线统计: 当前在线设备数={}", onlineCount);
            
        } catch (Exception e) {
            log.error("DeviceStatusScheduler -offline- 设备统计信息输出失败", e);
        }
    }
    
    /**
     * 扫描真实在线设备并校正索引和计数器
     * <p>
     *     注：高并发环境下会引起数据不一致，暂时不启用
     * </p>
     */
    private void performSimpleCalibration() {
        log.debug("DeviceStatusScheduler - 开始简单缓存校准");
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Step 1: 扫描所有device:status:*键，获取真实在线设备
            Set<Long> actualOnlineDevices = scanAndFilterOnlineDevices();
            
            // Step 2: 校正索引
            calibrateIndexSimple(actualOnlineDevices);
            
            // Step 3: 校正计数器
            calibrateCounterSimple(actualOnlineDevices.size());
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            log.info("DeviceStatusScheduler - 校准完成: 实际在线设备={}, 耗时={}ms",
                    actualOnlineDevices.size(), elapsed);
            
        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 校准失败，但不影响系统运行", e);
            // 校验失败不抛出异常，不影响统计任务继续执行
        }
    }
    
    /**
     * 扫描并过滤在线设备（基于device:status:*键扫描）
     */
    private Set<Long> scanAndFilterOnlineDevices() {
        Set<Long> onlineDevices = new HashSet<>();
        long currentTime = System.currentTimeMillis();
        long offlineThreshold = deviceConfigPort.getOfflineTimeoutThreshold();
        
        try {
            // 使用SCAN扫描device:status:*模式
            String pattern = "device:status:*";
            
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(200)  // 适中的批次大小
                    .build();
            
            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String statusKey = cursor.next();
                    
                    // 提取deviceId
                    Long deviceId = extractDeviceIdFromStatusKey(statusKey);
                    if (deviceId == null) continue;
                    
                    // 检查是否在线
                    try {
                        Object lastReportTimeObj = redisTemplate.opsForHash().get(statusKey, "lastReportTime");
                        if (lastReportTimeObj != null) {
                            long lastReportTime = Long.parseLong(lastReportTimeObj.toString());
                            
                            if (currentTime - lastReportTime <= offlineThreshold) {
                                onlineDevices.add(deviceId);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("DeviceStatusScheduler - 检查设备状态失败: deviceId={}", deviceId, e);
                        // 单个设备检查失败不影响整体校验
                    }
                }
            }
            
            log.debug("DeviceStatusScheduler - 扫描完成: 发现在线设备={}", onlineDevices.size());
            return onlineDevices;
            
        } catch (Exception e) {
            log.error("DeviceStatusScheduler - 扫描设备状态失败", e);
            return Collections.emptySet();
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
}