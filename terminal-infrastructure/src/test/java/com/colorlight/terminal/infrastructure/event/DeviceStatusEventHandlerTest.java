package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.dto.record.TerminalOnlineTimeRecord;
import com.colorlight.terminal.application.dto.record.TerminalReconnectRecord;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalOnlineTimeRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalReconnectRepository;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncTerminalLoginUpdatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * DeviceStatusEventHandler单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：处理设备状态变更事件，包括上线、重连、离线、心跳等
 * 2. 登录时间更新：首次上线立即更新，重连和心跳异步更新
 * 3. 在线时长记录：设备离线时保存在线时长到MongoDB
 * 4. 重连记录：设备重连时保存重连信息到MongoDB
 * 5. RPC通知：异步通知主服务器设备状态变更
 * 6. 异常处理：各个操作都有异常捕获和日志记录
 * <p>
 * 测试策略：
 * - 各种事件类型的处理测试
 * - 立即更新和异步更新逻辑测试
 * - 数据保存逻辑验证
 * - 异常处理测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceStatusEventHandler单元测试")
class DeviceStatusEventHandlerTest {

    @Mock
    private TerminalAccountRepository terminalAccountRepository;
    
    @Mock
    private TerminalOnlineTimeRepository terminalOnlineTimeRepository;
    
    @Mock
    private TerminalReconnectRepository terminalReconnectRepository;
    
    @Mock
    private AsyncTerminalLoginUpdatePort asyncTerminalLoginUpdatePort;
    
    @Mock
    private MainServerRpcPort mainServerRpcPort;
    
    @InjectMocks
    private DeviceStatusEventHandler deviceStatusEventHandler;
    
    private Long sampleDeviceId;
    private String sampleClientIp;
    private Long currentTime;

    @BeforeEach
    void setUp() {
        sampleDeviceId = 12345L;
        sampleClientIp = "192.168.1.100";
        currentTime = System.currentTimeMillis();
    }

    @Test
    @DisplayName("处理设备上线事件 - 成功场景")
    void handleDeviceOnline_Success() {
        // Given: 设备首次上线事件
        DeviceStatusEvent goLiveEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_GO_LIVE)
                .reportSource(ReportSource.HTTP)
                .clientIp(sampleClientIp)
                .eventTime(currentTime)
                .build();

        // When: 处理设备上线事件
        assertDoesNotThrow(() -> deviceStatusEventHandler.handleDeviceOnline(goLiveEvent));

        // Then: 验证立即更新登录时间被调用
        then(terminalAccountRepository).should().updateLoginTimeImmediate(
                eq(sampleDeviceId), eq(sampleClientIp), any(LocalDateTime.class));
        
