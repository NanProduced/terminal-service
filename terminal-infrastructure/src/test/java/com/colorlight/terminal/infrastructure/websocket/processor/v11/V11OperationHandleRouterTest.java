package com.colorlight.terminal.infrastructure.websocket.processor.v11;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.application.port.inbound.program.TerminalProgramUseCase;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.converter.V11WebsocketDtoConverter;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.CommandResponse;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.TerminalLogDTO;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * V11OperationHandleRouter 单元测试
 * <p>
 * 测试策略：
 * 1. 遵循上下文感知测试原则，专注路由器层的职责
 * 2. 使用lenient模式优化Mock，避免严格验证
 * 3. 专注核心业务逻辑：消息路由、数据验证、UseCase协调、异常处理
 * 4. 避免测试UseCase内部逻辑和上层已验证的条件
 * 
 * <p>
 * V11OperationHandleRouter 业务逻辑总结：
 * - 核心职责：V1.1协议的操作分发路由器，根据消息类型路由到对应处理逻辑
 * - 路由机制：基于V11WebsocketMessageTypeEnum进行switch-case分发
 * - 消息分类：请求-响应类型、上报类型、确认类型
 * - 数据处理：空数据使用EMPTY_JSON，通过转换器进行DTO转换
 * - 关键验证：指令确认的commandId验证、终端日志的数据验证
 * - 异常处理：捕获UseCase异常并发送错误响应
 * </p>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("V1.1操作路由器测试")
class V11OperationHandleRouterTest {

    @Mock
    private TerminalCommandUseCase terminalCommandUseCase;
    
    @Mock
    private TerminalReportUseCase terminalReportUseCase;
    
    @Mock
    private TerminalProgramUseCase terminalProgramUseCase;
    
    @Mock
    private V11WebsocketDtoConverter dtoConverter;
    
    @Mock
    private MessageProcessingContext messageProcessingContext;
    
    @Captor
    private ArgumentCaptor<V11WebsocketMessage> messageCaptor;
    
    @Captor
    private ArgumentCaptor<String> dataCaptor;
    
    @Captor
    private ArgumentCaptor<List<TerminalLog>> terminalLogsCaptor;
    
    @InjectMocks
    private V11OperationHandleRouter router;
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        public static V11WebsocketMessage buildMessage(V11WebsocketMessageTypeEnum type, Integer messageId) {
            V11WebsocketMessage message = new V11WebsocketMessage();
            message.setType(type.getId());
            message.setMessageId(messageId);
            return message;
        }
        
        public static V11WebsocketMessage buildMessageWithData(V11WebsocketMessageTypeEnum type, Integer messageId, Object data) {
            V11WebsocketMessage message = buildMessage(type, messageId);
            message.setData(data);
            return message;
        }
        
        public static V11WebsocketMessage buildConfirmCommandMessage(Integer messageId, int commandId, String content) {
            V11WebsocketMessage message = buildMessage(V11WebsocketMessageTypeEnum.CONFIRM_COMMAND, messageId);
            // 创建实际的Map对象而不是JSON字符串，避免双重序列化
            java.util.Map<String, Object> commandData = new java.util.HashMap<>();
            commandData.put("parent", commandId);
            commandData.put("content", content);
            message.setData(commandData);
            return message;
        }
        
        public static V11WebsocketMessage buildInvalidTypeMessage(Integer messageId) {
            V11WebsocketMessage message = new V11WebsocketMessage();
            message.setType(999); // 无效的消息类型
            message.setMessageId(messageId);
            return message;
        }
        
        public static List<TerminalCommand> buildTerminalCommands() {
            TerminalCommand command1 = TerminalCommand.builder()
                .commandId(1001)
                .deviceId(12345L)
                .contentRaw("restart")
                .build();
            
            TerminalCommand command2 = TerminalCommand.builder()
                .commandId(1002)
                .deviceId(12345L)
                .contentRaw("update")
                .build();
                
            return Arrays.asList(command1, command2);
        }
        
        public static List<CommandResponse> buildCommandResponses() {
            CommandResponse response1 = new CommandResponse();
            response1.setId(1001);
            response1.setPost(12345);
            
            CommandResponse response2 = new CommandResponse();
            response2.setId(1002);
            response2.setPost(12345);
            
            return Arrays.asList(response1, response2);
        }
        
