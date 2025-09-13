package com.colorlight.terminal.application.handler;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.*;

/**
 * ReportTimePopulator 工具类单元测试
 *
 * <p>测试范围：</p>
 * <ul>
 *   <li>构造函数测试 - 验证无法实例化</li>
 *   <li>reportTime字段填充测试 - 验证各种场景下的字段填充</li>
 *   <li>边界条件测试 - 空值、null值等处理</li>
 * </ul>
 *
 * @author Nan
 */
@DisplayName("ReportTimePopulator工具类测试")
class ReportTimePopulatorTest {

    private static final long TEST_TIMESTAMP = System.currentTimeMillis();

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("应该在尝试实例化时抛出TechnicalException异常")
        void should_throw_technical_exception_when_trying_to_instantiate() throws Exception {
            // Given
            Constructor<ReportTimePopulator> constructor = ReportTimePopulator.class.getDeclaredConstructor();
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
    @DisplayName("populateReportTime方法测试")
    class PopulateReportTimeTests {

        @Test
        @DisplayName("应该成功填充包含reportTime字段的子对象")
        void should_populate_report_time_fields_successfully() {
            // Given
            TerminalStatusReport.Terminal terminal = TerminalStatusReport.Terminal.builder()
                    .name("test_terminal")
                    .leddescription("test_description")
                    .build();

            TerminalStatusReport report = TerminalStatusReport.builder()
                    .terminal(terminal)
                    .build();

            // When
            ReportTimePopulator.populateReportTime(report, TEST_TIMESTAMP);

            // Then
            assertThat(terminal.getReportTime()).isEqualTo(TEST_TIMESTAMP);
        }

        @Test
        @DisplayName("应该忽略不包含reportTime字段的子对象")
        void should_ignore_objects_without_report_time_field() {
            // Given
            TerminalStatusReport.WebsocketStatus websocketStatus = TerminalStatusReport.WebsocketStatus.builder()
                    .status(1)
                    .build();

            TerminalStatusReport report = TerminalStatusReport.builder()
                    .websocketStatus(websocketStatus)
                    .build();

            // When
            ReportTimePopulator.populateReportTime(report, TEST_TIMESTAMP);

            // Then
            // 不应该抛出异常，且websocketStatus对象保持不变
            assertThat(websocketStatus.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("应该正确处理null报告对象")
        void should_handle_null_report_object() {
            // When & Then
            assertThatCode(() -> ReportTimePopulator.populateReportTime(null, TEST_TIMESTAMP))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("应该忽略null子对象")
        void should_ignore_null_sub_objects() {
            // Given
            TerminalStatusReport report = TerminalStatusReport.builder()
                    .terminal(null) // null子对象
                    .build();

            // When & Then
            assertThatCode(() -> ReportTimePopulator.populateReportTime(report, TEST_TIMESTAMP))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("应该处理多个包含reportTime字段的子对象")
        void should_handle_multiple_objects_with_report_time_fields() {
            // Given
            TerminalStatusReport.Terminal terminal = TerminalStatusReport.Terminal.builder()
                    .name("test_terminal")
                    .build();

            TerminalStatusReport.Dimension dimension = TerminalStatusReport.Dimension.builder()
                    .width(1920)
                    .height(1080)
                    .build();

            TerminalStatusReport report = TerminalStatusReport.builder()
                    .terminal(terminal)
                    .dimension(dimension)
                    .build();

            // When
            ReportTimePopulator.populateReportTime(report, TEST_TIMESTAMP);

            // Then
            assertThat(terminal.getReportTime()).isEqualTo(TEST_TIMESTAMP);
            assertThat(dimension.getReportTime()).isEqualTo(TEST_TIMESTAMP);
        }
    }
}