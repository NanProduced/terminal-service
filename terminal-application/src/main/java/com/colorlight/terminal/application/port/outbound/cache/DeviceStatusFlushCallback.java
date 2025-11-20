package com.colorlight.terminal.application.port.outbound.cache;

import com.colorlight.terminal.application.dto.cache.DeviceUpdateContext;

/**
 * 设备状态 Flush 回调接口
 * 用于处理设备缓存驱逐时的 flush 操作
 *
 * @author Nan
 */
public interface DeviceStatusFlushCallback {

    /**
     * 当设备缓存条目被驱逐时调用
     * 负责将待处理的设备状态 flush 到底层存储
     *
     * @param deviceId 被驱逐的设备ID
     * @param context 设备的更新上下文
     */
    void onContextEvicted(Long deviceId, DeviceUpdateContext context);
}
