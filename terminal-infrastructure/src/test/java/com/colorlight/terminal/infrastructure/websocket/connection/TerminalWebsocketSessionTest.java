package com.colorlight.terminal.infrastructure.websocket.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TerminalWebsocketSession 单元测试
 * 测试WebSocket会话管理的核心功能
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("终端WebSocket会话测试")
class TerminalWebsocketSessionTest {

    @Mock
    private Channel mockChannel;

    @Mock
    private ChannelFuture mockChannelFuture;

    private TerminalWebsocketSession session;

    @BeforeEach
    void setUp() {
        Long deviceId = 1001L;
        String sessionId = "TEST_SESSION_001";
        String clientIp = "192.168.1.100";
        session = TerminalWebsocketSession.builder()
                .sessionId(sessionId)
                .deviceId(deviceId)
                .nettyChannel(mockChannel)
                .connectTime(System.currentTimeMillis())
                .clientIp(clientIp)
                .build();
    }

    @Nested
    @DisplayName("WebSocket会话构建测试")
    class SessionBuildingTests {

        @Test
        @DisplayName("应该通过Builder成功创建会话对象")
        void should_create_session_with_builder_successfully() {
            // Given & When - 通过Builder创建会话
            Long testDeviceId = 2001L;
            String testSessionId = "TEST_SESSION_002";
            String testClientIp = "192.168.1.200";
            Long connectTime = System.currentTimeMillis();

            TerminalWebsocketSession testSession = TerminalWebsocketSession.builder()
                    .sessionId(testSessionId)
                    .deviceId(testDeviceId)
                    .nettyChannel(mockChannel)
                    .connectTime(connectTime)
                    .clientIp(testClientIp)
                    .build();

            // Then - 验证对象属性正确设置
            assertThat(testSession.getSessionId()).isEqualTo(testSessionId);
            assertThat(testSession.getDeviceId()).isEqualTo(testDeviceId);
            assertThat(testSession.getNettyChannel()).isEqualTo(mockChannel);
            assertThat(testSession.getConnectTime()).isEqualTo(connectTime);
            assertThat(testSession.getClientIp()).isEqualTo(testClientIp);
        }

        @Test
        @DisplayName("应该允许创建部分字段为null的会话对象")
        void should_allow_null_fields_in_session() {
            // Given & When - 创建部分字段为null的会话
            TerminalWebsocketSession nullFieldSession = TerminalWebsocketSession.builder()
                    .sessionId("NULL_TEST")
                    .deviceId(3001L)
                    .nettyChannel(null)
                    .connectTime(null)
                    .clientIp(null)
                    .build();

            // Then - 验证null字段被正确处理
            assertThat(nullFieldSession.getNettyChannel()).isNull();
            assertThat(nullFieldSession.getConnectTime()).isNull();
            assertThat(nullFieldSession.getClientIp()).isNull();
        }
    }

    @Nested
    @DisplayName("连接状态检查测试")
    class ConnectionStatusTests {

        @Test
        @DisplayName("当Channel活跃时应该返回连接状态为true")
        void should_return_true_when_channel_is_active() {
            // Given - Channel处于活跃状态
            when(mockChannel.isActive()).thenReturn(true);

            // When - 检查连接状态
            boolean isConnected = session.isConnected();

            // Then - 验证返回true
            assertThat(isConnected).isTrue();
            verify(mockChannel).isActive();
        }

        @Test
        @DisplayName("当Channel不活跃时应该返回连接状态为false")
        void should_return_false_when_channel_is_inactive() {
            // Given - Channel处于不活跃状态
            when(mockChannel.isActive()).thenReturn(false);

            // When - 检查连接状态
            boolean isConnected = session.isConnected();

            // Then - 验证返回false
            assertThat(isConnected).isFalse();
            verify(mockChannel).isActive();
        }

        @Test
        @DisplayName("当Channel为null时应该返回连接状态为false")
        void should_return_false_when_channel_is_null() {
            // Given - 创建Channel为null的会话
            TerminalWebsocketSession nullChannelSession = TerminalWebsocketSession.builder()
                    .sessionId("NULL_CHANNEL_TEST")
                    .deviceId(4001L)
                    .nettyChannel(null)
                    .build();

            // When - 检查连接状态
            boolean isConnected = nullChannelSession.isConnected();

            // Then - 验证返回false
            assertThat(isConnected).isFalse();
        }
    }

