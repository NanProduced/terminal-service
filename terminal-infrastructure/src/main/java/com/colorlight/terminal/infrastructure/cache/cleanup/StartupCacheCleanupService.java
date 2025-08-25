package com.colorlight.terminal.infrastructure.cache.cleanup;

import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineTimePort;
import com.colorlight.terminal.infrastructure.cache.redis.service.DeviceOnlineStatusRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动缓存清理服务
 * 
 * 在应用启动时主动清理可能的"僵尸"设备缓存，解决服务器宕机重启后的缓存污染问题
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Order(1000) // 确保在其他服务启动后运行
@ConditionalOnProperty(name = "terminal.device.startup-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class StartupCacheCleanupService implements ApplicationRunner {

    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final DeviceOnlineTimePort deviceOnlineTimePort;
    private final DeviceConfigPort deviceConfigPort;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("StartupCleanup - 开始执行启动缓存清理任务");
        
        StopWatch stopWatch = new StopWatch();
        int onlineDeviceCount = deviceOnlineStatusPort.getOnlineDeviceCount();
        log.info("StartupCleanup - 当前遗留的在线设备数量 - {}", onlineDeviceCount);

        deviceOnlineStatusPort.setOnlineDeviceCount(0);

        log.info("StartupCleanup - 在线设备数量已清零");

        stopWatch.start();
        
        try {
            CleanupResult result = executeCleanupStrategy();
            
            stopWatch.stop();
            
            log.info("StartupCleanup - 缓存清理任务完成: " +
                    "检查设备={}, 清理设备={}, 保留设备={}, 耗时={}ms",
                    result.totalChecked(), result.cleanedCount(), 
                    result.retainedCount(), stopWatch.getTotalTimeMillis());
            
            // 清理耗时过长记录警告
            if (stopWatch.getTotalTimeMillis() > 10000) { // 超过10秒
                log.warn("StartupCleanup - 缓存清理耗时较长: {}ms, 建议检查设备数量或Redis性能", 
                        stopWatch.getTotalTimeMillis());
            }

            int currentOnlineDeviceCount = deviceOnlineStatusPort.getOnlineDeviceCount();
            log.info("StartupCleanup - 当前在线设备数量 - {}", currentOnlineDeviceCount);
            deviceOnlineStatusPort.setOnlineDeviceCount(currentOnlineDeviceCount + result.retainedCount);
            log.info("StartupCleanup - 设置当前在线设备数量为 当前数量 + 保留设备数量 - {}", result.retainedCount + currentOnlineDeviceCount);


        } catch (Exception e) {
            log.error("StartupCleanup - 启动缓存清理任务失败", e);
            
            // 根据配置决定是否阻断启动
            if (deviceConfigPort.isStartupCleanupRequired()) {
                throw new RuntimeException("启动缓存清理失败，应用无法正常启动", e);
            }
        }
    }
    
    /**
     * 执行清理策略
     */
    private CleanupResult executeCleanupStrategy() {
        String strategy = deviceConfigPort.getStartupCleanupStrategy();
        
        log.info("StartupCleanup - 执行清理策略: {}", strategy);
        
        return switch (strategy.toLowerCase()) {
            case "conservative" -> executeConservativeCleanup();
            case "aggressive" -> executeAggressiveCleanup();
            case "smart" -> executeSmartCleanup();
            default -> {
                log.warn("StartupCleanup - 未知清理策略: {}, 使用smart策略", strategy);
                yield executeSmartCleanup();
            }
        };
    }
    
    /**
     * 保守清理策略
     * 只清理明显过期的设备（超过TTL + 缓冲时间）
     */
    private CleanupResult executeConservativeCleanup() {
        log.info("StartupCleanup - 执行保守清理策略");
        
        // 使用TTL + 额外缓冲时间作为清理阈值
        long bufferTime = deviceConfigPort.getStartupCleanupBufferSeconds();
        long cleanupThreshold = System.currentTimeMillis() - 
                (deviceConfigPort.getDeviceStatusTtlSeconds() + bufferTime) * 1000L;
        
        log.debug("StartupCleanup - 保守清理阈值: TTL={}s, 缓冲={}s", 
                deviceConfigPort.getDeviceStatusTtlSeconds(), bufferTime);
        
        return executeThresholdBasedCleanup(cleanupThreshold, "保守");
    }
    
    /**
     * 激进清理策略
     * 清理所有可能离线的设备（使用离线检测阈值）
     */
    private CleanupResult executeAggressiveCleanup() {
        log.info("StartupCleanup - 执行激进清理策略");
        
        // 使用离线检测阈值
        long cleanupThreshold = System.currentTimeMillis() - 
                deviceConfigPort.getOfflineCheckThresholdSeconds() * 1000L;
        
        log.debug("StartupCleanup - 激进清理阈值: {}s", 
                deviceConfigPort.getOfflineCheckThresholdSeconds());
        
        return executeThresholdBasedCleanup(cleanupThreshold, "激进");
    }
    
    /**
     * 智能清理策略
     * 根据系统配置智能选择清理阈值
     */
    private CleanupResult executeSmartCleanup() {
        log.info("StartupCleanup - 执行智能清理策略");
        
        // 计算智能阈值：离线阈值 + 定时任务间隔 + 少量缓冲
        long offlineThreshold = deviceConfigPort.getOfflineCheckThresholdSeconds();
        long taskInterval = deviceConfigPort.getOfflineCheckIntervalSeconds();
        long smartBuffer = Math.min(60, taskInterval / 2); // 最多60秒缓冲
        
        long cleanupThreshold = System.currentTimeMillis() - 
                (offlineThreshold + taskInterval + smartBuffer) * 1000L;
        
        log.debug("StartupCleanup - 智能清理阈值: 离线={}s, 间隔={}s, 缓冲={}s", 
                offlineThreshold, taskInterval, smartBuffer);
        
        return executeThresholdBasedCleanup(cleanupThreshold, "智能");
    }
    
    /**
     * 基于阈值的清理执行
     */
    private CleanupResult executeThresholdBasedCleanup(long cleanupThreshold, String strategyName) {
        AtomicInteger totalChecked = new AtomicInteger(0);
        AtomicInteger cleanedCount = new AtomicInteger(0);
        AtomicInteger retainedCount = new AtomicInteger(0);
        
        try {
            // 使用流式处理避免内存问题
            if (deviceOnlineStatusPort instanceof DeviceOnlineStatusRedisService redisService) {
                
                log.debug("StartupCleanup - 开始{}清理，使用流式处理", strategyName);
                
                redisService.streamAllDeviceIds(deviceId -> {
                    totalChecked.incrementAndGet();
                    
                    try {
                        if (shouldCleanupDevice(deviceId, cleanupThreshold)) {
                            cleanupDeviceCache(deviceId);
                            cleanedCount.incrementAndGet();
                        } else {
                            retainedCount.incrementAndGet();
                        }
                        
                        // 每处理1000个设备输出一次进度
                        if (totalChecked.get() % 1000 == 0) {
                            log.debug("StartupCleanup - {}清理进度: 已检查={}, 已清理={}", 
                                    strategyName, totalChecked.get(), cleanedCount.get());
                        }
                        
                    } catch (Exception e) {
                        log.warn("StartupCleanup - 处理设备清理失败: deviceId={}", deviceId, e);
                    }
                });
                
            } else {
                log.warn("StartupCleanup - 设备状态服务不支持流式处理，跳过启动清理");
                return new CleanupResult(0, 0, 0);
            }
            
        } catch (Exception e) {
            log.error("StartupCleanup - {}清理执行失败", strategyName, e);
            throw e;
        }
        
        return new CleanupResult(totalChecked.get(), cleanedCount.get(), retainedCount.get());
    }
    
    /**
     * 判断设备是否应该被清理
     */
    private boolean shouldCleanupDevice(Long deviceId, long cleanupThreshold) {
        try {
            // 获取设备最后上报时间
            Long lastReportTime = deviceOnlineStatusPort.getDeviceLastReportTime(deviceId);
            
            if (lastReportTime == null) {
                // 没有上报时间记录，清理设备状态
                log.debug("StartupCleanup - 设备无上报时间记录，标记清理: deviceId={}", deviceId);
                return true;
            }
            
            // 检查是否超过清理阈值
            boolean shouldCleanup = lastReportTime < cleanupThreshold;
            
            if (shouldCleanup) {
                log.debug("StartupCleanup - 设备超过清理阈值，标记清理: deviceId={}, lastReport={}, threshold={}", 
                        deviceId, lastReportTime, cleanupThreshold);
            }
            
            return shouldCleanup;
            
        } catch (Exception e) {
            log.warn("StartupCleanup - 检查设备清理条件失败: deviceId={}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 清理设备缓存
     */
    private void cleanupDeviceCache(Long deviceId) {
        try {
            // 1. 启动清理专用方法（不影响计数器）
            deviceOnlineStatusPort.removeDeviceStatusForStartupCleanup(deviceId);
            
            // 2. 移除在线时间记录
            deviceOnlineTimePort.removeOnlineStartTime(deviceId);
            
            log.debug("StartupCleanup - 设备缓存清理完成: deviceId={}", deviceId);
            
        } catch (Exception e) {
            log.error("StartupCleanup - 清理设备缓存失败: deviceId={}", deviceId, e);
            throw e;
        }
    }
    
    /**
     * 清理结果记录
     */
    public record CleanupResult(
            int totalChecked,    // 总检查数量
            int cleanedCount,    // 清理数量
            int retainedCount    // 保留数量
    ) {}
}