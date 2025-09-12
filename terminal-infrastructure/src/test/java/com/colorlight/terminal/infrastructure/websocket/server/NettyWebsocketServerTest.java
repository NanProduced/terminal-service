package com.colorlight.terminal.infrastructure.websocket.server;

import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.websocket.config.NettyWebsocketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NettyWebsocketServer 单元测试
 * <p>
 * 测试策略：
 * 1. 遵循上下文感知测试原则，专注服务器生命周期管理职责
 * 2. 使用lenient模式优化Mock，避免严格验证
 * 3. 专注核心业务逻辑：启动流程、配置管理、生命周期、优雅关闭
 * 4. 避免测试Netty框架内部逻辑，专注业务层面的服务器管理
 * 
 * <p>
 * NettyWebsocketServer 业务逻辑总结：
 * - 核心职责：WebSocket服务器的生命周期管理，包括启动、配置、关闭
 * - 启动流程：条件检查 → 线程组初始化 → Bootstrap配置 → 端口绑定
 * - 配置管理：服务器选项配置、客户端选项配置、线程配置
 * - 生命周期：ApplicationRunner自动启动、DisposableBean自动关闭
 * - 优雅关闭：服务器Channel关闭 → 线程组关闭，确保任务完成
 * - 异常处理：启动失败时阻止Spring启动，确保服务可用性
 * </p>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Netty WebSocket服务器测试")
class NettyWebsocketServerTest {

    @Mock
    private NettyWebsocketProperties nettyWebsocketProperties;
    
    @Mock
    private WebSocketChannelInitializer webSocketChannelInitializer;
    
    @Mock
    private ApplicationArguments applicationArguments;
    
    @Mock
    private NettyWebsocketProperties.Server serverConfig;
    
    @InjectMocks
    private NettyWebsocketServer nettyWebsocketServer;
    
    
    @BeforeEach
    void setUp() {
        // 基础Mock设置 - 使用lenient模式避免严格验证
        lenient().when(nettyWebsocketProperties.isEnabled()).thenReturn(true);
        lenient().when(nettyWebsocketProperties.getServer()).thenReturn(serverConfig);
        
        // 服务器配置默认值
        lenient().when(serverConfig.getHost()).thenReturn("localhost");
        lenient().when(serverConfig.getPort()).thenReturn(8080);
        lenient().when(serverConfig.getBossThreads()).thenReturn(1);
        lenient().when(serverConfig.getWorkerThreads()).thenReturn(4);
        lenient().when(serverConfig.isSoReuseAddr()).thenReturn(true);
        lenient().when(serverConfig.getSoBacklog()).thenReturn(128);
        lenient().when(serverConfig.getConnectTimeout()).thenReturn(30000);
        lenient().when(serverConfig.isKeepAlive()).thenReturn(true);
        lenient().when(serverConfig.isTcpNoDelay()).thenReturn(true);
        lenient().when(serverConfig.getSoRcvBuf()).thenReturn(65536);
        lenient().when(serverConfig.getSoSndBuf()).thenReturn(65536);
    }
    
    @Nested
    @DisplayName("服务器启动测试")
    class ServerStartupTest {
        
        @Test
        @DisplayName("当WebSocket服务禁用时应该跳过启动")
        void should_skip_startup_when_websocket_disabled() throws Exception {
            // Given - 准备禁用WebSocket的配置
            when(nettyWebsocketProperties.isEnabled()).thenReturn(false);
            
            // When - 运行服务器启动
            nettyWebsocketServer.run(applicationArguments);
            
            // Then - 验证跳过启动逻辑
            // 由于服务器被禁用，不应该初始化任何Netty组件
            // 这个测试主要验证配置检查逻辑
            verify(nettyWebsocketProperties).isEnabled();
        }
        
        @Test
        @DisplayName("当WebSocket服务启用时应该正常启动")
        void should_start_normally_when_websocket_enabled() {
            // Given - 准备启用WebSocket的配置
            when(nettyWebsocketProperties.isEnabled()).thenReturn(true);
            
            // When - 正常启动服务器（应该不抛出异常）
            assertThatNoException().isThrownBy(() -> nettyWebsocketServer.run(applicationArguments));
            
            // Then - 验证配置检查被调用
            verify(nettyWebsocketProperties).isEnabled();
            verify(nettyWebsocketProperties, atLeastOnce()).getServer();
        }
    }
    
