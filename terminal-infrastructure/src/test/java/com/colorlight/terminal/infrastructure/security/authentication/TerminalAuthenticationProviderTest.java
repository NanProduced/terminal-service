package com.colorlight.terminal.infrastructure.security.authentication;

import com.colorlight.terminal.application.dto.request.AuthRequest;
import com.colorlight.terminal.application.dto.result.AuthResult;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.inbound.auth.TerminalAuthUseCase;
import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.io.Serial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TerminalAuthenticationProvider单元测试
 * <p> 
 * 业务逻辑总结：
 * TerminalAuthenticationProvider是Spring Security的认证提供者，专门处理终端设备的Basic Auth认证。
 * 它将认证请求委托给TerminalAuthUseCase进行实际的认证处理，成功后创建包含设备ID和权限信息的
 * TerminalPrincipal对象。这是终端设备接入系统的核心认证组件。
 * <p> 
 * 主要业务流程：
 * 1. authenticate：提取认证信息 → 参数校验 → 委托认证 → 创建Principal
 * 2. supports：检查是否支持特定的认证类型（UsernamePasswordAuthenticationToken）
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TerminalAuthenticationProvider单元测试")
class TerminalAuthenticationProviderTest {

    @Mock
    private TerminalAuthUseCase terminalAuthUseCase;

    @InjectMocks
    private TerminalAuthenticationProvider terminalAuthenticationProvider;

    @BeforeEach
    void setUp() {
        // 使用lenient()避免严格模式报错 - 为默认成功场景设置Mock
        AuthResult defaultAuthResult = mock(AuthResult.class);
        lenient().when(defaultAuthResult.isSuccess()).thenReturn(true);
        lenient().when(defaultAuthResult.getDeviceId()).thenReturn(12345L);
        
        lenient().when(terminalAuthUseCase.authenticate(any(AuthRequest.class)))
                .thenReturn(defaultAuthResult);
    }

    @Nested
    @DisplayName("authenticate方法测试")
    class AuthenticateTests {

