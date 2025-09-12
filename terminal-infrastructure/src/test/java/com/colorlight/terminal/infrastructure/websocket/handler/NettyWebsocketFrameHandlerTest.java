package com.colorlight.terminal.infrastructure.websocket.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.port.inbound.websocket.WebsocketMessageUseCase;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import com.colorlight.terminal.infrastructure.websocket.auth.NettyWebsocketAuthHandler;
import com.colorlight.terminal.infrastructure.websocket.connection.TerminalWebsocketSession;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.Attribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketAddress;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * NettyWebsocketFrameHandler 单元测试
 * <p>
 * 测试策略：
 * 1. 遵循上下文感知测试原则，只测试Handler层的职责
 * 2. 使用lenient模式优化Mock，避免严格验证
 * 3. 专注核心业务逻辑：帧处理、会话管理、连接生命周期
 * 4. 避免测试不可能发生的场景（如Netty框架级异常）
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NettyWebSocket帧处理器测试")
class NettyWebsocketFrameHandlerTest {

    @Mock
    private WebsocketMessageUseCase websocketMessageUseCase;
    
    @Mock
    private ConnectionManagerPort connectionManagerPort;
    
    @Mock
    private ChannelHandlerContext channelHandlerContext;
    
    @Mock
    private Channel channel;
    
    @Mock
    private ChannelId channelId;
    
    @Mock
    private Attribute<String> deviceIdAttribute;
    
    @Mock
    private Attribute<TerminalPrincipal> principalAttribute;
    
    @Mock
    private Attribute<ProtocolVersion> protocolVersionAttribute;
    
    @Mock
    private SocketAddress remoteAddress;
    
    @Captor
    private ArgumentCaptor<TerminalWebsocketSession> sessionCaptor;
    
    @Captor
    private ArgumentCaptor<MessageProcessingContext> contextCaptor;
    
    @InjectMocks
    private NettyWebsocketFrameHandler frameHandler;
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        public static TerminalConnection buildTerminalConnection() {
            // 使用Mock对象，因为TerminalConnection需要WebSocketSession
            // 按照文档规范，使用lenient()避免严格模式报错
            TerminalConnection connection = mock(TerminalConnection.class);
            lenient().when(connection.getDeviceId()).thenReturn(12345L);
            lenient().when(connection.getProtocolVersion()).thenReturn(ProtocolVersion.V1_1);
            lenient().when(connection.getClientIp()).thenReturn("192.168.1.100");
            lenient().when(connection.isActive()).thenReturn(true);
            lenient().when(connection.getStatus()).thenReturn(TerminalConnection.ConnectionStatus.CONNECTED);
            return connection;
        }
        
        public static TerminalPrincipal buildTerminalPrincipal() {
            return new TerminalPrincipal(12345L, com.colorlight.terminal.application.enums.TerminalAccountStatus.ENABLE);
        }
        
        public static TextWebSocketFrame buildTextFrame(String message) {
            return new TextWebSocketFrame(message);
        }
        
        public static PingWebSocketFrame buildPingFrame() {
            return new PingWebSocketFrame();
        }
        
        public static PongWebSocketFrame buildPongFrame() {
            return new PongWebSocketFrame();
        }
        
