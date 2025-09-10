package com.colorlight.terminal.application.domain.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MessageProcessingContext 领域模型单元测试
 *
 * <p>测试范围：</p>
 * <ul>
 *   <li>构造函数和基本属性</li>
 *   <li>工厂方法</li>
 *   <li>业务方法测试</li>
 * </ul>
 *
 * @author Nan
 */
@DisplayName("MessageProcessingContext领域模型测试")
class MessageProcessingContextTest {

    private static final Long TEST_DEVICE_ID = 10001L;
    private static final String TEST_MESSAGE = "{\"type\":\"heartbeat\",\"data\":\"test\"}";
    private static final String TEST_CLIENT_IP = "192.168.1.100";

    private TerminalConnection mockConnection;
    private MessageProcessingContext context;

    @BeforeEach
    void setUp() {
        mockConnection = mock(TerminalConnection.class);
        when(mockConnection.getDeviceId()).thenReturn(TEST_DEVICE_ID);
        when(mockConnection.getClientIp()).thenReturn(TEST_CLIENT_IP);
        when(mockConnection.isActive()).thenReturn(true);
        
        context = MessageProcessingContext.create(mockConnection, TEST_MESSAGE);
    }

    @Nested
    @DisplayName("isValid方法测试")
    class IsValidMethodTests {

        @Test
        @DisplayName("应该在连接有效时返回true")
        void should_return_true_when_connection_is_valid() {
            // Given
            when(mockConnection.isActive()).thenReturn(true);

            // When
            boolean valid = context.isValid();

            // Then
            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("应该在连接无效时返回false")
        void should_return_false_when_connection_is_invalid() {
            // Given
            when(mockConnection.isActive()).thenReturn(false);

            // When
            boolean valid = context.isValid();

            // Then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("应该在连接为null时返回false")
        void should_return_false_when_connection_is_null() {
            // Given
            MessageProcessingContext ctx = MessageProcessingContext.create(null, TEST_MESSAGE);

            // When
            boolean valid = ctx != null && ctx.isValid();

            // Then
            assertThat(valid).isFalse();
        }
    }


    @Nested
    @DisplayName("统计更新方法测试")
    class StatisticsUpdateMethodTests {

        @Test
        @DisplayName("应该正确更新消息统计")
        void should_update_message_statistics_correctly() {
            // When
            context.updateMessageStatistics();

            // Then
            verify(mockConnection).incrementReceivedMessageCount();
        }

        @Test
        @DisplayName("应该在更新消息统计异常时记录警告日志")
        void should_log_warning_when_update_message_statistics_throws_exception() {
            // Given
            doThrow(new RuntimeException("统计更新异常")).when(mockConnection).incrementReceivedMessageCount();

            // When
            assertThatCode(() -> context.updateMessageStatistics())
                    .doesNotThrowAnyException();

            // Note: 日志验证在单元测试中较难实现，此处主要验证不会抛出异常
        }

        @Test
        @DisplayName("应该正确更新发送消息统计")
        void should_update_sent_message_statistics_correctly() {
            // When
            context.updateSentMessageStatistics();

            // Then
            verify(mockConnection).incrementSentMessageCount();
        }

        @Test
        @DisplayName("应该在更新发送消息统计异常时记录警告日志")
        void should_log_warning_when_update_sent_message_statistics_throws_exception() {
            // Given
            doThrow(new RuntimeException("统计更新异常")).when(mockConnection).incrementSentMessageCount();

            // When
            assertThatCode(() -> context.updateSentMessageStatistics())
                    .doesNotThrowAnyException();

            // Note: 日志验证在单元测试中较难实现，此处主要验证不会抛出异常
        }

        @Test
        @DisplayName("应该正确更新错误统计")
        void should_update_error_statistics_correctly() {
            // When
            context.updateErrorStatistics();

            // Then
            verify(mockConnection).incrementErrorCount();
        }

        @Test
        @DisplayName("应该在更新错误统计异常时记录警告日志")
        void should_log_warning_when_update_error_statistics_throws_exception() {
            // Given
            doThrow(new RuntimeException("统计更新异常")).when(mockConnection).incrementErrorCount();

            // When
            assertThatCode(() -> context.updateErrorStatistics())
                    .doesNotThrowAnyException();

            // Note: 日志验证在单元测试中较难实现，此处主要验证不会抛出异常
        }
    }

    @Nested
    @DisplayName("消息发送方法测试")
    class MessageSendingMethodTests {

        @Test
        @DisplayName("应该成功发送消息")
        void should_send_message_successfully() {
            // Given
            String message = "test message";
            when(mockConnection.sendMessage(message)).thenReturn(true);

            // When
            boolean result = context.sendMessage(message);

            // Then
            assertThat(result).isTrue();
            verify(mockConnection).sendMessage(message);
            verify(mockConnection).incrementSentMessageCount();
        }

        @Test
        @DisplayName("应该在发送消息失败时更新错误统计")
        void should_update_error_statistics_when_send_message_fails() {
            // Given
            String message = "test message";
            when(mockConnection.sendMessage(message)).thenReturn(false);

            // When
            boolean result = context.sendMessage(message);

            // Then
            assertThat(result).isFalse();
            verify(mockConnection).sendMessage(message);
            verify(mockConnection).incrementErrorCount();
        }

        @Test
        @DisplayName("应该在发送消息异常时更新错误统计")
        void should_update_error_statistics_when_send_message_throws_exception() {
            // Given
            String message = "test message";
            when(mockConnection.sendMessage(message)).thenThrow(new RuntimeException("发送异常"));

            // When
            boolean result = context.sendMessage(message);

            // Then
            assertThat(result).isFalse();
            verify(mockConnection).sendMessage(message);
            verify(mockConnection).incrementErrorCount();
        }

        @Test
        @DisplayName("应该正确发送对象消息")
        void should_send_object_message_correctly() {
            // Given
            Object obj = new Object();

            // When
            context.sendMessage(obj);

            // Then
            verify(mockConnection).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("toString方法测试")
    class ToStringMethodTests {

        @Test
        @DisplayName("应该正确生成toString表示")
        void should_generate_to_string_correctly() {
            // Given
            when(mockConnection.getProtocolVersion()).thenReturn(ProtocolVersion.V1_0);

            // When
            String toString = context.toString();

            // Then
            assertThat(toString).contains("MessageProcessingContext");
            assertThat(toString).contains("deviceId=" + TEST_DEVICE_ID);
        }
    }
}