        // 验证RPC通知被调用
        then(mainServerRpcPort).should().notifyDeviceLastReportTime(goLiveEvent);
    }

    @Test
    @DisplayName("处理设备上线事件 - 非上线事件类型")
    void handleDeviceOnline_NonGoLiveEvent() {
        // Given: 非上线事件
        DeviceStatusEvent heartbeatEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_HEARTBEAT)
                .eventTime(currentTime)
                .build();

        // When: 处理事件
        deviceStatusEventHandler.handleDeviceOnline(heartbeatEvent);

        // Then: 验证不执行任何操作
        then(terminalAccountRepository).should(never()).updateLoginTimeImmediate(any(), any(), any());
        then(mainServerRpcPort).should(never()).notifyDeviceLastReportTime(any());
    }

    @Test
    @DisplayName("处理设备重连事件 - 成功场景")
    void handlerDeviceReconnect_Success() {
        // Given: 设备重连事件
        Long onlineStartTime = currentTime - 3600000L; // 1小时前上线
        Long lastReportTime = currentTime - 60000L;    // 1分钟前最后上报
        
        DeviceStatusEvent reconnectEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_RECONNECT)
                .reportSource(ReportSource.WEBSOCKET)
                .clientIp(sampleClientIp)
                .eventTime(currentTime)
                .onlineStartTime(onlineStartTime)
                .lastReportTime(lastReportTime)
                .build();

        // When: 处理设备重连事件
        deviceStatusEventHandler.handlerDeviceReconnect(reconnectEvent);

        // Then: 验证异步更新登录时间被调用
        then(asyncTerminalLoginUpdatePort).should().submitLoginUpdate(
                eq(sampleDeviceId), eq(sampleClientIp), any(LocalDateTime.class));
        
        // 验证保存重连记录被调用
        ArgumentCaptor<TerminalReconnectRecord> reconnectCaptor = ArgumentCaptor.forClass(TerminalReconnectRecord.class);
        then(terminalReconnectRepository).should().saveReconnectRecord(reconnectCaptor.capture());
        
        TerminalReconnectRecord savedRecord = reconnectCaptor.getValue();
        assertEquals(sampleDeviceId, savedRecord.getDeviceId());
        assertEquals(sampleClientIp, savedRecord.getReconnectIp());
        assertEquals("WEBSOCKET", savedRecord.getReconnectSource());
        
        // 验证RPC通知被调用
        then(mainServerRpcPort).should().notifyDeviceLastReportTime(reconnectEvent);
    }

    @Test
    @DisplayName("处理设备检测离线事件 - 成功场景")
    void handleDetectedDeviceOffline_Success() {
        // Given: 设备检测离线事件
        Long onlineStartTime = currentTime - 7200000L; // 2小时前上线
        Long lastReportTime = currentTime - 300000L;   // 5分钟前最后上报
        
        DeviceStatusEvent offlineEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_DETECTED_OFFLINE)
                .eventTime(currentTime)
                .onlineStartTime(onlineStartTime)
                .lastReportTime(lastReportTime)
                .build();

        // When: 处理设备离线事件
        deviceStatusEventHandler.handleDetectedDeviceOffline(offlineEvent);

        // Then: 验证保存在线时长记录被调用
        ArgumentCaptor<TerminalOnlineTimeRecord> onlineTimeCaptor = ArgumentCaptor.forClass(TerminalOnlineTimeRecord.class);
        then(terminalOnlineTimeRepository).should().saveTerminalOnlineTime(onlineTimeCaptor.capture());
        
        TerminalOnlineTimeRecord savedRecord = onlineTimeCaptor.getValue();
        assertEquals(sampleDeviceId, savedRecord.getDeviceId());
        assertNotNull(savedRecord.getStartTime());
        assertNotNull(savedRecord.getEndTime());
    }

    @Test
    @DisplayName("处理设备检测离线事件 - 时间为空")
    void handleDetectedDeviceOffline_NullTimes() {
        // Given: 缺少时间信息的离线事件
        DeviceStatusEvent offlineEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_DETECTED_OFFLINE)
                .eventTime(currentTime)
                .onlineStartTime(null) // 上线时间为空
                .lastReportTime(null)  // 最后上报时间为空
                .build();

        // When: 处理事件
        deviceStatusEventHandler.handleDetectedDeviceOffline(offlineEvent);

        // Then: 验证不保存在线时长记录
        then(terminalOnlineTimeRepository).should(never()).saveTerminalOnlineTime(any());
    }

    @Test
    @DisplayName("处理设备确认离线事件 - 成功场景")
    void handleConfirmDeviceOffline_Success() {
        // Given: 设备确认离线事件
        DeviceStatusEvent confirmOfflineEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_CONFIRMED_OFFLINE)
                .eventTime(currentTime)
                .build();

        // When: 处理事件（仅记录日志，无其他操作）
        assertDoesNotThrow(() -> deviceStatusEventHandler.handleConfirmDeviceOffline(confirmOfflineEvent));

        // Then: 验证没有调用任何业务操作
        then(terminalAccountRepository).should(never()).updateLoginTimeImmediate(any(), any(), any());
        then(asyncTerminalLoginUpdatePort).should(never()).submitLoginUpdate(any(), any(), any());
        then(terminalOnlineTimeRepository).should(never()).saveTerminalOnlineTime(any());
    }

    @Test
    @DisplayName("处理设备状态更新事件(心跳) - 成功场景")
    void handleStatusUpdate_Success() {
        // Given: 设备心跳事件
        DeviceStatusEvent heartbeatEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_HEARTBEAT)
                .reportSource(ReportSource.HTTP)
                .clientIp(sampleClientIp)
                .eventTime(currentTime)
                .build();

        // When: 处理心跳事件
        deviceStatusEventHandler.handleStatusUpdate(heartbeatEvent);

        // Then: 验证异步更新登录时间被调用
        then(asyncTerminalLoginUpdatePort).should().submitLoginUpdate(
                eq(sampleDeviceId), eq(sampleClientIp), any(LocalDateTime.class));
        
        // 验证RPC通知被调用
        then(mainServerRpcPort).should().notifyDeviceLastReportTime(heartbeatEvent);
    }

    @Test
    @DisplayName("处理统一设备状态事件 - 成功场景")
    void handleAllDeviceStatusEvents_Success() {
        // Given: 任意设备状态事件
        DeviceStatusEvent anyEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_GO_LIVE)
                .eventTime(currentTime)
                .build();

        // When: 处理统一事件（仅记录日志）
        assertDoesNotThrow(() -> deviceStatusEventHandler.handleAllDeviceStatusEvents(anyEvent));

        // Then: 验证没有调用业务操作（此方法仅用于统一日志记录）
    }

    @Test
    @DisplayName("异步通知主服务器 - 成功场景")
    void notifyMainServerAsync_Success() {
        // Given: 设备状态事件
        DeviceStatusEvent event = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_GO_LIVE)
                .eventTime(currentTime)
                .build();

        // When: 异步通知主服务器
        assertDoesNotThrow(() -> deviceStatusEventHandler.notifyMainServerAsync(event));

        // Then: 验证RPC调用
        then(mainServerRpcPort).should().notifyDeviceLastReportTime(event);
    }

    @Test
    @DisplayName("异步通知主服务器 - RPC异常")
    void notifyMainServerAsync_RpcException() {
        // Given: RPC调用抛出异常
        DeviceStatusEvent event = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_HEARTBEAT)
                .eventTime(currentTime)
                .build();
        
        RuntimeException rpcException = new RuntimeException("RPC调用失败");
        willThrow(rpcException).given(mainServerRpcPort).notifyDeviceLastReportTime(event);

        // When: 异步通知（应该捕获异常）
        assertDoesNotThrow(() -> deviceStatusEventHandler.notifyMainServerAsync(event));

        // Then: 验证RPC被调用，异常被捕获
        then(mainServerRpcPort).should().notifyDeviceLastReportTime(event);
    }

    @Test
    @DisplayName("立即更新登录时间异常处理")
    void updateLoginTimeImmediate_Exception() {
        // Given: 立即更新抛出异常的上线事件
        DeviceStatusEvent goLiveEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_GO_LIVE)
                .reportSource(ReportSource.HTTP)
                .clientIp(sampleClientIp)
                .eventTime(currentTime)
                .build();
        
        RuntimeException updateException = new RuntimeException("数据库更新失败");
        willThrow(updateException).given(terminalAccountRepository)
                .updateLoginTimeImmediate(any(), any(), any());

        // When: 处理事件（应该捕获异常）
        assertDoesNotThrow(() -> deviceStatusEventHandler.handleDeviceOnline(goLiveEvent));

        // Then: 验证立即更新被调用，异常被捕获
        then(terminalAccountRepository).should().updateLoginTimeImmediate(any(), any(), any());
        // RPC通知仍然应该被调用
        then(mainServerRpcPort).should().notifyDeviceLastReportTime(goLiveEvent);
    }

    @Test
    @DisplayName("异步更新登录时间异常处理")
    void updateLoginTimeAsync_Exception() {
        // Given: 异步更新抛出异常的重连事件
        DeviceStatusEvent reconnectEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_RECONNECT)
                .reportSource(ReportSource.WEBSOCKET)
                .clientIp(sampleClientIp)
                .eventTime(currentTime)
                .onlineStartTime(currentTime - 3600000L)
                .lastReportTime(currentTime - 60000L)
                .build();
        
        RuntimeException asyncException = new RuntimeException("异步更新失败");
        willThrow(asyncException).given(asyncTerminalLoginUpdatePort)
                .submitLoginUpdate(any(), any(), any());

        // When: 处理事件（应该捕获异常）
        assertDoesNotThrow(() -> deviceStatusEventHandler.handlerDeviceReconnect(reconnectEvent));

        // Then: 验证异步更新被调用，异常被捕获
        then(asyncTerminalLoginUpdatePort).should().submitLoginUpdate(any(), any(), any());
        // 其他操作仍然应该正常执行
        then(terminalReconnectRepository).should().saveReconnectRecord(any());
        then(mainServerRpcPort).should().notifyDeviceLastReportTime(reconnectEvent);
    }
}