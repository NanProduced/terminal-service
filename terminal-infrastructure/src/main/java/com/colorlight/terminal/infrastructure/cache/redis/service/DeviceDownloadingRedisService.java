package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.report.DownloadingReport;
import com.colorlight.terminal.application.domain.report.ProgramDownloadingReport;
import com.colorlight.terminal.application.domain.report.UpgradePackageDownloadingReport;
import com.colorlight.terminal.application.port.outbound.status.DeviceDownloadingPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备下载状态异步处理服务
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDownloadingRedisService implements DeviceDownloadingPort {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 下载状态缓存TTL - 10分钟
     */
    private static final Duration STATUS_TTL = Duration.ofMinutes(10);

    @Override
    @Async("statisticsReportExecutor")
    public void saveDownloadingStatus(Long deviceId, DownloadingReport report) {
        try {
            // 保存到Redis缓存
            cacheDeviceDownloadingStatus(deviceId, report);
            
            log.info("AsyncDeviceDownloadingService - 异步保存下载状态成功: deviceId={}, type={}", 
                    deviceId, report.getWhat());
            
        } catch (Exception e) {
            log.error("AsyncDeviceDownloadingService - 异步保存下载状态失败: deviceId={}, type={}", 
                    deviceId, report != null ? report.getWhat() : "null", e);
        }
    }

    /*============================== Redis缓存方法 ==============================*/

    /**
     * 将设备的下载状态缓存到Redis中。
     * 该方法使用Redis事务保证操作的原子性，确保数据的一致性和完整性。
     *
     * @param deviceId 设备ID
     * @param report 下载状态报告，包含了当前设备的下载进度信息
     */
    @SuppressWarnings("unchecked")
    private void cacheDeviceDownloadingStatus(Long deviceId, DownloadingReport report) {
        String statusKey = String.format(RedisKeyConstant.DEVICE_DOWNLOADING_STATUS_KEY, deviceId);

        try {
            // 使用Redis事务保证原子性
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(@NotNull RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 构建更新字段
                    Map<String, Object> updateFields = buildUpdateFields(report);
                    operations.opsForHash().putAll(statusKey, updateFields);

                    // 设置TTL
                    operations.expire(statusKey, STATUS_TTL);

                    return operations.exec();
                }
            });

            log.debug("DeviceDownloadingStatus - Redis保存下载状态成功: deviceId={}, type={}",
                    deviceId, report.getWhat());

        } catch (Exception e) {
            log.error("DeviceDownloadingStatus - Redis保存下载状态失败: deviceId={}", deviceId, e);
            throw e;
        }
    }

    /**
     * 构建Redis Hash更新字段
     * @param report 下载状态报告
     * @return 更新字段映射
     */
    private Map<String, Object> buildUpdateFields(DownloadingReport report) {
        Map<String, Object> fields = new HashMap<>();
        long currentTime = System.currentTimeMillis();

        if (report instanceof ProgramDownloadingReport) {
            // 节目下载状态
            fields.put("programStatus", JsonUtils.toJson(report));
            fields.put("programUpdateTime", currentTime);

            log.debug("DeviceDownloadingStatus - Redis更新节目下载状态: updateTime={}", currentTime);

        } else if (report instanceof UpgradePackageDownloadingReport) {
            // 升级包下载状态
            fields.put("upgradeStatus", JsonUtils.toJson(report));
            fields.put("upgradeUpdateTime", currentTime);

            log.debug("DeviceDownloadingStatus - Redis更新升级包下载状态: updateTime={}", currentTime);

        } else {
            log.warn("DeviceDownloadingStatus - 未知的下载状态类型: {}", report.getClass().getSimpleName());
        }

        return fields;
    }
}
