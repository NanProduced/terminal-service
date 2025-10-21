package com.colorlight.terminal.infrastructure.websocket.processor.v10;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.application.domain.sensor.SensorReport;
import com.colorlight.terminal.application.dto.websocket.v10.V10WebsocketMessage;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolMessageProcessor.TextMessageProcessResult;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * V10ProtocolMessageProcessor 单元测试
 * <p>
 * 测试策略：
 * 1. 遵循上下文感知测试原则，专注V1.0协议处理器的职责
 * 2. 使用lenient模式优化Mock，避免严格验证
 * 3. 专注核心业务逻辑：心跳处理、GPS数据处理、消息格式解析
 * 4. 避免测试JSON解析库的技术细节
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("V1.0协议消息处理器测试")
class V10ProtocolMessageProcessorTest {

    @Mock
    private TerminalReportUseCase terminalReportUseCase;
    
    @Mock
    private MessageProcessingContext messageProcessingContext;
    
    @Mock
    private TerminalConnection terminalConnection;
    
    @Captor
    private ArgumentCaptor<Long> deviceIdCaptor;
    
    @Captor
    private ArgumentCaptor<LocalDateTime> dateTimeCaptor;
    
    @Captor
    private ArgumentCaptor<List> gpsDataCaptor;
    
    @InjectMocks
    private V10ProtocolMessageProcessor processor;
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {

        public static V10WebsocketMessage buildHeartbeatMessage() {
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setContent("heartbeat");
            return message;
        }

        public static V10WebsocketMessage buildGpsMessage() {
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setName("testDevice");

            // 构建单个GPS数据
            GpsReport gpsReport = new GpsReport();
            gpsReport.setSensorType("gps");
            gpsReport.setLatitude(39.9042);
            gpsReport.setLongitude(116.4074);
            gpsReport.setDeviceTime(LocalDateTime.of(2024, 12, 15, 10, 30, 0));

            List<GpsReport> gpsList = new ArrayList<>();
            gpsList.add(gpsReport);
            message.setGps(gpsList);

            return message;
        }

        public static V10WebsocketMessage buildGpsMessageWithMultiplePoints() {
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setName("testDevice");

            // 构建多个GPS数据点
            GpsReport gpsReport1 = new GpsReport();
            gpsReport1.setSensorType("gps");
            gpsReport1.setLatitude(39.9042);
            gpsReport1.setLongitude(116.4074);

            GpsReport gpsReport2 = new GpsReport();
            gpsReport2.setSensorType("gps");
            gpsReport2.setLatitude(40.0000);
            gpsReport2.setLongitude(116.5000);

            List<GpsReport> gpsList = new ArrayList<>();
            gpsList.add(gpsReport1);
            gpsList.add(gpsReport2);
            message.setGps(gpsList);

            return message;
        }

        public static V10WebsocketMessage buildUnknownMessage() {
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setContent("unknown_operation");
            message.setName("testDevice");
            return message;
        }

        public static String buildInvalidJsonMessage() {
            return "{\"content\":\"test\",\"invalid_json\":}";
        }
    }
    
    @BeforeEach
    void setUp() {
        // 基础Mock设置 - 使用lenient模式避免严格验证
        lenient().when(messageProcessingContext.getDeviceId()).thenReturn(12345L);
        lenient().when(messageProcessingContext.getConnection()).thenReturn(terminalConnection);
        
        // 默认sendMessage成功
        lenient().when(messageProcessingContext.sendMessage(anyString())).thenReturn(true);
        
        // 默认异步处理成功
        lenient().doNothing().when(terminalReportUseCase).asyncHandleSensorReport(anyLong(), any(LocalDateTime.class), any(List.class));
    }
    
    @Nested
    @DisplayName("协议版本支持测试")
    class ProtocolVersionSupportTest {
        
        @Test
        @DisplayName("应该支持V1.0协议版本")
        void should_support_v10_protocol_version() {
            // When - 获取支持的协议版本
            ProtocolVersion supportedVersion = processor.getSupportedVersion();
            
            // Then - 验证支持V1.0协议
            assertThat(supportedVersion).isEqualTo(ProtocolVersion.V1_0);
        }
    }
    
    @Nested
    @DisplayName("心跳消息处理测试")
    class HeartbeatMessageTest {
        
        @Test
        @DisplayName("应该成功处理空消息心跳")
        void should_handle_empty_message_heartbeat_successfully() {
            // Given - 准备空消息（V1.0协议的心跳方式之一）
            when(messageProcessingContext.getRawMessage()).thenReturn("");
            
            // When - 处理心跳消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证心跳处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();
            assertThat(result.errorMessage()).isNull();
            
            // 验证发送心跳响应
            verify(messageProcessingContext).sendMessage("heartbeat");
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
            
            verify(messageProcessingContext).sendMessage("heartbeat");
        }
        
