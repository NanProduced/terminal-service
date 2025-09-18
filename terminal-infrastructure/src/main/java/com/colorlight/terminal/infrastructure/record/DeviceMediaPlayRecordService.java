package com.colorlight.terminal.infrastructure.record;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.application.dto.cache.DeviceTimeZoneCache;
import com.colorlight.terminal.application.port.outbound.repository.MediaPlayRecordRepository;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceMediaPlayRecordPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceTimeZonePort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 素材播放记录上报处理实现类
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMediaPlayRecordService implements DeviceMediaPlayRecordPort {

    private final DeviceTimeZonePort deviceTimeZonePort;
    private final MediaPlayRecordRepository mediaPlayRecordRepository;
    private final TerminalStatsConfigProperties statsConfigProperties;
    private final MainServerRpcPort mainServerRpcPort;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 处理素材播放记录上报数据
     * @param deviceId 设备Id
     * @param reports 上报数据列表
     */
    @Override
    public void handleMediaPlayRecordReport(Long deviceId, List<MediaPlayRecordReport> reports) {

        // 检查是否启用时间校准功能
        if (statsConfigProperties.getMediaPlayRecord().isTimeCalibrationEnabled()) {
            calibrateDeviceTime(deviceId, reports);
        } else {
            log.debug("MediaStats - 播放时间校准功能已禁用, 跳过校准: deviceId={}", deviceId);
        }

        // 获取素材ID列表
        Map<String, Integer> mediaIdMapByMd5 = getMediaIdMapByMd5(reports.stream().map(MediaPlayRecordReport::getResMd5Name).collect(Collectors.toSet()));

        mediaPlayRecordRepository.saveMediaPlayRecords(deviceId, reports, mediaIdMapByMd5);
        log.info("MediaStats - 存储{}条素材播放记录,deviceId={}", reports.size(), deviceId);

    }

    /**
     * 校准播放记录的开始播放时间（UTC）
     * <p>原理：设备上报newRtc时区信息时，同时记录上报时的服务器标准时间（这里的延迟忽略不计），通过将设备的本地时区时间转为UTC再和服务器时间对比，得出误差（小于阈值忽略）</p>
     * @param deviceId 设备Id
     * @param reports 上报数据
     */
    private void calibrateDeviceTime(Long deviceId, List<MediaPlayRecordReport> reports) {
        DeviceTimeZoneCache deviceTimeZone = deviceTimeZonePort.getDeviceTimeZone(deviceId);
        // 缓存不存在则暂时跳过
        if (Objects.isNull(deviceTimeZone) || Objects.isNull(deviceTimeZone.getDeviation())) {
            log.warn("MediaStats - 设备 {} 时区信息缓存为null,播放时间校准失败: {}", deviceId, deviceTimeZone);
            return;
        }
        // 不存在标准时间偏差则跳过
        if (deviceTimeZone.getDeviation().isZero()) {
            return;
        }
        Duration deviation = deviceTimeZone.getDeviation();
        log.debug("MediaStats - 设备 {} 存在时间偏差: {}, 开始校准", deviceId, deviation);
        reports.forEach(e -> e.setAdjustStartTime(e.getStartUtcTime().plus(deviation)));

    }

    /**
     * 根据素材MD5列表获取素材ID列表
     * @param md5List 素材MD5列表
     * @return 素材ID列表
     */
    private Map<String, Integer> getMediaIdMapByMd5(Set<String> md5List) {
        Map<String, Integer> md5Map = Maps.newHashMap();
        for (String md5 : md5List) {
            md5Map.put(md5, getMediaIdByMd5(md5));
        }
        log.debug("MediaStats - 获取素材ID列表成功: {}", md5Map);
        return md5Map;
    }

    /**
     * 根据素材MD5获取素材ID
     * <p>
     *     使用Redis缓存，缓存时间为10分钟，每次成功获取素材ID后，缓存时间为10分钟，10分钟后失效，下次获取素材ID时，会重新获取素材ID并更新缓存
     * </p>
     * @param md5 素材MD5
     * @return 素材ID
     */
    private Integer getMediaIdByMd5(String md5) {
        String cacheKey = String.format(RedisKeyConstant.MEDIA_MD5_ID_MAP_KEY, md5);
        try {
            Integer mediaId = (Integer) redisTemplate.opsForValue().get(cacheKey);
            if (Objects.nonNull(mediaId)) {
                // 刷新缓存过期时间
                redisTemplate.expire(cacheKey, 10, TimeUnit.MINUTES);
                return mediaId;
            }

            // 缓存未命中，调用RPC获取
            Integer mediaIdByMd5 = mainServerRpcPort.getMediaIdByMd5(md5);
            if (Objects.nonNull(mediaIdByMd5)) {
                redisTemplate.opsForValue().set(cacheKey, mediaIdByMd5, 10, TimeUnit.MINUTES);
                log.debug("MediaStats - 缓存素材={} 的ID成功: {}", md5, mediaIdByMd5);
            }
            return mediaIdByMd5;
        } catch (Exception e) {
            log.warn("MediaStats - Redis缓存操作失败，降级到RPC调用: md5={}, error={}", md5, e.getMessage());
            // Redis异常时降级到直接RPC调用
            return mainServerRpcPort.getMediaIdByMd5(md5);
        }
    }
}