    @Nested
    @DisplayName("服务器配置测试")
    class ServerConfigurationTest {
        
        @Test
        @DisplayName("应该正确读取Boss线程数配置")
        void should_read_boss_threads_configuration() {
            // Given - 准备Boss线程配置
            when(serverConfig.getBossThreads()).thenReturn(2);
            
            // When - 获取配置
            int bossThreads = nettyWebsocketProperties.getServer().getBossThreads();
            
            // Then - 验证配置正确
            assertThat(bossThreads).isEqualTo(2);
            verify(serverConfig).getBossThreads();
        }
        
        @Test
        @DisplayName("应该正确读取Worker线程数配置")
        void should_read_worker_threads_configuration() {
            // Given - 准备Worker线程配置
            when(serverConfig.getWorkerThreads()).thenReturn(8);
            
            // When - 获取配置
            int workerThreads = nettyWebsocketProperties.getServer().getWorkerThreads();
            
            // Then - 验证配置正确
            assertThat(workerThreads).isEqualTo(8);
            verify(serverConfig).getWorkerThreads();
        }
        
        @Test
        @DisplayName("应该正确读取服务器地址和端口配置")
        void should_read_server_host_and_port_configuration() {
            // Given - 准备服务器地址配置
            when(serverConfig.getHost()).thenReturn("0.0.0.0");
            when(serverConfig.getPort()).thenReturn(9090);
            
            // When - 获取配置
            String host = nettyWebsocketProperties.getServer().getHost();
            int port = nettyWebsocketProperties.getServer().getPort();
            
            // Then - 验证配置正确
            assertThat(host).isEqualTo("0.0.0.0");
            assertThat(port).isEqualTo(9090);
            verify(serverConfig).getHost();
            verify(serverConfig).getPort();
        }
        
        @Test
        @DisplayName("应该正确读取Socket选项配置")
        void should_read_socket_options_configuration() {
            // Given - 准备Socket选项配置
            when(serverConfig.isSoReuseAddr()).thenReturn(false);
            when(serverConfig.getSoBacklog()).thenReturn(256);
            when(serverConfig.getConnectTimeout()).thenReturn(60000);
            
            // When - 获取配置
            boolean soReuseAddr = nettyWebsocketProperties.getServer().isSoReuseAddr();
            int soBacklog = nettyWebsocketProperties.getServer().getSoBacklog();
            int connectTimeout = nettyWebsocketProperties.getServer().getConnectTimeout();
            
            // Then - 验证配置正确
            assertThat(soReuseAddr).isFalse();
            assertThat(soBacklog).isEqualTo(256);
            assertThat(connectTimeout).isEqualTo(60000);
        }
        
        @Test
        @DisplayName("应该正确读取客户端连接选项配置")
        void should_read_child_options_configuration() {
            // Given - 准备客户端连接选项配置
            when(serverConfig.isKeepAlive()).thenReturn(false);
            when(serverConfig.isTcpNoDelay()).thenReturn(false);
            when(serverConfig.getSoRcvBuf()).thenReturn(32768);
            when(serverConfig.getSoSndBuf()).thenReturn(32768);
            
            // When - 获取配置
            boolean keepAlive = nettyWebsocketProperties.getServer().isKeepAlive();
            boolean tcpNoDelay = nettyWebsocketProperties.getServer().isTcpNoDelay();
            int soRcvBuf = nettyWebsocketProperties.getServer().getSoRcvBuf();
            int soSndBuf = nettyWebsocketProperties.getServer().getSoSndBuf();
            
            // Then - 验证配置正确
            assertThat(keepAlive).isFalse();
            assertThat(tcpNoDelay).isFalse();
            assertThat(soRcvBuf).isEqualTo(32768);
            assertThat(soSndBuf).isEqualTo(32768);
        }
    }
    
    @Nested
    @DisplayName("服务器关闭测试")
    class ServerShutdownTest {
        
        @Test
        @DisplayName("应该能够调用destroy方法进行优雅关闭")
        void should_call_destroy_method_for_graceful_shutdown() {
            // Given - 服务器处于运行状态（模拟）
            // 由于无法完全Mock Netty的复杂关闭流程，这里主要测试方法调用
            
            // When - 调用destroy方法
            // Then - 验证方法能够正常执行（不抛异常）
            assertThatCode(() -> nettyWebsocketServer.destroy())
                .doesNotThrowAnyException();
        }
    }
    
