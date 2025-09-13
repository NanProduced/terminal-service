package com.colorlight.terminal.infrastructure.rpc.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.application.dto.result.CommandSendResult;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.command.TerminalCommandDTO;
import com.colorlight.terminal.rpc.dto.request.SingleCommandRequestDTO;
import com.colorlight.terminal.rpc.dto.result.SingleCommandSendResultDTO;
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
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TerminalCommandRpcServiceImpl 单元测试
 * 
 * <p>测试策略：</p>
 * <ul>
 *   <li>验证RPC请求到Application层请求的数据转换</li>
 *   <li>验证Application层响应到RPC响应的数据转换</li>
 *   <li>验证异常处理和错误码映射</li>
 *   <li>验证日志记录的正确性</li>
 * </ul>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("终端指令RPC服务测试")
class TerminalCommandRpcServiceImplTest {

    @Mock
    private TerminalCommandUseCase terminalCommandUseCase;

    @InjectMocks
    private TerminalCommandRpcServiceImpl rpcService;

    @Captor
    private ArgumentCaptor<SendCommandRequest> requestCaptor;

    private ListAppender<ILoggingEvent> listAppender;

    // 测试常量
    private static final Long TEST_DEVICE_ID = 10001L;
    private static final String TEST_AUTHOR_URL = "api/brightness";
    private static final String TEST_CONTENT_RAW = "{\"brightness\":80}";
    private static final Integer TEST_KARMA = 1; // POST方式
    private static final String TEST_COMMAND_ID = "12345";
    private static final String TEST_SUCCESS_MESSAGE = "指令下发成功";

