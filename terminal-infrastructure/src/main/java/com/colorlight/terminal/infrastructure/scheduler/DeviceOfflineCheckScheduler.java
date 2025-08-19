package com.colorlight.terminal.infrastructure.scheduler;

import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    public void logDeviceStatistics() {
        try {
            int onlineCount = deviceOnlineStatusUseCase.getOnlineDeviceCount();
            
            log.info("DeviceStatusScheduler -offline- 设备在线统计: 当前在线设备数={}", onlineCount);
            
            // TODO: 可以扩展更多统计信息
            // - 各来源（HTTP/WebSocket）的设备数量
            // - 平均在线时长
            // - 设备活跃度分布等
            
        } catch (Exception e) {
            log.error("DeviceStatusScheduler -offline- 设备统计信息输出失败", e);
        }
    }
}