    @Nested
    @DisplayName("连接持续时间测试")
    class ConnectionDurationTests {

        @Test
        @DisplayName("应该正确计算连接持续时间")
        void should_calculate_connection_duration_correctly() {
            // Given - 设置连接时间为1秒前
            Long connectTime = System.currentTimeMillis() - 1000;
            TerminalWebsocketSession timedSession = TerminalWebsocketSession.builder()
                    .sessionId("TIMED_SESSION")
                    .deviceId(5001L)
                    .connectTime(connectTime)
                    .build();

            // When - 获取连接持续时间
            long duration = timedSession.getConnectionDuration();

            // Then - 验证持续时间大于等于1000毫秒
            assertThat(duration).isGreaterThanOrEqualTo(1000L);
        }

        @Test
        @DisplayName("当连接时间为null时应该返回0")
        void should_return_zero_when_connect_time_is_null() {
            // Given - 连接时间为null的会话
            TerminalWebsocketSession nullTimeSession = TerminalWebsocketSession.builder()
                    .sessionId("NULL_TIME_SESSION")
                    .deviceId(6001L)
                    .connectTime(null)
                    .build();

            // When - 获取连接持续时间
            long duration = nullTimeSession.getConnectionDuration();

            // Then - 验证返回0
            assertThat(duration).isZero();
        }
    }

    @Nested
    @DisplayName("消息发送测试")
    class MessageSendingTests {

        @Test
        @DisplayName("当连接活跃时应该成功发送消息")
        void should_send_message_successfully_when_connected() {
            // Given - 设置Channel为活跃状态
            when(mockChannel.isActive()).thenReturn(true);
            when(mockChannel.writeAndFlush(any(TextWebSocketFrame.class))).thenReturn(mockChannelFuture);

            String testMessage = "{\"type\":\"test\",\"data\":\"hello\"}";

            // When - 发送消息
            boolean result = session.sendMessage(testMessage);

            // Then - 验证消息发送成功
            assertThat(result).isTrue();

            // 验证Channel方法调用
            verify(mockChannel).isActive();
            
            ArgumentCaptor<TextWebSocketFrame> frameCaptor = ArgumentCaptor.forClass(TextWebSocketFrame.class);
            verify(mockChannel).writeAndFlush(frameCaptor.capture());
            
            TextWebSocketFrame capturedFrame = frameCaptor.getValue();
            assertThat(capturedFrame.text()).isEqualTo(testMessage);
        }

        @Test
        @DisplayName("当连接不活跃时应该发送消息失败")
        void should_fail_to_send_message_when_not_connected() {
            // Given - 设置Channel为不活跃状态
            when(mockChannel.isActive()).thenReturn(false);

            String testMessage = "{\"type\":\"test\",\"data\":\"hello\"}";

            // When - 发送消息
            boolean result = session.sendMessage(testMessage);

            // Then - 验证消息发送失败
            assertThat(result).isFalse();

            // 验证没有调用writeAndFlush
            verify(mockChannel, never()).writeAndFlush(any());
        }

        @Test
        @DisplayName("当Channel写入异常时应该发送消息失败")
        void should_fail_to_send_message_when_channel_throws_exception() {
            // Given - 设置Channel活跃，但writeAndFlush抛异常
            when(mockChannel.isActive()).thenReturn(true);
            when(mockChannel.writeAndFlush(any(TextWebSocketFrame.class)))
                    .thenThrow(new RuntimeException("Channel write error"));

            String testMessage = "{\"type\":\"test\",\"data\":\"error\"}";

            // When - 发送消息
            boolean result = session.sendMessage(testMessage);

            // Then - 验证消息发送失败
            assertThat(result).isFalse();
            verify(mockChannel).writeAndFlush(any(TextWebSocketFrame.class));
        }

