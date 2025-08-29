package com.colorlight.terminal.infrastructure.record;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.application.dto.cache.DeviceTimeZoneCache;
import com.colorlight.terminal.application.port.outbound.repository.MediaPlayRecordRepository;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceMediaPlayRecordPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceTimeZonePort;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

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
        
        mediaPlayRecordRepository.saveMediaPlayRecords(deviceId, reports);
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
}
