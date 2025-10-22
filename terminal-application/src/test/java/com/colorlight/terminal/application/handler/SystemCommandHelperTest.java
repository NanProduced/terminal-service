package com.colorlight.terminal.application.handler;

import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SystemCommandHelper 单元测试
 *
 * <p>测试范围：</p>
 * <ul>
 *   <li>构造器限制</li>
 *   <li>generateTimeReportCommand 生成逻辑</li>
 * </ul>
 */
@DisplayName("SystemCommandHelper 单元测试")
class SystemCommandHelperTest {

    @Nested
    @DisplayName("构造器约束")
    class ConstructorTests {

        @Test
        @DisplayName("尝试通过反射实例化时应抛出 TechnicalException")
        void should_throw_technical_exception_when_instantiating_via_reflection() throws Exception {
            // Given
            Constructor<SystemCommandHelper> constructor = SystemCommandHelper.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            // When & Then
            assertThatThrownBy(constructor::newInstance)
                    .isInstanceOf(InvocationTargetException.class)
                    .extracting("targetException")
                    .isInstanceOf(TechnicalException.class)
                    .extracting("errorCode")
                    .isEqualTo(TechErrorCode.INSTANTIATION_IS_PROHIBITED.getCode());
        }
    }

    @Nested
    @DisplayName("generateTimeReportCommand 方法")
    class GenerateTimeReportCommandTests {

        @Test
        @DisplayName("应返回符合约定的 SendCommandRequest")
        void should_generate_expected_command_request() {
            // Given
            Long deviceId = 9527L;

            // When
            SendCommandRequest request = SystemCommandHelper.generateTimeReportCommand(deviceId);

            // Then
            assertThat(request).satisfies(req -> {
                assertThat(req.getDeviceId()).isEqualTo(deviceId);
                assertThat(req.getKarma()).isZero();
                assertThat(req.getAuthorUrl()).isEqualTo("api/newrtc.json");
                assertThat(req.getContentRaw()).isEmpty();
            });
        }

        @Test
        @DisplayName("允许 deviceId 为 null 的场景以保持调用方灵活性")
        void should_support_null_device_id() {
            // When
            SendCommandRequest request = SystemCommandHelper.generateTimeReportCommand(null);

            // Then
            assertThat(request.getDeviceId()).isNull();
            assertThat(request.getAuthorUrl()).isEqualTo("api/newrtc.json");
            assertThat(request.getKarma()).isZero();
        }
    }
}
