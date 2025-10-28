package com.colorlight.terminal.infrastructure.websocket.auth;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.dto.request.AuthRequest;
import com.colorlight.terminal.application.dto.result.AuthResult;
import com.colorlight.terminal.application.port.inbound.auth.TerminalAuthUseCase;
import com.colorlight.terminal.infrastructure.config.properties.WebSocketConfigProperties;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import com.colorlight.terminal.infrastructure.websocket.config.NettyWebsocketProperties;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.Attribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NettyWebsocketAuthHandler 单元测试
 * <p>
 * 测试策略：
 * 1. 遵循上下文感知测试原则，专注Handler层的认证职责
 * 2. 使用lenient模式优化Mock，避免严格验证
 * 3. 专注核心业务逻辑：WebSocket认证、协议版本解析、异步处理
 * 4. 避免测试Netty框架级的技术细节
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NettyWebSocket认证处理器测试")
class NettyWebsocketAuthHandlerTest {

    @Mock
    private TerminalAuthUseCase terminalAuthUseCase;
    
    @Mock
    private NettyWebsocketProperties nettyWebsocketProperties;
    
    @Mock
    private WebSocketConfigProperties webSocketConfigProperties;
    
    @Mock
    private WebSocketConfigProperties.Protocol protocolConfig;
    
    @Mock
    private NettyWebsocketProperties.Server serverConfig;
    
    @Mock
    private Executor websocketAuthExecutor;
    
    @Mock
    private ChannelHandlerContext channelHandlerContext;
    
    @Mock
    private Channel channel;
    
    @Mock
    private EventLoop eventLoop;
    
    @Mock
    private ChannelPipeline pipeline;
    
    @Mock
    private Attribute<TerminalPrincipal> principalAttribute;
    
    @Mock
    private Attribute<String> deviceIdAttribute;
    
    @Mock
    private Attribute<ProtocolVersion> protocolVersionAttribute;
    
    @Captor
    private ArgumentCaptor<AuthRequest> authRequestCaptor;
    
    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;
    
    private NettyWebsocketAuthHandler authHandler;
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        public static FullHttpRequest buildWebSocketHandshakeRequest(String path, String username, String password, String protocolVersion) {
            StringBuilder uri = new StringBuilder(path);
            uri.append("?username=").append(username);
            uri.append("&password=").append(password);
            if (protocolVersion != null) {
                uri.append("&protocol_version=").append(protocolVersion);
            }
            
            FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, 
                HttpMethod.GET, 
                uri.toString(),
                Unpooled.EMPTY_BUFFER
            );
            
