package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.dto.record.TerminalOnlineTimeRecord;
import com.colorlight.terminal.application.dto.record.TerminalReconnectRecord;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalOnlineTimeRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalOnlineStatusRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalReconnectRepository;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncTerminalLoginUpdatePort;
import com.colorlight.terminal.commons.utils.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

/**
 * 设备状态事件处理器
 * 处理设备状态变更事件，包括登录时间更新
 * 
 * @author Nan
 */
@Slf4j
@Component
public class DeviceStatusEventHandler {
    
    private final TerminalAccountRepository terminalAccountRepository;
    private final TerminalOnlineTimeRepository terminalOnlineTimeRepository;
    private final TerminalOnlineStatusRepository terminalOnlineStatusRepository;
    private final TerminalReconnectRepository  terminalReconnectRepository;
    private final AsyncTerminalLoginUpdatePort asyncTerminalLoginUpdatePort;
    private final MainServerRpcPort mainServerRpcPort;
    private final Executor rpcExecutor;

    // 手动构造函数以支持@Qualifier注解
    public DeviceStatusEventHandler(TerminalAccountRepository terminalAccountRepository,
                                    TerminalOnlineTimeRepository terminalOnlineTimeRepository,
                                    TerminalOnlineStatusRepository terminalOnlineStatusRepository,
                                    TerminalReconnectRepository terminalReconnectRepository,
                                    AsyncTerminalLoginUpdatePort asyncTerminalLoginUpdatePort,
                                    MainServerRpcPort mainServerRpcPort,
                                    @Qualifier("rpcNotificationExecutor") Executor rpcExecutor) {
        this.terminalAccountRepository = terminalAccountRepository;
        this.terminalOnlineTimeRepository = terminalOnlineTimeRepository;
        this.terminalOnlineStatusRepository = terminalOnlineStatusRepository;
        this.terminalReconnectRepository = terminalReconnectRepository;
        this.asyncTerminalLoginUpdatePort = asyncTerminalLoginUpdatePort;
        this.mainServerRpcPort = mainServerRpcPort;
        this.rpcExecutor = rpcExecutor;
    }

    /**
     * 处理设备上线事件
     * 设备首次上线时立即更新登录时间到MySQL，确保firstLoginTime不丢失
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleDeviceOnline(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_GO_LIVE) {
            log.info("DeviceStatusEvent - 设备上线事件: deviceId={}, source={}, clientIp={}",
                    event.getDeviceId(), event.getReportSource(), event.getClientIp());
            
            // 首次上线立即更新登录时间，确保firstLoginTime不丢失
            updateLoginTimeImmediate(event);
            // 异步通知主服务（使用独立RPC线程池）
            rpcExecutor.execute(() -> notifyMainServerAsync(event));
            // upsert 在线状态
            terminalOnlineStatusRepository.upsertOnlineState(
                    event.getDeviceId(),
                    OnlineStatus.GO_LIVE,
                    TimeUtils.convertTimestampToLocalDateTime(event.getEventTime())
            );
        }
    }

    /**
     * 处理设备重连事件
     * 设备重连时提交到异步缓冲池批量更新
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handlerDeviceReconnect(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_RECONNECT) {
            log.info("DeviceStatusEvent - 设备短时间重连事件: deviceId={}, source={}, clientIp={}",
                    event.getDeviceId(), event.getReportSource(), event.getClientIp());
            
            LocalDateTime reconnectStartTime = event.getOnlineStartTime() != null
                    ? TimeUtils.convertTimestampToLocalDateTime(event.getOnlineStartTime())
                    : TimeUtils.convertTimestampToLocalDateTime(event.getEventTime());
            
            // 重连时提交到缓冲池异步更新
            updateLoginTimeAsync(event);
            // 记录重连信息
            saveTerminalReconnect(event);
            // 异步通知主服务（使用独立RPC线程池）
            rpcExecutor.execute(() -> notifyMainServerAsync(event));
            // upsert 在线状态
            terminalOnlineStatusRepository.upsertOnlineState(
                    event.getDeviceId(),
                    OnlineStatus.RECONNECT,
                    reconnectStartTime
            );
        }
    }
    
    /**
     * 处理设备离线事件（定时任务检测）
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleDetectedDeviceOffline(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_DETECTED_OFFLINE) {
            log.info("DeviceStatusEvent - 标记设备离线事件: deviceId={}", event.getDeviceId());

            // 记录在线时长
            saveTerminalOnlineTime(event);
        }
    }

    /**
     * 处理设备状态缓存过期监听事件
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleConfirmDeviceOffline(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_CONFIRMED_OFFLINE) {
            log.info("DeviceStatusEvent - 确认设备离线事件: deviceId={}", event.getDeviceId());
        }
    }
    
    /**
     * 处理设备状态更新事件（心跳）
     * 设备持续在线时提交到异步缓冲池批量更新
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleStatusUpdate(DeviceStatusEvent event) {
        if (event.getEventType() == DeviceStatusEvent.EventType.DEVICE_HEARTBEAT) {
            // 心跳事件提交到缓冲池异步更新
            updateLoginTimeAsync(event);
            // 异步通知主服务（使用独立RPC线程池）
            rpcExecutor.execute(() -> notifyMainServerAsync(event));
        }
    }

    /**
     * 统一事件处理入口（可选）
     * 如果需要对所有事件进行统一处理
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleAllDeviceStatusEvents(DeviceStatusEvent event) {
        // 统一的事件记录、指标更新等（仅用于监控和统计）
    }
    
    // ==================== 登录时间更新辅助方法 ====================
    
    /**
     * 立即更新登录时间（用于首次上线）
     * 直接更新MySQL，确保firstLoginTime不丢失
     */
    private void updateLoginTimeImmediate(DeviceStatusEvent event) {
        try {
            Long deviceId = event.getDeviceId();
            String clientIp = event.getClientIp();
            LocalDateTime loginTime = TimeUtils.convertTimestampToLocalDateTime(event.getEventTime());
            
            // 立即更新到MySQL
            terminalAccountRepository.updateLoginTimeImmediate(deviceId, clientIp, loginTime);
            
        } catch (Exception e) {
            log.error("DeviceLoginUpdate - 立即更新登录时间失败: deviceId={}", event.getDeviceId(), e);
        }
    }
    
