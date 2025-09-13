package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.dto.request.CreateTerminalAccountRequest;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.outbound.auth.EncoderPort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 终端账号应用服务单元测试
 * 
 * <p>测试策略：</p>
 * <ul>
 *   <li>验证账号创建的完整流程</li>
 *   <li>测试账号名重复检查机制</li>
 *   <li>验证密码加密处理</li>
 *   <li>测试数据库保存逻辑</li>
 *   <li>验证异常处理和边界条件</li>
 * </ul>
 * 
 * @author Nan
 */
@DisplayName("终端账号管理服务测试")
class TerminalAccountApplicationServiceTest extends BaseApplicationServiceTest {
    
    @Mock
    private TerminalAccountRepository terminalAccountRepository;
    
    @Mock
    private EncoderPort encoderPort;
    
    @InjectMocks
    private TerminalAccountApplicationService service;
    
    @Captor
    private ArgumentCaptor<TerminalAccount> terminalAccountCaptor;
    
    // 测试常量
    private static final String ENCODED_PASSWORD = "encoded_password_hash";
    private static final Long SAVED_DEVICE_ID = 20001L;
    
    @BeforeEach
    void setUp() {
        // 配置默认Mock行为
        lenient().when(encoderPort.encodeByPasswordEncoder(any())).thenReturn(ENCODED_PASSWORD);
    }
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        /**
         * 创建标准的创建账号请求
         */
        static CreateTerminalAccountRequest createStandardRequest() {
            return CreateTerminalAccountRequest.builder()
                    .accountName(TEST_ACCOUNT_NAME)
                    .rawPassword(TEST_PASSWORD)
                    .source(CreateTerminalAccountRequest.Source.CLOUD)
                    .build();
        }
        
        /**
         * 创建自定义的创建账号请求
         */
        static CreateTerminalAccountRequest createCustomRequest(String accountName, String password) {
            return CreateTerminalAccountRequest.builder()
                    .accountName(accountName)
                    .rawPassword(password)
                    .source(CreateTerminalAccountRequest.Source.CLOUD)
                    .build();
        }
        