        @Test
        @DisplayName("应该成功处理content为heartbeat的消息")
        void should_handle_content_heartbeat_message_successfully() {
            // Given - 准备content为heartbeat的JSON消息
            V10WebsocketMessage heartbeatMessage = TestDataBuilder.buildHeartbeatMessage();
            String jsonMessage = JsonUtils.toJson(heartbeatMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理心跳消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证心跳处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();
            
            verify(messageProcessingContext).sendMessage("heartbeat");
        }
        
        @Test
        @DisplayName("当心跳响应发送失败时应该返回失败结果")
        void should_return_failure_when_heartbeat_response_send_fails() {
            // Given - 准备心跳消息，但发送失败
            when(messageProcessingContext.getRawMessage()).thenReturn("");
            when(messageProcessingContext.sendMessage("heartbeat")).thenReturn(false);
            
            // When - 处理心跳消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证失败结果
            assertThat(result.success()).isFalse();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("PONG消息发送失败");
        }
    }
    
    @Nested
    @DisplayName("GPS数据处理测试")
    class GpsDataProcessingTest {
        
        @Test
        @DisplayName("应该成功处理GPS数据消息")
        void should_handle_gps_data_message_successfully() {
            // Given - 准备包含GPS数据的消息
            V10WebsocketMessage gpsMessage = TestDataBuilder.buildGpsMessage();
            String jsonMessage = JsonUtils.toJson(gpsMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理GPS消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse(); // GPS消息不是心跳
            assertThat(result.errorMessage()).isNull();
            
            // 验证异步处理GPS数据的调用
            verify(terminalReportUseCase).asyncHandleSensorReport(
                deviceIdCaptor.capture(),
                dateTimeCaptor.capture(),
                gpsDataCaptor.capture()
            );

            assertThat(deviceIdCaptor.getValue()).isEqualTo(12345L);
            assertThat(dateTimeCaptor.getValue()).isNotNull();
            // 验证GPS数据List被正确传递
            List<SensorReport> capturedGpsData = gpsDataCaptor.getValue();
            assertThat(capturedGpsData).isNotEmpty();
            assertThat(capturedGpsData).isEqualTo(new ArrayList<>(gpsMessage.getGps()));
        }
        
        @Test
        @DisplayName("应该成功处理包含多个GPS点的数据")
        void should_handle_multiple_gps_points_successfully() {
            // Given - 准备包含多个GPS点的消息
            V10WebsocketMessage multiGpsMessage = TestDataBuilder.buildGpsMessageWithMultiplePoints();
            String jsonMessage = JsonUtils.toJson(multiGpsMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理GPS消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();

            // 验证GPS数据被正确传递
            verify(terminalReportUseCase).asyncHandleSensorReport(
                eq(12345L),
                any(LocalDateTime.class),
                any(List.class)
            );
        }
        
        @Test
        @DisplayName("应该成功处理空GPS数据列表")
        void should_handle_empty_gps_list_as_unknown_message() {
            // Given - 准备GPS列表为空的消息
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setContent("some_content");
            message.setGps(Collections.emptyList()); // 空GPS列表
            String jsonMessage = JsonUtils.toJson(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);

            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);

            // Then - 验证作为未知消息处理
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();

            // 验证不调用GPS处理
            verify(terminalReportUseCase, never()).asyncHandleSensorReport(anyLong(), any(), any(List.class));
        }

        @Test
        @DisplayName("应该成功处理null GPS数据")
        void should_handle_null_gps_data_as_unknown_message() {
            // Given - 准备GPS字段为null的消息
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setContent("some_content");
            message.setGps(null); // null GPS数据
            String jsonMessage = JsonUtils.toJson(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);

            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);

            // Then - 验证作为未知消息处理
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();

            // 验证不调用GPS处理
            verify(terminalReportUseCase, never()).asyncHandleSensorReport(anyLong(), any(), any(List.class));
        }
        
        @Test
        @DisplayName("当GPS数据处理异步调用异常时应该捕获并记录")
        void should_catch_and_log_gps_processing_exception() {
            // Given - 准备GPS消息，但异步处理抛出异常
            V10WebsocketMessage gpsMessage = TestDataBuilder.buildGpsMessage();
            String jsonMessage = JsonUtils.toJson(gpsMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            doThrow(new RuntimeException("GPS处理服务异常"))
                .when(terminalReportUseCase).asyncHandleSensorReport(anyLong(), any(LocalDateTime.class), any(List.class));
            
            // When - 处理GPS消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证异常被捕获并返回失败结果
            assertThat(result.success()).isFalse();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).contains("V1.0消息处理异常");
        }
    }
    
    @Nested
    @DisplayName("未知消息处理测试")
    class UnknownMessageTest {
        
