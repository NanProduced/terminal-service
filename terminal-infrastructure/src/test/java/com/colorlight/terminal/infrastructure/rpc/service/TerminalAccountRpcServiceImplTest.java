package com.colorlight.terminal.infrastructure.rpc.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.dto.request.CreateTerminalAccountRequest;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.inbound.account.TerminalAccountUseCase;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.CreateTerminalAccountDTO;
import com.colorlight.terminal.rpc.dto.result.TerminalAccountResultDTO;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TerminalAccountRpcServiceImpl 单元测试
 * 
 * <p>测试策略：</p>
 * <ul>
 *   <li>验证RPC请求到Application层请求的数据转换</li>
 *   <li>验证Application层响应到RPC响应的数据转换</li>
 *   <li>验证异常处理和错误码映射</li>
 *   <li>验证日志记录的正确性</li>
 *   <li>验证业务异常和系统异常的区分处理</li>
 * </ul>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("终端账号RPC服务测试")
class TerminalAccountRpcServiceImplTest {

    @Mock
    private TerminalAccountUseCase terminalAccountUseCase;

    @InjectMocks
    private TerminalAccountRpcServiceImpl rpcService;

    @Captor
    private ArgumentCaptor<CreateTerminalAccountRequest> requestCaptor;

    private ListAppender<ILoggingEvent> listAppender;

    // 测试常量
    private static final String TEST_ACCOUNT_NAME = "test_account";
    private static final String TEST_RAW_PASSWORD = "test_password_123";
    private static final Long TEST_DEVICE_ID = 10001L;

    @BeforeEach
    void setUp() {
        // 设置日志监听器用于验证日志输出
        Logger logger = (Logger) LoggerFactory.getLogger(TerminalAccountRpcServiceImpl.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Nested
    @DisplayName("成功场景测试")
    class SuccessScenarioTests {

        @Test
        @DisplayName("应该成功创建终端账号")
        void should_successfully_create_terminal_account() {
            // Given - 构建RPC请求
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            // Mock Application层返回成功结果
            TerminalAccount mockAccount = createMockTerminalAccount();
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenReturn(mockAccount);

            // When - 调用RPC服务
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then - 验证结果
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData().getDeviceId()).isEqualTo(TEST_DEVICE_ID),
                    () -> assertThat(result.getData().getAccountName()).isEqualTo(TEST_ACCOUNT_NAME),
                    () -> assertThat(result.getData().getStatus()).isEqualTo(TerminalAccountStatus.ENABLE.name())
            );
        }

        @Test
        @DisplayName("应该正确转换RPC请求为Application层请求")
        void should_correctly_convert_rpc_request_to_app_request() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            TerminalAccount mockAccount = createMockTerminalAccount();
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenReturn(mockAccount);

            // When
            rpcService.createTerminalAccount(rpcRequest);

            // Then - 验证转换的请求参数
            verify(terminalAccountUseCase).createTerminalAccount(requestCaptor.capture());
            CreateTerminalAccountRequest capturedRequest = requestCaptor.getValue();
            
            assertAll(
                    () -> assertThat(capturedRequest.getAccountName()).isEqualTo(TEST_ACCOUNT_NAME),
                    () -> assertThat(capturedRequest.getRawPassword()).isEqualTo(TEST_RAW_PASSWORD),
                    () -> assertThat(capturedRequest.getSource()).isEqualTo(CreateTerminalAccountRequest.Source.CLOUD)
            );
        }

