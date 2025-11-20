package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.connection.WebSocketSession;
import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.dto.cache.DeviceUpdateContext;
import com.colorlight.terminal.application.port.outbound.cache.DeviceStatusCachePort;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncDeviceStatusUpdatePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("设备在线状态管理服务")
class DeviceOnlineStatusApplicationServiceTest {

    private static final Long DEVICE_ID = 10001L;
    private static final String CLIENT_IP = "192.168.100.10";

    @Mock
    private DeviceOnlineStatusPort deviceOnlineStatusPort;
    @Mock
    private DeviceStatusEventPort deviceStatusEventPort;
    @Mock
    private DeviceConfigPort deviceConfigPort;
    @Mock
    private ConnectionManagerPort connectionManagerPort;
    @Mock
    private DeviceStatusCachePort deviceStatusCachePort;
    @Mock
    private AsyncDeviceStatusUpdatePort asyncDeviceStatusUpdatePort;

    @Captor
    private ArgumentCaptor<DeviceOnlineStatus> statusCaptor;
    @Captor
    private ArgumentCaptor<DeviceStatusEvent> eventCaptor;

    private DeviceOnlineStatusApplicationService service;
    private DeviceUpdateContext deviceUpdateContext;

    @BeforeEach
    void setUp() {
        deviceUpdateContext = new DeviceUpdateContext();
        lenient().when(deviceStatusCachePort.getOrCreateContext(anyLong())).thenReturn(deviceUpdateContext);
        lenient().when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(60_000L);
        lenient().when(deviceConfigPort.isAsyncStatusUpdateEnabled()).thenReturn(false);

        service = new DeviceOnlineStatusApplicationService(
                deviceOnlineStatusPort,
                deviceStatusEventPort,
                deviceConfigPort,
                connectionManagerPort,
                deviceStatusCachePort,
                null
        );
    }

    @Test
    @DisplayName("应该为新设备创建GO_LIVE状态")
    void shouldCreateGoLiveStatusForNewDevice() {
        when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.empty());

        service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);

        verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
        DeviceOnlineStatus saved = statusCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OnlineStatus.GO_LIVE);
        assertThat(saved.getClientIp()).isEqualTo(CLIENT_IP);

        verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_GO_LIVE);
    }

    @Test
    @DisplayName("应该为在线设备刷新心跳")
    void shouldUpdateExistingDevice() {
        DeviceOnlineStatus existing = DeviceOnlineStatus.refreshOnline(DEVICE_ID, ReportSource.HTTP, CLIENT_IP, null);
        when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(existing));

        service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);

        verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
        DeviceOnlineStatus refreshed = statusCaptor.getValue();
        assertThat(refreshed.getStatus()).isEqualTo(OnlineStatus.ONLINE);
        assertThat(refreshed.getLastReportTime()).isNotNull();
        verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_HEARTBEAT);
    }

    @Test
    @DisplayName("应该在离线设备通过WebSocket重连时创建RECONNECT状态")
    void shouldCreateReconnectStatus() {
        DeviceOnlineStatus offline = DeviceOnlineStatus.builder()
                .deviceId(DEVICE_ID)
                .status(OnlineStatus.OFFLINE)
                .lastReportSource(ReportSource.HTTP)
                .lastReportTime(System.currentTimeMillis() - 120_000L)
                .build();
        when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(offline));
        when(connectionManagerPort.getConnection(DEVICE_ID)).thenReturn(Optional.of(createTerminalConnection()));

        service.updateLastReportTime(DEVICE_ID, ReportSource.WEBSOCKET, CLIENT_IP);

        verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
        DeviceOnlineStatus reconnect = statusCaptor.getValue();
        assertThat(reconnect.getStatus()).isEqualTo(OnlineStatus.RECONNECT);
        assertThat(reconnect.getVersion()).isEqualTo(ProtocolVersion.V1_1.getVersion());

        verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_RECONNECT);
    }

    @Test
    @DisplayName("应该在启用时将ONLINE更新委托给异步缓冲区")
    void shouldSubmitToAsyncBuffer() {
        when(deviceConfigPort.isAsyncStatusUpdateEnabled()).thenReturn(true);
        when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID))
                .thenReturn(Optional.of(DeviceOnlineStatus.refreshOnline(DEVICE_ID, ReportSource.HTTP, CLIENT_IP, null)));

        DeviceOnlineStatusApplicationService asyncService = new DeviceOnlineStatusApplicationService(
                deviceOnlineStatusPort,
                deviceStatusEventPort,
                deviceConfigPort,
                connectionManagerPort,
                deviceStatusCachePort,
                asyncDeviceStatusUpdatePort
        );

        asyncService.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);

        verify(asyncDeviceStatusUpdatePort).submitStatusUpdate(statusCaptor.capture());
        DeviceOnlineStatus submitted = statusCaptor.getValue();
        assertThat(submitted.getStatus()).isEqualTo(OnlineStatus.ONLINE);
        verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_HEARTBEAT);
    }

    @Test
    @DisplayName("应该在禁用时跳过异步提交")
    void shouldFallbackToSyncWhenAsyncDisabled() {
        DeviceOnlineStatus existing = DeviceOnlineStatus.refreshOnline(DEVICE_ID, ReportSource.HTTP, CLIENT_IP, null);
        when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(existing));

        service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);

        verify(asyncDeviceStatusUpdatePort, never()).submitStatusUpdate(any());
        verify(deviceOnlineStatusPort).smartDetermined(any(DeviceOnlineStatus.class));
    }

    private TerminalConnection createTerminalConnection() {
        WebSocketSession session = new WebSocketSession() {
            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public boolean sendMessage(String message) {
                return true;
            }

            @Override
            public void close() {
            }

            @Override
            public String getSessionId() {
                return "session-" + DEVICE_ID;
            }

            @Override
            public Long getDeviceId() {
                return DEVICE_ID;
            }

            @Override
            public String getClientIp() {
                return CLIENT_IP;
            }
        };
        return TerminalConnection.create(DEVICE_ID, session, ProtocolVersion.V1_1);
    }
}
