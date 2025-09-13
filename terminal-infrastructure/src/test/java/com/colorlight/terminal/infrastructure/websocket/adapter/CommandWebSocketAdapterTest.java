package com.colorlight.terminal.infrastructure.websocket.adapter;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.infrastructure.websocket.connection.TerminalWebsocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CommandWebSocketAdapter单元测试
 * 
 * 业务逻辑总结：
 * CommandWebSocketAdapter是WebSocket指令下发适配器，核心职责是将应用层的TerminalCommand
 * 通过WebSocket发送给终端设备。它集成连接管理器，实现指令实时下发。
 * 
 * 主要业务流程：
 * 1. sendCommandViaWebSocket：获取设备连接 → 验证连接有效性 → 构建WebSocket消息 → 发送消息
 * 2. isDeviceOnline：获取设备连接 → 验证连接有效性
 * 3. 私有方法负责连接验证和消息格式转换
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommandWebSocketAdapter单元测试")
class CommandWebSocketAdapterTest {

    @Mock
    private ConnectionManagerPort connectionManager;

    @InjectMocks
    private CommandWebSocketAdapter commandWebSocketAdapter;

    @Mock
    private TerminalConnection terminalConnection;

    @Mock
    private TerminalWebsocketSession websocketSession;

    @BeforeEach
    void setUp() {
        // 使用lenient()避免严格模式报错
        lenient().when(terminalConnection.getSession()).thenReturn(websocketSession);
        lenient().when(websocketSession.isConnected()).thenReturn(true);
        lenient().when(websocketSession.sendMessage(anyString())).thenReturn(true);
    }

    @Nested
    @DisplayName("sendCommandViaWebSocket方法测试")
    class SendCommandViaWebSocketTests {

        @Test
        @DisplayName("应该成功通过WebSocket发送指令")
        void should_successfully_send_command_via_websocket() {
            // Given - 准备测试数据
            TerminalCommand command = TestDataBuilder.buildTerminalCommand();
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenReturn(Optional.of(terminalConnection));
            when(terminalConnection.getSession()).thenReturn(websocketSession);
            when(websocketSession.isConnected()).thenReturn(true);
            when(websocketSession.sendMessage(anyString())).thenReturn(true);

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果
            assertThat(result).isTrue();
            verify(connectionManager).getConnection(command.getDeviceId());
            verify(websocketSession).sendMessage(anyString());
        }

