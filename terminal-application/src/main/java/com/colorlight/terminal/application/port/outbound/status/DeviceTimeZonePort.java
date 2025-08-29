package com.colorlight.terminal.application.port.outbound.status;

import com.colorlight.terminal.application.dto.cache.DeviceTimeZoneCache;

/**
 * 设备时间与时区相关接口端口
 *
 * @author Nan
 */
public interface DeviceTimeZonePort {

    /**
     * 获取终端的时区及偏移量信息
     * @param deviceId 设备Id
     * @return 设备时区信息
     */
    DeviceTimeZoneCache getDeviceTimeZone(Long deviceId);

    /**
     * 刷新终端的时区偏移量信息缓存
     * @param deviceId 设备Id
     */
    DeviceTimeZoneCache RefreshDeviceTimeZoneCache(Long deviceId);
}