        public static List<TerminalLogDTO> buildTerminalLogDTOs() {
            TerminalLogDTO log1 = new TerminalLogDTO();
            log1.setDeviceId(12345);
            log1.setLogType("INFO");
            log1.setDescription("系统启动");
            
            TerminalLogDTO log2 = new TerminalLogDTO();
            log2.setDeviceId(12345);
            log2.setLogType("ERROR");
            log2.setDescription("连接失败");
            
            return Arrays.asList(log1, log2);
        }
        
        public static List<TerminalLog> buildTerminalLogs() {
            TerminalLog log1 = TerminalLog.builder()
                .deviceId(12345L)
                .logType("runtime")
                .logSubtype1("memory")
                .logArg1("系统启动")
                .build();
                
            TerminalLog log2 = TerminalLog.builder()
                .deviceId(12345L)
                .logType("connectivity")
                .logSubtype1("4g")
                .logSubtype2("redial")
                .logArg1("连接失败")
                .build();
                
            return Arrays.asList(log1, log2);
        }
    }
    
    @BeforeEach
    void setUp() {
        // 基础Mock设置 - 使用lenient模式避免严格验证
        lenient().when(messageProcessingContext.getDeviceId()).thenReturn(12345L);
        lenient().when(messageProcessingContext.sendMessage(any(V11WebsocketMessage.class))).thenReturn(true);
        lenient().when(messageProcessingContext.sendMessage(anyString())).thenReturn(true);
        
        // 默认转换器行为
        lenient().when(dtoConverter.convertToCommandResponses(any())).thenReturn(TestDataBuilder.buildCommandResponses());
        lenient().when(dtoConverter.convertToTerminalLogs(any())).thenReturn(TestDataBuilder.buildTerminalLogs());
        
        // 默认UseCase行为 - 直接stub void方法
        lenient().when(terminalCommandUseCase.getPendingCommands(any(Long.class))).thenReturn(TestDataBuilder.buildTerminalCommands());
        lenient().doNothing().when(terminalCommandUseCase).confirmCommandExecution(any(Long.class), any(Integer.class), anyString());
        lenient().when(terminalProgramUseCase.getSchedule(any(Long.class))).thenReturn(null);
        
        // 使用doNothing()来配置void方法，避免方法签名问题
        lenient().doNothing().when(terminalReportUseCase).asyncHandleMediaPlayRecordReport(any(Long.class), anyString());
        lenient().doNothing().when(terminalReportUseCase).asyncHandleProgramPlayRecordReport(any(Long.class), anyString());
        lenient().doNothing().when(terminalReportUseCase).asyncHandleSensorReport(any(Long.class), any(LocalDateTime.class), anyString());
        lenient().doNothing().when(terminalReportUseCase).asyncSaveTerminalLog(any(Long.class), any());
    }
    
    @Nested
    @DisplayName("消息路由测试")
    class MessageRoutingTest {
        
        @Test
        @DisplayName("应该处理无效消息类型并抛出异常")
        void should_throw_exception_for_invalid_message_type() {
            // Given - 准备无效消息类型
            V11WebsocketMessage invalidMessage = TestDataBuilder.buildInvalidTypeMessage(1001);
            
            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> router.handleMessageByType(messageProcessingContext, invalidMessage))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM3001");
        }
        
        @Test
        @DisplayName("应该处理已弃用的心跳消息")
        @SuppressWarnings("deprecation")
        void should_handle_deprecated_heartbeat_message() {
            // Given - 准备已弃用的心跳消息
            V11WebsocketMessage heartbeatMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.HEARTBEAT, 1001);
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, heartbeatMessage);
            
            // Then - 验证发送空字符串响应
            verify(messageProcessingContext).sendMessage("");
        }
        
        @Test
        @DisplayName("应该正确路由COMMAND消息类型")
        void should_route_command_message_correctly() {
            // Given - 准备COMMAND消息
            V11WebsocketMessage commandMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.COMMAND, 1001);
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, commandMessage);
            
            // Then - 验证调用了指令处理逻辑
            verify(terminalCommandUseCase).getPendingCommands(12345L);
            verify(dtoConverter).convertToCommandResponses(any());
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.COMMAND.getId());
            assertThat(response.getReceiptId()).isEqualTo(1001);
        }

        @Test
        @DisplayName("应该正确路由SCHEDULE消息类型")
        void should_handle_schedule_request() {
            // Given - 准备排程获取消息
            V11WebsocketMessage scheduleMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.SCHEDULE, 8001);

            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, scheduleMessage);

            // Then - 验证发送排程响应（当前为null数据）
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.SCHEDULE.getId());
            assertThat(response.getReceiptId()).isEqualTo(8001);
            assertThat(response.getData()).isNull(); // 待实现功能
        }
        
        @Test
        @DisplayName("应该正确路由STATUS_REPORT消息类型")
        void should_route_status_report_message_correctly() {
            // Given - 准备STATUS_REPORT消息
            V11WebsocketMessage statusMessage = TestDataBuilder.buildMessageWithData(
                V11WebsocketMessageTypeEnum.STATUS_REPORT, 2001, "{\"status\":\"online\"}");
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, statusMessage);
            
            // Then - 验证发送了正确的响应消息（跳过异步方法验证，因为@Async方法难以Mock）
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.STATUS_REPORT.getId());
            assertThat(response.getReceiptId()).isEqualTo(2001);
        }
        
        @Test
        @DisplayName("应该正确路由LOG_REPORT消息类型")
        void should_route_log_report_message_correctly() {
            // Given - 准备LOG_REPORT消息，使用JSON字符串格式
            String logDataJson = "[{\"deviceId\":12345,\"logType\":\"INFO\",\"description\":\"系统启动\"},{\"deviceId\":12345,\"logType\":\"ERROR\",\"description\":\"连接失败\"}]";
            V11WebsocketMessage logMessage = TestDataBuilder.buildMessageWithData(
                V11WebsocketMessageTypeEnum.LOG_REPORT, 3001, logDataJson);
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, logMessage);
            
            // Then - 验证调用了日志处理逻辑
            verify(dtoConverter).convertToTerminalLogs(any());
            verify(terminalReportUseCase).asyncSaveTerminalLog(eq(12345L), terminalLogsCaptor.capture());
            
            List<TerminalLog> capturedLogs = terminalLogsCaptor.getValue();
            assertThat(capturedLogs).hasSize(2);
            
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.LOG_REPORT.getId());
        }
    }
    
    @Nested
    @DisplayName("指令处理测试")
    class CommandHandlingTest {
        
        @Test
        @DisplayName("应该成功获取待执行指令")
        void should_get_pending_commands_successfully() {
            // Given - 准备指令获取消息
            V11WebsocketMessage commandMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.COMMAND, 1001);
            List<TerminalCommand> pendingCommands = TestDataBuilder.buildTerminalCommands();
            List<CommandResponse> commandResponses = TestDataBuilder.buildCommandResponses();
            
            when(terminalCommandUseCase.getPendingCommands(12345L)).thenReturn(pendingCommands);
            when(dtoConverter.convertToCommandResponses(pendingCommands)).thenReturn(commandResponses);
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, commandMessage);
            
            // Then - 验证指令获取流程
            verify(terminalCommandUseCase).getPendingCommands(12345L);
            verify(dtoConverter).convertToCommandResponses(pendingCommands);
            
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.COMMAND.getId());
            assertThat(response.getReceiptId()).isEqualTo(1001);
            assertThat(response.getData()).isEqualTo(commandResponses);
        }
        
        @Test
        @DisplayName("应该处理空的待执行指令列表")
        void should_handle_empty_pending_commands_list() {
            // Given - 准备空的指令列表
            V11WebsocketMessage commandMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.COMMAND, 1001);
            when(terminalCommandUseCase.getPendingCommands(12345L)).thenReturn(Collections.emptyList());
            when(dtoConverter.convertToCommandResponses(Collections.emptyList())).thenReturn(Collections.emptyList());
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, commandMessage);
            
            // Then - 验证处理空列表
            verify(terminalCommandUseCase).getPendingCommands(12345L);
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getData()).isEqualTo(Collections.emptyList());
        }
        
        @Test
        @DisplayName("应该成功确认指令执行")
        void should_confirm_command_execution_successfully() {
            // Given - 准备指令确认消息
            V11WebsocketMessage confirmMessage = TestDataBuilder.buildConfirmCommandMessage(2001, 1001, "执行成功");
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, confirmMessage);
            
            // Then - 验证指令确认流程
            verify(terminalCommandUseCase).confirmCommandExecution(12345L, 1001, "执行成功");
            
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.CONFIRM_COMMAND.getId());
            assertThat(response.getReceiptId()).isEqualTo(2001);
        }
        
        @Test
        @DisplayName("当指令确认数据为空时应该抛出异常")
        void should_throw_exception_when_confirm_command_data_is_null() {
            // Given - 准备数据为空的指令确认消息
            V11WebsocketMessage confirmMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.CONFIRM_COMMAND, 2001);
            // data为null
            
            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> router.handleMessageByType(messageProcessingContext, confirmMessage))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM3002");
        }
        
        @Test
        @DisplayName("当指令ID无效时应该抛出异常")
        void should_throw_exception_when_command_id_is_invalid() {
            // Given - 准备无效commandId的指令确认消息
            V11WebsocketMessage confirmMessage = TestDataBuilder.buildConfirmCommandMessage(2001, 0, "执行成功"); // commandId = 0
            
            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> router.handleMessageByType(messageProcessingContext, confirmMessage))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM3002");
        }
        
        @Test
        @DisplayName("当指令确认执行失败时应该发送错误消息")
        void should_send_error_message_when_command_confirmation_fails() {
            // Given - 准备指令确认消息，但UseCase抛出异常
            V11WebsocketMessage confirmMessage = TestDataBuilder.buildConfirmCommandMessage(2001, 1001, "执行成功");
            doThrow(new RuntimeException("指令确认失败")).when(terminalCommandUseCase)
                .confirmCommandExecution(anyLong(), anyInt(), anyString());
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, confirmMessage);
            
            // Then - 验证发送错误消息
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage errorResponse = messageCaptor.getValue();
            assertThat(errorResponse.getType()).isEqualTo(V11WebsocketMessageTypeEnum.ERROR.getId());
            assertThat(errorResponse.getReceiptId()).isEqualTo(2001);
        }
    }
    
    @Nested
    @DisplayName("上报处理测试")
    class ReportHandlingTest {
        
        @Test
        @DisplayName("应该处理LED状态上报")
        void should_handle_led_status_report() {
            // Given - 准备状态上报消息
            V11WebsocketMessage statusMessage = TestDataBuilder.buildMessageWithData(
                V11WebsocketMessageTypeEnum.STATUS_REPORT, 3001, "{\"led_status\":\"normal\"}");
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, statusMessage);
            
            // Then - 验证状态上报处理（跳过异步方法验证，因为@Async方法难以Mock）
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.STATUS_REPORT.getId());
            assertThat(response.getReceiptId()).isEqualTo(3001);
        }
        
        @Test
        @DisplayName("应该处理空数据的状态上报")
        void should_handle_status_report_with_null_data() {
            // Given - 准备数据为空的状态上报消息
            V11WebsocketMessage statusMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.STATUS_REPORT, 3001);
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, statusMessage);
            
            // Then - 验证使用空JSON处理（跳过异步方法验证，因为@Async方法难以Mock）
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.STATUS_REPORT.getId());
        }
        
        @Test
        @DisplayName("应该处理素材播放记录上报")
        void should_handle_media_play_record_report() {
            // Given - 准备素材播放记录上报消息
            V11WebsocketMessage mediaMessage = TestDataBuilder.buildMessageWithData(
                V11WebsocketMessageTypeEnum.MEDIA_RECORD, 4001, "{\"media_id\":123,\"play_time\":300}");
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, mediaMessage);
            
            // Then - 验证素材播放记录处理
            verify(terminalReportUseCase).asyncHandleMediaPlayRecordReport(eq(12345L), anyString());
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.MEDIA_RECORD.getId());
        }
        
        @Test
        @DisplayName("应该处理传感器数据上报")
        void should_handle_sensor_data_report() {
            // Given - 准备传感器数据上报消息
            V11WebsocketMessage sensorMessage = TestDataBuilder.buildMessageWithData(
                V11WebsocketMessageTypeEnum.MONITOR_REPORT, 5001, "{\"temperature\":25.6,\"humidity\":60}");
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, sensorMessage);
            
            // Then - 验证传感器数据处理
            verify(terminalReportUseCase).asyncHandleSensorReport(eq(12345L), any(LocalDateTime.class), anyString());
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.MONITOR_REPORT.getId());
        }
        
        @Test
        @DisplayName("应该处理节目播放记录上报")
        void should_handle_program_play_record_report() {
            // Given - 准备节目播放记录上报消息
            V11WebsocketMessage programMessage = TestDataBuilder.buildMessageWithData(
                V11WebsocketMessageTypeEnum.PROGRAM_RECORD, 6001, "{\"program_id\":456,\"duration\":1800}");
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, programMessage);
            
            // Then - 验证节目播放记录处理
            verify(terminalReportUseCase).asyncHandleProgramPlayRecordReport(eq(12345L), anyString());
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.PROGRAM_RECORD.getId());
        }
    }
    
    @Nested
    @DisplayName("终端日志处理测试")
    class TerminalLogHandlingTest {
        
        @Test
        @DisplayName("应该成功处理终端日志上报")
        void should_handle_terminal_log_report_successfully() {
            // Given - 准备终端日志上报消息，使用JSON字符串格式
            String logDataJson = "[{\"deviceId\":12345,\"logType\":\"INFO\",\"description\":\"系统启动\"},{\"deviceId\":12345,\"logType\":\"ERROR\",\"description\":\"连接失败\"}]";
            V11WebsocketMessage logMessage = TestDataBuilder.buildMessageWithData(
                V11WebsocketMessageTypeEnum.LOG_REPORT, 7001, logDataJson);
            
            List<TerminalLog> convertedLogs = TestDataBuilder.buildTerminalLogs();
            when(dtoConverter.convertToTerminalLogs(any())).thenReturn(convertedLogs);
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, logMessage);
            
            // Then - 验证日志处理流程
            verify(dtoConverter).convertToTerminalLogs(any());
            verify(terminalReportUseCase).asyncSaveTerminalLog(eq(12345L), terminalLogsCaptor.capture());
            
            List<TerminalLog> capturedLogs = terminalLogsCaptor.getValue();
            assertThat(capturedLogs).hasSize(2).isEqualTo(convertedLogs);
            
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.LOG_REPORT.getId());
        }
        
        @Test
        @DisplayName("当终端日志数据为空时应该抛出异常")
        void should_throw_exception_when_terminal_log_data_is_null() {
            // Given - 准备数据为空的终端日志消息
            V11WebsocketMessage logMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.LOG_REPORT, 7001);
            
            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> router.handleMessageByType(messageProcessingContext, logMessage))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM3002");
        }
    }
    
    @Nested
    @DisplayName("待实现功能测试")
    class PendingFeatureTest {
        
        @Test
        @DisplayName("应该处理节目获取请求（待实现）")
        void should_handle_programs_request() {
            // Given - 准备节目获取消息
            V11WebsocketMessage programsMessage = TestDataBuilder.buildMessage(V11WebsocketMessageTypeEnum.PROGRAMS, 9001);
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, programsMessage);
            
            // Then - 验证发送节目响应（当前为null数据）
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.PROGRAMS.getId());
            assertThat(response.getReceiptId()).isEqualTo(9001);
            assertThat(response.getData()).isNull(); // 待实现功能
        }
        
        @Test
        @DisplayName("应该处理下载状态上报（待实现）")
        void should_handle_download_status_report() {
            // Given - 准备下载状态上报消息
            V11WebsocketMessage downloadMessage = TestDataBuilder.buildMessageWithData(
                V11WebsocketMessageTypeEnum.DOWNLOAD_STATUS, 10001, "{\"progress\":75}");
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, downloadMessage);
            
            // Then - 验证发送下载状态响应
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.DOWNLOAD_STATUS.getId());
        }
    }
    
    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {
        
        @Test
        @DisplayName("应该处理负数的指令ID")
        void should_handle_negative_command_id() {
            // Given - 准备负数commandId的指令确认消息
            V11WebsocketMessage confirmMessage = TestDataBuilder.buildConfirmCommandMessage(2001, -1, "执行成功");
            
            // When & Then - 验证抛出业务异常（commandId <= 0）
            assertThatThrownBy(() -> router.handleMessageByType(messageProcessingContext, confirmMessage))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM3002");
        }
        
        @Test
        @DisplayName("应该处理空内容的指令确认")
        void should_handle_command_confirmation_with_empty_content() {
            // Given - 准备空内容的指令确认消息
            V11WebsocketMessage confirmMessage = TestDataBuilder.buildConfirmCommandMessage(2001, 1001, "");
            
            // When - 处理消息
            router.handleMessageByType(messageProcessingContext, confirmMessage);
            
            // Then - 验证成功处理空内容
            verify(terminalCommandUseCase).confirmCommandExecution(12345L, 1001, "");
            verify(messageProcessingContext).sendMessage(messageCaptor.capture());
            
            V11WebsocketMessage response = messageCaptor.getValue();
            assertThat(response.getType()).isEqualTo(V11WebsocketMessageTypeEnum.CONFIRM_COMMAND.getId());
        }
    }
}