        @Test
        @DisplayName("当Channel为null时应该发送消息失败")
        void should_fail_to_send_message_when_channel_is_null() {
            // Given - 创建Channel为null的会话
            TerminalWebsocketSession nullChannelSession = TerminalWebsocketSession.builder()
                    .sessionId("NULL_CHANNEL_SEND_TEST")
                    .deviceId(7001L)
                    .nettyChannel(null)
                    .build();

            String testMessage = "{\"type\":\"test\",\"data\":\"null_channel\"}";

            // When - 发送消息
            boolean result = nullChannelSession.sendMessage(testMessage);

            // Then - 验证消息发送失败
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("连接关闭测试")
    class ConnectionCloseTests {

        @Test
        @DisplayName("当Channel活跃时应该成功关闭连接")
        void should_close_connection_successfully_when_channel_is_active() {
            // Given - 设置Channel为活跃状态
            when(mockChannel.isActive()).thenReturn(true);
            when(mockChannel.close()).thenReturn(mockChannelFuture);

            // When - 关闭连接
            assertThatCode(() -> session.close()).doesNotThrowAnyException();

            // Then - 验证调用了Channel的close方法
            verify(mockChannel).isActive();
            verify(mockChannel).close();
        }

        @Test
        @DisplayName("当Channel不活跃时应该跳过关闭操作")
        void should_skip_close_when_channel_is_inactive() {
            // Given - 设置Channel为不活跃状态
            when(mockChannel.isActive()).thenReturn(false);

            // When - 关闭连接
            assertThatCode(() -> session.close()).doesNotThrowAnyException();

            // Then - 验证检查了Channel状态但没有关闭
            verify(mockChannel).isActive();
            verify(mockChannel, never()).close();
        }

        @Test
        @DisplayName("当Channel为null时应该不抛出异常")
        void should_not_throw_exception_when_channel_is_null() {
            // Given - 创建Channel为null的会话
            TerminalWebsocketSession nullChannelSession = TerminalWebsocketSession.builder()
                    .sessionId("NULL_CHANNEL_CLOSE_TEST")
                    .deviceId(8001L)
                    .nettyChannel(null)
                    .build();

            // When & Then - 关闭连接不应该抛异常
            assertThatCode(nullChannelSession::close).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("边界条件和异常处理测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应该正确处理空字符串消息")
        void should_handle_empty_string_message() {
            // Given - 设置Channel为活跃状态
            when(mockChannel.isActive()).thenReturn(true);
            when(mockChannel.writeAndFlush(any(TextWebSocketFrame.class))).thenReturn(mockChannelFuture);

            // When - 发送空字符串消息
            boolean result = session.sendMessage("");

            // Then - 验证能够发送空字符串
            assertThat(result).isTrue();
            verify(mockChannel).writeAndFlush(any(TextWebSocketFrame.class));
        }

        @Test
        @DisplayName("应该正确处理很长的消息")
        void should_handle_long_message() {
            // Given - 设置Channel为活跃状态和很长的消息
            when(mockChannel.isActive()).thenReturn(true);
            when(mockChannel.writeAndFlush(any(TextWebSocketFrame.class))).thenReturn(mockChannelFuture);

            String longMessage = "a".repeat(10000); // 10KB消息

            // When - 发送长消息
            boolean result = session.sendMessage(longMessage);

            // Then - 验证能够发送长消息
            assertThat(result).isTrue();
            
            ArgumentCaptor<TextWebSocketFrame> frameCaptor = ArgumentCaptor.forClass(TextWebSocketFrame.class);
            verify(mockChannel).writeAndFlush(frameCaptor.capture());
            
            assertThat(frameCaptor.getValue().text()).isEqualTo(longMessage);
        }

        @Test
        @DisplayName("应该正确处理特殊字符消息")
        void should_handle_special_characters_message() {
            // Given - 设置Channel为活跃状态
            when(mockChannel.isActive()).thenReturn(true);
            when(mockChannel.writeAndFlush(any(TextWebSocketFrame.class))).thenReturn(mockChannelFuture);

            String specialMessage = "{\"unicode\":\"测试中文\",\"emoji\":\"😀\",\"symbols\":\"!@#$%^&*()\"}";

            // When - 发送包含特殊字符的消息
            boolean result = session.sendMessage(specialMessage);

            // Then - 验证能够发送特殊字符消息
            assertThat(result).isTrue();
            
            ArgumentCaptor<TextWebSocketFrame> frameCaptor = ArgumentCaptor.forClass(TextWebSocketFrame.class);
            verify(mockChannel).writeAndFlush(frameCaptor.capture());
            
            assertThat(frameCaptor.getValue().text()).isEqualTo(specialMessage);
        }
    }
}