        /**
         * 创建已保存的终端账号
         */
        static TerminalAccount createSavedAccount() {
            return TerminalAccount.builder()
                    .deviceId(SAVED_DEVICE_ID)
                    .accountName(TEST_ACCOUNT_NAME)
                    .passwordHash(ENCODED_PASSWORD)
                    .status(TerminalAccountStatus.ENABLE)
                    .build();
        }
    }
    
    @Nested
    @DisplayName("账号创建测试")
    class CreateTerminalAccountTests {
        
        @Test
        @DisplayName("应该成功创建新的终端账号")
        void should_create_terminal_account_successfully() {
            // Given - 准备测试数据
            CreateTerminalAccountRequest request = TestDataBuilder.createStandardRequest();
            TerminalAccount savedAccount = TestDataBuilder.createSavedAccount();
            
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(false);
            when(encoderPort.encodeByPasswordEncoder(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(terminalAccountRepository.save(any(TerminalAccount.class))).thenReturn(savedAccount);
            
            // When - 执行目标方法
            TerminalAccount result = service.createTerminalAccount(request);
            
            // Then - 验证结果
            // 验证返回结果
            assertThat(result).isNotNull();
            assertThat(result.getDeviceId()).isEqualTo(SAVED_DEVICE_ID);
            assertThat(result.getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
            assertThat(result.getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
            assertThat(result.getStatus()).isEqualTo(TerminalAccountStatus.ENABLE);
            
            // 验证账号名重复检查
            verify(terminalAccountRepository).ifExistTerminalAccount(TEST_ACCOUNT_NAME);
            
            // 验证密码加密
            verify(encoderPort).encodeByPasswordEncoder(TEST_PASSWORD);
            
            // 验证保存操作
            verify(terminalAccountRepository).save(terminalAccountCaptor.capture());
            TerminalAccount capturedAccount = terminalAccountCaptor.getValue();
            assertThat(capturedAccount.getAccountName()).isEqualTo(TEST_ACCOUNT_NAME);
            assertThat(capturedAccount.getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
            assertThat(capturedAccount.getStatus()).isEqualTo(TerminalAccountStatus.ENABLE);
        }
        
        @Test
        @DisplayName("应该在账号名已存在时抛出业务异常")
        void should_throw_business_exception_when_account_name_exists() {
            // Given - 账号名已存在
            CreateTerminalAccountRequest request = TestDataBuilder.createStandardRequest();
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(true);
            
            // When & Then - 验证抛出异常
            assertThatThrownBy(() -> service.createTerminalAccount(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CommonErrorCode.TERMINAL_ACCOUNT_EXIST.getMessage())
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.TERMINAL_ACCOUNT_EXIST.getCode());
            
            // 验证只检查了账号名，没有进行后续操作
            verify(terminalAccountRepository).ifExistTerminalAccount(TEST_ACCOUNT_NAME);
            verify(encoderPort, never()).encodeByPasswordEncoder(any());
            verify(terminalAccountRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("应该正确处理密码加密")
        void should_handle_password_encoding_correctly() {
            // Given - 准备测试数据
            String originalPassword = "original_password_123";
            String expectedEncodedPassword = "encoded_hash_result";
            CreateTerminalAccountRequest request = TestDataBuilder.createCustomRequest(TEST_ACCOUNT_NAME, originalPassword);
            TerminalAccount savedAccount = TestDataBuilder.createSavedAccount();
            
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(false);
            when(encoderPort.encodeByPasswordEncoder(originalPassword)).thenReturn(expectedEncodedPassword);
            when(terminalAccountRepository.save(any(TerminalAccount.class))).thenReturn(savedAccount);
            
            // When - 执行创建
            service.createTerminalAccount(request);
            
            // Then - 验证密码加密
            verify(encoderPort).encodeByPasswordEncoder(originalPassword);
            verify(terminalAccountRepository).save(terminalAccountCaptor.capture());
            assertThat(terminalAccountCaptor.getValue().getPasswordHash()).isEqualTo(expectedEncodedPassword);
        }
        
        @Test
        @DisplayName("应该设置正确的账号状态")
        void should_set_correct_account_status() {
            // Given - 准备测试数据
            CreateTerminalAccountRequest request = TestDataBuilder.createStandardRequest();
            TerminalAccount savedAccount = TestDataBuilder.createSavedAccount();
            
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(false);
            when(terminalAccountRepository.save(any(TerminalAccount.class))).thenReturn(savedAccount);
            
            // When - 执行创建
            service.createTerminalAccount(request);
            
            // Then - 验证状态设置
            verify(terminalAccountRepository).save(terminalAccountCaptor.capture());
            TerminalAccount capturedAccount = terminalAccountCaptor.getValue();
            assertThat(capturedAccount.getStatus()).isEqualTo(TerminalAccountStatus.ENABLE);
        }
    }
    
    @Nested
    @DisplayName("边界条件和异常测试")
    class EdgeCaseAndExceptionTests {
        
        @Test
        @DisplayName("应该处理空账号名")
        void should_handle_empty_account_name() {
            // Given - 空账号名
            CreateTerminalAccountRequest request = TestDataBuilder.createCustomRequest("", TEST_PASSWORD);
            when(terminalAccountRepository.ifExistTerminalAccount("")).thenReturn(false);
            when(terminalAccountRepository.save(any(TerminalAccount.class)))
                    .thenReturn(TestDataBuilder.createSavedAccount());
            
            // When - 执行创建（应该成功，业务层不做参数验证）
            TerminalAccount result = service.createTerminalAccount(request);
            
            // Then - 验证结果
            assertThat(result).isNotNull();
            verify(terminalAccountRepository).ifExistTerminalAccount("");
        }
        
        @Test
        @DisplayName("应该处理空密码")
        void should_handle_empty_password() {
            // Given - 空密码
            CreateTerminalAccountRequest request = TestDataBuilder.createCustomRequest(TEST_ACCOUNT_NAME, "");
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(false);
            when(encoderPort.encodeByPasswordEncoder("")).thenReturn("encoded_empty");
            when(terminalAccountRepository.save(any(TerminalAccount.class)))
                    .thenReturn(TestDataBuilder.createSavedAccount());
            
            // When - 执行创建
            TerminalAccount result = service.createTerminalAccount(request);
            
            // Then - 验证空密码被加密
            assertThat(result).isNotNull();
            verify(encoderPort).encodeByPasswordEncoder("");
        }
        
        @Test
        @DisplayName("应该在数据库保存失败时抛出异常")
        void should_throw_exception_when_database_save_fails() {
            // Given - 数据库保存失败
            CreateTerminalAccountRequest request = TestDataBuilder.createStandardRequest();
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(false);
            when(terminalAccountRepository.save(any(TerminalAccount.class)))
                    .thenThrow(new RuntimeException("数据库保存失败"));
            
            // When & Then - 验证抛出异常
            assertThatThrownBy(() -> service.createTerminalAccount(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("数据库保存失败");
            
            // 验证前置操作已执行
            verify(terminalAccountRepository).ifExistTerminalAccount(TEST_ACCOUNT_NAME);
            verify(encoderPort).encodeByPasswordEncoder(TEST_PASSWORD);
            verify(terminalAccountRepository).save(any(TerminalAccount.class));
        }
        
        @Test
        @DisplayName("应该在密码加密失败时抛出异常")
        void should_throw_exception_when_password_encoding_fails() {
            // Given - 密码加密失败
            CreateTerminalAccountRequest request = TestDataBuilder.createStandardRequest();
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(false);
            when(encoderPort.encodeByPasswordEncoder(TEST_PASSWORD))
                    .thenThrow(new RuntimeException("密码加密失败"));
            
            // When & Then - 验证抛出异常
            assertThatThrownBy(() -> service.createTerminalAccount(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("密码加密失败");
            
            // 验证账号检查已执行，但没有保存
            verify(terminalAccountRepository).ifExistTerminalAccount(TEST_ACCOUNT_NAME);
            verify(encoderPort).encodeByPasswordEncoder(TEST_PASSWORD);
            verify(terminalAccountRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("应该在账号存在检查失败时抛出异常")
        void should_throw_exception_when_account_existence_check_fails() {
            // Given - 账号存在检查失败
            CreateTerminalAccountRequest request = TestDataBuilder.createStandardRequest();
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME))
                    .thenThrow(new RuntimeException("数据库查询失败"));
            
            // When & Then - 验证抛出异常
            assertThatThrownBy(() -> service.createTerminalAccount(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("数据库查询失败");
            
            // 验证没有执行后续操作
            verify(terminalAccountRepository).ifExistTerminalAccount(TEST_ACCOUNT_NAME);
            verify(encoderPort, never()).encodeByPasswordEncoder(any());
            verify(terminalAccountRepository, never()).save(any());
        }
    }
    
    @Nested
    @DisplayName("业务逻辑验证测试")
    class BusinessLogicValidationTests {
        
        @Test
        @DisplayName("应该验证完整的创建流程顺序")
        void should_validate_complete_creation_flow_order() {
            // Given - 准备测试数据
            CreateTerminalAccountRequest request = TestDataBuilder.createStandardRequest();
            TerminalAccount savedAccount = TestDataBuilder.createSavedAccount();
            
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(false);
            when(terminalAccountRepository.save(any(TerminalAccount.class))).thenReturn(savedAccount);
            
            // When - 执行创建
            service.createTerminalAccount(request);
            
            // Then - 验证调用顺序
            var inOrder = inOrder(terminalAccountRepository, encoderPort);
            inOrder.verify(terminalAccountRepository).ifExistTerminalAccount(TEST_ACCOUNT_NAME);
            inOrder.verify(encoderPort).encodeByPasswordEncoder(TEST_PASSWORD);
            inOrder.verify(terminalAccountRepository).save(any(TerminalAccount.class));
        }
        
        @Test
        @DisplayName("应该只调用一次每个依赖方法")
        void should_call_each_dependency_method_exactly_once() {
            // Given - 准备测试数据
            CreateTerminalAccountRequest request = TestDataBuilder.createStandardRequest();
            TerminalAccount savedAccount = TestDataBuilder.createSavedAccount();
            
            when(terminalAccountRepository.ifExistTerminalAccount(TEST_ACCOUNT_NAME)).thenReturn(false);
            when(terminalAccountRepository.save(any(TerminalAccount.class))).thenReturn(savedAccount);
            
            // When - 执行创建
            service.createTerminalAccount(request);
            
            // Then - 验证调用次数
            verify(terminalAccountRepository, times(1)).ifExistTerminalAccount(TEST_ACCOUNT_NAME);
            verify(encoderPort, times(1)).encodeByPasswordEncoder(TEST_PASSWORD);
            verify(terminalAccountRepository, times(1)).save(any(TerminalAccount.class));
        }
        
        @Test
        @DisplayName("应该传递正确的参数给依赖服务")
        void should_pass_correct_parameters_to_dependencies() {
            // Given - 准备测试数据
            String customAccountName = "custom_account";
            String customPassword = "custom_password";
            CreateTerminalAccountRequest request = TestDataBuilder.createCustomRequest(customAccountName, customPassword);
            TerminalAccount savedAccount = TestDataBuilder.createSavedAccount();
            
            when(terminalAccountRepository.ifExistTerminalAccount(customAccountName)).thenReturn(false);
            when(terminalAccountRepository.save(any(TerminalAccount.class))).thenReturn(savedAccount);
            
            // When - 执行创建
            service.createTerminalAccount(request);
            
            // Then - 验证参数传递
            verify(terminalAccountRepository).ifExistTerminalAccount(eq(customAccountName));
            verify(encoderPort).encodeByPasswordEncoder(eq(customPassword));
        }
    }
}