package com.colorlight.terminal.infrastructure.websocket.processor.v11;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolMessageProcessor.TextMessageProcessResult;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.utils.JsonUtils;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * V11ProtocolMessageProcessor 单元测试
 * <p>
 * 测试策略：
 * 1. 遵循上下文感知测试原则，专注V1.1协议处理器的职责
 * 2. 使用lenient模式优化Mock，避免严格验证
 * 3. 专注核心业务逻辑：心跳处理、消息验证、路由委托、错误分类处理
 * 4. 避免测试JSON解析库和路由器的技术细节
 * 
 * <p>
 * V11ProtocolMessageProcessor 业务逻辑总结：
 * - 核心职责：处理V1.1协议的结构化WebSocket消息
 * - 心跳机制：空消息心跳，响应空字符串（省流量设计）
 * - 消息格式：结构化JSON（messageId/receiptId/type/data）
 * - 验证机制：严格的messageId必填验证
 * - 路由策略：委托给V11OperationHandleRouter处理具体业务
 * - 错误处理：完善的错误分类和客户端反馈机制
 * - 异常体系：技术异常、业务异常、系统异常的分层处理
 * </p>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("V1.1协议消息处理器测试")
class V11ProtocolMessageProcessorTest {

    @Mock
    private V11OperationHandleRouter operationHandleRouter;
    
    @Mock
    private MessageProcessingContext messageProcessingContext;
    
    @Captor
    private ArgumentCaptor<V11WebsocketMessage> messageCaptor;
    
    @Captor
    private ArgumentCaptor<V11WebsocketMessage> errorMessageCaptor;
    
    @InjectMocks
    private V11ProtocolMessageProcessor processor;
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        public static V11WebsocketMessage buildValidMessage() {
            V11WebsocketMessage message = new V11WebsocketMessage();
            message.setMessageId(1001);
            message.setType(V11WebsocketMessageTypeEnum.STATUS_REPORT.getId());
            message.setData("{\"status\":\"online\"}");
            return message;
        }
        
        public static V11WebsocketMessage buildValidCommandMessage() {
            V11WebsocketMessage message = new V11WebsocketMessage();
            message.setMessageId(2001);
            message.setReceiptId(1001);
            message.setType(V11WebsocketMessageTypeEnum.COMMAND.getId());
            message.setData("{\"command\":\"restart\"}");
            return message;
        }
        
        public static V11WebsocketMessage buildMessageWithoutMessageId() {
            V11WebsocketMessage message = new V11WebsocketMessage();
            // messageId为null
            message.setType(V11WebsocketMessageTypeEnum.STATUS_REPORT.getId());
            message.setData("{\"status\":\"online\"}");
            return message;
        }
        
        public static V11WebsocketMessage buildComplexMessage() {
            V11WebsocketMessage message = new V11WebsocketMessage();
            message.setMessageId(3001);
            message.setReceiptId(2001);
            message.setType(V11WebsocketMessageTypeEnum.MONITOR_REPORT.getId());
            message.setData("{\"sensors\":[{\"type\":\"temperature\",\"value\":25.6}]}");
            return message;
        }
        
        public static String buildInvalidJsonMessage() {
            return "{\"messageId\":1001,\"type\":6,\"data\":}"; // 无效JSON
        }
        
