package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncDeviceStatusUpdatePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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
    private final ConnectionManagerPort connectionManagerPort;
    
    // 可选的异步状态更新服务 - 当配置启用时注入
    @Autowired(required = false)
    private AsyncDeviceStatusUpdatePort asyncDeviceStatusUpdatePort;
    
    @Override
    @Async("deviceStatusExecutor")
    public void updateLastReportTime(Long deviceId, ReportSource source, String clientIp) {
        // 使用基础设施层的分布式锁防止并发竞态
        Boolean acquired = false;
        
        try {
            // 尝试获取分布式锁，超时5秒
            acquired = deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(deviceId, 5000L);
            
            if (!acquired) {
                log.warn("ApplicationService - 获取设备更新锁失败，跳过此次更新: deviceId={}, source={}", 
                        deviceId, source);
                return;
            }
            
            log.debug("ApplicationService - 更新设备上报时间: deviceId={}, source={}, async={}", 
                    deviceId, source, deviceConfigPort.isAsyncStatusUpdateEnabled());
            
            // 获取当前状态（在锁保护下）
            Optional<DeviceOnlineStatus> currentStatusOpt = deviceOnlineStatusPort.getDeviceStatus(deviceId);

            /*
              终端状态缓存还存在，两种情况：
              1.终端持续在线，仅刷新最后上报时间
              2.终端被定时任务标记为离线，但是状态缓存还未过期，重新上报（短时间内重连）
             */
            if (currentStatusOpt.isPresent()) {
                // 当前状态
                DeviceOnlineStatus currentStatus = currentStatusOpt.get();

                DeviceOnlineStatus updateStatus = determinedUpdateStatus(currentStatus, source, clientIp);

                updateDeviceStatusWithMode(updateStatus);

                // 如果从离线变为在线，发布上线事件（这里应该是短暂的掉线后重连）
                if (updateStatus.getStatus() == OnlineStatus.RECONNECT) {
                    // 创建“重连”事件
                    DeviceStatusEvent event = DeviceStatusEvent.createReconnectEvent(deviceId, source, clientIp, currentStatus.getOnlineStartTime(), currentStatus.getLastReportTime());
                    deviceStatusEventPort.publishStatusEvent(event);
                    log.info("ApplicationService - 设备上线（网络波动、超时重连）: deviceId={}, source={}", deviceId, source);
                } else {
                    // 发布维持在线状态事件
                    DeviceStatusEvent event = DeviceStatusEvent.createHeartbeatEvent(deviceId, source, clientIp);
                    deviceStatusEventPort.publishStatusEvent(event);
                }
            } else {
                // 创建新状态
                String version = getProtocolVersionForDevice(deviceId, source);
                DeviceOnlineStatus newStatus = DeviceOnlineStatus.createGoLive(deviceId, source, clientIp, version);

                updateDeviceStatusWithMode(newStatus);
                
                // 发布上线事件
                DeviceStatusEvent event = DeviceStatusEvent.createGoLiveEvent(deviceId, source, clientIp);
                deviceStatusEventPort.publishStatusEvent(event);
                log.info("ApplicationService - 设备上线: deviceId={}, source={}", deviceId, source);
            }
            
        } catch (Exception e) {
            log.error("ApplicationService - 更新设备上报时间失败: deviceId={}, source={}", deviceId, source, e);
        } finally {
            // 释放分布式锁
            if (acquired) {
                deviceOnlineStatusPort.releaseDeviceUpdateLock(deviceId);
            }
        }
    }
    
    /**
     * 根据配置选择同步或异步方式更新设备状态
     * 对于GO_LIVE状态强制同步处理，确保状态立即写入
     * 
     * @param status 设备状态
     */
    private void updateDeviceStatusWithMode(DeviceOnlineStatus status) {
        // 对于首次上线，强制同步处理确保状态立即写入，避免竞态条件
        if (status.getStatus() == OnlineStatus.GO_LIVE || status.getStatus() == OnlineStatus.RECONNECT) {
            deviceOnlineStatusPort.smartDetermined(status);
            log.debug("ApplicationService - 同步保存关键状态: deviceId={}, status={}", 
                    status.getDeviceId(), status.getStatus());
            return;
        }
        
        // 其他状态（如ONLINE心跳）使用配置的异步/同步模式
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
                deviceOnlineStatusPort.smartDetermined(status);
            }
        } else {
            // 同步模式：直接保存
            deviceOnlineStatusPort.smartDetermined(status);
            log.debug("ApplicationService - 同步保存状态更新: deviceId={}", status.getDeviceId());
        }
    }

    /**
     * 推断状态更新类型（智能优化版本）
     * @param currentStatus 当前的状态缓存
     * @param source 上报来源
     * @param clientIp 客户端Ip
     * @return
     */
    private DeviceOnlineStatus determinedUpdateStatus(DeviceOnlineStatus currentStatus, ReportSource source, String clientIp) {
        OnlineStatus currentState = currentStatus.getStatus();
        long currentTime = System.currentTimeMillis();
        
        // 离线 → 重连
        if (currentState == OnlineStatus.OFFLINE) {
            String version = getProtocolVersionForReconnect(currentStatus, source, currentStatus.getDeviceId());
            return DeviceOnlineStatus.createReconnect(currentStatus, source, clientIp, version);
        }
        
        // GO_LIVE/RECONNECT → ONLINE (第一次转为稳定在线状态)
        if (currentState == OnlineStatus.GO_LIVE || currentState == OnlineStatus.RECONNECT) {
            return DeviceOnlineStatus.builder()
                .deviceId(currentStatus.getDeviceId())
                .lastReportTime(currentTime)
                .lastReportSource(source)
                .status(OnlineStatus.ONLINE)  // ✅ 状态转换
                .statusChangeTime(currentTime)
                .onlineStartTime(currentStatus.getOnlineStartTime()) // 保持原有上线时间
                .clientIp(clientIp)
                .version(currentStatus.getVersion()) // 保持协议版本
                .build();
        }
        
        // ONLINE → 心跳维持 (只更新时间，不更新状态)
        else {
            String version = source == ReportSource.WEBSOCKET ? getProtocolVersionFromConnection(currentStatus.getDeviceId()) : null;
            DeviceOnlineStatus refreshStatus = DeviceOnlineStatus.refreshOnline(
                    currentStatus.getDeviceId(), source, clientIp, version);

            // 版本字段合并（如果是http刷新没有更新version则使用之前的version）
            if (refreshStatus.getVersion() == null) {
                refreshStatus.setVersion(currentStatus.getVersion());
            }
            return refreshStatus;
        }
    }
    
    @Override
    public boolean isDeviceOnline(Long deviceId) {
        try {
            Optional<DeviceOnlineStatus> statusOpt = deviceOnlineStatusPort.getDeviceStatus(deviceId);
            
            if (statusOpt.isPresent()) {
                // 使用配置化的超时阈值
                long timeoutThreshold = deviceConfigPort.getOfflineTimeoutThreshold();
                return statusOpt.get().isOnline(timeoutThreshold);
            }
            
            return false;

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
            long timeoutThreshold = deviceConfigPort.getOfflineTimeoutThreshold();
            
            return deviceIds.stream()
                    .collect(Collectors.toMap(
                            deviceId -> deviceId,
                            deviceId -> {
                                DeviceOnlineStatus status = statusMap.get(deviceId);
                                return status != null && status.isOnline(timeoutThreshold);
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
            
            long timeoutThreshold = deviceConfigPort.getOfflineTimeoutThreshold();
            
            return statusMap.values().stream()
                    .filter(status -> status.isOnline(timeoutThreshold))
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
            long expireThreshold = startTime - deviceConfigPort.getOfflineTimeoutThreshold();
            
            log.debug("ApplicationService - 开始检查离线设备: expireThreshold={}", expireThreshold);
            
            // 获取可能过期的设备ID列表
            List<Long> candidateDeviceIds = deviceOnlineStatusPort.findExpiredDevices(expireThreshold);
            
            if (candidateDeviceIds.isEmpty()) {
                log.debug("ApplicationService - 无离线设备");
                return 0;
            }
            
            log.info("ApplicationService - 发现可能离线设备: count={}", candidateDeviceIds.size());
            
            // 处理离线设备并收集事件
            List<DeviceStatusEvent> offlineEvents = new ArrayList<>();
            int processedCount = 0;
            
            for (Long deviceId : candidateDeviceIds) {
                try {
                    // 使用新的原子化方法：检查+标记离线+重置TTL
                    DeviceOnlineStatus offlineStatus = deviceOnlineStatusPort.markOfflineAndResetTtl(deviceId);
                    
                    if (offlineStatus != null) {
                        // 创建离线事件
                        DeviceStatusEvent event = DeviceStatusEvent.createDetectedOfflineEvent(
                            deviceId, 
                            offlineStatus.getOnlineStartTime(), 
                            offlineStatus.getLastReportTime()
                        );
                        offlineEvents.add(event);
                        processedCount++;
                        
                        log.debug("ApplicationService - 设备标记离线成功: deviceId={}", deviceId);
                    }
                    
                } catch (Exception e) {
                    log.warn("ApplicationService - 处理单个设备离线失败: deviceId={}", deviceId, e);
                }
            }
            
            // 批量发布离线事件
            if (!offlineEvents.isEmpty()) {
                deviceStatusEventPort.batchPublishStatusEvents(offlineEvents);
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("ApplicationService - 离线设备检查完成: 候选设备={}, 实际处理={}, 耗时={}ms", 
                    candidateDeviceIds.size(), processedCount, elapsed);
            
            return processedCount;
            
        } catch (Exception e) {
            log.error("ApplicationService - 处理离线设备失败", e);
            return 0;
        }
    }
    
    /**
     * 获取设备协议版本（新建状态时使用）
     * @param deviceId 设备ID
     * @param source 上报来源
     * @return 协议版本字符串，HTTP来源返回null
     */
    private String getProtocolVersionForDevice(Long deviceId, ReportSource source) {
        if (source == ReportSource.WEBSOCKET) {
            return getProtocolVersionFromConnection(deviceId);
        }
        // HTTP来源新建状态时无协议版本信息
        return null;
    }
    
    /**
     * 获取设备协议版本（重连状态时使用）
     * @param currentStatus 当前状态
     * @param source 上报来源
     * @param deviceId 设备ID
     * @return 协议版本字符串
     */
    private String getProtocolVersionForReconnect(DeviceOnlineStatus currentStatus, ReportSource source, Long deviceId) {
        if (source == ReportSource.WEBSOCKET) {
            // WebSocket: 获取权威版本
            return getProtocolVersionFromConnection(deviceId);
        }
        // HTTP: 保持原版本
        return currentStatus.getVersion();
    }
    
    /**
     * 从连接管理器获取协议版本
     * @param deviceId 设备ID
     * @return 协议版本字符串，连接不存在时返回默认版本
     */
    private String getProtocolVersionFromConnection(Long deviceId) {
        return connectionManagerPort.getConnection(deviceId)
            .map(conn -> conn.getProtocolVersion().getVersion())
            .orElse(ProtocolVersion.V1_0.getVersion());
    }
    
}