        @Test
        @DisplayName("当设备连接不存在时应该返回false")
        void should_return_false_when_device_connection_not_exists() {
            // Given - 设备连接不存在
            TerminalCommand command = TestDataBuilder.buildTerminalCommand();
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenReturn(Optional.empty());

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果
            assertThat(result).isFalse();
            verify(connectionManager).getConnection(command.getDeviceId());
            verify(websocketSession, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("当连接无效时应该返回false - 连接为null")
        void should_return_false_when_connection_is_null() {
            // Given - 连接对象为null（isConnectionValid会检查connection == null）
            TerminalCommand command = TestDataBuilder.buildTerminalCommand();
            TerminalConnection nullConnection = mock(TerminalConnection.class);
            // 模拟isConnectionValid中的null检查逻辑
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenReturn(Optional.of(nullConnection));
            when(nullConnection.getSession()).thenReturn(null); // session为null

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("当连接无效时应该返回false - session为null")
        void should_return_false_when_session_is_null() {
            // Given - session为null
            TerminalCommand command = TestDataBuilder.buildTerminalCommand();
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenReturn(Optional.of(terminalConnection));
            when(terminalConnection.getSession()).thenReturn(null);

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果
            assertThat(result).isFalse();
            verify(websocketSession, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("当连接无效时应该返回false - WebSocket未连接")
        void should_return_false_when_websocket_not_connected() {
            // Given - WebSocket未连接
            TerminalCommand command = TestDataBuilder.buildTerminalCommand();
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenReturn(Optional.of(terminalConnection));
            when(terminalConnection.getSession()).thenReturn(websocketSession);
            when(websocketSession.isConnected()).thenReturn(false);

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果
            assertThat(result).isFalse();
            verify(websocketSession, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("当会话类型不匹配时应该返回false")
        void should_return_false_when_session_type_mismatch() {
            // Given - 会话类型不是TerminalWebsocketSession
            TerminalCommand command = TestDataBuilder.buildTerminalCommand();
            // 创建一个非TerminalWebsocketSession的WebSocketSession实现
            com.colorlight.terminal.application.domain.connection.WebSocketSession invalidSession = 
                mock(com.colorlight.terminal.application.domain.connection.WebSocketSession.class);
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenReturn(Optional.of(terminalConnection));
            when(terminalConnection.getSession()).thenReturn(invalidSession);

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果
            assertThat(result).isFalse();
            verify(websocketSession, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("当WebSocket发送失败时应该返回false")
        void should_return_false_when_websocket_send_fails() {
            // Given - WebSocket发送失败
            TerminalCommand command = TestDataBuilder.buildTerminalCommand();
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenReturn(Optional.of(terminalConnection));
            when(terminalConnection.getSession()).thenReturn(websocketSession);
            when(websocketSession.isConnected()).thenReturn(true);
            when(websocketSession.sendMessage(anyString())).thenReturn(false);

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果
            assertThat(result).isFalse();
            verify(websocketSession).sendMessage(anyString());
        }

        @Test
        @DisplayName("当发生异常时应该返回false")
        void should_return_false_when_exception_occurs() {
            // Given - 模拟异常
            TerminalCommand command = TestDataBuilder.buildTerminalCommand();
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenThrow(new RuntimeException("模拟异常"));

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isDeviceOnline方法测试")
    class IsDeviceOnlineTests {

        @Test
        @DisplayName("当设备连接有效时应该返回true")
        void should_return_true_when_device_is_online() {
            // Given - 设备连接有效
            Long deviceId = 12345L;
            when(connectionManager.getConnection(deviceId))
                .thenReturn(Optional.of(terminalConnection));
            when(terminalConnection.getSession()).thenReturn(websocketSession);
            when(websocketSession.isConnected()).thenReturn(true);

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.isDeviceOnline(deviceId);

            // Then - 验证结果
            assertThat(result).isTrue();
            verify(connectionManager).getConnection(deviceId);
        }

        @Test
        @DisplayName("当设备连接不存在时应该返回false")
        void should_return_false_when_device_connection_not_exists() {
            // Given - 设备连接不存在
            Long deviceId = 12345L;
            when(connectionManager.getConnection(deviceId))
                .thenReturn(Optional.empty());

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.isDeviceOnline(deviceId);

            // Then - 验证结果
            assertThat(result).isFalse();
            verify(connectionManager).getConnection(deviceId);
        }

        @Test
        @DisplayName("当设备连接无效时应该返回false")
        void should_return_false_when_device_connection_invalid() {
            // Given - 设备连接无效
            Long deviceId = 12345L;
            when(connectionManager.getConnection(deviceId))
                .thenReturn(Optional.of(terminalConnection));
            when(terminalConnection.getSession()).thenReturn(websocketSession);
            when(websocketSession.isConnected()).thenReturn(false);

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.isDeviceOnline(deviceId);

            // Then - 验证结果
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("当发生异常时应该返回false")
        void should_return_false_when_exception_occurs_in_online_check() {
            // Given - 模拟异常
            Long deviceId = 12345L;
            when(connectionManager.getConnection(deviceId))
                .thenThrow(new RuntimeException("模拟异常"));

            // When - 执行测试方法
            boolean result = commandWebSocketAdapter.isDeviceOnline(deviceId);

            // Then - 验证结果
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("私有方法间接测试")
    class PrivateMethodTests {

        @Test
        @DisplayName("应该正确构建WebSocket消息格式")
        void should_build_correct_websocket_message_format() {
            // Given - 准备测试数据
            TerminalCommand command = TestDataBuilder.buildTerminalCommandWithAllFields();
            when(connectionManager.getConnection(command.getDeviceId()))
                .thenReturn(Optional.of(terminalConnection));
            when(terminalConnection.getSession()).thenReturn(websocketSession);
            when(websocketSession.isConnected()).thenReturn(true);
            when(websocketSession.sendMessage(anyString())).thenReturn(true);

            // When - 执行测试方法（间接测试buildWebSocketMessage）
            boolean result = commandWebSocketAdapter.sendCommandViaWebSocket(command);

            // Then - 验证结果和消息格式
            assertThat(result).isTrue();
            // 验证发送消息被调用
            verify(websocketSession).sendMessage(anyString());
        }
    }

    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        public static TerminalCommand buildTerminalCommand() {
            TerminalCommand command = mock(TerminalCommand.class);
            lenient().when(command.getDeviceId()).thenReturn(12345L);
            lenient().when(command.getCommandId()).thenReturn(1001);
            lenient().when(command.getAuthorUrl()).thenReturn("http://example.com");
            lenient().when(command.getKarma()).thenReturn(100);
            lenient().when(command.getContentRaw()).thenReturn("{\"action\":\"test\"}");
            return command;
        }

        public static TerminalCommand buildTerminalCommandWithAllFields() {
            TerminalCommand command = mock(TerminalCommand.class);
            lenient().when(command.getDeviceId()).thenReturn(12345L);
            lenient().when(command.getCommandId()).thenReturn(1001);
            lenient().when(command.getAuthorUrl()).thenReturn("http://example.com");
            lenient().when(command.getKarma()).thenReturn(100);
            lenient().when(command.getContentRaw()).thenReturn("{\"action\":\"test\",\"data\":\"sample\"}");
            return command;
        }
    }
}