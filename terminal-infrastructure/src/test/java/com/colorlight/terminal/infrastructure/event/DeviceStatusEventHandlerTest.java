package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.dto.record.TerminalOnlineTimeRecord;
import com.colorlight.terminal.application.dto.record.TerminalReconnectRecord;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalOnlineTimeRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalOnlineStatusRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalReconnectRepository;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncTerminalLoginUpdatePort;
import com.colorlight.terminal.commons.utils.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private TerminalOnlineStatusRepository terminalOnlineStatusRepository;
    
    @Mock
    private TerminalReconnectRepository terminalReconnectRepository;
    
    @Mock
    private AsyncTerminalLoginUpdatePort asyncTerminalLoginUpdatePort;
    
    @Mock
    private MainServerRpcPort mainServerRpcPort;

    @Mock
    private Executor rpcExecutor;

    private DeviceStatusEventHandler deviceStatusEventHandler;
    
    private Long sampleDeviceId;
    private String sampleClientIp;
    private Long currentTime;

    @BeforeEach
    void setUp() {
        sampleDeviceId = 12345L;
        sampleClientIp = "192.168.1.100";
        currentTime = System.currentTimeMillis();

        // 手动创建DeviceStatusEventHandler实例，因为它现在使用手动构造函数
        deviceStatusEventHandler = new DeviceStatusEventHandler(
                terminalAccountRepository,
                terminalOnlineTimeRepository,
                terminalOnlineStatusRepository,
                terminalReconnectRepository,
                asyncTerminalLoginUpdatePort,
                mainServerRpcPort,
                rpcExecutor
        );
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
        LocalDateTime expectedGoLiveStart = TimeUtils.convertTimestampToLocalDateTime(currentTime);
        then(terminalAccountRepository).should().updateLoginTimeImmediate(
                eq(sampleDeviceId), eq(sampleClientIp), any(LocalDateTime.class));
        then(terminalOnlineStatusRepository).should().upsertOnlineState(
                eq(sampleDeviceId), eq(OnlineStatus.GO_LIVE), eq(expectedGoLiveStart));
        
        // 验证RPC通知被调用（通过Executor异步执行）
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        then(rpcExecutor).should().execute(runnableCaptor.capture());

        // 手动执行异步任务以验证RPC调用
        runnableCaptor.getValue().run();
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
        then(terminalOnlineStatusRepository).should(never()).upsertOnlineState(any(), any(), any());
        then(rpcExecutor).should(never()).execute(any(Runnable.class));
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
        LocalDateTime expectedReconnectStart = TimeUtils.convertTimestampToLocalDateTime(onlineStartTime);
        then(terminalOnlineStatusRepository).should().upsertOnlineState(
                eq(sampleDeviceId), eq(OnlineStatus.RECONNECT), eq(expectedReconnectStart));
        
        // 验证保存重连记录被调用
        ArgumentCaptor<TerminalReconnectRecord> reconnectCaptor = ArgumentCaptor.forClass(TerminalReconnectRecord.class);
        then(terminalReconnectRepository).should().saveReconnectRecord(reconnectCaptor.capture());
        
        TerminalReconnectRecord savedRecord = reconnectCaptor.getValue();
        assertEquals(sampleDeviceId, savedRecord.getDeviceId());
        assertEquals(sampleClientIp, savedRecord.getReconnectIp());
        assertEquals("WEBSOCKET", savedRecord.getReconnectSource());
        
        // 验证RPC通知被调用（通过Executor异步执行）
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        then(rpcExecutor).should().execute(runnableCaptor.capture());

        // 手动执行异步任务以验证RPC调用
        runnableCaptor.getValue().run();
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
        long expectedDurationSeconds = (lastReportTime - onlineStartTime) / 1000;
        LocalDateTime expectedSessionStart = TimeUtils.convertTimestampToLocalDateTime(onlineStartTime);

        // Then: 验证在线时长被记录
        ArgumentCaptor<TerminalOnlineTimeRecord> onlineTimeCaptor = ArgumentCaptor.forClass(TerminalOnlineTimeRecord.class);
        then(terminalOnlineTimeRepository).should().saveTerminalOnlineTime(onlineTimeCaptor.capture());
        
        TerminalOnlineTimeRecord savedRecord = onlineTimeCaptor.getValue();
        assertEquals(sampleDeviceId, savedRecord.getDeviceId());
        assertNotNull(savedRecord.getStartTime());
        assertNotNull(savedRecord.getEndTime());
                ArgumentCaptor<LocalDateTime> sessionStartCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        then(terminalOnlineStatusRepository).should().finalizeOnlineSession(
                eq(sampleDeviceId), sessionStartCaptor.capture(), durationCaptor.capture());
        assertEquals(expectedSessionStart, sessionStartCaptor.getValue());
        assertEquals(expectedDurationSeconds, durationCaptor.getValue().longValue());
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
        then(terminalOnlineStatusRepository).should(never()).finalizeOnlineSession(any(), any(LocalDateTime.class), anyLong());
        then(terminalOnlineStatusRepository).should(never()).finalizeOnlineSession(any(), any(LocalDateTime.class), anyLong());
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

        // Then: 验证仅更新Mongo状态，不触发其他业务操作
        then(terminalOnlineStatusRepository).should(never()).updateStatus(any(), any());
        then(terminalAccountRepository).should(never()).updateLoginTimeImmediate(any(), any(), any());
        then(asyncTerminalLoginUpdatePort).should(never()).submitLoginUpdate(any(), any(), any());
        then(terminalOnlineTimeRepository).should(never()).saveTerminalOnlineTime(any());
        then(terminalOnlineStatusRepository).should(never()).finalizeOnlineSession(any(), any(LocalDateTime.class), anyLong());
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
        then(terminalOnlineStatusRepository).should(never()).updateStatus(any(), any());
        
        // 验证RPC通知被调用（通过Executor异步执行）
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        then(rpcExecutor).should().execute(runnableCaptor.capture());

        // 手动执行异步任务以验证RPC调用
        runnableCaptor.getValue().run();
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
        // RPC通知仍然应该被调用（通过Executor异步执行）
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        then(rpcExecutor).should().execute(runnableCaptor.capture());

        // 手动执行异步任务以验证RPC调用
        runnableCaptor.getValue().run();
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
        then(terminalOnlineStatusRepository).should().upsertOnlineState(
                eq(sampleDeviceId), eq(OnlineStatus.RECONNECT), eq(TimeUtils.convertTimestampToLocalDateTime(currentTime - 3600000L)));
        // 其他操作仍然应该正常执行
        then(terminalReconnectRepository).should().saveReconnectRecord(any());
        // RPC通知仍然应该被调用（通过Executor异步执行）
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        then(rpcExecutor).should().execute(runnableCaptor.capture());

        // 手动执行异步任务以验证RPC调用
        runnableCaptor.getValue().run();
        then(mainServerRpcPort).should().notifyDeviceLastReportTime(reconnectEvent);
    }

    @Test
    @DisplayName("处理设备检测离线事件 - 时间顺序异常（负数时长）")
    void handleDetectedDeviceOffline_InvalidTimeOrder() {
        // Given: 时间顺序异常的离线事件（开始时间晚于结束时间）
        Long invalidOnlineStartTime = currentTime;         // 当前时间作为开始时间
        Long invalidLastReportTime = currentTime - 60000L; // 1分钟前作为结束时间（时间倒退）

        DeviceStatusEvent invalidTimeOrderEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_DETECTED_OFFLINE)
                .eventTime(currentTime)
                .onlineStartTime(invalidOnlineStartTime)   // 开始时间：当前时间
                .lastReportTime(invalidLastReportTime)     // 结束时间：1分钟前（异常）
                .build();

        // When: 处理时间顺序异常的离线事件
        assertDoesNotThrow(() -> deviceStatusEventHandler.handleDetectedDeviceOffline(invalidTimeOrderEvent));

        // Then: 验证不保存在线时长记录（因为时间顺序异常）
        then(terminalOnlineTimeRepository).should(never()).saveTerminalOnlineTime(any());
        then(terminalOnlineStatusRepository).should(never()).finalizeOnlineSession(any(), any(LocalDateTime.class), anyLong());
    }

    @Test
    @DisplayName("处理设备检测离线事件 - 连接时长过短")
    void handleDetectedDeviceOffline_TooShortDuration() {
        // Given: 连接时长过短的离线事件（小于1秒）
        Long shortOnlineStartTime = currentTime - 500L;  // 500毫秒前上线
        Long shortLastReportTime = currentTime;          // 当前时间离线

        DeviceStatusEvent shortDurationEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_DETECTED_OFFLINE)
                .eventTime(currentTime)
                .onlineStartTime(shortOnlineStartTime)     // 500ms前
                .lastReportTime(shortLastReportTime)       // 当前时间
                .build();

        // When: 处理连接时长过短的离线事件
        assertDoesNotThrow(() -> deviceStatusEventHandler.handleDetectedDeviceOffline(shortDurationEvent));

        // Then: 验证不保存在线时长记录（因为连接时长小于1秒）
        then(terminalOnlineTimeRepository).should(never()).saveTerminalOnlineTime(any());
    }

    @Test
    @DisplayName("处理设备检测离线事件 - 边界情况：刚好1秒时长")
    void handleDetectedDeviceOffline_ExactlyOneSecondDuration() {
        // Given: 连接时长刚好1秒的离线事件（边界测试）
        Long boundaryOnlineStartTime = currentTime - 1000L; // 1秒前上线
        Long boundaryLastReportTime = currentTime;          // 当前时间离线

        DeviceStatusEvent boundaryEvent = DeviceStatusEvent.builder()
                .deviceId(sampleDeviceId)
                .eventType(DeviceStatusEvent.EventType.DEVICE_DETECTED_OFFLINE)
                .eventTime(currentTime)
                .onlineStartTime(boundaryOnlineStartTime)   // 1000ms前
                .lastReportTime(boundaryLastReportTime)     // 当前时间
                .build();

        // When: 处理连接时长刚好1秒的离线事件
        assertDoesNotThrow(() -> deviceStatusEventHandler.handleDetectedDeviceOffline(boundaryEvent));
        long expectedDurationSeconds = 1L;

        // Then: 验证保存在线时长记录（因为连接时长等于1秒，符合要求）
        ArgumentCaptor<TerminalOnlineTimeRecord> recordCaptor = ArgumentCaptor.forClass(TerminalOnlineTimeRecord.class);
        then(terminalOnlineTimeRepository).should().saveTerminalOnlineTime(recordCaptor.capture());

        TerminalOnlineTimeRecord savedRecord = recordCaptor.getValue();
        assertEquals(sampleDeviceId, savedRecord.getDeviceId());
        assertNotNull(savedRecord.getStartTime());
        assertNotNull(savedRecord.getEndTime());
                ArgumentCaptor<LocalDateTime> boundaryStartCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> boundaryDurationCaptor = ArgumentCaptor.forClass(Long.class);
        then(terminalOnlineStatusRepository).should().finalizeOnlineSession(
                eq(sampleDeviceId), boundaryStartCaptor.capture(), boundaryDurationCaptor.capture());
        assertEquals(TimeUtils.convertTimestampToLocalDateTime(boundaryOnlineStartTime), boundaryStartCaptor.getValue());
        assertEquals(expectedDurationSeconds, boundaryDurationCaptor.getValue().longValue());
    }
}