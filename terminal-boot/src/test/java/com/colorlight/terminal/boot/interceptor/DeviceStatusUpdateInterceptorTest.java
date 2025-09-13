package com.colorlight.terminal.boot.interceptor;

import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeviceStatusUpdateInterceptor 单元测试
 *
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备状态更新拦截器测试")
class DeviceStatusUpdateInterceptorTest {

    @Mock
    private DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private DeviceStatusUpdateInterceptor interceptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Object handler;

    private final Long TEST_DEVICE_ID = 12345L;
    private TerminalPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        handler = new Object();
        
        testPrincipal = new TerminalPrincipal(TEST_DEVICE_ID, com.colorlight.terminal.application.enums.TerminalAccountStatus.ENABLE);
        
        // 设置Spring Security上下文
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @Nested
    @DisplayName("正常流程测试")
    class NormalFlowTest {

        @Test
        @DisplayName("应该成功更新设备状态")
        void shouldUpdateDeviceStatusSuccessfully() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.setRemoteAddr("192.168.1.100");
            request.setRequestURI("/api/device/reportTerminalStatus");

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "192.168.1.100"
            );
        }

        @ParameterizedTest
        @CsvSource({
                "X-Forwarded-For, '203.0.113.1, 192.168.1.100', 203.0.113.1",
                "X-Real-IP, 203.0.113.2, 203.0.113.2",
                "Proxy-Client-IP, 203.0.113.3, 203.0.113.3"
        })
        @DisplayName("应该正确获取代理头部的IP")
        void shouldGetIpFromProxyHeaders(String headerName, String headerValue, String expectedIp) throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.addHeader(headerName, headerValue);
            request.setRemoteAddr("192.168.1.1");

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    expectedIp
            );
        }

        @Test
        @DisplayName("应该回退到RemoteAddr当没有代理头部时")
        void shouldFallbackToRemoteAddrWhenNoProxyHeaders() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.setRemoteAddr("192.168.1.200");

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "192.168.1.200"
            );
        }
    }

    @Nested
    @DisplayName("异常情况处理测试")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("应该忽略unknown的IP值")
        void shouldIgnoreUnknownIpValues() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.addHeader("X-Forwarded-For", "unknown");
            request.addHeader("X-Real-IP", "UNKNOWN");
            request.setRemoteAddr("192.168.1.100");

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "192.168.1.100"
            );
        }

        @Test
        @DisplayName("应该忽略空的IP头部")
        void shouldIgnoreEmptyIpHeaders() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.addHeader("X-Forwarded-For", "");
            request.setRemoteAddr("192.168.1.100");

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "192.168.1.100"
            );
        }

        @Test
        @DisplayName("应该处理非TerminalPrincipal的认证主体")
        void shouldHandleNonTerminalPrincipal() throws Exception {
            // Given
            String stringPrincipal = "non_terminal_principal";
            when(authentication.getPrincipal()).thenReturn(stringPrincipal);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verifyNoInteractions(deviceOnlineStatusUseCase);
        }

        @Test
        @DisplayName("应该处理空的认证信息")
        void shouldHandleNullAuthentication() throws Exception {
            // Given
            when(securityContext.getAuthentication()).thenReturn(null);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verifyNoInteractions(deviceOnlineStatusUseCase);
        }

        @Test
        @DisplayName("应该捕获并记录状态更新异常但不影响请求继续")
        void shouldCatchStatusUpdateExceptionButContinueRequest() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.setRemoteAddr("192.168.1.100");
            
            doThrow(new RuntimeException("数据库连接失败"))
                    .when(deviceOnlineStatusUseCase).updateLastReportTime(any(), any(), any());

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue(); // 即使异常也要继续处理请求
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "192.168.1.100"
            );
        }
    }

    @Nested
    @DisplayName("IP地址解析优先级测试")
    class IpParsingPriorityTest {

        @Test
        @DisplayName("X-Forwarded-For应该有最高优先级")
        void xForwardedForShouldHaveHighestPriority() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.addHeader("X-Forwarded-For", "203.0.113.1");
            request.addHeader("X-Real-IP", "203.0.113.2");
            request.addHeader("Proxy-Client-IP", "203.0.113.3");
            request.setRemoteAddr("192.168.1.1");

            // When
            interceptor.preHandle(request, response, handler);

            // Then
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "203.0.113.1"
            );
        }

        @Test
        @DisplayName("应该从多IP的X-Forwarded-For中取第一个")
        void shouldTakeFirstIpFromMultipleForwardedFor() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.addHeader("X-Forwarded-For", "203.0.113.1, 203.0.113.2, 192.168.1.1");

            // When
            interceptor.preHandle(request, response, handler);

            // Then
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "203.0.113.1"
            );
        }

        @Test
        @DisplayName("应该清理IP地址中的空格")
        void shouldTrimWhitespaceFromIp() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            request.addHeader("X-Forwarded-For", "  203.0.113.1  , 192.168.1.1");

            // When
            interceptor.preHandle(request, response, handler);

            // Then
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "203.0.113.1"
            );
        }

        @Test
        @DisplayName("应该按头部优先级顺序检查")
        void shouldCheckHeadersInPriorityOrder() throws Exception {
            // Given
            when(authentication.getPrincipal()).thenReturn(testPrincipal);
            // 故意不设置X-Forwarded-For，让它检查下一个
            request.addHeader("X-Real-IP", "203.0.113.2");
            request.addHeader("Proxy-Client-IP", "203.0.113.3");
            request.setRemoteAddr("192.168.1.1");

            // When
            interceptor.preHandle(request, response, handler);

            // Then
            verify(deviceOnlineStatusUseCase).updateLastReportTime(
                    TEST_DEVICE_ID, 
                    ReportSource.HTTP, 
                    "203.0.113.2"
            );
        }
    }
}