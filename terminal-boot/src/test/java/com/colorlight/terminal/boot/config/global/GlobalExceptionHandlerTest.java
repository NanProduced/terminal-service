package com.colorlight.terminal.boot.config.global;

import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 单元测试
 *
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("全局异常处理器测试")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/device/getCommands");
        request.setMethod("GET");
    }

    @Nested
    @DisplayName("DeviceResponseException处理测试")
    class DeviceResponseExceptionHandlingTest {

        @Test
        @DisplayName("应该正确处理认证失败异常")
        void shouldHandleAuthenticationFailedException() {
            // Given
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.AUTHENTICATION_FAILED);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("应该正确处理参数缺失异常")
        void shouldHandleParameterMissingException() {
            // Given
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.PARAMETER_MISSING);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("应该正确处理系统错误异常")
        void shouldHandleSystemErrorException() {
            // Given
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("应该正确处理参数格式错误异常")
        void shouldHandleParameterFormatErrorException() {
            // Given
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.PARAMETER_FORMAT_ERROR);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("应该正确处理账户不存在异常")
        void shouldHandleAccountNotFoundException() {
            // Given
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.ACCOUNT_NOT_FOUND);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("应该处理带有自定义消息的设备异常")
        void shouldHandleDeviceExceptionWithCustomMessage() {
            // Given
            String customMessage = "自定义错误消息";
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR, customMessage);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNull();
            assertThat(exception.getMessage()).contains(customMessage);
        }

        @Test
        @DisplayName("应该处理带有根本原因的设备异常")
        void shouldHandleDeviceExceptionWithCause() {
            // Given
            RuntimeException cause = new RuntimeException("数据库连接失败");
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR, cause);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNull();
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("响应格式验证测试")
    class ResponseFormatValidationTest {

        @Test
        @DisplayName("响应应该总是返回空body")
        void responseShouldAlwaysReturnEmptyBody() {
            // Given
            DeviceResponseException[] exceptions = {
                new DeviceResponseException(CommonErrorCode.AUTHENTICATION_FAILED),
                new DeviceResponseException(CommonErrorCode.PARAMETER_MISSING),
                new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR),
                new DeviceResponseException(CommonErrorCode.ACCOUNT_NOT_FOUND)
            };

            for (DeviceResponseException exception : exceptions) {
                // When
                ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

                // Then
                assertThat(response.getBody()).isNull();
                assertThat(response.hasBody()).isFalse();
            }
        }

        @Test
        @DisplayName("响应状态码应该与错误码匹配")
        void responseStatusShouldMatchErrorCode() {
            // Given & When & Then
            assertHttpStatusMapping(CommonErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
            assertHttpStatusMapping(CommonErrorCode.PARAMETER_MISSING, HttpStatus.BAD_REQUEST);
            assertHttpStatusMapping(CommonErrorCode.PARAMETER_FORMAT_ERROR, HttpStatus.BAD_REQUEST);
            assertHttpStatusMapping(CommonErrorCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
            assertHttpStatusMapping(CommonErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.UNAUTHORIZED);
        }

        private void assertHttpStatusMapping(CommonErrorCode errorCode, HttpStatus expectedStatus) {
            DeviceResponseException exception = new DeviceResponseException(errorCode);
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);
            assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        }
    }

    @Nested
    @DisplayName("请求上下文处理测试")
    class RequestContextHandlingTest {

        @Test
        @DisplayName("应该处理不同URI的请求")
        void shouldHandleDifferentRequestUris() {
            // Given
            String[] uris = {
                "/api/device/getCommands",
                "/api/device/reportTerminalStatus",
                "/api/device/confirmCommand",
                "/api/device/reportScreenshot"
            };
            
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR);

            for (String uri : uris) {
                // When
                request.setRequestURI(uri);
                ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                assertThat(response.getBody()).isNull();
            }
        }

        @Test
        @DisplayName("应该处理不同HTTP方法的请求")
        void shouldHandleDifferentHttpMethods() {
            // Given
            String[] methods = {"GET", "POST", "PUT", "DELETE"};
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.PARAMETER_FORMAT_ERROR);

            for (String method : methods) {
                // When
                request.setMethod(method);
                ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody()).isNull();
            }
        }

        @Test
        @DisplayName("应该处理带有参数的请求")
        void shouldHandleRequestWithParameters() {
            // Given
            request.setParameter("deviceId", "12345");
            request.setParameter("cltType", "test");
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.AUTHENTICATION_FAILED);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("应该处理带有请求头的请求")
        void shouldHandleRequestWithHeaders() {
            // Given
            request.addHeader("Authorization", "Basic dGVzdDp0ZXN0");
            request.addHeader("Content-Type", "application/json");
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.ACCOUNT_NOT_FOUND);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNull();
        }
    }

    @Nested
    @DisplayName("异常信息保持测试")
    class ExceptionInformationPreservationTest {

        @Test
        @DisplayName("应该保持原始异常的错误码")
        void shouldPreserveOriginalErrorCode() {
            // Given
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.AUTHENTICATION_FAILED);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.AUTHENTICATION_FAILED.getCode());
            assertThat(response.getStatusCode().value()).isEqualTo(exception.getHttpStatus().getValue());
        }

        @Test
        @DisplayName("应该保持原始异常的消息内容")
        void shouldPreserveOriginalMessage() {
            // Given
            String originalMessage = "原始错误消息";
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR, originalMessage);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(exception.getMessage()).contains(originalMessage);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("应该保持原始异常的堆栈信息")
        void shouldPreserveOriginalStackTrace() {
            // Given
            RuntimeException cause = new RuntimeException("根本原因");
            DeviceResponseException exception = new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR, cause);

            // When
            ResponseEntity<Void> response = exceptionHandler.handleDeviceException(exception, request);

            // Then
            assertThat(exception.getCause()).isEqualTo(cause);
            assertThat(exception.getStackTrace()).isNotEmpty();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}