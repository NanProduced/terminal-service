package com.colorlight.terminal.boot.config.actuator;

import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import static com.colorlight.terminal.boot.config.actuator.ActuatorConstant.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备状态监控端点
 * 提供设备在线状态、离线检查等统计信息
 * <p>
 * 访问路径: /actuator/devices
 *
 * @author Nan
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "terminal.metrics.enabled",
        havingValue = "true"
)
@Endpoint(id = "devices")
@RequiredArgsConstructor
public class DeviceStatusEndpoint {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取设备状态统计信息
     * GET /actuator/devices
     *
     * @return 设备状态统计数据
     */
    @ReadOperation
    public Map<String, Object> deviceStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 基本信息
            stats.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stats.put(FieldNames.ENDPOINT, EndpointNames.DEVICE_STATUS_STATISTICS);

            // 在线设备统计
            Map<String, Object> onlineStats = getOnlineDeviceStats();
            stats.put(DeviceFields.ONLINE, onlineStats);

            // TTL统计
            Map<String, Object> ttlStats = getTtlStats();
            stats.put(DeviceFields.TTL, ttlStats);

            // 设备状态摘要
            Map<String, Object> summary = new HashMap<>();
            int onlineCount = (Integer) onlineStats.getOrDefault(DeviceFields.TOTAL_ONLINE_DEVICES, 0);
            int indexedCount = (Integer) onlineStats.getOrDefault(DeviceFields.INDEXED_DEVICES, 0);

            summary.put(DeviceFields.TOTAL_ONLINE_DEVICES, onlineCount);
            summary.put(DeviceFields.INDEXED_DEVICES, indexedCount);
            summary.put(DeviceFields.DATA_CONSISTENCY, onlineCount == indexedCount ? StatusValues.CONSISTENT : StatusValues.INCONSISTENT);
            summary.put(DeviceFields.TTL_INFO, ttlStats.getOrDefault(FieldNames.DESCRIPTION, TtlValues.TTL_CONFIGURED_DESCRIPTION));

            stats.put(FieldNames.SUMMARY, summary);

            // 健康状态评估
            Map<String, Object> health = new HashMap<>();
            evaluateDeviceHealth(health, onlineCount, indexedCount);
            stats.put(FieldNames.HEALTH, health);

            log.debug("DeviceStatusEndpoint - 返回设备统计: online={}, indexed={}",
                     onlineCount, indexedCount);

            return stats;

        } catch (Exception e) {
            log.error("DeviceStatusEndpoint - 获取设备统计失败", e);

            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put(FieldNames.ERROR, ErrorMessages.FAILED_TO_RETRIEVE_DEVICE_STATISTICS);
            errorStats.put(FieldNames.MESSAGE, e.getMessage());
            errorStats.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return errorStats;
        }
    }

    /**
     * 获取在线设备统计信息
     */
    private Map<String, Object> getOnlineDeviceStats() {
        Map<String, Object> onlineStats = new HashMap<>();

        try {
            // 从Redis获取在线设备计数
            Object onlineCountObj = redisTemplate.opsForValue().get(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);
            int onlineCount = onlineCountObj != null ? (Integer) onlineCountObj : 0;
            onlineStats.put(DeviceFields.TOTAL_ONLINE_DEVICES, onlineCount);

            // 获取设备状态索引集合大小（作为在线设备参考）
            Long indexSize = redisTemplate.opsForSet().size(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY);
            onlineStats.put(DeviceFields.INDEXED_DEVICES, indexSize != null ? indexSize.intValue() : 0);

            // 在线设备统计说明
            onlineStats.put(FieldNames.DESCRIPTION, TtlValues.REDIS_BASED_DESCRIPTION);

        } catch (Exception e) {
            log.warn("DeviceStatusEndpoint - 获取在线设备统计失败", e);
            onlineStats.put(FieldNames.ERROR, ErrorMessages.FAILED_TO_GET_ONLINE_DEVICE_STATS);
        }

        return onlineStats;
    }

    /**
     * 获取TTL统计信息
     */
    private Map<String, Object> getTtlStats() {
        Map<String, Object> ttlStats = new HashMap<>();

        try {
            // 基于现有的设备状态统计TTL相关信息
            // 由于具体的TTL Hash Key需要进一步确认，这里提供基础的TTL机制说明
            ttlStats.put(DeviceFields.ACTIVE_TTL_DEVICES, TtlValues.TTL_CALCULATION_NOTE);

            // TTL统计信息
            ttlStats.put(DeviceFields.TTL_MECHANISM, TtlValues.DUAL_TTL_MECHANISM);
            ttlStats.put(DeviceFields.INITIAL_TTL, TtlValues.INITIAL_TTL_1_HOUR);
            ttlStats.put(DeviceFields.RECONNECT_WINDOW, TtlValues.RECONNECT_WINDOW_2_MINUTES);
            ttlStats.put(FieldNames.DESCRIPTION, TtlValues.TTL_DESCRIPTION_TEXT);

            // 可以通过遍历所有设备状态来统计TTL信息（性能考虑，这里先提供机制说明）
            ttlStats.put(FieldNames.NOTE, TtlValues.TTL_STATISTICS_NOTE);

        } catch (Exception e) {
            log.warn("DeviceStatusEndpoint - 获取TTL统计失败", e);
            ttlStats.put(FieldNames.ERROR, ErrorMessages.FAILED_TO_GET_TTL_STATS);
        }

        return ttlStats;
    }

    /**
     * 评估设备健康状态
     */
    private void evaluateDeviceHealth(Map<String, Object> health, int onlineCount, int indexedCount) {
        health.put(FieldNames.STATUS, StatusValues.HEALTHY);

        // 检查在线设备数异常
        if (onlineCount > 20000) {
            health.put(FieldNames.STATUS, StatusValues.HIGH_LOAD);
            health.put(FieldNames.WARNING, "在线设备数超过20K，系统高负载");
        } else if (onlineCount > 15000) {
            health.put(FieldNames.STATUS, StatusValues.MEDIUM_LOAD);
            health.put(FieldNames.WARNING, "在线设备数超过15K，建议监控");
        }

        // 检查计数器与索引一致性
        if (indexedCount > 0) {
            double discrepancy = Math.abs((double) (onlineCount - indexedCount) / indexedCount);
            if (discrepancy > 0.1) {
                health.put(DeviceFields.DATA_CONSISTENCY, StatusValues.INCONSISTENT);
                health.put(DeviceFields.CONSISTENCY_WARNING,
                          String.format("在线设备计数(%d)与索引设备数(%d)差异较大: %.1f%%",
                                       onlineCount, indexedCount, discrepancy * 100));
            } else {
                health.put(DeviceFields.DATA_CONSISTENCY, StatusValues.CONSISTENT);
            }
        }

        // TTL机制状态
        health.put(DeviceFields.TTL_MECHANISM, StatusValues.CONFIGURED);
        health.put(DeviceFields.TTL_DESCRIPTION, TtlValues.TTL_CONFIGURED_DESCRIPTION);

        // 性能建议
        if (onlineCount > 10000) {
            health.put(DeviceFields.PERFORMANCE_RECOMMENDATION, "建议启用设备分片和连接池优化");
        }
    }
}