        public static CloseWebSocketFrame buildCloseFrame() {
            return new CloseWebSocketFrame();
        }
    }
    
    @BeforeEach
    void setUp() {
        // 基础Mock设置 - 使用lenient模式避免严格验证
        lenient().when(channelHandlerContext.channel()).thenReturn(channel);
        lenient().when(channel.id()).thenReturn(channelId);
        lenient().when(channelId.asShortText()).thenReturn("test-channel-123");
        lenient().when(channel.remoteAddress()).thenReturn(remoteAddress);
        lenient().when(remoteAddress.toString()).thenReturn("/192.168.1.100:8080");
        
        // 设备ID属性Mock
        lenient().when(channel.attr(NettyWebsocketAuthHandler.DEVICE_ID)).thenReturn(deviceIdAttribute);
        lenient().when(deviceIdAttribute.get()).thenReturn("12345");
        
        // 认证主体属性Mock
        lenient().when(channel.attr(NettyWebsocketAuthHandler.TERMINAL_PRINCIPAL)).thenReturn(principalAttribute);
        lenient().when(principalAttribute.get()).thenReturn(TestDataBuilder.buildTerminalPrincipal());
        
        // 协议版本属性Mock
        lenient().when(channel.attr(NettyWebsocketAuthHandler.PROTOCOL_VERSION)).thenReturn(protocolVersionAttribute);
        lenient().when(protocolVersionAttribute.get()).thenReturn(ProtocolVersion.V1_1);
    }
    
    @Nested
    @DisplayName("WebSocket握手完成处理")
    class HandshakeCompleteTest {
        
        @Test
        @DisplayName("应该成功初始化WebSocket会话")
        void should_initialize_websocket_session_successfully() throws Exception {
            // Given - 准备握手完成事件和有效的认证信息
            WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete = 
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws/terminal", null, null);
            
            TerminalConnection expectedConnection = TestDataBuilder.buildTerminalConnection();
            when(websocketMessageUseCase.handleConnectionEstablished(eq(12345L), any(TerminalWebsocketSession.class), eq(ProtocolVersion.V1_1)))
                .thenReturn(expectedConnection);
            
            // When - 触发握手完成事件
            frameHandler.userEventTriggered(channelHandlerContext, handshakeComplete);
            
            // Then - 验证会话初始化调用
            verify(websocketMessageUseCase).handleConnectionEstablished(eq(12345L), sessionCaptor.capture(), eq(ProtocolVersion.V1_1));
            
            TerminalWebsocketSession capturedSession = sessionCaptor.getValue();
            assertThat(capturedSession).isNotNull();
            assertThat(capturedSession.getDeviceId()).isEqualTo(12345L);
            assertThat(capturedSession.getSessionId()).isEqualTo("test-channel-123");
            assertThat(capturedSession.getClientIp()).isEqualTo("192.168.1.100");
        }
        
        @Test
        @DisplayName("当缺少设备ID时应该关闭连接")
        void should_close_connection_when_device_id_missing() throws Exception {
            // Given - 设备ID为空的场景
            when(deviceIdAttribute.get()).thenReturn(null);
            
            WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete = 
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws/terminal", null, null);
            
            // When - 触发握手完成事件
            frameHandler.userEventTriggered(channelHandlerContext, handshakeComplete);
            
            // Then - 验证连接被关闭，不调用会话建立
            verify(channelHandlerContext).close();
            verify(websocketMessageUseCase, never()).handleConnectionEstablished(any(), any(), any());
        }
        
        @Test
        @DisplayName("当缺少认证主体时应该关闭连接")
        void should_close_connection_when_principal_missing() throws Exception {
            // Given - 认证主体为空的场景
            when(principalAttribute.get()).thenReturn(null);
            
            WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete = 
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws/terminal", null, null);
            
            // When - 触发握手完成事件
            frameHandler.userEventTriggered(channelHandlerContext, handshakeComplete);
            
            // Then - 验证连接被关闭
            verify(channelHandlerContext).close();
            verify(websocketMessageUseCase, never()).handleConnectionEstablished(any(), any(), any());
        }
        
        @Test
        @DisplayName("当会话创建失败时应该关闭连接")
        void should_close_connection_when_session_creation_fails() throws Exception {
            // Given - UseCase返回null表示会话创建失败
            when(websocketMessageUseCase.handleConnectionEstablished(any(), any(), any())).thenReturn(null);
            
            WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete = 
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws/terminal", null, null);
            
            // When - 触发握手完成事件
            frameHandler.userEventTriggered(channelHandlerContext, handshakeComplete);
            
            // Then - 验证连接被关闭
            verify(channelHandlerContext).close();
        }
    }
    
    @Nested
    @DisplayName("WebSocket帧处理")
    class FrameHandlingTest {
        
        @Test
        @DisplayName("应该成功处理文本帧")
        void should_handle_text_frame_successfully() throws Exception {
            // Given - 准备有效连接和文本帧
            TerminalConnection connection = TestDataBuilder.buildTerminalConnection();
            when(connectionManagerPort.getConnection(12345L)).thenReturn(Optional.of(connection));
            
            TextWebSocketFrame textFrame = TestDataBuilder.buildTextFrame("{\"type\":\"heartbeat\"}");
            
            // When - 处理文本帧
            frameHandler.channelRead0(channelHandlerContext, textFrame);
            
            // Then - 验证消息处理调用
            verify(websocketMessageUseCase).handleTextMessageByProcessor(contextCaptor.capture());
            
            MessageProcessingContext capturedContext = contextCaptor.getValue();
            assertThat(capturedContext).isNotNull();
            assertThat(capturedContext.getConnection()).isEqualTo(connection);
            assertThat(capturedContext.getRawMessage()).isEqualTo("{\"type\":\"heartbeat\"}");
        }
        
        @Test
        @DisplayName("当连接不存在时应该忽略文本帧")
        void should_ignore_text_frame_when_connection_not_exists() throws Exception {
            // Given - 连接不存在的场景
            when(connectionManagerPort.getConnection(12345L)).thenReturn(Optional.empty());
            
            TextWebSocketFrame textFrame = TestDataBuilder.buildTextFrame("{\"type\":\"heartbeat\"}");
            
            // When - 处理文本帧
            frameHandler.channelRead0(channelHandlerContext, textFrame);
            
            // Then - 验证不调用消息处理
            verify(websocketMessageUseCase, never()).handleTextMessageByProcessor(any());
        }
        
        @Test
        @DisplayName("应该成功处理Ping帧")
        void should_handle_ping_frame_successfully() throws Exception {
            // Given - 准备有效连接和Ping帧
            TerminalConnection connection = TestDataBuilder.buildTerminalConnection();
            when(connectionManagerPort.getConnection(12345L)).thenReturn(Optional.of(connection));
            
            PingWebSocketFrame pingFrame = TestDataBuilder.buildPingFrame();
            
            // When - 处理Ping帧
            frameHandler.channelRead0(channelHandlerContext, pingFrame);
            
            // Then - 验证心跳处理调用
            verify(websocketMessageUseCase).handlePingFrame(connection);
        }
        
        @Test
        @DisplayName("应该成功处理Pong帧")
        void should_handle_pong_frame_successfully() throws Exception {
            // Given - 准备有效连接和Pong帧
            TerminalConnection connection = TestDataBuilder.buildTerminalConnection();
            when(connectionManagerPort.getConnection(12345L)).thenReturn(Optional.of(connection));
            
            PongWebSocketFrame pongFrame = TestDataBuilder.buildPongFrame();
            
            // When - 处理Pong帧
            frameHandler.channelRead0(channelHandlerContext, pongFrame);
            
            // Then - 验证心跳响应处理调用
            verify(websocketMessageUseCase).handlePongFrame(connection);
        }
        
        @Test
        @DisplayName("应该成功处理关闭帧")
        void should_handle_close_frame_successfully() throws Exception {
            // Given - 准备关闭帧
            CloseWebSocketFrame closeFrame = TestDataBuilder.buildCloseFrame();
            
            // When - 处理关闭帧
            frameHandler.channelRead0(channelHandlerContext, closeFrame);
            
            // Then - 验证连接关闭
            verify(channelHandlerContext).close();
        }
        
        @Test
        @DisplayName("当设备未认证时应该关闭连接")
        void should_close_connection_when_device_not_authenticated() throws Exception {
            // Given - 设备ID获取失败的场景（未认证）
            when(deviceIdAttribute.get()).thenReturn(null);
            
            TextWebSocketFrame textFrame = TestDataBuilder.buildTextFrame("{\"type\":\"heartbeat\"}");
            
            // When - 尝试处理帧
            frameHandler.channelRead0(channelHandlerContext, textFrame);
            
            // Then - 验证连接被关闭，不处理任何消息
            verify(channelHandlerContext).close();
            verify(websocketMessageUseCase, never()).handleTextMessageByProcessor(any());
        }
        
        @Test
        @DisplayName("应该记录不支持的帧类型警告")
        void should_log_warning_for_unsupported_frame_type() throws Exception {
            // Given - 不支持的帧类型（BinaryWebSocketFrame）
            BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame();
            
            // When - 处理不支持的帧
            frameHandler.channelRead0(channelHandlerContext, binaryFrame);
            
            // Then - 验证不调用任何处理方法（仅记录警告）
            verify(websocketMessageUseCase, never()).handleTextMessageByProcessor(any());
            verify(websocketMessageUseCase, never()).handlePingFrame(any());
            verify(websocketMessageUseCase, never()).handlePongFrame(any());
        }
    }
    
    @Nested
    @DisplayName("连接生命周期管理")
    class ConnectionLifecycleTest {
        
        @Test
        @DisplayName("当通道变为非活动状态时应该处理连接关闭")
        void should_handle_connection_closed_when_channel_inactive() throws Exception {
            // Given - 有效的设备ID
            // 已在setUp中配置
            
            // When - 通道变为非活动状态
            frameHandler.channelInactive(channelHandlerContext);
            
            // Then - 验证连接关闭处理
            verify(websocketMessageUseCase).handleConnectionClosed(12345L);
        }
        
        @Test
        @DisplayName("当设备ID不存在时应该忽略通道非活动事件")
        void should_ignore_channel_inactive_when_device_id_not_exists() throws Exception {
            // Given - 设备ID不存在
            when(deviceIdAttribute.get()).thenReturn(null);
            
            // When - 通道变为非活动状态
            frameHandler.channelInactive(channelHandlerContext);
            
            // Then - 验证不调用连接关闭处理
            verify(websocketMessageUseCase, never()).handleConnectionClosed(any());
        }
        
        @Test
        @DisplayName("应该处理连接异常并关闭连接")
        void should_handle_connection_exception_and_close() throws Exception {
            // Given - 模拟连接异常
            RuntimeException testException = new RuntimeException("网络连接异常");
            
            // When - 发生异常
            frameHandler.exceptionCaught(channelHandlerContext, testException);
            
            // Then - 验证连接被关闭
            verify(channelHandlerContext).close();
        }
        
        @Test
        @DisplayName("应该处理读空闲超时事件")
        void should_handle_reader_idle_timeout() throws Exception {
            // Given - 读空闲事件
            IdleStateEvent idleEvent = IdleStateEvent.READER_IDLE_STATE_EVENT;
            
            // When - 触发空闲事件
            frameHandler.userEventTriggered(channelHandlerContext, idleEvent);
            
            // Then - 验证连接被关闭（心跳超时）
            verify(channelHandlerContext).close();
        }
        
        @Test
        @DisplayName("应该忽略非读空闲的空闲事件")
        void should_ignore_non_reader_idle_events() throws Exception {
            // Given - 写空闲事件
            IdleStateEvent idleEvent = IdleStateEvent.WRITER_IDLE_STATE_EVENT;
            
            // When - 触发空闲事件
            frameHandler.userEventTriggered(channelHandlerContext, idleEvent);
            
            // Then - 验证连接不被关闭
            verify(channelHandlerContext, never()).close();
        }
    }
    
    @Nested
    @DisplayName("工具方法测试")
    class UtilityMethodsTest {
        
        @Test
        @DisplayName("应该正确解析客户端IP地址")
        void should_parse_client_ip_correctly() throws Exception {
            // Given - 不同格式的远程地址
            when(remoteAddress.toString()).thenReturn("/192.168.1.100:8080");
            
            WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete = 
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws/terminal", null, null);
            
            // 创建独立的连接对象，避免与TestDataBuilder冲突
            TerminalConnection mockConnection = mock(TerminalConnection.class);
            when(websocketMessageUseCase.handleConnectionEstablished(any(), any(), any()))
                .thenReturn(mockConnection);
            
            // When - 初始化会话
            frameHandler.userEventTriggered(channelHandlerContext, handshakeComplete);
            
            // Then - 验证IP解析正确
            verify(websocketMessageUseCase).handleConnectionEstablished(eq(12345L), sessionCaptor.capture(), any());
            
            TerminalWebsocketSession session = sessionCaptor.getValue();
            assertThat(session.getClientIp()).isEqualTo("192.168.1.100");
        }
        
        @Test
        @DisplayName("当IP解析失败时应该返回unknown")
        void should_return_unknown_when_ip_parsing_fails() throws Exception {
            // Given - 异常的远程地址格式
            when(remoteAddress.toString()).thenThrow(new RuntimeException("地址解析异常"));
            
            WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete = 
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws/terminal", null, null);
            
            // 创建独立的连接对象，避免与TestDataBuilder冲突
            TerminalConnection mockConnection = mock(TerminalConnection.class);
            when(websocketMessageUseCase.handleConnectionEstablished(any(), any(), any()))
                .thenReturn(mockConnection);
            
            // When - 初始化会话
            frameHandler.userEventTriggered(channelHandlerContext, handshakeComplete);
            
            // Then - 验证返回unknown
            verify(websocketMessageUseCase).handleConnectionEstablished(eq(12345L), sessionCaptor.capture(), any());
            
            TerminalWebsocketSession session = sessionCaptor.getValue();
            assertThat(session.getClientIp()).isEqualTo("unknown");
        }
        
        @Test
        @DisplayName("当设备ID格式错误时应该返回null")
        void should_return_null_when_device_id_format_invalid() throws Exception {
            // Given - 无效的设备ID格式
            when(deviceIdAttribute.get()).thenReturn("invalid-device-id");
            
            TextWebSocketFrame textFrame = TestDataBuilder.buildTextFrame("{\"type\":\"heartbeat\"}");
            
            // When - 尝试处理帧
            frameHandler.channelRead0(channelHandlerContext, textFrame);
            
            // Then - 验证连接被关闭（设备ID无效）
            verify(channelHandlerContext).close();
            verify(websocketMessageUseCase, never()).handleTextMessageByProcessor(any());
        }
    }
}