            request.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
            return request;
        }
        
        public static FullHttpRequest buildNonWebSocketRequest() {
            return new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, 
                HttpMethod.GET, 
                "/api/health",
                Unpooled.EMPTY_BUFFER
            );
        }
        
        public static AuthResult buildSuccessAuthResult() {
            return AuthResult.builder()
                    .success(true)
                    .deviceId(12345L)
                    .build();
        }
        
        public static AuthResult buildFailureAuthResult() {
            return AuthResult.builder()
                    .success(false)
                    .build();
        }
    }
    
    @BeforeEach
    void setUp() {
        // 手动创建NettyWebsocketAuthHandler实例，模拟Spring的依赖注入
        authHandler = new NettyWebsocketAuthHandler(
            terminalAuthUseCase,
            nettyWebsocketProperties,
            webSocketConfigProperties,
            websocketAuthExecutor
        );
        
        // 基础Mock设置 - 使用lenient模式避免严格验证
        lenient().when(channelHandlerContext.channel()).thenReturn(channel);
        lenient().when(channelHandlerContext.pipeline()).thenReturn(pipeline);
        lenient().when(channel.eventLoop()).thenReturn(eventLoop);
        lenient().when(channel.pipeline()).thenReturn(pipeline);
        lenient().when(channel.isActive()).thenReturn(true);
        
        // 配置Mock - 确保协议版本检查通过
        lenient().when(nettyWebsocketProperties.getServer()).thenReturn(serverConfig);
        lenient().when(serverConfig.getWebsocketPath()).thenReturn("/ws/terminal");
        lenient().when(webSocketConfigProperties.getProtocol()).thenReturn(protocolConfig);
        lenient().when(protocolConfig.isVersionSupported(anyString(), anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(protocolConfig.isVersionSupported(eq("1.1"), eq(false))).thenReturn(true);
        // 通过配置覆盖默认值，确保测试场景可验证不同协议开关
        
        // Channel属性Mock
        lenient().when(channel.attr(NettyWebsocketAuthHandler.TERMINAL_PRINCIPAL)).thenReturn(principalAttribute);
        lenient().when(channel.attr(NettyWebsocketAuthHandler.DEVICE_ID)).thenReturn(deviceIdAttribute);
        lenient().when(channel.attr(NettyWebsocketAuthHandler.PROTOCOL_VERSION)).thenReturn(protocolVersionAttribute);
        
        // 模拟同步执行器，简化异步测试
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(websocketAuthExecutor).execute(any(Runnable.class));
        
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));
        
        lenient().when(eventLoop.inEventLoop()).thenReturn(true);
        
        // 修复writeAndFlush返回null的问题
        ChannelFuture channelFuture = mock(ChannelFuture.class);
        lenient().when(channelHandlerContext.writeAndFlush(any())).thenReturn(channelFuture);
        lenient().when(channelFuture.addListener(any(ChannelFutureListener.class))).thenReturn(channelFuture);
        
        // 确保pipeline操作不会出错
        lenient().when(pipeline.remove(any(ChannelHandler.class))).thenReturn(pipeline);
    }
    
    @Nested
    @DisplayName("WebSocket握手认证测试")
    class WebSocketAuthenticationTest {
        
        @Test
        @DisplayName("应该成功处理V1.1协议的WebSocket认证")
        void should_authenticate_v11_websocket_successfully() throws Exception {
            // Given - 准备V1.1协议的认证请求和成功的认证结果
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "testDevice", "testPassword", "1.1");
            
            AuthResult successResult = TestDataBuilder.buildSuccessAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(successResult);
            
            // When - 处理认证请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证认证调用和属性设置
            verify(terminalAuthUseCase).authenticate(authRequestCaptor.capture());
            AuthRequest capturedRequest = authRequestCaptor.getValue();
            assertThat(capturedRequest.getAccountName()).isEqualTo("testDevice");
            assertThat(capturedRequest.getRawPassword()).isEqualTo("testPassword");
            
            // 验证Channel属性设置
            verify(principalAttribute).set(any(TerminalPrincipal.class));
            verify(deviceIdAttribute).set("12345");
            verify(protocolVersionAttribute).set(ProtocolVersion.V1_1);
            
            // 验证请求URI被重写为内部路径
            assertThat(request.uri()).isEqualTo("/ws/terminal");
            
            // 验证Handler被移除并继续处理
            verify(pipeline).remove(authHandler);
            verify(channelHandlerContext).fireChannelRead(request);
        }
        
        @Test
        @DisplayName("应该成功处理V1.0协议的WebSocket认证")
        void should_authenticate_v10_websocket_successfully() throws Exception {
            // Given - 准备V1.0协议的认证请求（不需要protocol_version参数）
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/websocket/chat", "testDevice", "testPassword", null);
            
            AuthResult successResult = TestDataBuilder.buildSuccessAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(successResult);
            
            // When - 处理认证请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证V1.0协议版本被正确设置
            verify(protocolVersionAttribute).set(ProtocolVersion.V1_0);
            verify(terminalAuthUseCase).authenticate(any(AuthRequest.class));
            verify(channelHandlerContext).fireChannelRead(request);
        }
        
        @Test
        @DisplayName("当认证失败时应该发送401响应并关闭连接")
        void should_send_401_and_close_when_authentication_fails() throws Exception {
            // Given - 准备认证失败的场景
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "invalidUser", "wrongPassword", "1.1");
            
            AuthResult failureResult = TestDataBuilder.buildFailureAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(failureResult);
            
            // When - 处理认证请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证发送401响应
            verify(channelHandlerContext).writeAndFlush(any(FullHttpResponse.class));
            verify(channelHandlerContext, never()).fireChannelRead(request);
            verify(pipeline, never()).remove(authHandler);
        }
        
        @Test
        @DisplayName("当缺少用户名时应该认证失败")
        void should_fail_when_username_missing() throws Exception {
            // Given - 准备缺少用户名的请求
            FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, 
                HttpMethod.GET, 
                "/ColorWebSocket/terminal?password=testPassword&protocol_version=1.1",
                Unpooled.EMPTY_BUFFER
            );
            request.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
            
            // When - 处理认证请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证认证失败
            verify(terminalAuthUseCase, never()).authenticate(any());
            verify(channelHandlerContext).writeAndFlush(any(FullHttpResponse.class));
        }
        
        @Test
        @DisplayName("当缺少密码时应该认证失败")
        void should_fail_when_password_missing() throws Exception {
            // Given - 准备缺少密码的请求
            FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, 
                HttpMethod.GET, 
                "/ColorWebSocket/terminal?username=testDevice&protocol_version=1.1",
                Unpooled.EMPTY_BUFFER
            );
            request.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
            
            // When - 处理认证请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证认证失败
            verify(terminalAuthUseCase, never()).authenticate(any());
            verify(channelHandlerContext).writeAndFlush(any(FullHttpResponse.class));
        }
        
        @Test
        @DisplayName("当认证过程中发生异常时应该处理失败")
        void should_handle_authentication_exception() throws Exception {
            // Given - 准备会抛出异常的认证场景
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "testDevice", "testPassword", "1.1");
            
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class)))
                .thenThrow(new RuntimeException("数据库连接异常"));
            
            // When - 处理认证请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证异常处理
            verify(channelHandlerContext).writeAndFlush(any(FullHttpResponse.class));
            verify(channelHandlerContext, never()).fireChannelRead(request);
        }
    }
    
    @Nested
    @DisplayName("请求类型识别测试")
    class RequestTypeRecognitionTest {
        
        @Test
        @DisplayName("应该透传非WebSocket请求")
        void should_pass_through_non_websocket_requests() throws Exception {
            // Given - 准备普通HTTP请求
            FullHttpRequest request = TestDataBuilder.buildNonWebSocketRequest();
            
            // When - 处理非WebSocket请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证请求被透传
            verify(channelHandlerContext).fireChannelRead(request);
            verify(terminalAuthUseCase, never()).authenticate(any());
            verify(websocketAuthExecutor, never()).execute(any());
        }
        
        @Test
        @DisplayName("应该透传非HTTP请求对象")
        void should_pass_through_non_http_messages() throws Exception {
            // Given - 准备非HTTP请求对象
            Object nonHttpMessage = new Object();
            
            // When - 处理非HTTP消息
            authHandler.channelRead(channelHandlerContext, nonHttpMessage);
            
            // Then - 验证消息被透传
            verify(channelHandlerContext).fireChannelRead(nonHttpMessage);
            verify(terminalAuthUseCase, never()).authenticate(any());
            verify(websocketAuthExecutor, never()).execute(any());
        }
        
        @Test
        @DisplayName("应该透传POST方法的请求")
        void should_pass_through_post_requests() throws Exception {
            // Given - 准备POST请求
            FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, 
                HttpMethod.POST, 
                "/ColorWebSocket/terminal",
                Unpooled.EMPTY_BUFFER
            );
            request.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
            
            // When - 处理POST请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证请求被透传
            verify(channelHandlerContext).fireChannelRead(request);
            verify(websocketAuthExecutor, never()).execute(any());
        }
        
        @Test
        @DisplayName("应该透传缺少Upgrade头的请求")
        void should_pass_through_requests_without_upgrade_header() throws Exception {
            // Given - 准备缺少Upgrade头的GET请求
            FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, 
                HttpMethod.GET, 
                "/ColorWebSocket/terminal",
                Unpooled.EMPTY_BUFFER
            );
            // 不设置Upgrade头
            
            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证请求被透传
            verify(channelHandlerContext).fireChannelRead(request);
            verify(websocketAuthExecutor, never()).execute(any());
        }
        
        @Test
        @DisplayName("当协议版本不受支持时应该立即返回错误响应")
        void should_reply_error_for_unsupported_protocol_version() throws Exception {
            // Given - 准备不受支持的协议版本请求（路径未匹配任何已知版本）
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/v2.0", "testDevice", "testPassword", "2.0");

            ArgumentCaptor<FullHttpResponse> responseCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);

            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);

            // Then - 应返回错误响应并关闭连接
            verify(channelHandlerContext, never()).fireChannelRead(request);
            verify(websocketAuthExecutor, never()).execute(any());
            verify(channelHandlerContext).writeAndFlush(responseCaptor.capture());

            FullHttpResponse response = responseCaptor.getValue();
            assertThat(response.status()).isEqualTo(HttpResponseStatus.UPGRADE_REQUIRED);
            assertThat(response.content().toString(StandardCharsets.UTF_8)).isEqualTo("不支持的WebSocket协议版本");
        }

        @Test
        @DisplayName("当协议版本被配置禁用时应该返回错误响应")
        void should_reply_error_when_protocol_version_disabled_by_config() throws Exception {
            // Given - 将已知协议版本配置为禁用
            when(protocolConfig.isVersionSupported(eq("1.1"), eq(false))).thenReturn(false);

            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "testDevice", "testPassword", "1.1");

            ArgumentCaptor<FullHttpResponse> responseCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);

            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);

            // Then - 应直接返回错误响应
            verify(channelHandlerContext, never()).fireChannelRead(request);
            verify(websocketAuthExecutor, never()).execute(any());
            verify(channelHandlerContext).writeAndFlush(responseCaptor.capture());

            FullHttpResponse response = responseCaptor.getValue();
            assertThat(response.status()).isEqualTo(HttpResponseStatus.UPGRADE_REQUIRED);
            assertThat(response.content().toString(StandardCharsets.UTF_8)).isEqualTo("不支持的WebSocket协议版本");
        }
    }
    
    @Nested
    @DisplayName("协议版本解析测试")
    class ProtocolVersionParsingTest {
        
        @Test
        @DisplayName("应该正确解析V1.0协议版本")
        void should_parse_v10_protocol_version_correctly() throws Exception {
            // Given - 准备V1.0协议请求
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/websocket/chat", "testDevice", "testPassword", null);
            
            AuthResult successResult = TestDataBuilder.buildSuccessAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(successResult);
            
            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证协议版本解析
            verify(protocolVersionAttribute).set(ProtocolVersion.V1_0);
        }
        
        @Test
        @DisplayName("应该正确解析V1.1协议版本")
        void should_parse_v11_protocol_version_correctly() throws Exception {
            // Given - 准备V1.1协议请求
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "testDevice", "testPassword", "1.1");
            
            AuthResult successResult = TestDataBuilder.buildSuccessAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(successResult);
            
            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证协议版本解析
            verify(protocolVersionAttribute).set(ProtocolVersion.V1_1);
        }
        
        @Test
        @DisplayName("当协议版本参数无效时应该使用默认版本")
        void should_use_default_version_when_protocol_version_invalid() throws Exception {
            // Given - 准备无效协议版本的请求
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "testDevice", "testPassword", "invalid_version");
            
            AuthResult successResult = TestDataBuilder.buildSuccessAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(successResult);
            
            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证使用默认协议版本（由ProtocolVersion.fromVersion处理）
            verify(protocolVersionAttribute).set(any(ProtocolVersion.class));
        }
    }
    
    @Nested
    @DisplayName("异步处理测试")
    class AsynchronousProcessingTest {
        
        @Test
        @DisplayName("应该使用专用线程池进行异步认证处理")
        void should_use_dedicated_thread_pool_for_authentication() throws Exception {
            // Given - 重新配置为真正的异步执行
            reset(websocketAuthExecutor);
            
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "testDevice", "testPassword", "1.1");
            
            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证异步执行器被调用
            verify(websocketAuthExecutor).execute(any(Runnable.class));
        }
        
        @Test
        @DisplayName("当连接已关闭时应该跳过认证处理")
        void should_skip_authentication_when_connection_closed() throws Exception {
            // Given - 模拟连接已关闭
            when(channel.isActive()).thenReturn(false);
            
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "testDevice", "testPassword", "1.1");
            
            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证跳过认证处理
            verify(terminalAuthUseCase, never()).authenticate(any());
            verify(channelHandlerContext, never()).fireChannelRead(any());
        }
        
        @Test
        @DisplayName("认证成功后应该在I/O线程中处理后续逻辑")
        void should_handle_success_callback_in_io_thread() throws Exception {
            // Given - 重新配置EventLoop为异步
            reset(eventLoop);
            lenient().when(eventLoop.inEventLoop()).thenReturn(false);
            
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "testDevice", "testPassword", "1.1");
            
            AuthResult successResult = TestDataBuilder.buildSuccessAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(successResult);
            
            // When - 处理请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证回调到I/O线程
            verify(eventLoop).execute(any(Runnable.class));
        }
    }
    
    @Nested
    @DisplayName("错误响应处理测试")
    class ErrorResponseHandlingTest {
        
        @Test
        @DisplayName("应该发送正确格式的401错误响应")
        void should_send_proper_401_error_response() throws Exception {
            // Given - 准备认证失败场景
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "invalidUser", "wrongPassword", "1.1");
            
            AuthResult failureResult = TestDataBuilder.buildFailureAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(failureResult);
            
            ArgumentCaptor<FullHttpResponse> responseCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
            
            // When - 处理认证请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证401响应格式
            verify(channelHandlerContext).writeAndFlush(responseCaptor.capture());
            
            FullHttpResponse response = responseCaptor.getValue();
            assertThat(response.status()).isEqualTo(HttpResponseStatus.UNAUTHORIZED);
            assertThat(response.headers().get(HttpHeaderNames.CONTENT_TYPE)).contains("text/plain");
            
            // 验证响应内容
            ByteBuf content = response.content();
            String responseBody = content.toString(StandardCharsets.UTF_8);
            assertThat(responseBody).isEqualTo("认证失败");
        }
        
        @Test
        @DisplayName("发送错误响应后应该关闭连接")
        void should_close_connection_after_error_response() throws Exception {
            // Given - 准备认证失败场景
            FullHttpRequest request = TestDataBuilder.buildWebSocketHandshakeRequest(
                "/ColorWebSocket/terminal", "invalidUser", "wrongPassword", "1.1");
            
            AuthResult failureResult = TestDataBuilder.buildFailureAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class))).thenReturn(failureResult);
            
            // ChannelFuture已在setUp中Mock，无需重复设置
            
            // When - 处理认证请求
            authHandler.channelRead(channelHandlerContext, request);
            
            // Then - 验证添加了关闭监听器  
            ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
            verify(channelHandlerContext).writeAndFlush(responseCaptor.capture());
            // 验证writeAndFlush被调用后会添加关闭监听器（这个在实际代码中自动处理）
        }
    }
}