        public static String buildValidJsonString(V11WebsocketMessage message) {
            return JsonUtils.toJson(message);
        }
    }
    
    @BeforeEach
    void setUp() {
        // 基础Mock设置 - 使用lenient模式避免严格验证
        lenient().when(messageProcessingContext.getDeviceId()).thenReturn(12345L);
        
        // 默认sendMessage成功
        lenient().when(messageProcessingContext.sendMessage(anyString())).thenReturn(true);
        
        // 默认路由处理成功
        lenient().doNothing().when(operationHandleRouter).handleMessageByType(any(), any());
    }
    
    @Nested
    @DisplayName("协议版本支持测试")
    class ProtocolVersionSupportTest {
        
        @Test
        @DisplayName("应该支持V1.1协议版本")
        void should_support_v11_protocol_version() {
            // When - 获取支持的协议版本
            ProtocolVersion supportedVersion = processor.getSupportedVersion();
            
            // Then - 验证支持V1.1协议
            assertThat(supportedVersion).isEqualTo(ProtocolVersion.V1_1);
        }
    }
    
    @Nested
    @DisplayName("心跳消息处理测试")
    class HeartbeatMessageTest {
        
        @Test
        @DisplayName("应该成功处理空消息心跳")
        void should_handle_empty_message_heartbeat_successfully() {
            // Given - 准备空消息（V1.1协议的心跳方式）
            when(messageProcessingContext.getRawMessage()).thenReturn("");
            
            // When - 处理心跳消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证心跳处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();
            assertThat(result.errorMessage()).isNull();
            
            // 验证发送空字符串心跳响应（V1.1省流量设计）
            verify(messageProcessingContext).sendMessage("");
        }
        
        @Test
        @DisplayName("应该成功处理空白消息心跳")
        void should_handle_blank_message_heartbeat_successfully() {
            // Given - 准备空白消息
            when(messageProcessingContext.getRawMessage()).thenReturn("   ");
            
            // When - 处理心跳消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证心跳处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();
            
            verify(messageProcessingContext).sendMessage("");
        }
        
        @Test
        @DisplayName("应该成功处理null消息心跳")
        void should_handle_null_message_heartbeat_successfully() {
            // Given - 准备null消息
            when(messageProcessingContext.getRawMessage()).thenReturn(null);
            
            // When - 处理心跳消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证心跳处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();
            
            verify(messageProcessingContext).sendMessage("");
        }
        
        @Test
        @DisplayName("当心跳响应发送失败时应该返回失败结果")
        void should_return_failure_when_heartbeat_response_send_fails() {
            // Given - 准备心跳消息，但发送失败
            when(messageProcessingContext.getRawMessage()).thenReturn("");
            when(messageProcessingContext.sendMessage("")).thenReturn(false);
            
            // When - 处理心跳消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证失败结果
            assertThat(result.success()).isFalse();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("PONG消息发送失败");
        }
    }
    
    @Nested
    @DisplayName("消息验证测试")
    class MessageValidationTest {
        
        @Test
        @DisplayName("应该成功处理有效的V1.1消息")
        void should_handle_valid_v11_message_successfully() {
            // Given - 准备有效的V1.1消息
            V11WebsocketMessage validMessage = TestDataBuilder.buildValidMessage();
            String jsonMessage = TestDataBuilder.buildValidJsonString(validMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse(); // 非心跳消息
            assertThat(result.errorMessage()).isNull();
            
            // 验证消息被路由处理
            verify(operationHandleRouter).handleMessageByType(eq(messageProcessingContext), messageCaptor.capture());
            
            V11WebsocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getMessageId()).isEqualTo(1001);
            assertThat(capturedMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.STATUS_REPORT.getId());
        }
        
        @Test
        @DisplayName("应该成功处理包含receiptId的命令消息")
        void should_handle_command_message_with_receipt_id_successfully() {
            // Given - 准备包含receiptId的命令消息
            V11WebsocketMessage commandMessage = TestDataBuilder.buildValidCommandMessage();
            String jsonMessage = TestDataBuilder.buildValidJsonString(commandMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();
            
            // 验证消息内容正确传递
            verify(operationHandleRouter).handleMessageByType(eq(messageProcessingContext), messageCaptor.capture());
            
            V11WebsocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getMessageId()).isEqualTo(2001);
            assertThat(capturedMessage.getReceiptId()).isEqualTo(1001);
            assertThat(capturedMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.COMMAND.getId());
        }
        
        @Test
        @DisplayName("当消息ID为空时应该返回失败并发送错误消息")
        void should_fail_and_send_error_when_message_id_is_null() {
            // Given - 准备缺少messageId的消息
            V11WebsocketMessage messageWithoutId = TestDataBuilder.buildMessageWithoutMessageId();
            String jsonMessage = TestDataBuilder.buildValidJsonString(messageWithoutId);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证失败结果
            assertThat(result.success()).isFalse();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("V1.1-消息Id错误");
            
            // 验证发送错误消息给客户端
            verify(messageProcessingContext).sendMessage(errorMessageCaptor.capture());
            V11WebsocketMessage errorMessage = errorMessageCaptor.getValue();
            
            // 验证错误消息格式正确
            assertThat(errorMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorMessage.getData()).isNotNull();
            
            // 验证不调用路由处理
            verify(operationHandleRouter, never()).handleMessageByType(any(), any());
        }
        
        @Test
        @DisplayName("应该成功处理复杂的监控上报消息")
        void should_handle_complex_monitor_report_message_successfully() {
            // Given - 准备复杂的监控上报消息
            V11WebsocketMessage complexMessage = TestDataBuilder.buildComplexMessage();
            String jsonMessage = TestDataBuilder.buildValidJsonString(complexMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();
            
            // 验证复杂消息的字段都正确传递
            verify(operationHandleRouter).handleMessageByType(eq(messageProcessingContext), messageCaptor.capture());
            
            V11WebsocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getMessageId()).isEqualTo(3001);
            assertThat(capturedMessage.getReceiptId()).isEqualTo(2001);
            assertThat(capturedMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.MONITOR_REPORT.getId());
            assertThat(capturedMessage.getData()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTest {
        
        @Test
        @DisplayName("应该处理JSON解析技术异常")
        void should_handle_json_parsing_technical_exception() {
            // Given - 准备无效JSON消息
            String invalidJsonMessage = TestDataBuilder.buildInvalidJsonMessage();
            when(messageProcessingContext.getRawMessage()).thenReturn(invalidJsonMessage);
            
            // When - 处理无效JSON消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证技术异常处理
            assertThat(result.success()).isFalse();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("V1.1-消息序列化异常");
            
            // 验证发送技术异常错误消息
            verify(messageProcessingContext).sendMessage(errorMessageCaptor.capture());
            V11WebsocketMessage errorMessage = errorMessageCaptor.getValue();
            
            assertThat(errorMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorMessage.getData()).isNotNull();
        }
        
        @Test
        @DisplayName("应该处理路由器抛出的业务异常 - 无效消息数据")
        void should_handle_business_exception_invalid_message_data() {
            // Given - 准备有效消息，但路由器抛出业务异常
            V11WebsocketMessage validMessage = TestDataBuilder.buildValidMessage();
            String jsonMessage = TestDataBuilder.buildValidJsonString(validMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            BusinessException businessException = new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_DATA);
            doThrow(businessException).when(operationHandleRouter).handleMessageByType(any(), any());
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证业务异常处理
            assertThat(result.success()).isFalse();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("V1.1-消息处理异常");
            
            // 验证发送对应的错误消息
            verify(messageProcessingContext).sendMessage(errorMessageCaptor.capture());
            V11WebsocketMessage errorMessage = errorMessageCaptor.getValue();
            
            assertThat(errorMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorMessage.getData()).isNotNull();
        }
        
        @Test
        @DisplayName("应该处理路由器抛出的业务异常 - 无效消息类型")
        void should_handle_business_exception_invalid_message_type() {
            // Given - 准备有效消息，但路由器抛出消息类型异常
            V11WebsocketMessage validMessage = TestDataBuilder.buildValidMessage();
            String jsonMessage = TestDataBuilder.buildValidJsonString(validMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            BusinessException businessException = new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_TYPE);
            doThrow(businessException).when(operationHandleRouter).handleMessageByType(any(), any());
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证业务异常处理
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("V1.1-消息处理异常");
            
            // 验证发送对应的错误消息
            verify(messageProcessingContext).sendMessage(errorMessageCaptor.capture());
            V11WebsocketMessage errorMessage = errorMessageCaptor.getValue();
            
            assertThat(errorMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorMessage.getData()).isNotNull();
        }
        
        @Test
        @DisplayName("应该处理路由器抛出的其他业务异常")
        void should_handle_other_business_exceptions() {
            // Given - 准备有效消息，但路由器抛出其他业务异常
            V11WebsocketMessage validMessage = TestDataBuilder.buildValidMessage();
            String jsonMessage = TestDataBuilder.buildValidJsonString(validMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            BusinessException businessException = new BusinessException(CommonErrorCode.PARAMETER_MISSING);
            doThrow(businessException).when(operationHandleRouter).handleMessageByType(any(), any());
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证其他业务异常处理（默认为INVALID_MESSAGE_DATA）
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("V1.1-消息处理异常");
            
            // 验证发送默认错误消息
            verify(messageProcessingContext).sendMessage(errorMessageCaptor.capture());
            V11WebsocketMessage errorMessage = errorMessageCaptor.getValue();
            
            assertThat(errorMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorMessage.getData()).isNotNull();
        }
        
        @Test
        @DisplayName("应该处理路由器抛出的系统异常")
        void should_handle_system_exceptions() {
            // Given - 准备有效消息，但路由器抛出系统异常
            V11WebsocketMessage validMessage = TestDataBuilder.buildValidMessage();
            String jsonMessage = TestDataBuilder.buildValidJsonString(validMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            RuntimeException systemException = new RuntimeException("系统内部错误");
            doThrow(systemException).when(operationHandleRouter).handleMessageByType(any(), any());
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证系统异常处理
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("V1.1-消息处理异常");
            
            // 验证发送服务器错误消息
            verify(messageProcessingContext).sendMessage(errorMessageCaptor.capture());
            V11WebsocketMessage errorMessage = errorMessageCaptor.getValue();
            
            assertThat(errorMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorMessage.getData()).isNotNull();
        }
        
    }
    
    @Nested
    @DisplayName("错误消息格式测试")
    class ErrorMessageFormatTest {
        
        @Test
        @DisplayName("错误消息应该包含正确的V1.1格式结构")
        void should_generate_correct_v11_error_message_format() {
            // Given - 准备缺少messageId的消息
            V11WebsocketMessage messageWithoutId = TestDataBuilder.buildMessageWithoutMessageId();
            String jsonMessage = TestDataBuilder.buildValidJsonString(messageWithoutId);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证错误消息格式
            verify(messageProcessingContext).sendMessage(errorMessageCaptor.capture());
            V11WebsocketMessage errorMessage = errorMessageCaptor.getValue();
            
            // 验证错误消息结构
            assertThat(errorMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorMessage.getData()).isNotNull();
        }
        
        @Test
        @DisplayName("技术异常错误消息应该包含异常详情")
        void should_include_exception_details_in_technical_error_message() {
            // Given - 准备无效JSON消息
            String invalidJsonMessage = TestDataBuilder.buildInvalidJsonMessage();
            when(messageProcessingContext.getRawMessage()).thenReturn(invalidJsonMessage);
            
            // When - 处理无效JSON消息
            processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证错误消息包含异常详情
            verify(messageProcessingContext).sendMessage(errorMessageCaptor.capture());
            V11WebsocketMessage errorMessage = errorMessageCaptor.getValue();
            
            // 验证包含技术异常信息
            assertThat(errorMessage.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorMessage.getData()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {
        
        @Test
        @DisplayName("应该处理messageId为0的消息")
        void should_handle_message_with_zero_message_id() {
            // Given - 准备messageId为0的消息
            V11WebsocketMessage message = TestDataBuilder.buildValidMessage();
            message.setMessageId(0);
            String jsonMessage = TestDataBuilder.buildValidJsonString(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果（messageId为0也是有效的）
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();
            
            verify(operationHandleRouter).handleMessageByType(any(), any());
        }
        
        @Test
        @DisplayName("应该处理没有data字段的消息")
        void should_handle_message_without_data_field() {
            // Given - 准备没有data字段的消息
            V11WebsocketMessage message = new V11WebsocketMessage();
            message.setMessageId(1001);
            message.setType(V11WebsocketMessageTypeEnum.COMMAND.getId());
            // 不设置data字段
            String jsonMessage = TestDataBuilder.buildValidJsonString(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();
            
            verify(operationHandleRouter).handleMessageByType(eq(messageProcessingContext), messageCaptor.capture());
            
            V11WebsocketMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getMessageId()).isEqualTo(1001);
            assertThat(capturedMessage.getData()).isNull();
        }
        
        @Test
        @DisplayName("应该处理负数messageId的消息")
        void should_handle_message_with_negative_message_id() {
            // Given - 准备负数messageId的消息
            V11WebsocketMessage message = TestDataBuilder.buildValidMessage();
            message.setMessageId(-1);
            String jsonMessage = TestDataBuilder.buildValidJsonString(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果（负数messageId也是有效的）
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();
            
            verify(operationHandleRouter).handleMessageByType(any(), any());
        }
    }
}