        @Test
        @DisplayName("应该成功认证并返回包含TerminalPrincipal的Authentication")
        void should_authenticate_successfully_and_return_authentication_with_principal() {
            // Given - 准备有效的认证信息
            Authentication authentication = TestDataBuilder.buildValidAuthentication();
            AuthResult successAuthResult = TestDataBuilder.buildSuccessAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class)))
                    .thenReturn(successAuthResult);

            // When - 执行认证
            Authentication result = terminalAuthenticationProvider.authenticate(authentication);

            // Then - 验证认证结果
            assertThat(result).isNotNull();
            assertThat(result.isAuthenticated()).isTrue();
            assertThat(result.getPrincipal()).isInstanceOf(TerminalPrincipal.class);
            
            TerminalPrincipal principal = (TerminalPrincipal) result.getPrincipal();
            assertThat(principal.getDeviceId()).isEqualTo(successAuthResult.getDeviceId());
            assertThat(principal.getStatus()).isEqualTo(TerminalAccountStatus.ENABLE);
            assertThat(principal.getAuthorities()).hasSize(1);
            assertThat(principal.getAuthorities().iterator().next().getAuthority()).isEqualTo("TERMINAL");

            // 验证UseCase调用
            verify(terminalAuthUseCase).authenticate(argThat(authRequest -> 
                authRequest.getAccountName().equals("testDevice") &&
                authRequest.getRawPassword().equals("testPassword")
            ));
        }

        @Test
        @DisplayName("当用户名为空时应该抛出DeviceResponseException")
        void should_throw_device_response_exception_when_username_is_blank() {
            // Given - 用户名为空的认证信息
            Authentication authentication = TestDataBuilder.buildAuthenticationWithBlankUsername();

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> terminalAuthenticationProvider.authenticate(authentication))
                    .isInstanceOf(DeviceResponseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "TM0002");

            // 验证UseCase不被调用
            verify(terminalAuthUseCase, never()).authenticate(any(AuthRequest.class));
        }

        @Test
        @DisplayName("当密码为空时应该抛出DeviceResponseException")
        void should_throw_device_response_exception_when_password_is_blank() {
            // Given - 密码为空的认证信息
            Authentication authentication = TestDataBuilder.buildAuthenticationWithBlankPassword();

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> terminalAuthenticationProvider.authenticate(authentication))
                    .isInstanceOf(DeviceResponseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "TM0002");

            // 验证UseCase不被调用
            verify(terminalAuthUseCase, never()).authenticate(any(AuthRequest.class));
        }

        @Test
        @DisplayName("当认证失败时应该抛出AuthenticationCredentialsNotFoundException")
        void should_throw_authentication_credentials_not_found_exception_when_auth_fails() {
            // Given - 认证失败的场景
            Authentication authentication = TestDataBuilder.buildValidAuthentication();
            AuthResult failedAuthResult = TestDataBuilder.buildFailedAuthResult();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class)))
                    .thenReturn(failedAuthResult);

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> terminalAuthenticationProvider.authenticate(authentication))
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                    .hasMessage("Authentication failed");

            // 验证UseCase被调用
            verify(terminalAuthUseCase).authenticate(any(AuthRequest.class));
        }

        @Test
        @DisplayName("当UseCase抛出异常时应该向上传播")
        void should_propagate_exception_when_use_case_throws_exception() {
            // Given - UseCase抛出异常
            Authentication authentication = TestDataBuilder.buildValidAuthentication();
            when(terminalAuthUseCase.authenticate(any(AuthRequest.class)))
                    .thenThrow(new RuntimeException("认证服务异常"));

            // When & Then - 验证异常传播
            assertThatThrownBy(() -> terminalAuthenticationProvider.authenticate(authentication))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("认证服务异常");

            // 验证UseCase被调用
            verify(terminalAuthUseCase).authenticate(any(AuthRequest.class));
        }
    }

    @Nested
    @DisplayName("supports方法测试")
    class SupportsTests {

        @Test
        @DisplayName("应该支持UsernamePasswordAuthenticationToken类型")
        void should_support_username_password_authentication_token() {
            // Given - UsernamePasswordAuthenticationToken类型
            Class<?> authenticationType = UsernamePasswordAuthenticationToken.class;

            // When - 检查支持性
            boolean result = terminalAuthenticationProvider.supports(authenticationType);

            // Then - 验证支持
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("应该支持UsernamePasswordAuthenticationToken的子类")
        void should_support_subclass_of_username_password_authentication_token() {
            // Given - UsernamePasswordAuthenticationToken的子类
            Class<?> authenticationType = CustomUsernamePasswordAuthenticationToken.class;

            // When - 检查支持性
            boolean result = terminalAuthenticationProvider.supports(authenticationType);

            // Then - 验证支持
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("不应该支持其他认证类型")
        void should_not_support_other_authentication_types() {
            // Given - 其他认证类型
            Class<?> authenticationType = Authentication.class;

            // When - 检查支持性
            boolean result = terminalAuthenticationProvider.supports(authenticationType);

            // Then - 验证不支持
            assertThat(result).isFalse();
        }
    }

    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {

        public static Authentication buildValidAuthentication() {
            return new UsernamePasswordAuthenticationToken("testDevice", "testPassword");
        }

        public static Authentication buildAuthenticationWithBlankUsername() {
            return new UsernamePasswordAuthenticationToken("", "testPassword");
        }

        public static Authentication buildAuthenticationWithBlankPassword() {
            return new UsernamePasswordAuthenticationToken("testDevice", "");
        }

        public static AuthResult buildSuccessAuthResult() {
            AuthResult authResult = mock(AuthResult.class);
            lenient().when(authResult.isSuccess()).thenReturn(true);
            lenient().when(authResult.getDeviceId()).thenReturn(12345L);
            return authResult;
        }

        public static AuthResult buildFailedAuthResult() {
            AuthResult authResult = mock(AuthResult.class);
            lenient().when(authResult.isSuccess()).thenReturn(false);
            return authResult;
        }
    }

    /**
     * 用于测试继承关系的自定义认证Token
     */
    private static class CustomUsernamePasswordAuthenticationToken extends UsernamePasswordAuthenticationToken {
        @Serial
        private static final long serialVersionUID = 3165677684729326242L;

        public CustomUsernamePasswordAuthenticationToken(Object principal, Object credentials) {
            super(principal, credentials);
        }
    }
}