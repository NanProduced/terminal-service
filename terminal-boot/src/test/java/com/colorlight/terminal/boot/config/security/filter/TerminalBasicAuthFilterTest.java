package com.colorlight.terminal.boot.config.security.filter;

import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TerminalBasicAuthFilter 单元测试
 *
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("终端Basic认证过滤器测试")
class TerminalBasicAuthFilterTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private FilterChain filterChain;

    @Mock
    private SecurityContext securityContext;

    private TerminalBasicAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new TerminalBasicAuthFilter(authenticationManager);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("认证流程测试")
    class AuthenticationFlowTest {

        @Test
        @DisplayName("应该成功认证设备")
        void shouldAuthenticateDeviceSuccessfully() throws Exception {
            // Given
            String deviceId = "test_device_123";
            String password = "test_password";
            String authHeader = createBasicAuthHeader(deviceId, password);
            
            request.addHeader("Authorization", authHeader);
            request.setRequestURI("/api/device/getCommands");
            
            TerminalPrincipal principal = new TerminalPrincipal(123L, TerminalAccountStatus.ENABLE);
            Authentication successAuth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            
            when(authenticationManager.authenticate(any())).thenReturn(successAuth);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            ArgumentCaptor<UsernamePasswordAuthenticationToken> captor = 
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager, times(2)).authenticate(captor.capture());
            
            UsernamePasswordAuthenticationToken authRequest = captor.getValue();
            assertThat(authRequest.getName()).isEqualTo(deviceId);
            assertThat(authRequest.getCredentials()).isEqualTo(password);
            
            verify(securityContext).setAuthentication(successAuth);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("应该跳过不需要认证的路径")
        void shouldSkipAuthenticationForExcludedPaths() throws Exception {
            // Given
            request.setRequestURI("/actuator/health");
            request.addHeader("Authorization", createBasicAuthHeader("device", "password"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(authenticationManager);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("应该继续处理缺少Authorization头的请求")
        void shouldContinueWhenNoAuthorizationHeader() throws Exception {
            // Given
            request.setRequestURI("/api/device/getCommands");
            // 没有设置Authorization头

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(authenticationManager);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("应该继续处理非Basic认证头的请求")
        void shouldContinueWhenNonBasicAuthHeader() throws Exception {
            // Given
            request.setRequestURI("/api/device/getCommands");
            request.addHeader("Authorization", "Bearer token123");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(authenticationManager);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("应该处理认证失败异常")
        void shouldHandleAuthenticationFailure() throws Exception {
            // Given
            request.setRequestURI("/api/device/getCommands");
            request.addHeader("Authorization", createBasicAuthHeader("device", "wrong_password"));
            
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("认证失败"));

            // When & Then
            assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                    .isInstanceOf(DeviceResponseException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.AUTHENTICATION_FAILED.getCode());

            verify(securityContext, never()).setAuthentication(any());
            // 验证SecurityContextHolder静态方法调用清除上下文
            verifyNoInteractions(filterChain);
        }
    }

    @Nested
    @DisplayName("Basic Auth解析测试")
    class BasicAuthParsingTest {

        @Test
        @DisplayName("应该正确解析标准的Basic Auth头")
        void shouldParseStandardBasicAuthHeader() throws Exception {
            // Given
            String deviceId = "device123";
            String password = "password456";
            request.setRequestURI("/api/device/getCommands");
            request.addHeader("Authorization", createBasicAuthHeader(deviceId, password));

            Authentication mockAuth = mock(Authentication.class);
            when(authenticationManager.authenticate(any())).thenReturn(mockAuth);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            ArgumentCaptor<UsernamePasswordAuthenticationToken> captor = 
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager, times(2)).authenticate(captor.capture());

            UsernamePasswordAuthenticationToken token = captor.getValue();
            assertThat(token.getName()).isEqualTo(deviceId);
            assertThat(token.getCredentials()).isEqualTo(password);
        }

        @Test
        @DisplayName("应该处理包含特殊字符的认证信息")
        void shouldHandleSpecialCharactersInCredentials() throws Exception {
            // Given
            String deviceId = "device@domain.com";
            String password = "pass:word#123";
            request.setRequestURI("/api/device/getCommands");
            request.addHeader("Authorization", createBasicAuthHeader(deviceId, password));

            Authentication mockAuth = mock(Authentication.class);
            when(authenticationManager.authenticate(any())).thenReturn(mockAuth);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            ArgumentCaptor<UsernamePasswordAuthenticationToken> captor = 
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager, times(2)).authenticate(captor.capture());

            UsernamePasswordAuthenticationToken token = captor.getValue();
            assertThat(token.getName()).isEqualTo(deviceId);
            assertThat(token.getCredentials()).isEqualTo(password);
        }

        @Test
        @DisplayName("应该处理格式错误的Basic Auth头")
        void shouldHandleMalformedBasicAuthHeader() throws Exception {
            // Given
            request.setRequestURI("/api/device/getCommands");
            request.addHeader("Authorization", "Basic invalid_base64!");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(authenticationManager);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("应该处理缺少冒号分隔符的认证信息")
        void shouldHandleMissingColonInCredentials() throws Exception {
            // Given
            String invalidCredentials = Base64.getEncoder()
                    .encodeToString("devicepassword".getBytes(StandardCharsets.UTF_8));
            
            request.setRequestURI("/api/device/getCommands");
            request.addHeader("Authorization", "Basic " + invalidCredentials);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(authenticationManager);
            verify(filterChain).doFilter(request, response);
        }

    }

    @Nested
    @DisplayName("路径认证需求测试")
    class PathAuthenticationRequirementTest {

        @Test
        @DisplayName("API路径应该需要认证")
        void apiPathsShouldRequireAuthentication() throws Exception {
            // Given
            String[] apiPaths = {
                    "/api/device/getCommands",
                    "/api/device/reportTerminalStatus",
                    "/api/device/confirmCommand",
                    "/api/other/path"
            };

            for (String path : apiPaths) {
                // When
                request.setRequestURI(path);
                request.addHeader("Authorization", createBasicAuthHeader("device", "password"));
                
                Authentication mockAuth = mock(Authentication.class);
                lenient().when(mockAuth.getName()).thenReturn("device");
                lenient().when(authenticationManager.authenticate(any())).thenReturn(mockAuth);

                filter.doFilterInternal(request, response, filterChain);

                // Then
                verify(authenticationManager, atLeastOnce()).authenticate(any());
                
                // Reset for next iteration
                reset(authenticationManager, filterChain);
                request = new MockHttpServletRequest();
                response = new MockHttpServletResponse();
            }
        }

        @Test
        @DisplayName("Actuator路径应该跳过认证")
        void actuatorPathsShouldSkipAuthentication() throws Exception {
            // Given
            String[] actuatorPaths = {
                    "/actuator",
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/metrics"
            };

            for (String path : actuatorPaths) {
                // When
                request.setRequestURI(path);
                // 故意不设置Authorization头，确保跳过认证
                
                filter.doFilterInternal(request, response, filterChain);

                // Then
                verifyNoInteractions(authenticationManager);
                verify(filterChain).doFilter(request, response);
                
                // Reset for next iteration
                reset(authenticationManager, filterChain);
                request = new MockHttpServletRequest();
                response = new MockHttpServletResponse();
            }
        }
    }

    @Nested
    @DisplayName("大小写敏感性测试")
    class CaseSensitivityTest {

        @Test
        @DisplayName("应该正确处理大小写混合的Basic认证头")
        void shouldHandleMixedCaseBasicHeader() throws Exception {
            // Given
            request.setRequestURI("/api/device/getCommands");
            request.addHeader("Authorization", "basic " + Base64.getEncoder()
                    .encodeToString("device:password".getBytes(StandardCharsets.UTF_8)));

            Authentication mockAuth = mock(Authentication.class);
            when(authenticationManager.authenticate(any())).thenReturn(mockAuth);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(authenticationManager, times(2)).authenticate(any());
        }

        @Test
        @DisplayName("应该正确处理全大写的Basic认证头")
        void shouldHandleUpperCaseBasicHeader() throws Exception {
            // Given
            request.setRequestURI("/api/device/getCommands");
            request.addHeader("Authorization", "BASIC " + Base64.getEncoder()
                    .encodeToString("device:password".getBytes(StandardCharsets.UTF_8)));

            Authentication mockAuth = mock(Authentication.class);
            when(authenticationManager.authenticate(any())).thenReturn(mockAuth);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(authenticationManager, times(2)).authenticate(any());
        }
    }

    /**
     * 创建Basic认证头
     */
    private String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}