        @Test
        @DisplayName("应该处理未知消息类型并返回成功")
        void should_handle_unknown_message_type_successfully() {
            // Given - 准备未知消息类型
            V10WebsocketMessage unknownMessage = TestDataBuilder.buildUnknownMessage();
            String jsonMessage = JsonUtils.toJson(unknownMessage);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理未知消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证处理结果（成功但非心跳）
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).isNull();
            
            // 验证不调用任何业务处理
            verify(terminalReportUseCase, never()).asyncHandleSensorReport(anyLong(), any(), any(List.class));
            verify(messageProcessingContext, never()).sendMessage(anyString());
        }
        
        @Test
        @DisplayName("应该处理只有content字段的消息")
        void should_handle_content_only_message() {
            // Given - 准备只有content字段的消息
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setContent("some_operation");
            String jsonMessage = JsonUtils.toJson(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证作为未知消息成功处理
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isFalse();
            
            verify(terminalReportUseCase, never()).asyncHandleSensorReport(anyLong(), any(), any(List.class));
        }
    }
    
    @Nested
    @DisplayName("消息解析异常处理测试")
    class MessageParsingExceptionTest {
        
        @Test
        @DisplayName("应该处理JSON解析异常")
        void should_handle_json_parsing_exception() {
            // Given - 准备无效JSON消息
            String invalidJsonMessage = TestDataBuilder.buildInvalidJsonMessage();
            when(messageProcessingContext.getRawMessage()).thenReturn(invalidJsonMessage);
            
            // When - 处理无效JSON消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证异常处理
            assertThat(result.success()).isFalse();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).contains("V1.0消息处理异常");
            
            // 验证不调用任何业务处理
            verify(terminalReportUseCase, never()).asyncHandleSensorReport(anyLong(), any(), any(List.class));
        }
        
        @Test
        @DisplayName("应该处理null消息")
        void should_handle_null_message() {
            // Given - 准备null消息
            when(messageProcessingContext.getRawMessage()).thenReturn(null);
            
            // When - 处理null消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证作为心跳处理（StringUtils.isBlank对null返回true）
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();
            
            verify(messageProcessingContext).sendMessage("heartbeat");
        }
        
        @Test
        @DisplayName("应该处理消息上下文异常")
        void should_handle_context_exception() {
            // Given - 准备正常消息，但上下文调用异常
            lenient().when(messageProcessingContext.getRawMessage()).thenReturn("");
            lenient().when(messageProcessingContext.getDeviceId()).thenThrow(new RuntimeException("上下文异常"));
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证异常处理
            assertThat(result.success()).isFalse();
            assertThat(result.heartbeat()).isFalse();
            assertThat(result.errorMessage()).contains("V1.0消息处理异常");
        }
    }
    
    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {
        
        @Test
        @DisplayName("应该处理大小写不敏感的heartbeat")
        void should_handle_case_insensitive_heartbeat() {
            // Given - 准备大写的HEARTBEAT消息
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setContent("HEARTBEAT");
            String jsonMessage = JsonUtils.toJson(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证心跳处理
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();
            
            verify(messageProcessingContext).sendMessage("heartbeat");
        }
        
        @Test
        @DisplayName("应该处理混合大小写的HeartBeat")
        void should_handle_mixed_case_heartbeat() {
            // Given - 准备混合大小写的HeartBeat消息
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setContent("HeartBeat");
            String jsonMessage = JsonUtils.toJson(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);
            
            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);
            
            // Then - 验证心跳处理
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();
            
            verify(messageProcessingContext).sendMessage("heartbeat");
        }
        
        @Test
        @DisplayName("应该处理同时包含content和gps的消息（优先处理heartbeat）")
        void should_prioritize_heartbeat_over_gps_when_both_present() {
            // Given - 准备同时包含heartbeat content和GPS数据的消息
            V10WebsocketMessage message = new V10WebsocketMessage();
            message.setContent("heartbeat");

            // 构建GPS数据列表
            GpsReport gpsReport = new GpsReport();
            gpsReport.setSensorType("gps"); // 必须设置sensorType用于JSON序列化
            gpsReport.setLatitude(39.9042);
            gpsReport.setLongitude(116.4074);
            List<GpsReport> gpsList = new ArrayList<>();
            gpsList.add(gpsReport);
            message.setGps(gpsList);

            String jsonMessage = JsonUtils.toJson(message);
            when(messageProcessingContext.getRawMessage()).thenReturn(jsonMessage);

            // When - 处理消息
            TextMessageProcessResult result = processor.processTextMessage(messageProcessingContext);

            // Then - 验证优先处理心跳
            assertThat(result.success()).isTrue();
            assertThat(result.heartbeat()).isTrue();

            verify(messageProcessingContext).sendMessage("heartbeat");
            verify(terminalReportUseCase, never()).asyncHandleSensorReport(anyLong(), any(), any(List.class));
        }
    }
}