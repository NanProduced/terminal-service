package com.colorlight.terminal.application.port.outbound.cache;

import com.colorlight.terminal.application.dto.cache.DeviceUpdateContext;

/**
 * 设备状态缓存端口接口
 *
 * @author Nan
 */
public interface DeviceStatusCachePort {

    /**
     * 获取或创建设备的更新上下文
     * <p>
     * 缓存策略：
     * - 不存在时，创建新的 DeviceUpdateContext
     * - 存在时，直接返回现有实例
     * - 访问后更新过期时间（expireAfterAccess）
     * - 超出大小限制时，按 LRU 驱逐并触发 flush
     *
     * @param deviceId 设备ID
     * @return 设备级的状态更新上下文
     */
    DeviceUpdateContext getOrCreateContext(Long deviceId);

    /**
     * 手动驱逐指定设备的缓存条目
     * @param deviceId 设备ID
     */
    void invalidate(Long deviceId);

    /**
     * 清空所有设备缓存条目
     * 注意：清空前会触发所有条目的 flush 回调
     */
    void clearAll();
}