    @BeforeEach
    void setUp() {
        // 设置日志监听器用于验证日志输出
        Logger logger = (Logger) LoggerFactory.getLogger(TerminalCommandRpcServiceImpl.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Nested
    @DisplayName("成功场景测试")
    class SuccessScenarioTests {

        @Test
        @DisplayName("应该成功处理WebSocket下发指令")
        void should_successfully_send_command_via_websocket() {
            // Given - 构建RPC请求
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            
            // Mock Application层返回成功结果
            CommandSendResult appResult = CommandSendResult.success(
                    TEST_COMMAND_ID, 
                    CommandSendResult.SendMethod.WEBSOCKET, 
                    TEST_SUCCESS_MESSAGE
            );
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenReturn(appResult);

            // When - 调用RPC服务
            RpcResult<SingleCommandSendResultDTO> result = rpcService.sendCommand(rpcRequest);

            // Then - 验证结果
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData().isSuccess()).isTrue(),
                    () -> assertThat(result.getData().getCommandId()).isEqualTo(TEST_COMMAND_ID),
                    () -> assertThat(result.getData().getSendMethod()).isEqualTo("WEBSOCKET"),
                    () -> assertThat(result.getData().getMessage()).isEqualTo(TEST_SUCCESS_MESSAGE)
            );
        }

        @Test
        @DisplayName("应该成功处理Redis缓存下发指令")
        void should_successfully_send_command_via_redis_cache() {
            // Given
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            
            CommandSendResult appResult = CommandSendResult.success(
                    TEST_COMMAND_ID, 
                    CommandSendResult.SendMethod.REDIS_CACHE, 
                    "指令已缓存，等待设备轮询"
            );
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenReturn(appResult);

            // When
            RpcResult<SingleCommandSendResultDTO> result = rpcService.sendCommand(rpcRequest);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData().getSendMethod()).isEqualTo("REDIS_CACHE"),
                    () -> assertThat(result.getData().getMessage()).contains("缓存")
            );
        }

        @Test
        @DisplayName("应该正确转换RPC请求为Application层请求")
        void should_correctly_convert_rpc_request_to_app_request() {
            // Given
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            
            CommandSendResult appResult = CommandSendResult.success(TEST_COMMAND_ID, 
                    CommandSendResult.SendMethod.WEBSOCKET, TEST_SUCCESS_MESSAGE);
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenReturn(appResult);

            // When
            rpcService.sendCommand(rpcRequest);

            // Then - 验证转换的请求参数
            verify(terminalCommandUseCase).sendCommandToDevice(requestCaptor.capture());
            SendCommandRequest capturedRequest = requestCaptor.getValue();
            
            assertAll(
                    () -> assertThat(capturedRequest.getDeviceId()).isEqualTo(TEST_DEVICE_ID),
                    () -> assertThat(capturedRequest.getAuthorUrl()).isEqualTo(TEST_AUTHOR_URL),
                    () -> assertThat(capturedRequest.getContentRaw()).isEqualTo(TEST_CONTENT_RAW),
                    () -> assertThat(capturedRequest.getKarma()).isEqualTo(TEST_KARMA)
            );
        }

        @Test
        @DisplayName("应该记录正确的请求和响应日志")
        void should_log_request_and_response_correctly() {
            // Given
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            
            CommandSendResult appResult = CommandSendResult.success(TEST_COMMAND_ID, 
                    CommandSendResult.SendMethod.WEBSOCKET, TEST_SUCCESS_MESSAGE);
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenReturn(appResult);

            // When
            rpcService.sendCommand(rpcRequest);

            // Then - 验证日志记录
            assertThat(listAppender.list).hasSize(2);
            
            // 验证请求日志
            ILoggingEvent requestLog = listAppender.list.get(0);
            assertAll(
                    () -> assertThat(requestLog.getLevel()).isEqualTo(Level.INFO),
                    () -> assertThat(requestLog.getFormattedMessage()).contains("收到指令下发请求"),
                    () -> assertThat(requestLog.getFormattedMessage()).contains("deviceId: " + TEST_DEVICE_ID),
                    () -> assertThat(requestLog.getFormattedMessage()).contains("authorUrl: " + TEST_AUTHOR_URL)
            );
            
            // 验证响应日志
            ILoggingEvent responseLog = listAppender.list.get(1);
            assertAll(
                    () -> assertThat(responseLog.getLevel()).isEqualTo(Level.INFO),
                    () -> assertThat(responseLog.getFormattedMessage()).contains("指令下发完成"),
                    () -> assertThat(responseLog.getFormattedMessage()).contains("success: true"),
                    () -> assertThat(responseLog.getFormattedMessage()).contains("commandId: " + TEST_COMMAND_ID),
                    () -> assertThat(responseLog.getFormattedMessage()).contains("method: WEBSOCKET")
            );
        }
    }

    @Nested
    @DisplayName("失败场景测试")
    class FailureScenarioTests {

        @Test
        @DisplayName("应该处理Application层返回的失败结果")
        void should_handle_application_failure_result() {
            // Given
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            
            CommandSendResult appResult = CommandSendResult.failed("DEVICE_OFFLINE", "设备离线");
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenReturn(appResult);

            // When
            RpcResult<SingleCommandSendResultDTO> result = rpcService.sendCommand(rpcRequest);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(), // RPC调用本身成功
                    () -> assertThat(result.getData().isSuccess()).isFalse(), // 业务逻辑失败
                    () -> assertThat(result.getData().getSendMethod()).isEqualTo("FAILED"),
                    () -> assertThat(result.getData().getMessage()).isEqualTo("设备离线")
            );
        }

        @Test
        @DisplayName("应该处理参数异常并返回对应错误码")
        void should_handle_illegal_argument_exception() {
            // Given
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenThrow(new IllegalArgumentException("设备ID不能为空"));

            // When
            RpcResult<SingleCommandSendResultDTO> result = rpcService.sendCommand(rpcRequest);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getMessage())
            );
        }

        @Test
        @DisplayName("应该处理系统异常并返回对应错误码")
        void should_handle_system_exception() {
            // Given
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenThrow(new RuntimeException("Redis连接失败"));

            // When
            RpcResult<SingleCommandSendResultDTO> result = rpcService.sendCommand(rpcRequest);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getMessage())
            );
        }

        @Test
        @DisplayName("应该记录参数异常的警告日志")
        void should_log_warning_for_illegal_argument_exception() {
            // Given
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            String exceptionMessage = "设备ID不能为空";
            
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenThrow(new IllegalArgumentException(exceptionMessage));

            // When
            rpcService.sendCommand(rpcRequest);

            // Then - 验证异常日志
            assertThat(listAppender.list).hasSize(2); // 请求日志 + 异常日志
            
            ILoggingEvent exceptionLog = listAppender.list.get(1);
            assertAll(
                    () -> assertThat(exceptionLog.getLevel()).isEqualTo(Level.WARN),
                    () -> assertThat(exceptionLog.getFormattedMessage()).contains("指令下发参数错误"),
                    () -> assertThat(exceptionLog.getFormattedMessage()).contains(exceptionMessage)
            );
        }

        @Test
        @DisplayName("应该记录系统异常的错误日志")
        void should_log_error_for_system_exception() {
            // Given
            SingleCommandRequestDTO rpcRequest = createTestRpcRequest();
            
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenThrow(new RuntimeException("Redis连接失败"));

            // When
            rpcService.sendCommand(rpcRequest);

            // Then - 验证异常日志
            assertThat(listAppender.list).hasSize(2);
            
            ILoggingEvent exceptionLog = listAppender.list.get(1);
            assertAll(
                    () -> assertThat(exceptionLog.getLevel()).isEqualTo(Level.ERROR),
                    () -> assertThat(exceptionLog.getFormattedMessage()).contains("指令下发异常")
            );
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("应该处理空的Content对象")
        void should_handle_null_content() {
            // Given - Content为null的请求
            TerminalCommandDTO command = new TerminalCommandDTO(TEST_AUTHOR_URL, null, TEST_KARMA);
            SingleCommandRequestDTO rpcRequest = new SingleCommandRequestDTO(TEST_DEVICE_ID, command);
            
            CommandSendResult appResult = CommandSendResult.success(TEST_COMMAND_ID, 
                    CommandSendResult.SendMethod.WEBSOCKET, TEST_SUCCESS_MESSAGE);
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenReturn(appResult);

            // When & Then - 应该不抛异常
            RpcResult<SingleCommandSendResultDTO> result = rpcService.sendCommand(rpcRequest);
            
            assertThat(result.isSuccess()).isTrue();
            
            // 验证转换的请求参数
            verify(terminalCommandUseCase).sendCommandToDevice(requestCaptor.capture());
            SendCommandRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getContentRaw()).isEmpty();
        }

        @Test
        @DisplayName("应该处理空的raw内容")
        void should_handle_empty_raw_content() {
            // Given - raw内容为空的请求
            TerminalCommandDTO.Content content = new TerminalCommandDTO.Content("");
            TerminalCommandDTO command = new TerminalCommandDTO(TEST_AUTHOR_URL, content, TEST_KARMA);
            SingleCommandRequestDTO rpcRequest = new SingleCommandRequestDTO(TEST_DEVICE_ID, command);
            
            CommandSendResult appResult = CommandSendResult.success(TEST_COMMAND_ID, 
                    CommandSendResult.SendMethod.REDIS_CACHE, "内容为空，已缓存");
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenReturn(appResult);

            // When
            RpcResult<SingleCommandSendResultDTO> result = rpcService.sendCommand(rpcRequest);

            // Then
            assertThat(result.isSuccess()).isTrue();
            
            verify(terminalCommandUseCase).sendCommandToDevice(requestCaptor.capture());
            SendCommandRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getContentRaw()).isEmpty();
        }

        @Test
        @DisplayName("应该处理特殊字符的authorUrl")
        void should_handle_special_characters_in_author_url() {
            // Given - 包含特殊字符的authorUrl
            String specialAuthorUrl = "api/test?param=value&other=测试";
            TerminalCommandDTO.Content content = new TerminalCommandDTO.Content(TEST_CONTENT_RAW);
            TerminalCommandDTO command = new TerminalCommandDTO(specialAuthorUrl, content, TEST_KARMA);
            SingleCommandRequestDTO rpcRequest = new SingleCommandRequestDTO(TEST_DEVICE_ID, command);
            
            CommandSendResult appResult = CommandSendResult.success(TEST_COMMAND_ID, 
                    CommandSendResult.SendMethod.WEBSOCKET, TEST_SUCCESS_MESSAGE);
            when(terminalCommandUseCase.sendCommandToDevice(any(SendCommandRequest.class)))
                    .thenReturn(appResult);

            // When
            RpcResult<SingleCommandSendResultDTO> result = rpcService.sendCommand(rpcRequest);

            // Then
            assertThat(result.isSuccess()).isTrue();
            
            verify(terminalCommandUseCase).sendCommandToDevice(requestCaptor.capture());
            SendCommandRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getAuthorUrl()).isEqualTo(specialAuthorUrl);
        }
    }

    /**
     * 创建测试用的RPC请求
     */
    private SingleCommandRequestDTO createTestRpcRequest() {
        TerminalCommandDTO.Content content = new TerminalCommandDTO.Content(TEST_CONTENT_RAW);
        TerminalCommandDTO command = new TerminalCommandDTO(TEST_AUTHOR_URL, content, TEST_KARMA);
        return new SingleCommandRequestDTO(TEST_DEVICE_ID, command);
    }
}