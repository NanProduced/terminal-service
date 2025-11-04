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

    /**
     * 更新设备最后上报时间
     *
     * @param deviceId 设备ID
     * @param source   上报源
     * @param clientIp 终端IP
     */
    @Override
    @Async("deviceStatusExecutor")
    public void updateLastReportTime(Long deviceId, ReportSource source, String clientIp) {
        boolean acquired = false;
        try {
            acquired = deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(deviceId, 5000L);
            if (!acquired) {
                log.warn("获取设备更新锁失败，跳过更新: deviceId={}, source={}", deviceId, source);
                return;
            }

            processDeviceStatusUpdate(deviceId, source, clientIp);
        } catch (Exception e) {
            log.error("更新设备上报时间失败: deviceId={}, source={}", deviceId, source, e);
        } finally {
            if (acquired) {
                deviceOnlineStatusPort.releaseDeviceUpdateLock(deviceId);
            }
        }
    }

    /**
     * 处理设备状态更新逻辑
     *
     * @param deviceId 设备ID
     * @param source   上报源
     * @param clientIp 客户端IP
     */
    private void processDeviceStatusUpdate(Long deviceId, ReportSource source, String clientIp) {
        Optional<DeviceOnlineStatus> currentStatusOpt = deviceOnlineStatusPort.getDeviceStatus(deviceId);

        if (currentStatusOpt.isPresent()) {
            handleExistingDevice(deviceId, currentStatusOpt.get(), source, clientIp);
        } else {
            handleNewDevice(deviceId, source, clientIp);
        }
    }

    /**
     * 处理已存在的设备状态（更新/重连）
     * @param deviceId 设备ID
     * @param currentStatus 当前设备状态
     * @param source   上报源
     * @param clientIp 客户端IP
     */
    private void handleExistingDevice(Long deviceId, DeviceOnlineStatus currentStatus,
                                     ReportSource source, String clientIp) {
        DeviceOnlineStatus updatedStatus = determinedUpdateStatus(currentStatus, source, clientIp);
        updateDeviceStatusWithMode(updatedStatus);
        publishStatusEvent(deviceId, updatedStatus);
    }

    /**
     * 处理新上线的设备
     * @param deviceId 设备ID
     * @param source   上报源
     * @param clientIp 客户端IP
     */
    private void handleNewDevice(Long deviceId, ReportSource source, String clientIp) {
        String version = getProtocolVersionForDevice(deviceId, source);
        DeviceOnlineStatus newStatus = DeviceOnlineStatus.createGoLive(deviceId, source, clientIp, version);
        updateDeviceStatusWithMode(newStatus);

        DeviceStatusEvent event = DeviceStatusEvent.createGoLiveEvent(deviceId, source, clientIp);
        deviceStatusEventPort.publishStatusEvent(event);
        log.info("设备上线: deviceId={}, source={}", deviceId, source);
    }

    /**
     * 根据状态转换发布相应的事件
     * @param deviceId 设备ID
     * @param updatedStatus 更新后的设备状态
     */
    private void publishStatusEvent(Long deviceId, DeviceOnlineStatus updatedStatus) {
        DeviceStatusEvent event;

        if (updatedStatus.getStatus() == OnlineStatus.RECONNECT) {
            event = DeviceStatusEvent.createReconnectEvent(deviceId, updatedStatus.getLastReportSource(),
                    updatedStatus.getClientIp(), updatedStatus.getOnlineStartTime(),
                    updatedStatus.getLastReportTime());
            log.info("设备重连: deviceId={}, source={}", deviceId, updatedStatus.getLastReportSource());
        } else {
            event = DeviceStatusEvent.createHeartbeatEvent(deviceId, updatedStatus.getLastReportSource(),
                    updatedStatus.getClientIp());
        }

        deviceStatusEventPort.publishStatusEvent(event);
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
            return;
        }
        
        // 其他状态（如ONLINE心跳）使用配置的异步/同步模式
        boolean useAsync = deviceConfigPort.isAsyncStatusUpdateEnabled() 
                && asyncDeviceStatusUpdatePort != null;
        
        if (useAsync) {
            try {
                // 异步模式：提交到缓冲池
                asyncDeviceStatusUpdatePort.submitStatusUpdate(status);
            } catch (Exception e) {
                log.warn("ApplicationService - 异步提交失败，降级到同步模式: deviceId={}", status.getDeviceId(), e);
                // 降级到同步模式
                deviceOnlineStatusPort.smartDetermined(status);
            }
        } else {
            // 同步模式：直接保存
            deviceOnlineStatusPort.smartDetermined(status);
        }
    }

    /**
     * 决策设备状态转换
     * <p>
     * 状态转换流程：
     *  <li> OFFLINE → RECONNECT（设备重新连接）</li>
     *  <li> GO_LIVE/RECONNECT → ONLINE（状态稳定）</li>
     *  <li> ONLINE → ONLINE（心跳维持，仅刷新时间）</li>
     * </p>
     *
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param clientIp 上报客户端IP
     */
    private DeviceOnlineStatus determinedUpdateStatus(DeviceOnlineStatus currentStatus,
                                                      ReportSource source, String clientIp) {
        OnlineStatus currentState = currentStatus.getStatus();

        // 离线状态 → 重连
        if (currentState == OnlineStatus.OFFLINE) {
            return handleOfflineReconnect(currentStatus, source, clientIp);
        }

        // 初始状态 → 在线稳定
        if (currentState == OnlineStatus.GO_LIVE || currentState == OnlineStatus.RECONNECT) {
            return transitionToOnline(currentStatus, source, clientIp);
        }

        // 在线状态 → 心跳维持
        return refreshOnlineStatus(currentStatus, source, clientIp);
    }

    /**
     * 处理离线状态下的设备重连
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param clientIp 上报客户端IP
     */
    private DeviceOnlineStatus handleOfflineReconnect(DeviceOnlineStatus currentStatus,
                                                      ReportSource source, String clientIp) {
        String version = getProtocolVersionForReconnect(currentStatus, source, currentStatus.getDeviceId());
        return DeviceOnlineStatus.createReconnect(currentStatus, source, clientIp, version);
    }

    /**
     * 从初始状态转换为在线稳定状态
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param clientIp 上报客户端IP
     */
    private DeviceOnlineStatus transitionToOnline(DeviceOnlineStatus currentStatus,
                                                   ReportSource source, String clientIp) {
        long currentTime = System.currentTimeMillis();
        return DeviceOnlineStatus.builder()
            .deviceId(currentStatus.getDeviceId())
            .lastReportTime(currentTime)
            .lastReportSource(source)
            .status(OnlineStatus.ONLINE)
            .statusChangeTime(currentTime)
            .onlineStartTime(currentStatus.getOnlineStartTime())
            .clientIp(clientIp)
            .version(currentStatus.getVersion())
            .build();
    }

    /**
     * 刷新在线状态（仅更新心跳时间）
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param clientIp 上报客户端IP
     */
    private DeviceOnlineStatus refreshOnlineStatus(DeviceOnlineStatus currentStatus,
                                                    ReportSource source, String clientIp) {
        String version = source == ReportSource.WEBSOCKET
            ? getProtocolVersionFromConnection(currentStatus.getDeviceId())
            : null;

        DeviceOnlineStatus refreshStatus = DeviceOnlineStatus.refreshOnline(
            currentStatus.getDeviceId(), source, clientIp, version);

        // 如果WebSocket获取版本失败，保持原有版本
        if (refreshStatus.getVersion() == null) {
            refreshStatus.setVersion(currentStatus.getVersion());
        }
        return refreshStatus;
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

    /**
     * 批量处理离线设备
     * @return 处理的离线设备数量
     */
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
            
            // 批量处理离线设备
            int processedCount = 0;

            // 使用批量处理优化网络往返
            List<DeviceOnlineStatus> offlineStatuses = deviceOnlineStatusPort.batchMarkOfflineAndResetTtl(candidateDeviceIds);

            // 创建离线事件
            List<DeviceStatusEvent> offlineEvents = offlineStatuses.stream()
                    .map(offlineStatus -> {
                        try {
                            DeviceStatusEvent e = DeviceStatusEvent.createDetectedOfflineEvent(
                                    offlineStatus.getDeviceId(),
                                    offlineStatus.getOnlineStartTime(),
                                    offlineStatus.getLastReportTime()
                            );
                            log.debug("ApplicationService - 设备标记离线成功: deviceId={}", offlineStatus.getDeviceId());
                            return e;
                        } catch (Exception ex) {
                            log.warn("ApplicationService - 处理单个设备离线失败: deviceId={}", offlineStatus.getDeviceId(), ex);
                            return null; // 出错的跳过
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            processedCount += offlineEvents.size();
            
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
     * 获取新建设备的协议版本
     * WebSocket: 从连接获取版本; HTTP: 返回null（首次上报无版本）
     *
     * @param deviceId 设备ID
     * @param source 上报数据源
     */
    private String getProtocolVersionForDevice(Long deviceId, ReportSource source) {
        return source == ReportSource.WEBSOCKET
            ? getProtocolVersionFromConnection(deviceId)
            : null;
    }

    /**
     * 获取重连设备的协议版本
     * WebSocket: 从连接获取最新版本; HTTP: 保持原有版本
     *
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param deviceId 设备ID
     */
    private String getProtocolVersionForReconnect(DeviceOnlineStatus currentStatus,
                                                   ReportSource source, Long deviceId) {
        return source == ReportSource.WEBSOCKET
            ? getProtocolVersionFromConnection(deviceId)
            : currentStatus.getVersion();
    }

    /**
     * 从连接获取设备的协议版本
     * 连接不存在时返回默认版本 V1.0
     * @param deviceId 设备ID
     */
    private String getProtocolVersionFromConnection(Long deviceId) {
        return connectionManagerPort.getConnection(deviceId)
            .map(conn -> conn.getProtocolVersion().getVersion())
            .orElse(ProtocolVersion.V1_0.getVersion());
    }
    
}