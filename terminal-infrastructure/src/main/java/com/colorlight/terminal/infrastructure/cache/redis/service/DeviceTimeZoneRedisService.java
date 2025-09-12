package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.dto.cache.DeviceTimeZoneCache;
import com.colorlight.terminal.application.port.outbound.command.SystemCommandPort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.application.port.outbound.status.DeviceTimeZonePort;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.commons.utils.TimeUtils;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 处理设备时区信息与计算偏移量的实现类(redis缓存)
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTimeZoneRedisService implements DeviceTimeZonePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private final TerminalStatusReportRepository terminalStatusReportRepository;
    private final SystemCommandPort systemCommandPort;
    private final TerminalStatsConfigProperties statsConfigProperties;

    @Override
    public DeviceTimeZoneCache getDeviceTimeZone(Long deviceId) {
        DeviceTimeZoneCache deviceTimeZoneCache = getDeviceTimeZoneCache(deviceId);
        if (Objects.isNull(deviceTimeZoneCache)) {
            deviceTimeZoneCache = RefreshDeviceTimeZoneCache(deviceId);
        }
        log.debug("DeviceTimeZone - 获取设备时区缓存: deviceId={}, cache={}", deviceId, deviceTimeZoneCache);
        return deviceTimeZoneCache;
    }

    @Override
    public DeviceTimeZoneCache RefreshDeviceTimeZoneCache(Long deviceId) {
        DeviceTimeZoneCache deviceTimeZoneCache = calculateAndSaveTimeOffset(deviceId);
        // newRtc上报部分不存在，内部下发获取配置指令
        if (Objects.isNull(deviceTimeZoneCache)) {
            try {
                systemCommandPort.requestTimeZoneReport(deviceId, DeviceTimeZoneRedisService.class.getSimpleName());
                log.info("DeviceTimeZone - 请求设备上报时间配置项: deviceId={}", deviceId);
            } catch (Exception e) {
                log.error("DeviceTimeZone -SystemCommand- 请求设备上报时间配置项失败: deviceId={}", deviceId, e);
            }
        }
        return deviceTimeZoneCache;
    }

    /**
     * 计算并保存设备时区及标准偏移缓存
     * @param deviceId 设备Id
     * @return 时区信息
     */
    private DeviceTimeZoneCache calculateAndSaveTimeOffset(Long deviceId) {
        // 获取led_status元数据
        Optional<TerminalStatusReport> reportData = terminalStatusReportRepository.getReportData(deviceId);
        // 判断是否存在newRtc部分上报
        if (reportData.isPresent() && Objects.nonNull(reportData.get().getNewrtc())) {
            TerminalStatusReport.NewRtc rtc = reportData.get().getNewrtc();
            // 构建设备时区缓存结构
            DeviceTimeZoneCache deviceTimeZoneCache = new DeviceTimeZoneCache(deviceId, rtc.getTimezoneId(), rtc.getTimezone());
            LocalDateTime serverTime = TimeUtils.convertTimestampToUtc(rtc.getReportTime() * 1000);
            LocalDateTime localTime = TimeUtils.convertStringToLocalDateTime(rtc.getTime(), null);
            // 计算设备本地时间（相比与本地时区）的偏差（对比服务器标准时间）
            Duration deviation = Duration.between(TimeUtils.transTimeToUTC(localTime, deviceTimeZoneCache.getDeviceZoneId()), serverTime);
            // 如果大于偏差阈值，设置偏差值
            long offsetThreshold = statsConfigProperties.getTimeCalibration().getOffsetThresholdSeconds();
            if (deviation.abs().toSeconds() > offsetThreshold) {
                deviceTimeZoneCache.setDeviation(deviation);
            }
            // 保存缓存
            cacheDeviceTimeZoneCache(deviceTimeZoneCache);
            return deviceTimeZoneCache;
        }

        log.error("DeviceTimeZone - 设备 {} 的时区信息为null", deviceId);
        return null;
    }

    /*=============================== Redis缓存辅助方法 ===============================*/

    /**
     * 获取时区缓存
     * @param deviceId 设备id
     * @return 设备时区缓存
     */
    private DeviceTimeZoneCache getDeviceTimeZoneCache(Long deviceId) {
        String key = String.format(RedisKeyConstant.DEVICE_TIME_ZONE_KEY, deviceId);
        return (DeviceTimeZoneCache) redisTemplate.opsForValue().get(key);
    }

    /**
     * 缓存设备时区信息
     * @param cache 设备时区信息
     */
    private void cacheDeviceTimeZoneCache(DeviceTimeZoneCache cache) {
        if (cache.getDeviceId() == null) {
            log.warn("DeviceTimeZone - 缓存设备时区信息失败,deviceId为null: {}", cache);
        }
        try {
            String key = String.format(RedisKeyConstant.DEVICE_TIME_ZONE_KEY, cache.getDeviceId());
            long ttlHours = statsConfigProperties.getTimeCalibration().getTimeZoneCacheTtlHours();
            redisTemplate.opsForValue().set(key, cache, ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.REDIS_ERROR, e);
        }
    }
}
