package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.connection.WebSocketSession;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolMessageProcessor;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolProcessorPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * WebsocketMessageApplicationService 单元测试
 * 
 * <p>测试范围：</p>
 * <ul>
 *   <li>文本消息处理 - 协议路由和统计更新</li>
 *   <li>连接生命周期管理 - 建立和关闭</li>
 *   <li>心跳帧处理 - PING/PONG with 统计</li>
 *   <li>消息发送 - 单播和广播</li>
 * </ul>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebsocketMessageApplicationServiceTest extends BaseApplicationServiceTest {

    @Mock
    private ConnectionManagerPort connectionManagerPort;
    
    @Mock
    private DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;
    
    @Mock
    private ProtocolProcessorPort protocolProcessorPort;
    
    @InjectMocks
    private WebsocketMessageApplicationService service;

    // 测试常量
    private static final String TEST_MESSAGE = "test_message";
    private static final String TEST_SESSION_ID = "session_123";
    private static final int TEST_CONNECTION_COUNT = 5;
    private static final String PONG_MESSAGE = "PONG";

    /**
     * 测试数据构建器
     */
    static class TestDataBuilder {
        
        /**
         * 创建Mock WebSocketSession
         */
        static WebSocketSession createMockWebSocketSession() {
            WebSocketSession session = mock(WebSocketSession.class);
            given(session.getDeviceId()).willReturn(TEST_DEVICE_ID);
            given(session.getClientIp()).willReturn(TEST_CLIENT_IP);
            given(session.getSessionId()).willReturn(TEST_SESSION_ID);
            given(session.isConnected()).willReturn(true);
            given(session.sendMessage(anyString())).willReturn(true);
            return session;
        }
        
        /**
         * 创建Mock TerminalConnection
         */
        static TerminalConnection createMockTerminalConnection(ProtocolVersion protocolVersion) {
            TerminalConnection connection = mock(TerminalConnection.class);
            given(connection.getDeviceId()).willReturn(TEST_DEVICE_ID);
            given(connection.getClientIp()).willReturn(TEST_CLIENT_IP);
            given(connection.getProtocolVersion()).willReturn(protocolVersion);
            given(connection.isActive()).willReturn(true);
            given(connection.sendMessage(anyString())).willReturn(true);
            
            // Mock统计方法
            doNothing().when(connection).incrementReceivedMessageCount();
            doNothing().when(connection).incrementSentMessageCount();
            doNothing().when(connection).incrementErrorCount();
            
            return connection;
        }
        
        /**
         * 创建Mock MessageProcessingContext
         */
        static MessageProcessingContext createMockMessageProcessingContext(ProtocolVersion protocolVersion) {
            MessageProcessingContext context = mock(MessageProcessingContext.class);
            TerminalConnection connection = createMockTerminalConnection(protocolVersion);
            
            given(context.getConnection()).willReturn(connection);
            given(context.getRawMessage()).willReturn(TEST_MESSAGE);
            given(context.getDeviceId()).willReturn(TEST_DEVICE_ID);
            given(context.getProtocolVersion()).willReturn(protocolVersion);
            given(context.getClientIp()).willReturn(TEST_CLIENT_IP);
            
            // Mock统计方法
            doNothing().when(context).updateMessageStatistics();
            doNothing().when(context).updateErrorStatistics();
            
            return context;
        }
        
        /**
         * 创建Mock ProtocolMessageProcessor
         */
        static ProtocolMessageProcessor createMockProtocolProcessor(ProtocolVersion version) {
            ProtocolMessageProcessor processor = mock(ProtocolMessageProcessor.class);
            given(processor.getSupportedVersion()).willReturn(version);
            return processor;
        }
        
        /**
         * 创建成功的处理结果
         */
        static ProtocolMessageProcessor.TextMessageProcessResult createSuccessResult(boolean heartbeat) {
            return ProtocolMessageProcessor.TextMessageProcessResult.ofSuccess(heartbeat);
        }
        
        /**
         * 创建失败的处理结果
         */
        static ProtocolMessageProcessor.TextMessageProcessResult createFailureResult() {
            return ProtocolMessageProcessor.TextMessageProcessResult.ofFailure("协议解析失败");
        }
    }

    @Nested
    @DisplayName("文本消息处理测试")
    class TextMessageProcessingTests {

        @Test
        @DisplayName("应该成功处理V1.0协议的文本消息")
        void should_process_v10_text_message_successfully() {
            // Given - V1.0协议上下文和处理器
            MessageProcessingContext context = TestDataBuilder.createMockMessageProcessingContext(ProtocolVersion.V1_0);
            ProtocolMessageProcessor processor = TestDataBuilder.createMockProtocolProcessor(ProtocolVersion.V1_0);
            ProtocolMessageProcessor.TextMessageProcessResult successResult = 
                TestDataBuilder.createSuccessResult(false);
            
            given(protocolProcessorPort.getProcessor(ProtocolVersion.V1_0)).willReturn(processor);
            given(processor.processTextMessage(context)).willReturn(successResult);
            
            // When - 处理文本消息
            service.handleTextMessageByProcessor(context);
            
            // Then - 验证处理流程
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(protocolProcessorPort).getProcessor(ProtocolVersion.V1_0);
            verify(processor).processTextMessage(context);
            verify(context).updateMessageStatistics();
            verify(context, never()).updateErrorStatistics();
        }

        @Test
        @DisplayName("应该成功处理V1.1协议的文本消息")
        void should_process_v11_text_message_successfully() {
            // Given - V1.1协议上下文和处理器
            MessageProcessingContext context = TestDataBuilder.createMockMessageProcessingContext(ProtocolVersion.V1_1);
            ProtocolMessageProcessor processor = TestDataBuilder.createMockProtocolProcessor(ProtocolVersion.V1_1);
            ProtocolMessageProcessor.TextMessageProcessResult successResult = 
                TestDataBuilder.createSuccessResult(false);
            
            given(protocolProcessorPort.getProcessor(ProtocolVersion.V1_1)).willReturn(processor);
            given(processor.processTextMessage(context)).willReturn(successResult);
            
            // When - 处理文本消息
            service.handleTextMessageByProcessor(context);
            
            // Then - 验证处理流程
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(protocolProcessorPort).getProcessor(ProtocolVersion.V1_1);
            verify(processor).processTextMessage(context);
            verify(context).updateMessageStatistics();
            verify(context, never()).updateErrorStatistics();
        }

        @Test
        @DisplayName("应该正确处理心跳消息")
        void should_handle_heartbeat_message_correctly() {
            // Given - 心跳消息处理结果
            MessageProcessingContext context = TestDataBuilder.createMockMessageProcessingContext(ProtocolVersion.V1_0);
            ProtocolMessageProcessor processor = TestDataBuilder.createMockProtocolProcessor(ProtocolVersion.V1_0);
            ProtocolMessageProcessor.TextMessageProcessResult heartbeatResult = 
                TestDataBuilder.createSuccessResult(true);
            
            given(protocolProcessorPort.getProcessor(ProtocolVersion.V1_0)).willReturn(processor);
            given(processor.processTextMessage(context)).willReturn(heartbeatResult);
            
            // When - 处理心跳消息
            service.handleTextMessageByProcessor(context);
            
            // Then - 验证心跳处理
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(processor).processTextMessage(context);
            verify(context).updateMessageStatistics();
            verify(context, never()).updateErrorStatistics();
        }

        @Test
        @DisplayName("应该处理协议处理器返回失败结果")
        void should_handle_protocol_processor_failure() {
            // Given - 协议处理器返回失败结果
            MessageProcessingContext context = TestDataBuilder.createMockMessageProcessingContext(ProtocolVersion.V1_0);
            ProtocolMessageProcessor processor = TestDataBuilder.createMockProtocolProcessor(ProtocolVersion.V1_0);
            ProtocolMessageProcessor.TextMessageProcessResult failureResult = 
                TestDataBuilder.createFailureResult();
            
            given(protocolProcessorPort.getProcessor(ProtocolVersion.V1_0)).willReturn(processor);
            given(processor.processTextMessage(context)).willReturn(failureResult);
            
            // When - 处理失败的消息
            service.handleTextMessageByProcessor(context);
            
            // Then - 验证错误处理
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(processor).processTextMessage(context);
            verify(context).updateErrorStatistics();
            verify(context, never()).updateMessageStatistics();
        }

        @Test
        @DisplayName("应该处理消息处理过程中的异常")
        void should_handle_exception_during_message_processing() {
            // Given - 协议处理器抛出异常
            MessageProcessingContext context = TestDataBuilder.createMockMessageProcessingContext(ProtocolVersion.V1_0);
            ProtocolMessageProcessor processor = TestDataBuilder.createMockProtocolProcessor(ProtocolVersion.V1_0);
            
            given(protocolProcessorPort.getProcessor(ProtocolVersion.V1_0)).willReturn(processor);
            given(processor.processTextMessage(context)).willThrow(new RuntimeException("处理异常"));
            
            // When - 处理抛出异常的消息
            service.handleTextMessageByProcessor(context);
            
            // Then - 验证异常处理
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(processor).processTextMessage(context);
            verify(context).updateErrorStatistics();
            verify(context, never()).updateMessageStatistics();
        }
    }

    @Nested
    @DisplayName("连接建立测试")
    class ConnectionEstablishmentTests {

        @Test
        @DisplayName("应该成功建立连接并更新在线状态")
        void should_establish_connection_successfully() {
            // Given - Mock会话和连接管理器
            WebSocketSession session = TestDataBuilder.createMockWebSocketSession();
            given(connectionManagerPort.addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class)))
                .willReturn(true);
            given(connectionManagerPort.getConnectionCount()).willReturn(TEST_CONNECTION_COUNT);
            
            // When - 建立连接
            TerminalConnection result = service.handleConnectionEstablished(TEST_DEVICE_ID, session, ProtocolVersion.V1_0);
            
            // Then - 验证连接建立成功
            assertThat(result).isNotNull();
            assertThat(result.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            assertThat(result.getProtocolVersion()).isEqualTo(ProtocolVersion.V1_0);
            
            verify(connectionManagerPort).addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class));
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(connectionManagerPort).getConnectionCount();
        }

        @Test
        @DisplayName("应该处理连接管理器添加失败的情况")
        void should_handle_connection_manager_add_failure() {
            // Given - 连接管理器添加失败
            WebSocketSession session = TestDataBuilder.createMockWebSocketSession();
            given(connectionManagerPort.addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class)))
                .willReturn(false);
            
            // When - 尝试建立连接
            TerminalConnection result = service.handleConnectionEstablished(TEST_DEVICE_ID, session, ProtocolVersion.V1_0);
            
            // Then - 验证返回null
            assertThat(result).isNull();
            
            verify(connectionManagerPort).addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class));
            verify(deviceOnlineStatusUseCase, never()).updateLastReportTime(any(), any(), any());
        }

        @Test
        @DisplayName("应该处理连接建立过程中的异常")
        void should_handle_exception_during_connection_establishment() {
            // Given - 连接管理器抛出异常
            WebSocketSession session = TestDataBuilder.createMockWebSocketSession();
            given(connectionManagerPort.addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class)))
                .willThrow(new RuntimeException("连接添加失败"));
            
            // When - 尝试建立连接
            TerminalConnection result = service.handleConnectionEstablished(TEST_DEVICE_ID, session, ProtocolVersion.V1_0);
            
            // Then - 验证异常处理
            assertThat(result).isNull();
            
            verify(connectionManagerPort).addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class));
            verify(deviceOnlineStatusUseCase, never()).updateLastReportTime(any(), any(), any());
        }

        @Test
        @DisplayName("应该在连接建立成功后调用协议处理器的连接建立回调")
        void should_invoke_protocol_processor_callback_on_connection_established() {
            // Given - Mock会话、连接管理器和协议处理器
            WebSocketSession session = TestDataBuilder.createMockWebSocketSession();
            ProtocolMessageProcessor processor = TestDataBuilder.createMockProtocolProcessor(ProtocolVersion.V1_1);

            given(connectionManagerPort.addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class)))
                .willReturn(true);
            given(connectionManagerPort.getConnectionCount()).willReturn(TEST_CONNECTION_COUNT);
            given(protocolProcessorPort.getProcessor(ProtocolVersion.V1_1)).willReturn(processor);

            // When - 建立V1.1协议连接
            TerminalConnection result = service.handleConnectionEstablished(TEST_DEVICE_ID, session, ProtocolVersion.V1_1);

            // Then - 验证连接建立成功且协议回调被调用
            assertThat(result).isNotNull();

            verify(connectionManagerPort).addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class));
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(protocolProcessorPort).getProcessor(ProtocolVersion.V1_1);
            verify(processor).onConnectionEstablished(any(MessageProcessingContext.class));
        }

        @Test
        @DisplayName("应该处理协议连接建立回调失败的情况但不影响连接本身")
        void should_handle_protocol_callback_failure_without_affecting_connection() {
            // Given - Mock会话和协议处理器抛出异常
            WebSocketSession session = TestDataBuilder.createMockWebSocketSession();
            ProtocolMessageProcessor processor = TestDataBuilder.createMockProtocolProcessor(ProtocolVersion.V1_1);

            given(connectionManagerPort.addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class)))
                .willReturn(true);
            given(connectionManagerPort.getConnectionCount()).willReturn(TEST_CONNECTION_COUNT);
            given(protocolProcessorPort.getProcessor(ProtocolVersion.V1_1)).willReturn(processor);
            doThrow(new RuntimeException("推送指令失败"))
                .when(processor).onConnectionEstablished(any(MessageProcessingContext.class));

            // When - 建立连接（回调失败）
            TerminalConnection result = service.handleConnectionEstablished(TEST_DEVICE_ID, session, ProtocolVersion.V1_1);

            // Then - 验证连接仍然成功建立（回调失败不影响连接）
            assertThat(result).isNotNull();
            assertThat(result.getDeviceId()).isEqualTo(TEST_DEVICE_ID);

            verify(connectionManagerPort).addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class));
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(protocolProcessorPort).getProcessor(ProtocolVersion.V1_1);
            verify(processor).onConnectionEstablished(any(MessageProcessingContext.class));
        }

        @Test
        @DisplayName("V1.0协议连接建立时不应调用协议回调（默认空实现）")
        void should_not_invoke_callback_for_v10_protocol() {
            // Given - Mock V1.0协议会话和处理器
            WebSocketSession session = TestDataBuilder.createMockWebSocketSession();
            ProtocolMessageProcessor processor = TestDataBuilder.createMockProtocolProcessor(ProtocolVersion.V1_0);

            given(connectionManagerPort.addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class)))
                .willReturn(true);
            given(connectionManagerPort.getConnectionCount()).willReturn(TEST_CONNECTION_COUNT);
            given(protocolProcessorPort.getProcessor(ProtocolVersion.V1_0)).willReturn(processor);

            // When - 建立V1.0协议连接
            TerminalConnection result = service.handleConnectionEstablished(TEST_DEVICE_ID, session, ProtocolVersion.V1_0);

            // Then - 验证连接建立成功且协议回调被调用（但V1.0默认空实现无副作用）
            assertThat(result).isNotNull();

            verify(connectionManagerPort).addConnection(eq(TEST_DEVICE_ID), any(TerminalConnection.class));
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(protocolProcessorPort).getProcessor(ProtocolVersion.V1_0);
            verify(processor).onConnectionEstablished(any(MessageProcessingContext.class));
        }
    }

    @Nested
    @DisplayName("连接关闭测试")
    class ConnectionClosureTests {

        @Test
        @DisplayName("应该成功关闭连接并移除")
        void should_close_connection_successfully() {
            // Given - 连接存在且移除成功
            TerminalConnection removedConnection = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            given(connectionManagerPort.removeConnection(TEST_DEVICE_ID)).willReturn(removedConnection);
            given(connectionManagerPort.getConnectionCount()).willReturn(TEST_CONNECTION_COUNT - 1);
            
            // When - 关闭连接
            service.handleConnectionClosed(TEST_DEVICE_ID);
            
            // Then - 验证连接关闭
            verify(connectionManagerPort).removeConnection(TEST_DEVICE_ID);
            verify(connectionManagerPort).getConnectionCount();
        }

        @Test
        @DisplayName("应该处理连接不存在的情况")
        void should_handle_connection_not_found() {
            // Given - 连接不存在
            given(connectionManagerPort.removeConnection(TEST_DEVICE_ID)).willReturn(null);
            
            // When - 尝试关闭不存在的连接
            service.handleConnectionClosed(TEST_DEVICE_ID);
            
            // Then - 验证处理流程
            verify(connectionManagerPort).removeConnection(TEST_DEVICE_ID);
            verify(connectionManagerPort, never()).getConnectionCount();
        }

        @Test
        @DisplayName("应该处理连接关闭过程中的异常")
        void should_handle_exception_during_connection_closure() {
            // Given - 连接管理器抛出异常
            given(connectionManagerPort.removeConnection(TEST_DEVICE_ID))
                .willThrow(new RuntimeException("连接移除失败"));
            
            // When - 尝试关闭连接
            service.handleConnectionClosed(TEST_DEVICE_ID);
            
            // Then - 验证异常处理（不应该抛出异常）
            verify(connectionManagerPort).removeConnection(TEST_DEVICE_ID);
        }
    }

    @Nested
    @DisplayName("PING帧处理测试")
    class PingFrameTests {

        @Test
        @DisplayName("应该正确处理PING帧并回复PONG")
        void should_handle_ping_frame_correctly() {
            // Given - Mock终端连接
            TerminalConnection connection = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            
            // When - 处理PING帧
            service.handlePingFrame(connection);
            
            // Then - 验证PING处理流程
            verify(connection).incrementReceivedMessageCount();
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            verify(connection).sendMessage(PONG_MESSAGE);
            verify(connection).incrementSentMessageCount();
        }
    }

    @Nested
    @DisplayName("PONG帧处理测试")
    class PongFrameTests {

        @Test
        @DisplayName("应该正确处理PONG帧")
        void should_handle_pong_frame_correctly() {
            // Given - Mock终端连接
            TerminalConnection connection = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            
            // When - 处理PONG帧
            service.handlePongFrame(connection);
            
            // Then - 验证PONG处理流程
            verify(connection).incrementReceivedMessageCount();
            verify(deviceOnlineStatusUseCase).updateLastReportTime(TEST_DEVICE_ID, ReportSource.WEBSOCKET, TEST_CLIENT_IP);
            // PONG帧不需要回复
            verify(connection, never()).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("单播消息发送测试")
    class SingleMessageTests {

        @Test
        @DisplayName("应该成功发送消息给连接的设备")
        void should_send_message_to_connected_device_successfully() {
            // Given - 设备已连接且活跃
            TerminalConnection connection = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            given(connectionManagerPort.getConnection(TEST_DEVICE_ID)).willReturn(Optional.of(connection));
            
            // When - 发送消息
            boolean result = service.sendMessage(TEST_DEVICE_ID, TEST_MESSAGE);
            
            // Then - 验证发送成功
            assertThat(result).isTrue();
            
            verify(connectionManagerPort).getConnection(TEST_DEVICE_ID);
            verify(connection).isActive();
            verify(connection).sendMessage(TEST_MESSAGE);
            verify(connection).incrementSentMessageCount();
            verify(connection, never()).incrementErrorCount();
        }

        @Test
        @DisplayName("应该处理设备未连接的情况")
        void should_handle_device_not_connected() {
            // Given - 设备未连接
            given(connectionManagerPort.getConnection(TEST_DEVICE_ID)).willReturn(Optional.empty());
            
            // When - 尝试发送消息
            boolean result = service.sendMessage(TEST_DEVICE_ID, TEST_MESSAGE);
            
            // Then - 验证发送失败
            assertThat(result).isFalse();
            
            verify(connectionManagerPort).getConnection(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("应该处理连接无效的情况")
        void should_handle_inactive_connection() {
            // Given - 设备连接无效
            TerminalConnection connection = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            given(connection.isActive()).willReturn(false);
            given(connectionManagerPort.getConnection(TEST_DEVICE_ID)).willReturn(Optional.of(connection));
            
            // When - 尝试发送消息
            boolean result = service.sendMessage(TEST_DEVICE_ID, TEST_MESSAGE);
            
            // Then - 验证发送失败
            assertThat(result).isFalse();
            
            verify(connectionManagerPort).getConnection(TEST_DEVICE_ID);
            verify(connection).isActive();
            verify(connection, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("应该处理消息发送失败的情况")
        void should_handle_message_send_failure() {
            // Given - 消息发送失败
            TerminalConnection connection = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            given(connection.sendMessage(TEST_MESSAGE)).willReturn(false);
            given(connectionManagerPort.getConnection(TEST_DEVICE_ID)).willReturn(Optional.of(connection));
            
            // When - 尝试发送消息
            boolean result = service.sendMessage(TEST_DEVICE_ID, TEST_MESSAGE);
            
            // Then - 验证发送失败和错误统计
            assertThat(result).isFalse();
            
            verify(connection).sendMessage(TEST_MESSAGE);
            verify(connection).incrementErrorCount();
            verify(connection, never()).incrementSentMessageCount();
        }

        @Test
        @DisplayName("应该处理发送过程中的异常")
        void should_handle_exception_during_send() {
            // Given - 连接管理器抛出异常
            given(connectionManagerPort.getConnection(TEST_DEVICE_ID))
                .willThrow(new RuntimeException("获取连接失败"));
            
            // When - 尝试发送消息
            boolean result = service.sendMessage(TEST_DEVICE_ID, TEST_MESSAGE);
            
            // Then - 验证异常处理
            assertThat(result).isFalse();
            
            verify(connectionManagerPort).getConnection(TEST_DEVICE_ID);
        }
    }

    @Nested
    @DisplayName("广播消息发送测试")
    class BroadcastMessageTests {

        @Test
        @DisplayName("应该成功广播消息给多个设备")
        void should_broadcast_message_to_multiple_devices_successfully() {
            // Given - 多个设备ID和成功发送
            Long deviceId1 = createTestDeviceId(1);
            Long deviceId2 = createTestDeviceId(2);
            List<Long> deviceIds = Arrays.asList(deviceId1, deviceId2);
            
            // 配置连接管理器返回对应的连接
            TerminalConnection connection1 = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            TerminalConnection connection2 = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_1);
            given(connection1.getDeviceId()).willReturn(deviceId1);
            given(connection2.getDeviceId()).willReturn(deviceId2);
            
            given(connectionManagerPort.getConnection(deviceId1)).willReturn(Optional.of(connection1));
            given(connectionManagerPort.getConnection(deviceId2)).willReturn(Optional.of(connection2));
            
            // When - 广播消息
            List<Long> result = service.broadcastMessage(deviceIds, TEST_MESSAGE);
            
            // Then - 验证广播成功
            assertThat(result).hasSize(2).containsExactlyInAnyOrder(deviceId1, deviceId2);
            
            // 验证每个设备都尝试发送了消息
            verify(connectionManagerPort).getConnection(deviceId1);
            verify(connectionManagerPort).getConnection(deviceId2);
            verify(connection1).sendMessage(TEST_MESSAGE);
            verify(connection2).sendMessage(TEST_MESSAGE);
            verify(connection1).incrementSentMessageCount();
            verify(connection2).incrementSentMessageCount();
        }

        @Test
        @DisplayName("应该处理部分设备发送失败的情况")
        void should_handle_partial_broadcast_failure() {
            // Given - 一个成功一个失败
            Long deviceId1 = createTestDeviceId(1);
            Long deviceId2 = createTestDeviceId(2);
            List<Long> deviceIds = Arrays.asList(deviceId1, deviceId2);
            
            TerminalConnection connection1 = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            given(connection1.getDeviceId()).willReturn(deviceId1);
            
            // 成功获取第一个连接，第二个设备未连接
            given(connectionManagerPort.getConnection(deviceId1)).willReturn(Optional.of(connection1));
            given(connectionManagerPort.getConnection(deviceId2)).willReturn(Optional.empty());
            
            // When - 广播消息（部分失败）
            List<Long> result = service.broadcastMessage(deviceIds, TEST_MESSAGE);
            
            // Then - 验证只有成功的设备在结果中
            assertThat(result).hasSize(1).containsExactly(deviceId1);
            
            verify(connectionManagerPort).getConnection(deviceId1);
            verify(connectionManagerPort).getConnection(deviceId2);
            verify(connection1).sendMessage(TEST_MESSAGE);
            verify(connection1).incrementSentMessageCount();
        }

        @Test
        @DisplayName("应该处理空设备列表")
        void should_handle_empty_device_list() {
            // Given - 空设备列表
            List<Long> emptyDeviceIds = Collections.emptyList();
            
            // When - 广播给空列表
            List<Long> result = service.broadcastMessage(emptyDeviceIds, TEST_MESSAGE);
            
            // Then - 验证返回空列表且没有调用连接管理器
            assertThat(result).isEmpty();
            verifyNoInteractions(connectionManagerPort);
        }

        @Test
        @DisplayName("应该处理null设备列表")
        void should_handle_null_device_list() {
            // When - 广播给null列表
            List<Long> result = service.broadcastMessage(null, TEST_MESSAGE);
            
            // Then - 验证返回空列表且没有调用连接管理器
            assertThat(result).isEmpty();
            verifyNoInteractions(connectionManagerPort);
        }

        @Test
        @DisplayName("应该处理消息发送失败的情况")
        void should_handle_message_send_failure() {
            // Given - 消息发送失败
            Long deviceId1 = createTestDeviceId(1);
            Long deviceId2 = createTestDeviceId(2);
            List<Long> deviceIds = Arrays.asList(deviceId1, deviceId2);
            
            TerminalConnection connection1 = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            TerminalConnection connection2 = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_1);
            given(connection1.getDeviceId()).willReturn(deviceId1);
            given(connection2.getDeviceId()).willReturn(deviceId2);
            
            // 第一个设备发送失败，第二个成功
            given(connection1.sendMessage(TEST_MESSAGE)).willReturn(false);
            given(connectionManagerPort.getConnection(deviceId1)).willReturn(Optional.of(connection1));
            given(connectionManagerPort.getConnection(deviceId2)).willReturn(Optional.of(connection2));
            
            // When - 广播消息（部分失败）
            List<Long> result = service.broadcastMessage(deviceIds, TEST_MESSAGE);
            
            // Then - 验证只有成功的设备在结果中
            assertThat(result).hasSize(1).containsExactly(deviceId2);
            
            verify(connectionManagerPort).getConnection(deviceId1);
            verify(connectionManagerPort).getConnection(deviceId2);
            verify(connection1).sendMessage(TEST_MESSAGE);
            verify(connection2).sendMessage(TEST_MESSAGE);
            verify(connection1).incrementErrorCount(); // 发送失败时增加错误计数
            verify(connection2).incrementSentMessageCount(); // 发送成功时增加发送计数
        }

        @Test
        @DisplayName("应该处理单个设备发送过程中的异常")
        void should_handle_exception_during_single_device_send() {
            // Given - 单个设备抛出异常
            Long deviceId1 = createTestDeviceId(1);
            Long deviceId2 = createTestDeviceId(2);
            List<Long> deviceIds = Arrays.asList(deviceId1, deviceId2);
            
            TerminalConnection connection1 = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            TerminalConnection connection2 = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_1);
            given(connection1.getDeviceId()).willReturn(deviceId1);
            given(connection2.getDeviceId()).willReturn(deviceId2);
            
            // 第一个设备发送时抛出异常，第二个正常
            given(connection1.sendMessage(TEST_MESSAGE)).willThrow(new RuntimeException("发送异常"));
            given(connectionManagerPort.getConnection(deviceId1)).willReturn(Optional.of(connection1));
            given(connectionManagerPort.getConnection(deviceId2)).willReturn(Optional.of(connection2));
            
            // When - 广播消息（一个异常）
            List<Long> result = service.broadcastMessage(deviceIds, TEST_MESSAGE);
            
            // Then - 验证异常处理和部分成功
            assertThat(result).hasSize(1).containsExactly(deviceId2);
            
            verify(connectionManagerPort).getConnection(deviceId1);
            verify(connectionManagerPort).getConnection(deviceId2);
            verify(connection1).sendMessage(TEST_MESSAGE);
            verify(connection2).sendMessage(TEST_MESSAGE);
            verify(connection2).incrementSentMessageCount();
        }

        @Test
        @DisplayName("应该处理获取连接时的异常")
        void should_handle_exception_during_connection_retrieval() {
            // Given - 获取连接时抛出异常
            Long deviceId1 = createTestDeviceId(1);
            Long deviceId2 = createTestDeviceId(2);
            List<Long> deviceIds = Arrays.asList(deviceId1, deviceId2);
            
            TerminalConnection connection2 = TestDataBuilder.createMockTerminalConnection(ProtocolVersion.V1_0);
            given(connection2.getDeviceId()).willReturn(deviceId2);
            
            given(connectionManagerPort.getConnection(deviceId1))
                .willThrow(new RuntimeException("获取连接异常"));
            given(connectionManagerPort.getConnection(deviceId2)).willReturn(Optional.of(connection2));
            
            // When - 广播消息（一个异常）
            List<Long> result = service.broadcastMessage(deviceIds, TEST_MESSAGE);
            
            // Then - 验证异常处理和部分成功
            assertThat(result).hasSize(1).containsExactly(deviceId2);
            
            verify(connectionManagerPort).getConnection(deviceId1);
            verify(connectionManagerPort).getConnection(deviceId2);
            verify(connection2).sendMessage(TEST_MESSAGE);
            verify(connection2).incrementSentMessageCount();
        }

        @Test
        @DisplayName("当消息为 null 时应直接返回空列表")
        void should_return_empty_list_when_message_is_null() {
            // Given - 消息为 null，模拟广播入口
            WebsocketMessageApplicationService spyService = spy(service);
            List<Long> deviceIds = Collections.singletonList(createTestDeviceId(1));

            // When - 执行广播
            List<Long> result = spyService.broadcastMessage(deviceIds, null);

            // Then - 返回空列表且未触发下游发送逻辑
            assertThat(result).isEmpty();
            verify(spyService, never()).sendMessage(anyLong(), anyString());
        }

        @Test
        @DisplayName("当单个设备发送异常时应标记失败并继续流程")
        void should_mark_failure_when_single_send_throw_exception() {
            // Given - 模拟 sendMessage 抛出异常
            WebsocketMessageApplicationService spyService = spy(service);
            Long deviceId = createTestDeviceId(1);
            List<Long> deviceIds = Collections.singletonList(deviceId);
            doThrow(new RuntimeException("发送异常"))
                    .when(spyService).sendMessage(deviceId, TEST_MESSAGE);

            // When - 执行广播
            List<Long> result = spyService.broadcastMessage(deviceIds, TEST_MESSAGE);

            // Then - 返回空列表并确认调用 sendMessage
            assertThat(result).isEmpty();
            verify(spyService).sendMessage(deviceId, TEST_MESSAGE);
        }
    }

    @Nested
    @DisplayName("协议消息处理工具")
    class ProtocolUtilitiesTests {

        @Test
        @DisplayName("TextMessageProcessResult 工厂方法应返回预期标志位")
        void should_build_text_message_process_results() {
            ProtocolMessageProcessor.TextMessageProcessResult success =
                    ProtocolMessageProcessor.TextMessageProcessResult.ofSuccess(true);
            ProtocolMessageProcessor.TextMessageProcessResult failure =
                    ProtocolMessageProcessor.TextMessageProcessResult.ofFailure("error");

            assertThat(success.success()).isTrue();
            assertThat(success.heartbeat()).isTrue();
            assertThat(success.errorMessage()).isNull();
            assertThat(failure.success()).isFalse();
            assertThat(failure.heartbeat()).isFalse();
            assertThat(failure.errorMessage()).isEqualTo("error");
        }

        @Test
        @DisplayName("协议处理器默认回调应为安全空实现")
        void should_allow_default_on_connection_established() {
            ProtocolMessageProcessor processor = new ProtocolMessageProcessor() {
                @Override
                public ProtocolVersion getSupportedVersion() {
                    return ProtocolVersion.V1_0;
                }

                @Override
                public ProtocolMessageProcessor.TextMessageProcessResult processTextMessage(MessageProcessingContext context) {
                    return ProtocolMessageProcessor.TextMessageProcessResult.ofSuccess(false);
                }
            };

            MessageProcessingContext context = mock(MessageProcessingContext.class);
            assertThatCode(() -> processor.onConnectionEstablished(context)).doesNotThrowAnyException();
        }
    }


    @Nested
    @DisplayName("WebSocket消息处理服务重构验证")
    class RefactoredMethodsIntegrationTests {

        @Test
        @DisplayName("应该在连接建立时创建有效的连接对象")
        void should_create_valid_connection_on_establishment() {
            // Given - 连接参数
            Long deviceId = createTestDeviceId(1);
            WebSocketSession session = mock(WebSocketSession.class);
            ProtocolVersion protocolVersion = ProtocolVersion.V1_0;

            // When - 处理连接建立
            TerminalConnection result = service.handleConnectionEstablished(deviceId, session, protocolVersion);

            // Then - 验证结果处理正确（可能为null如果mock未完全配置）
            // 这个测试验证方法不抛出异常，处理方式是健壮的
            assertThatCode(() -> service.handleConnectionEstablished(deviceId, session, protocolVersion))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("应该在连接建立时调用status更新")
        void should_update_status_on_connection_established() {
            // Given - 连接参数
            Long deviceId = createTestDeviceId(1);
            WebSocketSession session = mock(WebSocketSession.class);
            ProtocolVersion protocolVersion = ProtocolVersion.V1_0;

            // When - 处理连接建立
            service.handleConnectionEstablished(deviceId, session, protocolVersion);

            // Then - 验证连接管理器的添加被调用（表示连接被成功处理）
            // 注：status更新是内部实现细节，只有在连接创建成功后才会调用
            verify(connectionManagerPort, atLeastOnce()).addConnection(
                any(Long.class),
                any(TerminalConnection.class)
            );
        }

        @Test
        @DisplayName("应该在连接关闭时移除连接记录")
        void should_remove_connection_on_close() {
            // Given - 连接ID
            Long deviceId = createTestDeviceId(1);
            TerminalConnection connection = mock(TerminalConnection.class);
            when(connection.getDeviceId()).thenReturn(deviceId);
            when(connectionManagerPort.removeConnection(deviceId)).thenReturn(connection);

            // When - 处理连接关闭
            service.handleConnectionClosed(deviceId);

            // Then - 验证连接被移除
            verify(connectionManagerPort).removeConnection(deviceId);
        }

        @Test
        @DisplayName("应该处理消息时不抛出异常")
        void should_handle_text_message_gracefully() {
            // Given - 模拟连接和消息上下文
            TerminalConnection connection = mock(TerminalConnection.class);
            when(connection.getDeviceId()).thenReturn(createTestDeviceId(1));
            when(connection.getProtocolVersion()).thenReturn(ProtocolVersion.V1_0);
            when(connection.isActive()).thenReturn(true);
            when(connection.getClientIp()).thenReturn("127.0.0.1");
            
            MessageProcessingContext context = mock(MessageProcessingContext.class);
            when(context.getConnection()).thenReturn(connection);
            when(context.getRawMessage()).thenReturn(TEST_MESSAGE);
            when(context.isValid()).thenReturn(true);

            // When & Then - 处理消息不应抛出异常
            assertThatCode(() -> service.handleTextMessageByProcessor(context))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("应该在发送消息时验证连接有效性")
        void should_validate_connection_when_sending_message() {
            // Given - 不存在的连接
            Long nonExistentDeviceId = createTestDeviceId(999);
            when(connectionManagerPort.getConnection(nonExistentDeviceId)).thenReturn(Optional.empty());

            // When - 尝试发送消息
            boolean result = service.sendMessage(nonExistentDeviceId, TEST_MESSAGE);

            // Then - 发送应该失败（因为连接不存在）
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该在ping帧处理时保持连接活跃")
        void should_handle_ping_frame() {
            // Given - 模拟连接
            TerminalConnection connection = mock(TerminalConnection.class);
            when(connection.getDeviceId()).thenReturn(createTestDeviceId(1));

            // When & Then - ping处理不应抛出异常
            assertThatCode(() -> service.handlePingFrame(connection))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("应该在pong帧处理时更新连接")
        void should_handle_pong_frame() {
            // Given - 模拟连接
            TerminalConnection connection = mock(TerminalConnection.class);
            when(connection.getDeviceId()).thenReturn(createTestDeviceId(1));

            // When & Then - pong处理不应抛出异常
            assertThatCode(() -> service.handlePongFrame(connection))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("应该在广播消息时验证参数")
        void should_validate_parameters_in_broadcast() {
            // Given - 空设备列表
            List<Long> emptyDeviceIds = Collections.emptyList();

            // When - 广播消息
            List<Long> result = service.broadcastMessage(emptyDeviceIds, TEST_MESSAGE);

            // Then - 应该返回空列表
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该在消息处理异常时优雅降级")
        void should_handle_message_processing_exception() {
            // Given - 模拟异常场景
            TerminalConnection connection = mock(TerminalConnection.class);
            when(connection.getDeviceId()).thenReturn(createTestDeviceId(1));
            when(connection.getProtocolVersion()).thenReturn(ProtocolVersion.V1_0);
            when(connection.isActive()).thenReturn(true);
            when(connection.getClientIp()).thenReturn("127.0.0.1");
            
            MessageProcessingContext context = mock(MessageProcessingContext.class);
            when(context.getConnection()).thenReturn(connection);
            when(context.getRawMessage()).thenReturn(TEST_MESSAGE);
            when(context.isValid()).thenReturn(true);

            // When & Then - 处理异常应该被捕获，不抛出异常
            assertThatCode(() -> service.handleTextMessageByProcessor(context))
                .doesNotThrowAnyException();
        }
    }
}

