package com.colorlight.terminal.infrastructure.cache.cleanup;

import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.Set;

/**
 * 启动缓存清理服务
 * 
 * 应用启动时清理过时的设备缓存，基于TTL方案的清理策略
 *
 * <p><b>先暂时关闭，可能有并发问题</b></p>
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
    private final DeviceConfigPort deviceConfigPort;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("StartupCleanup - 开始执行启动缓存清理");
        
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        
        try {
            CleanupResult result = executeCleanup();
            
            stopWatch.stop();
            
            log.info("StartupCleanup - 清理完成: 检查={}, 清理={}, 保留={}, 耗时={}ms",
                    result.totalChecked, result.cleanedCount, result.retainedCount, stopWatch.getTotalTimeMillis());
            
            if (stopWatch.getTotalTimeMillis() > 10000) {
                log.warn("StartupCleanup - 清理耗时较长: {}ms", stopWatch.getTotalTimeMillis());
            }

            // 重置在线设备计数器
            deviceOnlineStatusPort.setOnlineDeviceCount(result.retainedCount);
            log.info("StartupCleanup - 重置计数器: {}", result.retainedCount);

        } catch (Exception e) {
            log.error("StartupCleanup - 清理失败", e);
            // 失败不阻断启动，记录错误继续启动
        }
    }
    
    /**
     * 执行清理逻辑
     */
    private CleanupResult executeCleanup() {
        // 计算清理阈值：当前时间 - (离线超时 + 5分钟安全缓冲)
        long cleanupThreshold = System.currentTimeMillis() - 
                (deviceConfigPort.getOfflineTimeoutThreshold() + 300_000);
        
        log.debug("StartupCleanup - 清理阈值: {}ms 之前的设备", cleanupThreshold);
        
        // 获取所有设备并逐个检查
        Set<Long> allDeviceIds = deviceOnlineStatusPort.getAllDeviceIds();
        int totalChecked = allDeviceIds.size();
        int cleanedCount = 0;
        int retainedCount = 0;
        
        log.info("StartupCleanup - 发现设备总数: {}", totalChecked);
        
        for (Long deviceId : allDeviceIds) {
            try {
                if (shouldCleanupDevice(deviceId, cleanupThreshold)) {
                    deviceOnlineStatusPort.removeDeviceStatusForStartupCleanup(deviceId);
                    cleanedCount++;
                } else {
                    retainedCount++;
                }
                
                // 每100个设备输出进度
                if ((cleanedCount + retainedCount) % 100 == 0) {
                    log.debug("StartupCleanup - 进度: 已处理={}/{}", cleanedCount + retainedCount, totalChecked);
                }
                
            } catch (Exception e) {
                log.warn("StartupCleanup - 处理设备失败: deviceId={}", deviceId, e);
                retainedCount++; // 失败时保守处理，算作保留
            }
        }
        
        return new CleanupResult(totalChecked, cleanedCount, retainedCount);
    }
    
    /**
     * 判断设备是否需要清理
     */
    private boolean shouldCleanupDevice(Long deviceId, long cleanupThreshold) {
        try {
            Long lastReportTime = deviceOnlineStatusPort.getDeviceLastReportTime(deviceId);
            
            if (lastReportTime == null) {
                log.debug("StartupCleanup - 设备无上报记录，清理: deviceId={}", deviceId);
                return true;
            }
            
            boolean shouldCleanup = lastReportTime < cleanupThreshold;
            
            if (shouldCleanup) {
                long ageMinutes = (System.currentTimeMillis() - lastReportTime) / 60000;
                log.debug("StartupCleanup - 设备过时清理: deviceId={}, 无活动{}分钟", deviceId, ageMinutes);
            }
            
            return shouldCleanup;
            
        } catch (Exception e) {
            log.warn("StartupCleanup - 检查设备失败: deviceId={}", deviceId, e);
            return false; // 异常时不清理，保守处理
        }
    }
    
    /**
     * 清理结果
     */
    public record CleanupResult(
            int totalChecked,    // 总检查数量
            int cleanedCount,    // 清理数量
            int retainedCount    // 保留数量
    ) {}
}