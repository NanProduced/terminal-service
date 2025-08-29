package com.colorlight.terminal.application.dto.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.ZoneId;

/**
 * 设备时区信息的缓存
 * <p>主要用于素材播放上报校准播放时间</p>
 *
 * @author Nan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceTimeZoneCache {

    /**
     * 设备Id
     */
    private Long deviceId;

    /**
     * 设备时区
     */
    private ZoneId deviceZoneId;

    /**
     * 时区偏移量
     */
    private Double timeZoneOffset;

    /**
     * 与服务器标准时间的偏移量
     */
    private Duration deviation;

    public DeviceTimeZoneCache(Long deviceId, String timezone, Double timezoneOffset) {
        this.deviceId = deviceId;
        this.deviceZoneId = ZoneId.of(timezone);
        this.timeZoneOffset = timezoneOffset;
        this.deviation = Duration.ZERO;
    }

}