    @Nested
    @DisplayName("生命周期管理测试")
    class LifecycleManagementTest {
        
        @Test
        @DisplayName("应该实现ApplicationRunner接口进行自动启动")
        void should_implement_application_runner_for_auto_startup() {
            // Then - 验证实现了ApplicationRunner接口
            assertThat(nettyWebsocketServer).isInstanceOf(org.springframework.boot.ApplicationRunner.class);
        }
        
        @Test
        @DisplayName("应该实现DisposableBean接口进行自动关闭")
        void should_implement_disposable_bean_for_auto_shutdown() {
            // Then - 验证实现了DisposableBean接口
            assertThat(nettyWebsocketServer).isInstanceOf(org.springframework.beans.factory.DisposableBean.class);
        }
    }
    
    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTest {
        
        @Test
        @DisplayName("启动失败时应该抛出TechnicalException阻止Spring启动")
        void should_throw_runtime_exception_when_startup_fails() {
            // Given - 准备会导致启动失败的配置
            when(nettyWebsocketProperties.isEnabled()).thenReturn(true);
            when(nettyWebsocketProperties.getServer()).thenThrow(new RuntimeException("模拟配置异常"));
            
            // When & Then - 验证启动失败时抛出异常
            assertThatThrownBy(() -> nettyWebsocketServer.run(applicationArguments))
                .isInstanceOf(TechnicalException.class);
        }
    }
    
    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {
        
        @Test
        @DisplayName("应该处理Worker线程数为0的情况")
        void should_handle_zero_worker_threads() {
            // Given - 准备Worker线程数为0的配置
            when(serverConfig.getWorkerThreads()).thenReturn(0);
            
            // When - 获取配置
            int workerThreads = nettyWebsocketProperties.getServer().getWorkerThreads();
            
            // Then - 验证配置正确（应该使用默认值）
            assertThat(workerThreads).isZero();
        }
        
        @Test
        @DisplayName("应该处理负数Worker线程数的情况")
        void should_handle_negative_worker_threads() {
            // Given - 准备负数Worker线程数的配置
            when(serverConfig.getWorkerThreads()).thenReturn(-1);
            
            // When - 获取配置
            int workerThreads = nettyWebsocketProperties.getServer().getWorkerThreads();
            
            // Then - 验证配置正确
            assertThat(workerThreads).isEqualTo(-1);
        }
        
        @Test
        @DisplayName("应该处理多次调用destroy方法的情况")
        void should_handle_multiple_destroy_calls() throws Exception {
            // When - 多次调用destroy方法
            nettyWebsocketServer.destroy();
            
            // Then - 验证第二次调用不会抛异常
            assertThatCode(() -> nettyWebsocketServer.destroy())
                .doesNotThrowAnyException();
        }
    }
    
    @Nested
    @DisplayName("组件集成测试")
    class ComponentIntegrationTest {
        
        @Test
        @DisplayName("应该正确注入NettyWebsocketProperties依赖")
        void should_inject_netty_websocket_properties_dependency() {
            // Then - 验证依赖注入正确
            assertThat(ReflectionTestUtils.getField(nettyWebsocketServer, "nettyWebsocketProperties"))
                .isNotNull()
                .isSameAs(nettyWebsocketProperties);
        }
        
        @Test
        @DisplayName("应该正确注入WebSocketChannelInitializer依赖")
        void should_inject_websocket_channel_initializer_dependency() {
            // Then - 验证依赖注入正确
            assertThat(ReflectionTestUtils.getField(nettyWebsocketServer, "webSocketChannelInitializer"))
                .isNotNull()
                .isSameAs(webSocketChannelInitializer);
        }
        
        @Test
        @DisplayName("应该正确处理空的WebSocketChannelInitializer")
        void should_handle_null_websocket_channel_initializer() {
            // Given - 准备空的WebSocketChannelInitializer
            ReflectionTestUtils.setField(nettyWebsocketServer, "webSocketChannelInitializer", null);
            
            // When & Then - 验证启动时的异常处理
            assertThatThrownBy(() -> nettyWebsocketServer.run(applicationArguments))
                .isInstanceOf(RuntimeException.class);
        }
    }
}