    /**
     * 异步更新登录时间（用于重连和心跳）
     * 提交到缓冲池批量处理
     */
    private void updateLoginTimeAsync(DeviceStatusEvent event) {
        try {
            Long deviceId = event.getDeviceId();
            String clientIp = event.getClientIp();
            LocalDateTime loginTime = TimeUtils.convertTimestampToLocalDateTime(event.getEventTime());

            // 提交到异步缓冲池
            asyncTerminalLoginUpdatePort.submitLoginUpdate(deviceId, clientIp, loginTime);
        } catch (Exception e) {
            log.error("DeviceLoginUpdate - 提交登录时间异步更新失败: deviceId={}", event.getDeviceId(), e);
        }
    }

    // ==================== 记录在线时长辅助方法 ====================

    /**
     * 保存设备在线时长记录
     * @param event 设备离线事件
     */
    private void saveTerminalOnlineTime(DeviceStatusEvent event) {

        if (event.getOnlineStartTime() == null || event.getLastReportTime() == null) {
            log.error("DeviceOnlineTime - 上线/离线时间为空，保存失败: deviceId={}, event={}", event.getDeviceId(), event);
            return;
        }

        // 时间合法性检查：防止时间顺序异常导致负数时长
        if (event.getOnlineStartTime() > event.getLastReportTime()) {
            log.error("DeviceOnlineTime - 时间顺序异常，跳过保存: deviceId={}, startTime={}, endTime={}",
                event.getDeviceId(), event.getOnlineStartTime(), event.getLastReportTime());
            return;
        }

        // 最小时长校验：忽略无意义的极短连接记录（小于1秒）
        long durationMs = event.getLastReportTime() - event.getOnlineStartTime();
        if (durationMs < 1000) {
            log.debug("DeviceOnlineTime - 连接时长过短，跳过保存: deviceId={}, duration={}ms",
                event.getDeviceId(), durationMs);
            return;
        }

        TerminalOnlineTimeRecord onlineTimeRecord = TerminalOnlineTimeRecord.builder()
                .deviceId(event.getDeviceId())
                .startTime(TimeUtils.convertTimestampToLocalDateTime(event.getOnlineStartTime()))
                .endTime(TimeUtils.convertTimestampToLocalDateTime(event.getLastReportTime()))
                .build();

        terminalOnlineTimeRepository.saveTerminalOnlineTime(onlineTimeRecord);
        long durationSeconds = durationMs / 1000;
        terminalOnlineStatusRepository.finalizeOnlineSession(
                event.getDeviceId(),
                TimeUtils.convertTimestampToLocalDateTime(event.getOnlineStartTime()),
                durationSeconds
        );
        log.debug("DeviceOnlineTime - 设备在线时长记录保存成功: deviceId={}, duration={}s",
            event.getDeviceId(), durationSeconds);
    }

    // ==================== 终端异常重连记录辅助方法 ====================

    /**
     * 保存设备重连信息
     * @param event 重连事件
     */
    private void saveTerminalReconnect(DeviceStatusEvent event) {

        TerminalReconnectRecord reconnectRecord = TerminalReconnectRecord.builder()
                .deviceId(event.getDeviceId())
                .startOnlineTime(TimeUtils.convertTimestampToLocalDateTime(event.getOnlineStartTime()))
                .lastReportTime(TimeUtils.convertTimestampToLocalDateTime(event.getLastReportTime()))
                .reconnectTime(TimeUtils.convertTimestampToLocalDateTime(event.getEventTime()))
                .reconnectIp(event.getClientIp())
                .reconnectSource(event.getReportSource().name())
                .build();

        terminalReconnectRepository.saveReconnectRecord(reconnectRecord);
        log.debug("DeviceReconnect - 设备重连信息保存成功: deviceId={}, info={}", event.getDeviceId(), event);
    }

    // ==================== RPC通知辅助方法 ====================

    /**
     * 异步通知主服务
     *
     * @param event 设备状态事件
     */
    public void notifyMainServerAsync(DeviceStatusEvent event) {
        mainServerRpcPort.notifyDeviceLastReportTime(event);
    }
}