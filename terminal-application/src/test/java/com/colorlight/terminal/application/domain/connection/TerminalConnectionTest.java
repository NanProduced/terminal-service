package com.colorlight.terminal.application.domain.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * TerminalConnection 领域模型单元测试
 *
 * <p>测试范围：</p>
 * <ul>
 *   <li>构造函数和基本属性</li>
 *   <li>静态工厂方法</li>
 *   <li>计数器相关方法</li>
 *   <li>连接状态检查方法</li>
 *   <li>消息发送方法</li>
 * </ul>
 *
 * @author Nan
 */
@DisplayName("TerminalConnection领域模型测试")
class TerminalConnectionTest {

    private static final Long TEST_DEVICE_ID = 10001L;
    private static final String TEST_CLIENT_IP = "192.168.1.100";
    private static final String TEST_SESSION_ID = "session_123456";

    private WebSocketSession mockSession;
    private TerminalConnection connection;

    @BeforeEach
    void setUp() {
        mockSession = mock(WebSocketSession.class);
        when(mockSession.getDeviceId()).thenReturn(TEST_DEVICE_ID);
        when(mockSession.getClientIp()).thenReturn(TEST_CLIENT_IP);
        when(mockSession.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(mockSession.isConnected()).thenReturn(true);

        connection = TerminalConnection.create(TEST_DEVICE_ID, mockSession, ProtocolVersion.V1_0);
    }

    @Nested
    @DisplayName("计数器方法测试")
    class CounterMethodTests {

        @Test
        @DisplayName("应该正确获取初始计数器值")
        void should_get_initial_counter_values_correctly() {
            // When
            long sentCount = connection.getSentMessageCount();
            long receivedCount = connection.getReceivedMessageCount();
            long errorCount = connection.getErrorCount();

            // Then
            assertThat(sentCount).isZero();
            assertThat(receivedCount).isZero();
            assertThat(errorCount).isZero();
        }

        @Test
        @DisplayName("应该正确增加发送消息计数")
        void should_increment_sent_message_count_correctly() {
            // Given
            long initialSentCount = connection.getSentMessageCount();
            LocalDateTime originalActiveTime = connection.getLastActiveTime();
            connection.setLastActiveTime(originalActiveTime.minusSeconds(1));

            // When
            connection.incrementSentMessageCount();

            // Then
            assertThat(connection.getSentMessageCount()).isEqualTo(initialSentCount + 1);
            assertThat(connection.getLastActiveTime()).isAfter(originalActiveTime.minusSeconds(1));
        }

        @Test
        @DisplayName("应该正确增加接收消息计数")
        void should_increment_received_message_count_correctly() {
            // Given
            long initialReceivedCount = connection.getReceivedMessageCount();
            LocalDateTime originalActiveTime = connection.getLastActiveTime();
            connection.setLastActiveTime(originalActiveTime.minusSeconds(1));

            // When
            connection.incrementReceivedMessageCount();

            // Then
            assertThat(connection.getReceivedMessageCount()).isEqualTo(initialReceivedCount + 1);
            assertThat(connection.getLastActiveTime()).isAfter(originalActiveTime.minusSeconds(1));
        }

        @Test
        @DisplayName("应该正确增加错误计数")
        void should_increment_error_count_correctly() {
            // Given
            long initialErrorCount = connection.getErrorCount();
            LocalDateTime originalActiveTime = connection.getLastActiveTime();
            connection.setLastActiveTime(originalActiveTime.minusSeconds(1));

            // When
            connection.incrementErrorCount();

            // Then
            assertThat(connection.getErrorCount()).isEqualTo(initialErrorCount + 1);
            assertThat(connection.getLastActiveTime()).isAfter(originalActiveTime.minusSeconds(1));
        }
    }

    @Nested
    @DisplayName("连接状态检查方法测试")
    class ConnectionStatusCheckMethodTests {

        @Test
        @DisplayName("应该正确获取WebSocket会话")
        void should_get_websocket_session_correctly() {
            // When
            WebSocketSession session = connection.getWebSocketSession();

            // Then
            assertThat(session).isEqualTo(mockSession);
        }

        @Test
        @DisplayName("应该正确获取连接持续时间")
        void should_get_connection_duration_correctly() {
            // When
            long duration = connection.getConnectionDurationSeconds();

            // Then
            assertThat(duration).isNotNegative();
        }

        @Test
        @DisplayName("应该在connectTime为null时返回0作为连接持续时间")
        void should_return_zero_as_connection_duration_when_connect_time_is_null() {
            // Given
            connection.setConnectTime(null);

            // When
            long duration = connection.getConnectionDurationSeconds();

            // Then
            assertThat(duration).isZero();
        }

        @Test
        @DisplayName("应该正确获取空闲时间")
        void should_get_idle_time_correctly() {
            // When
            long idleTime = connection.getIdleTimeSeconds();

            // Then
            assertThat(idleTime).isNotNegative();
        }

        @Test
        @DisplayName("应该在lastActiveTime为null时返回0作为空闲时间")
        void should_return_zero_as_idle_time_when_last_active_time_is_null() {
            // Given
            connection.setLastActiveTime(null);

            // When
            long idleTime = connection.getIdleTimeSeconds();

            // Then
            assertThat(idleTime).isZero();
        }

        @Test
        @DisplayName("应该正确检查连接是否活跃")
        void should_check_connection_is_active_correctly() {
            // Given
            when(mockSession.isConnected()).thenReturn(true);

            // When
            boolean active = connection.isActive();

            // Then
            assertThat(active).isTrue();
        }

        @Test
        @DisplayName("应该在session为null时返回false作为活跃状态")
        void should_return_false_as_active_status_when_session_is_null() {
            // Given
            connection.setSession(null);

            // When
            boolean active = connection.isActive();

            // Then
            assertThat(active).isFalse();
        }

        @Test
        @DisplayName("应该在session未连接时返回false作为活跃状态")
        void should_return_false_as_active_status_when_session_is_not_connected() {
            // Given
            when(mockSession.isConnected()).thenReturn(false);

            // When
            boolean active = connection.isActive();

            // Then
            assertThat(active).isFalse();
        }
    }

    @Nested
    @DisplayName("消息发送方法测试")
    class MessageSendingMethodTests {

        @Test
        @DisplayName("应该成功发送字符串消息")
        void should_send_string_message_successfully() {
            // Given
            String message = "test message";
            when(mockSession.sendMessage(message)).thenReturn(true);

            // When
            boolean result = connection.sendMessage(message);

            // Then
            assertThat(result).isTrue();
            verify(mockSession).sendMessage(message);
        }

        @Test
        @DisplayName("应该在session为null时发送消息失败")
        void should_fail_to_send_message_when_session_is_null() {
            // Given
            connection.setSession(null);
            String message = "test message";

            // When
            boolean result = connection.sendMessage(message);

            // Then
            assertThat(result).isFalse();
            verify(mockSession, never()).sendMessage(message);
        }

        @Test
        @DisplayName("应该成功发送对象消息")
        void should_send_object_message_successfully() {
            // Given
            Object messageObj = new Object();
            when(mockSession.sendMessage(anyString())).thenReturn(true);

            // When
            boolean result = connection.sendMessage(messageObj);

            // Then
            assertThat(result).isTrue();
            verify(mockSession).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("内部枚举测试")
    class InnerEnumTests {

        @Test
        @DisplayName("应该正确包含所有连接状态")
        void should_contain_all_connection_statuses() {
            // When & Then
            assertThat(TerminalConnection.ConnectionStatus.values())
                    .containsExactlyInAnyOrder(
                            TerminalConnection.ConnectionStatus.CONNECTED,
                            TerminalConnection.ConnectionStatus.HEARTBEAT_TIMEOUT,
                            TerminalConnection.ConnectionStatus.DISCONNECTED,
                            TerminalConnection.ConnectionStatus.ERROR
                    );
        }
    }
}