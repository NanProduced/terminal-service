package com.colorlight.terminal.application.port.outbound.status;

import java.util.List;
import java.util.Map;

/**
 * 设备在线时长监测端口接口
 *
 * @author Nan
 */
public interface DeviceOnlineTimePort {

    /**
     * 记录设备上线时间
     *
     * @param deviceId 设备ID
     * @param onlineStartTime 上线开始时间（毫秒时间戳）
     */
    void recordOnlineStartTime(Long deviceId, Long onlineStartTime);

    /**
     * 获取设备上线时间
     *
     * @param deviceId 设备ID
     * @return 上线开始时间，如果不存在返回null
     */
    Long getOnlineStartTime(Long deviceId);

    /**
     * 计算设备在线时长
     *
     * @param deviceId 设备ID
     * @return 在线时长（毫秒），如果无法计算返回0
     */
    long calculateOnlineDuration(Long deviceId);

    /**
     * 移除设备上线时间记录
     *
     * @param deviceId 设备ID
     */
    void removeOnlineStartTime(Long deviceId);

    /**
     * 批量获取设备在线时长
     *
     * @param deviceIds 设备ID列表
     * @return 设备ID -> 在线时长映射
     */
    Map<Long, Long> batchCalculateOnlineDuration(List<Long> deviceIds);

    /**
     * 检查设备是否有上线时间记录
     *
     * @param deviceId 设备ID
     * @return 是否存在上线时间记录
     */
    boolean hasOnlineTimeRecord(Long deviceId);

}
