package com.colorlight.terminal.application.port.outbound.status;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 设备在线状态存储端口接口
 * 
 * @author Nan
 */
public interface DeviceOnlineStatusPort {

    /**
     * 根据类型推断保存还是更新
     * @param status
     */
    void smartDetermined(DeviceOnlineStatus status);

    /**
     * 保存设备状态（新上线）
     * 
     * @param status 完整的设备状态
     */
    void saveDeviceStatus(DeviceOnlineStatus status);

    /**
     * 更新设备状态（部分覆盖）
     * @param status 部分设备状态字段
     */
    void updateDeviceStatus(DeviceOnlineStatus status);
    
    /**
     * 获取设备状态
     * 
     * @param deviceId 设备ID
     * @return 设备状态
     */
    Optional<DeviceOnlineStatus> getDeviceStatus(Long deviceId);
    
    /**
     * 批量获取设备状态
     * 
     * @param deviceIds 设备ID列表
     * @return 设备状态映射
     */
    Map<Long, DeviceOnlineStatus> batchGetDeviceStatus(List<Long> deviceIds);

    /**
     * 获取设备最后上报时间
     * @param deviceId 设备ID
     * @return 设备最后上报时间时间戳
     */
    Long getDeviceLastReportTime(Long deviceId);

    /**
     * 删除设备状态
     * 
     * @param deviceId 设备ID
     */
    void removeDeviceStatus(Long deviceId);

    /**
     * 删除设备状态索引
     * @param deviceId
     */
    void removeDeviceIndex(Long deviceId);
    
    /**
     * 获取所有设备状态的key
     * 用于定时检查任务
     * 
     * @return 设备ID集合
     */
    Set<Long> getAllDeviceIds();
    
    /**
     * 批量检查设备最后上报时间
     * 用于离线检测优化
     * 
     * @param expireThreshold 过期时间阈值(毫秒)
     * @return 过期的设备ID列表
     */
    List<Long> findExpiredDevices(long expireThreshold);
    
    /**
     * 批量更新设备状态为离线
     * 
     * @param deviceIds 设备ID列表
     */
    void batchMarkOffline(List<Long> deviceIds);
    
    /**
     * 获取在线设备数量
     * 
     * @return 在线设备数
     */
    int getOnlineDeviceCount();

    /**
     * 重置在线设备数量
     * @param onlineDeviceCount 在线设备数
     */
    void setOnlineDeviceCount(int onlineDeviceCount);

    // ==================== 并发控制方法 ====================

    /**
     * 尝试获取设备更新分布式锁
     * 
     * @param deviceId 设备ID
     * @param timeoutMs 锁超时时间（毫秒）
     * @return 是否成功获取锁
     */
    Boolean tryAcquireDeviceUpdateLock(Long deviceId, Long timeoutMs);

    /**
     * 释放设备更新分布式锁
     * 
     * @param deviceId 设备ID
     */
    void releaseDeviceUpdateLock(Long deviceId);

    /**
     * 启动时清理设备状态（不影响计数器）
     * 
     * @param deviceId 设备ID
     */
    void removeDeviceStatusForStartupCleanup(Long deviceId);
}