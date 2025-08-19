package com.colorlight.terminal.infrastructure.scheduler;

import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineTimePort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import com.colorlight.terminal.infrastructure.cache.redis.service.DeviceOnlineStatusRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设备在线时间TTL刷新服务
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "terminal.device.expiration-listener.auto-fresh-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceOnlineTimeTtlRefreshService {
    
    private final DeviceOnlineTimePort deviceOnlineTimePort;
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final DeviceConfigPort deviceConfigPort;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 每日TTL刷新任务
     * 使用配置的TTL刷新间隔，默认23小时，为24小时TTL留出1小时缓冲时间
     */
    @Scheduled(fixedRateString = "#{@deviceConfigPort.getTtlRefreshIntervalHours() * 3600000}")
    public void refreshOnlineDeviceTtl() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        
        try {
            log.info("TtlRefresh -OnlineTimeKey- 开始每日TTL刷新任务");
            
            Duration ttl = Duration.ofHours(deviceConfigPort.getOnlineTimeTtlHours());
            int refreshedCount = executeRefreshWithOptimalStrategy(ttl);
            
            stopWatch.stop();
            log.info("TtlRefresh -OnlineTimeKey- 每日TTL刷新完成: 刷新设备={}, 耗时={}ms, TTL={}小时",
                    refreshedCount, stopWatch.getTotalTimeMillis(), deviceConfigPort.getOnlineTimeTtlHours());
            
            // 如果耗时过长记录警告
            if (stopWatch.getTotalTimeMillis() > 30000) { // 超过30秒
                log.warn("TtlRefresh -OnlineTimeKey- TTL刷新耗时较长: {}ms, 建议检查设备数量或Redis性能", stopWatch.getTotalTimeMillis());
            }
            
        } catch (Exception e) {
            log.error("TtlRefresh -OnlineTimeKey- 每日TTL刷新任务失败", e);
        }
    }
    
    /**
     * 使用最优策略执行TTL刷新
     * 智能选择传统查询或流式查询方式
     * 
     * @param ttl TTL时长
     * @return 刷新的设备数量
     */
    private int executeRefreshWithOptimalStrategy(Duration ttl) {
        // 获取当前在线设备数量用于策略选择
        int onlineDeviceCount = deviceOnlineStatusPort.getOnlineDeviceCount();
        int threshold = deviceConfigPort.getStreamQueryThreshold();
        
        log.debug("TtlRefresh -OnlineTimeKey- 设备数量统计: 在线设备={}, 流式查询阈值={}", onlineDeviceCount, threshold);
        
        // 智能策略选择
        if (shouldUseStreamQuery(onlineDeviceCount, threshold)) {
            return refreshWithStreamQuery(ttl, onlineDeviceCount);
        } else {
            return refreshWithTraditionalQuery(ttl, onlineDeviceCount);
        }
    }
    
    /**
     * 判断是否应该使用流式查询
     * 
     * @param onlineDeviceCount 在线设备数量
     * @param threshold 流式查询阈值
     * @return 是否使用流式查询
     */
    private boolean shouldUseStreamQuery(int onlineDeviceCount, int threshold) {
        boolean useStream = onlineDeviceCount > threshold && deviceConfigPort.isStreamQueryEnabled();
        
        log.debug("TtlRefresh -OnlineTimeKey- 查询策略选择: 在线设备={}, 阈值={}, 启用流式={}, 选择={}",
                onlineDeviceCount, threshold, deviceConfigPort.isStreamQueryEnabled(), 
                useStream ? "流式查询" : "传统查询");
        
        return useStream;
    }
    
    /**
     * 使用传统查询方式刷新TTL
     * 适用于设备数量较少的场景，性能更优
     */
    private int refreshWithTraditionalQuery(Duration ttl, int expectedCount) {
        log.debug("TtlRefresh -OnlineTimeKey- 使用传统查询刷新TTL: 预期设备数={}", expectedCount);
        
        Set<Long> allDeviceIds = deviceOnlineStatusPort.getAllDeviceIds();
        return refreshDeviceListTtl(allDeviceIds, ttl, "传统查询");
    }
    
    /**
     * 使用流式查询方式刷新TTL
     * 适用于设备数量很大的场景，避免内存问题
     */
    private int refreshWithStreamQuery(Duration ttl, int expectedCount) {
        log.debug("TtlRefresh -OnlineTimeKey- 使用流式查询刷新TTL: 预期设备数={}", expectedCount);
        
        AtomicInteger refreshedCount = new AtomicInteger(0);
        
        try {
            // 检查是否支持流式查询
            if (deviceOnlineStatusPort instanceof DeviceOnlineStatusRedisService redisService) {

                // 使用流式迭代处理所有设备
                redisService.streamAllDeviceIds(deviceId -> {
                    try {
                        if (refreshSingleDeviceTtl(deviceId, ttl)) {
                            refreshedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.warn("TtlRefresh -OnlineTimeKey- 流式刷新单个设备TTL失败: deviceId={}", deviceId, e);
                    }
                });
                
                log.debug("TtlRefresh -OnlineTimeKey- 流式查询完成: 刷新设备数={}", refreshedCount.get());
                
            } else {
                log.warn("TtlRefresh -OnlineTimeKey- 设备状态服务不支持流式查询，降级到传统查询");
                return refreshWithTraditionalQuery(ttl, expectedCount);
            }
            
        } catch (Exception e) {
            log.error("TtlRefresh -OnlineTimeKey- 流式查询失败，降级到传统查询", e);
            return refreshWithTraditionalQuery(ttl, expectedCount);
        }
        
        return refreshedCount.get();
    }
    
    /**
     * 刷新设备列表的TTL
     * 
     * @param deviceIds 设备ID集合
     * @param ttl TTL时长
     * @param queryType 查询类型（用于日志）
     * @return 刷新的设备数量
     */
    private int refreshDeviceListTtl(Set<Long> deviceIds, Duration ttl, String queryType) {
        if (deviceIds.isEmpty()) {
            log.debug("TtlRefresh -OnlineTimeKey- 无设备需要刷新TTL: queryType={}", queryType);
            return 0;
        }
        
        int refreshedCount = 0;
        
        for (Long deviceId : deviceIds) {
            try {
                if (refreshSingleDeviceTtl(deviceId, ttl)) {
                    refreshedCount++;
                }
            } catch (Exception e) {
                log.warn("TtlRefresh -OnlineTimeKey- 刷新单个设备TTL失败: deviceId={}, queryType={}", deviceId, queryType, e);
            }
        }
        
        log.debug("TtlRefresh -OnlineTimeKey- {} 刷新完成: 总设备数={}, 刷新数量={}", queryType, deviceIds.size(), refreshedCount);
        return refreshedCount;
    }
    
    /**
     * 刷新单个设备的在线时间TTL
     * 
     * @param deviceId 设备ID
     * @param ttl TTL时长
     * @return 是否成功刷新
     */
    private boolean refreshSingleDeviceTtl(Long deviceId, Duration ttl) {
        // 只有存在在线时间记录的设备才需要刷新TTL
        if (!deviceOnlineTimePort.hasOnlineTimeRecord(deviceId)) {
            return false;
        }
        
        try {
            String onlineTimeKey = String.format(RedisKeyConstant.DEVICE_ONLINE_TIME_KEY, deviceId);
            
            // 刷新TTL
            Boolean success = redisTemplate.expire(onlineTimeKey, ttl);
            
            if (Boolean.TRUE.equals(success)) {
                log.trace("TtlRefresh -OnlineTimeKey- 设备TTL刷新成功: deviceId={}", deviceId);
                return true;
            } else {
                log.debug("TtlRefresh -OnlineTimeKey- 设备TTL刷新失败（key不存在）: deviceId={}", deviceId);
                return false;
            }
            
        } catch (Exception e) {
            log.warn("TtlRefresh -OnlineTimeKey- 设备TTL刷新异常: deviceId={}", deviceId, e);
            return false;
        }
    }
}