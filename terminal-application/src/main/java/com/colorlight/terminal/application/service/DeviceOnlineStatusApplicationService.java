package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncDeviceStatusUpdatePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备在线状态管理应用服务
 * 支持同步/异步配置切换
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOnlineStatusApplicationService implements DeviceOnlineStatusUseCase {
    
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final DeviceStatusEventPort deviceStatusEventPort;
    private final DeviceConfigPort deviceConfigPort;
    
    // 可选的异步状态更新服务 - 当配置启用时注入
    @Autowired(required = false)
    private AsyncDeviceStatusUpdatePort asyncDeviceStatusUpdatePort;
    
    @Override
    public void updateLastReportTime(Long deviceId, ReportSource source, String clientIp) {
        try {
            log.debug("ApplicationService - 更新设备上报时间: deviceId={}, source={}, async={}", 
                    deviceId, source, deviceConfigPort.isAsyncStatusUpdateEnabled());
            
            // 获取当前状态
            Optional<DeviceOnlineStatus> currentStatusOpt = deviceOnlineStatusPort.getDeviceStatus(deviceId);
            
            if (currentStatusOpt.isPresent()) {
                // 更新现有状态
                DeviceOnlineStatus currentStatus = currentStatusOpt.get();
                boolean wasOffline = currentStatus.getStatus() == OnlineStatus.OFFLINE;
                
                currentStatus.updateReportTime(source);
                
                // 根据配置选择同步或异步处理
                saveDeviceStatusWithMode(currentStatus);
                
                // 如果从离线变为在线，发布上线事件（这里应该是短暂的掉线后重连）
                if (wasOffline) {
                    DeviceStatusEvent event = DeviceStatusEvent.createOnlineEvent(deviceId, source, clientIp);
                    deviceStatusEventPort.publishStatusEvent(event);
                    log.info("ApplicationService - 设备上线（网络波动、超时重连）: deviceId={}, source={}", deviceId, source);
                } else {
                    // 发布状态更新事件
                    DeviceStatusEvent event = DeviceStatusEvent.createUpdateEvent(deviceId, source);
                    deviceStatusEventPort.publishStatusEvent(event);
                }
            } else {
                // 创建新状态
                DeviceOnlineStatus newStatus = DeviceOnlineStatus.createOnline(deviceId, source, clientIp);
                
                // 根据配置选择同步或异步处理
                saveDeviceStatusWithMode(newStatus);
                
                // 发布上线事件
                DeviceStatusEvent event = DeviceStatusEvent.createOnlineEvent(deviceId, source, clientIp);
                deviceStatusEventPort.publishStatusEvent(event);
                log.info("ApplicationService - 设备上线: deviceId={}, source={}", deviceId, source);
            }
            
        } catch (Exception e) {
            log.error("ApplicationService - 更新设备上报时间失败: deviceId={}, source={}", deviceId, source, e);
        }
    }
    
    /**
     * 根据配置选择同步或异步方式保存设备状态
     * 
     * @param status 设备状态
     */
    private void saveDeviceStatusWithMode(DeviceOnlineStatus status) {
        // 检查是否启用异步模式且异步服务可用
        boolean useAsync = deviceConfigPort.isAsyncStatusUpdateEnabled() 
                && asyncDeviceStatusUpdatePort != null;
        
        if (useAsync) {
            try {
                // 异步模式：提交到缓冲池
                asyncDeviceStatusUpdatePort.submitStatusUpdate(status);
                log.debug("ApplicationService - 异步提交状态更新: deviceId={}", status.getDeviceId());
                
            } catch (Exception e) {
                log.warn("ApplicationService - 异步提交失败，降级到同步模式: deviceId={}", status.getDeviceId(), e);
                // 降级到同步模式
                deviceOnlineStatusPort.saveDeviceStatus(status);
            }
        } else {
            // 同步模式：直接保存
            deviceOnlineStatusPort.saveDeviceStatus(status);
            log.debug("ApplicationService - 同步保存状态更新: deviceId={}", status.getDeviceId());
        }
    }
    
    @Override
    public boolean isDeviceOnline(Long deviceId) {
        try {
            Optional<DeviceOnlineStatus> statusOpt = deviceOnlineStatusPort.getDeviceStatus(deviceId);

            return statusOpt.map(DeviceOnlineStatus::isOnline).orElse(false);

        } catch (Exception e) {
            log.error("ApplicationService - 查询设备在线状态失败: deviceId={}", deviceId, e);
            // 故障时返回未知状态（保守策略）
            return false;
        }
    }
    
    @Override
    public Optional<DeviceOnlineStatus> getDeviceStatus(Long deviceId) {
        try {
            return deviceOnlineStatusPort.getDeviceStatus(deviceId);
        } catch (Exception e) {
            log.error("ApplicationService - 获取设备状态详情失败: deviceId={}", deviceId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public Map<Long, Boolean> batchCheckOnline(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        try {
            log.debug("ApplicationService - 批量检查设备在线状态: count={}", deviceIds.size());
            
            Map<Long, DeviceOnlineStatus> statusMap = deviceOnlineStatusPort.batchGetDeviceStatus(deviceIds);
            
            return deviceIds.stream()
                    .collect(Collectors.toMap(
                            deviceId -> deviceId,
                            deviceId -> {
                                DeviceOnlineStatus status = statusMap.get(deviceId);
                                return status != null && status.isOnline();
                            }
                    ));
                    
        } catch (Exception e) {
            log.error("ApplicationService - 批量检查设备在线状态失败: deviceIds.size={}", deviceIds.size(), e);
            // 故障降级：返回全部离线
            return deviceIds.stream()
                    .collect(Collectors.toMap(deviceId -> deviceId, deviceId -> false));
        }
    }
    
    @Override
    public Map<Long, DeviceOnlineStatus> batchGetDeviceStatus(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        try {
            return deviceOnlineStatusPort.batchGetDeviceStatus(deviceIds);
        } catch (Exception e) {
            log.error("ApplicationService - 批量获取设备状态详情失败: deviceIds.size={}", deviceIds.size(), e);
            return Collections.emptyMap();
        }
    }
    
    @Override
    public Set<Long> getOnlineDeviceIds() {
        try {
            Set<Long> allDeviceIds = deviceOnlineStatusPort.getAllDeviceIds();
            Map<Long, DeviceOnlineStatus> statusMap = deviceOnlineStatusPort.batchGetDeviceStatus(
                    new ArrayList<>(allDeviceIds));
            
            return statusMap.values().stream()
                    .filter(DeviceOnlineStatus::isOnline)
                    .map(DeviceOnlineStatus::getDeviceId)
                    .collect(Collectors.toSet());
                    
        } catch (Exception e) {
            log.error("ApplicationService - 获取在线设备ID列表失败", e);
            return Collections.emptySet();
        }
    }
    
    @Override
    public int getOnlineDeviceCount() {
        try {
            return deviceOnlineStatusPort.getOnlineDeviceCount();
        } catch (Exception e) {
            log.error("ApplicationService - 获取在线设备数量失败", e);
            return 0;
        }
    }

    @Override
    public int processOfflineDevices() {
        try {
            long startTime = System.currentTimeMillis();
            long expireThreshold = startTime - 70_000; // 70秒超时
            
            log.debug("ApplicationService - 开始检查离线设备: expireThreshold={}", expireThreshold);
            
            // 使用优化的批量查询
            List<Long> expiredDeviceIds = deviceOnlineStatusPort.findExpiredDevices(expireThreshold);
            
            if (expiredDeviceIds.isEmpty()) {
                log.debug("ApplicationService - 无离线设备");
                return 0;
            }
            
            log.info("ApplicationService - 发现离线设备: count={}", expiredDeviceIds.size());
            
            // 获取这些设备的详细状态，计算在线时长
            Map<Long, DeviceOnlineStatus> statusMap = deviceOnlineStatusPort.batchGetDeviceStatus(expiredDeviceIds);
            List<DeviceStatusEvent> offlineEvents = new ArrayList<>();
            
            for (Long deviceId : expiredDeviceIds) {
                DeviceOnlineStatus status = statusMap.get(deviceId);
                if (status != null && status.getStatus() == OnlineStatus.ONLINE) {
                    long onlineDurationMs = status.markOffline();
                    
                    // 创建离线事件
                    DeviceStatusEvent event = DeviceStatusEvent.createOfflineEvent(deviceId, onlineDurationMs);
                    offlineEvents.add(event);
                }
            }
            
            // 批量更新为离线状态
            deviceOnlineStatusPort.batchMarkOffline(expiredDeviceIds);
            
            // 批量发布离线事件
            if (!offlineEvents.isEmpty()) {
                deviceStatusEventPort.batchPublishStatusEvents(offlineEvents);
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("ApplicationService - 离线设备检查完成: 处理设备={}, 耗时={}ms", expiredDeviceIds.size(), elapsed);
            
            return expiredDeviceIds.size();
            
        } catch (Exception e) {
            log.error("ApplicationService - 处理离线设备失败", e);
            return 0;
        }
    }
}