        @Test
        @DisplayName("应该正确转换Domain对象为RPC结果")
        void should_correctly_convert_domain_to_rpc_result() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            // 创建具有不同状态的账号
            TerminalAccount mockAccount = TerminalAccount.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .accountName(TEST_ACCOUNT_NAME)
                    .passwordHash("hashed_password")
                    .status(TerminalAccountStatus.DISABLE) // 使用不同状态测试转换
                    .firstLoginTime(LocalDateTime.now())
                    .build();
            
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenReturn(mockAccount);

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then - 验证转换结果
            TerminalAccountResultDTO resultData = result.getData();
            assertAll(
                    () -> assertThat(resultData.getDeviceId()).isEqualTo(TEST_DEVICE_ID),
                    () -> assertThat(resultData.getAccountName()).isEqualTo(TEST_ACCOUNT_NAME),
                    () -> assertThat(resultData.getStatus()).isEqualTo("DISABLE") // 验证枚举转字符串
            );
        }
    }

    @Nested
    @DisplayName("业务异常测试")
    class BusinessExceptionTests {

        @Test
        @DisplayName("应该处理账号已存在的业务异常")
        void should_handle_account_already_exists_exception() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            BusinessException businessException = new BusinessException(CommonErrorCode.TERMINAL_ACCOUNT_EXIST);
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenThrow(businessException);

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo("SYSTEM_ERROR"),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("系统内部错误")
            );
        }

        @Test
        @DisplayName("应该处理参数校验异常")
        void should_handle_illegal_argument_exception() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenThrow(new IllegalArgumentException("账号名称不能为空"));

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo("VALIDATION_ERROR"),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("账号名称不能为空")
            );
        }

        @Test
        @DisplayName("应该记录业务异常的警告日志")
        void should_log_warning_for_business_exception() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            String exceptionMessage = "账号名称已存在";
            
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenThrow(new IllegalArgumentException(exceptionMessage));

            // When
            rpcService.createTerminalAccount(rpcRequest);

            // Then - 验证异常日志
            assertThat(listAppender.list).hasSize(1);
            
            ILoggingEvent exceptionLog = listAppender.list.get(0);
            assertAll(
                    () -> assertThat(exceptionLog.getLevel()).isEqualTo(Level.WARN),
                    () -> assertThat(exceptionLog.getFormattedMessage()).contains("创建终端账号失败"),
                    () -> assertThat(exceptionLog.getFormattedMessage()).contains(exceptionMessage)
            );
        }
    }

    @Nested
    @DisplayName("系统异常测试")
    class SystemExceptionTests {

        @Test
        @DisplayName("应该处理数据库连接异常")
        void should_handle_database_connection_exception() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenThrow(new RuntimeException("数据库连接失败"));

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo("SYSTEM_ERROR"),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("系统内部错误")
            );
        }

        @Test
        @DisplayName("应该记录系统异常的错误日志")
        void should_log_error_for_system_exception() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenThrow(new RuntimeException("数据库连接失败"));

            // When
            rpcService.createTerminalAccount(rpcRequest);

            // Then - 验证异常日志
            assertThat(listAppender.list).hasSize(1);
            
            ILoggingEvent exceptionLog = listAppender.list.get(0);
            assertAll(
                    () -> assertThat(exceptionLog.getLevel()).isEqualTo(Level.ERROR),
                    () -> assertThat(exceptionLog.getFormattedMessage()).contains("创建终端账号异常")
            );
        }

        @Test
        @DisplayName("应该处理空指针异常")
        void should_handle_null_pointer_exception() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenThrow(new NullPointerException("密码编码器未初始化"));

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo("SYSTEM_ERROR"),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("系统内部错误")
            );
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("应该处理空的账号名称")
        void should_handle_empty_account_name() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO("", TEST_RAW_PASSWORD);
            
            TerminalAccount mockAccount = createMockTerminalAccount();
            mockAccount.setAccountName(""); // 设置空账号名
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenReturn(mockAccount);

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getAccountName()).isEmpty();
            
            verify(terminalAccountUseCase).createTerminalAccount(requestCaptor.capture());
            CreateTerminalAccountRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getAccountName()).isEmpty();
        }

        @Test
        @DisplayName("应该处理空的原始密码")
        void should_handle_empty_raw_password() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, "");
            
            TerminalAccount mockAccount = createMockTerminalAccount();
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenReturn(mockAccount);

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then
            assertThat(result.isSuccess()).isTrue();
            
            verify(terminalAccountUseCase).createTerminalAccount(requestCaptor.capture());
            CreateTerminalAccountRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getRawPassword()).isEmpty();
        }

        @Test
        @DisplayName("应该处理特殊字符的账号名称")
        void should_handle_special_characters_in_account_name() {
            // Given - 包含特殊字符的账号名
            String specialAccountName = "test_账号@#$%^&*()";
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(specialAccountName, TEST_RAW_PASSWORD);
            
            TerminalAccount mockAccount = createMockTerminalAccount();
            mockAccount.setAccountName(specialAccountName);
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenReturn(mockAccount);

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getAccountName()).isEqualTo(specialAccountName);
            
            verify(terminalAccountUseCase).createTerminalAccount(requestCaptor.capture());
            CreateTerminalAccountRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getAccountName()).isEqualTo(specialAccountName);
        }

        @Test
        @DisplayName("应该处理长密码")
        void should_handle_long_password() {
            // Given - 很长的密码
            String longPassword = "a".repeat(100);
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, longPassword);
            
            TerminalAccount mockAccount = createMockTerminalAccount();
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenReturn(mockAccount);

            // When
            RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

            // Then
            assertThat(result.isSuccess()).isTrue();
            
            verify(terminalAccountUseCase).createTerminalAccount(requestCaptor.capture());
            CreateTerminalAccountRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getRawPassword()).isEqualTo(longPassword);
        }
    }

    @Nested
    @DisplayName("数据转换完整性测试")
    class DataConversionIntegrityTests {

        @Test
        @DisplayName("应该确保Source字段始终设置为CLOUD")
        void should_always_set_source_to_cloud() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            TerminalAccount mockAccount = createMockTerminalAccount();
            when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                    .thenReturn(mockAccount);

            // When
            rpcService.createTerminalAccount(rpcRequest);

            // Then - 验证Source字段始终为CLOUD
            verify(terminalAccountUseCase).createTerminalAccount(requestCaptor.capture());
            CreateTerminalAccountRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getSource()).isEqualTo(CreateTerminalAccountRequest.Source.CLOUD);
        }

        @Test
        @DisplayName("应该正确处理所有账号状态枚举值")
        void should_handle_all_account_status_enum_values() {
            // Given
            CreateTerminalAccountDTO rpcRequest = new CreateTerminalAccountDTO(TEST_ACCOUNT_NAME, TEST_RAW_PASSWORD);
            
            // 测试所有可能的状态值
            TerminalAccountStatus[] allStatuses = TerminalAccountStatus.values();
            
            for (TerminalAccountStatus status : allStatuses) {
                // 重置mock
                TerminalAccount mockAccount = TerminalAccount.builder()
                        .deviceId(TEST_DEVICE_ID)
                        .accountName(TEST_ACCOUNT_NAME)
                        .passwordHash("hashed_password")
                        .status(status)
                        .build();
                
                when(terminalAccountUseCase.createTerminalAccount(any(CreateTerminalAccountRequest.class)))
                        .thenReturn(mockAccount);

                // When
                RpcResult<TerminalAccountResultDTO> result = rpcService.createTerminalAccount(rpcRequest);

                // Then - 验证状态转换正确
                assertThat(result.getData().getStatus()).isEqualTo(status.name());
            }
        }
    }

    /**
     * 创建测试用的TerminalAccount Mock对象
     */
    private TerminalAccount createMockTerminalAccount() {
        return TerminalAccount.builder()
                .deviceId(TEST_DEVICE_ID)
                .accountName(TEST_ACCOUNT_NAME)
                .passwordHash("hashed_password_123")
                .status(TerminalAccountStatus.ENABLE)
                .firstLoginTime(LocalDateTime.now())
                .lastLoginTime(LocalDateTime.now())
                .lastLoginIp("192.168.1.100")
                .build();
    }
}