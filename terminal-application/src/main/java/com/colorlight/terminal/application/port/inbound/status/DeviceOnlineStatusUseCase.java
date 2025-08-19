package com.colorlight.terminal.application.port.inbound.status;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 设备在线状态管理用例接口
 * 
 * @author Nan
 */
public interface DeviceOnlineStatusUseCase {
    
    /**
     * 更新设备最后上报时间
     * HTTP请求和WebSocket消息都会调用此方法
     * 
     * @param deviceId 设备ID
     * @param source 上报来源
     * @param clientIp 客户端IP（可选）
     */
    void updateLastReportTime(Long deviceId, ReportSource source, String clientIp);
    
    /**
     * 检查设备是否在线
     * 基于70秒超时判断（60秒业务超时 + 10秒容错）
     * 
     * @param deviceId 设备ID
     * @return 是否在线
     */
    boolean isDeviceOnline(Long deviceId);
    
    /**
     * 获取设备在线状态详情
     * 
     * @param deviceId 设备ID
     * @return 设备状态详情
     */
    Optional<DeviceOnlineStatus> getDeviceStatus(Long deviceId);
    
    /**
     * 批量检查设备在线状态
     * 主服务可能需要批量查询大量设备
     * 
     * @param deviceIds 设备ID列表
     * @return 设备ID -> 是否在线的映射
     */
    Map<Long, Boolean> batchCheckOnline(List<Long> deviceIds);
    
    /**
     * 批量获取设备状态详情
     * 
     * @param deviceIds 设备ID列表
     * @return 设备状态详情列表
     */
    Map<Long, DeviceOnlineStatus> batchGetDeviceStatus(List<Long> deviceIds);
    
    /**
     * 获取所有在线设备ID列表
     * 
     * @return 在线设备ID集合
     */
    Set<Long> getOnlineDeviceIds();
    
    /**
     * 获取在线设备数量统计
     * 
     * @return 在线设备总数
     */
    int getOnlineDeviceCount();
    
    /**
     * 检查并处理超时离线的设备
     * 定时任务调用，检查所有设备的超时状态
     * 
     * @return 本次处理的离线设备数量
     */
    int processOfflineDevices();
}