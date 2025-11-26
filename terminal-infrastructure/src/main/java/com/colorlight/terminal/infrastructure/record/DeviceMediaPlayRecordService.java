package com.colorlight.terminal.infrastructure.record;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.application.dto.cache.DeviceTimeZoneCache;
import com.colorlight.terminal.application.port.outbound.repository.MediaPlayRecordRepository;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.dto.rpc.MediaInfo;
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
        Map<String, MediaInfo> mediaInfoMap = getMediaInfoMapByMd5(reports.stream().map(MediaPlayRecordReport::getResMd5Name).collect(Collectors.toSet()));

        mediaPlayRecordRepository.saveMediaPlayRecords(deviceId, reports, mediaInfoMap);
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
     * 根据素材MD5列表获取素材信息列表
     * @param md5List 素材MD5列表
     * @return 素材信息列表
     */
    private Map<String, MediaInfo> getMediaInfoMapByMd5(Set<String> md5List) {
        Map<String, MediaInfo> md5Map = Maps.newHashMap();
        for (String md5 : md5List) {
            md5Map.put(md5, getMediaInfoByMd5(md5));
        }
        log.debug("MediaStats - 获取素材信息列表成功: {}", md5Map);
        return md5Map;
    }

    /**
     * 从 resMd5Name 中提取 MD5 值
     * <p>
     *     格式规则：F_2EADDA07770BF31C7DE78642F4C3C8CC_11669035.mp4
     *     - 固定 F 开头
     *     - 下划线分隔
     *     - MD5（32位十六进制字符）
     *     - 下划线分隔
     *     - 文件大小（数字）
     *     - 文件扩展名
     * </p>
     * @param resMd5Name 素材 resMd5Name 格式字符串
     * @return 提取出的 MD5 值，如果格式不正确则返回原始字符串
     */
    private String extractMd5FromResMd5Name(String resMd5Name) {
        if (Objects.isNull(resMd5Name) || resMd5Name.isEmpty()) {
            log.warn("MediaStats - resMd5Name 为空，无法提取 MD5");
            return resMd5Name;
        }

        try {
            // 使用下划线分割字符串
            String[] parts = resMd5Name.split("_");

            // 格式应该是: F_MD5_SIZE.ext，至少需要3个部分
            if (parts.length < 3) {
                log.warn("MediaStats - resMd5Name 格式不正确，无法提取 MD5: {}", resMd5Name);
                return resMd5Name;
            }

            // 提取 MD5 值（索引为1的部分）
            String md5 = parts[1];
            log.debug("MediaStats - 成功从 {} 中提取 MD5: {}", resMd5Name, md5);
            return md5;

        } catch (Exception e) {
            log.error("MediaStats - 提取 MD5 时发生异常: resMd5Name={}, error={}", resMd5Name, e.getMessage(), e);
            return resMd5Name;
        }
    }

    /**
     * 根据素材 resMd5Name 获取素材信息（包含ID和名称）
     * <p>
     *     使用Redis缓存，缓存时间为10分钟，每次成功获取素材ID后，缓存时间为10分钟，10分钟后失效，下次获取素材ID时，会重新获取素材ID并更新缓存
     * </p>
     * <p>
     *     resMd5Name 格式: F_2EADDA07770BF31C7DE78642F4C3C8CC_11669035.mp4，需要提取中间的 MD5 值
     * </p>
     * @param resMd5Name 素材 resMd5Name 格式字符串
     * @return 素材ID
     */
    private MediaInfo getMediaInfoByMd5(String resMd5Name) {
        // 提取 MD5 值
        String md5 = extractMd5FromResMd5Name(resMd5Name);

        String cacheKey = String.format(RedisKeyConstant.MEDIA_MD5_ID_MAP_KEY, md5);
        try {
            MediaInfo mediaInfo = (MediaInfo) redisTemplate.opsForValue().get(cacheKey);
            if (Objects.nonNull(mediaInfo)) {
                // 刷新缓存过期时间
                redisTemplate.expire(cacheKey, 10, TimeUnit.MINUTES);
                return mediaInfo;
            }

            // 缓存未命中，调用RPC获取
            MediaInfo mediaInfoByMd5 = mainServerRpcPort.getMediaInfoByMd5(md5);
            if (Objects.nonNull(mediaInfoByMd5)) {
                redisTemplate.opsForValue().set(cacheKey, mediaInfoByMd5, 10, TimeUnit.MINUTES);
                log.debug("MediaStats - 缓存素材={} 的信息成功: {}", md5, mediaInfoByMd5);
            }
            return mediaInfoByMd5;
        } catch (Exception e) {
            log.warn("MediaStats - Redis缓存操作失败，降级到RPC调用: md5={}, error={}", md5, e.getMessage());
            // Redis异常时降级到直接RPC调用
            return mainServerRpcPort.getMediaInfoByMd5(md5);
        }
    }
}
