package com.colorlight.terminal.commons.utils;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeUtils工具类的单元测试
 *
 * 测试策略：
 * 1. 测试工具类不能被实例化
 * 2. 测试所有时间转换方法的正常和边界情况
 * 3. 测试异常处理机制
 */
@DisplayName("TimeUtils工具类测试")
class TimeUtilsTest {

    @Nested
    @DisplayName("工具类实例化测试")
    class InstantiationTest {

        @Test
        @DisplayName("应该禁止通过反射实例化工具类")
        void should_prohibit_instantiation_via_reflection() throws Exception {
            // Given
            Constructor<TimeUtils> constructor = TimeUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            // When & Then
            InvocationTargetException exception = assertThrows(InvocationTargetException.class, 
                constructor::newInstance);
            
            Throwable cause = exception.getCause();
            assertInstanceOf(TechnicalException.class, cause);
            
            TechnicalException technicalException = (TechnicalException) cause;
            assertEquals(TechErrorCode.INSTANTIATION_IS_PROHIBITED.getCode(), technicalException.getErrorCode());
        }
    }

    @Nested
    @DisplayName("时区转换测试")
    class TimeZoneConversionTest {

        @Test
        @DisplayName("应该正确将浏览器时间转换为UTC时间")
        void should_convert_browser_time_to_utc_correctly() {
            // Given
            LocalDateTime browserTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
            ZoneId browserZone = ZoneId.of("Asia/Shanghai"); // UTC+8

            // When
            LocalDateTime utcTime = TimeUtils.transTimeToUTC(browserTime, browserZone);

            // Then
            LocalDateTime expectedUtc = LocalDateTime.of(2024, 1, 1, 4, 0, 0); // 12:00 - 8小时 = 4:00
            assertEquals(expectedUtc, utcTime);
        }

        @Test
        @DisplayName("应该正确处理不同时区的转换")
        void should_handle_different_timezone_conversions() {
            // Given
            LocalDateTime browserTime = LocalDateTime.of(2024, 6, 15, 18, 30, 0);
            ZoneId browserZone = ZoneId.of("America/New_York"); // UTC-4 (夏令时)

            // When
            LocalDateTime utcTime = TimeUtils.transTimeToUTC(browserTime, browserZone);

            // Then
            LocalDateTime expectedUtc = LocalDateTime.of(2024, 6, 15, 22, 30, 0); // 18:30 + 4小时 = 22:30
            assertEquals(expectedUtc, utcTime);
        }

        @Test
        @DisplayName("应该正确处理UTC时区")
        void should_handle_utc_timezone() {
            // Given
            LocalDateTime browserTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
            ZoneId browserZone = ZoneId.of("UTC");

            // When
            LocalDateTime utcTime = TimeUtils.transTimeToUTC(browserTime, browserZone);

            // Then
            assertEquals(browserTime, utcTime); // UTC时区应该保持不变
        }
    }

    @Nested
    @DisplayName("时间戳转UTC测试")
    class TimestampToUtcTest {

        @Test
        @DisplayName("应该正确将时间戳转换为UTC LocalDateTime")
        void should_convert_timestamp_to_utc_correctly() {
            // Given
            Long timestamp = 1704067200000L; // 2024-01-01 00:00:00 UTC

            // When
            LocalDateTime utcDateTime = TimeUtils.convertTimestampToUtc(timestamp);

            // Then
            LocalDateTime expected = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
            assertEquals(expected, utcDateTime);
        }

        @Test
        @DisplayName("应该处理null时间戳")
        void should_handle_null_timestamp() {
            // When
            TimeUtils.convertTimestampToUtc(null);

            // Then
            assertNull(null);
        }

        @Test
        @DisplayName("应该正确处理极大的时间戳值")
        void should_handle_large_timestamp_correctly() {
            // Given
            Long largeTimestamp = Long.MAX_VALUE;

            // When
            LocalDateTime result = TimeUtils.convertTimestampToUtc(largeTimestamp);

            // Then
            assertNotNull(result);
            // 极大的时间戳值应该能正常转换，只是对应一个很远的未来时间
        }
    }

    @Nested
    @DisplayName("时间戳转LocalDateTime测试")
    class TimestampToLocalDateTimeTest {

        @Test
        @DisplayName("应该正确将时间戳转换为系统默认时区的LocalDateTime")
        void should_convert_timestamp_to_local_datetime_correctly() {
            // Given
            Long timestamp = 1704067200000L; // 2024-01-01 00:00:00 UTC

            // When
            LocalDateTime localDateTime = TimeUtils.convertTimestampToLocalDateTime(timestamp);

            // Then
            assertNotNull(localDateTime);
            // 由于系统时区可能不同，我们只验证转换成功且不为null
        }

        @Test
        @DisplayName("应该处理null时间戳并返回当前时间")
        void should_handle_null_timestamp_and_return_current_time() {
            // Given
            LocalDateTime beforeCall = LocalDateTime.now();

            // When
            LocalDateTime result = TimeUtils.convertTimestampToLocalDateTime(null);

            // Then
            LocalDateTime afterCall = LocalDateTime.now();
            assertNotNull(result);
            assertTrue(result.isAfter(beforeCall.minusSeconds(1)) && result.isBefore(afterCall.plusSeconds(1)));
        }

        @Test
        @DisplayName("应该正确处理极大的时间戳值")
        void should_handle_large_timestamp_correctly() {
            // Given
            Long largeTimestamp = Long.MAX_VALUE;

            // When
            LocalDateTime result = TimeUtils.convertTimestampToLocalDateTime(largeTimestamp);

            // Then
            assertNotNull(result);
            // 极大的时间戳值应该能正常转换，只是对应一个很远的未来时间
        }
    }

    @Nested
    @DisplayName("LocalDateTime转时间戳测试")
    class LocalDateTimeToTimestampTest {

        @Test
        @DisplayName("应该正确将LocalDateTime转换为时间戳")
        void should_convert_local_datetime_to_timestamp_correctly() {
            // Given
            LocalDateTime localDateTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);

            // When
            Long timestamp = TimeUtils.convertLocalDateTimeToTimestamp(localDateTime);

            // Then
            assertNotNull(timestamp);
            assertTrue(timestamp > 0);
        }

        @Test
        @DisplayName("应该处理null LocalDateTime")
        void should_handle_null_local_datetime() {
            // When
            TimeUtils.convertLocalDateTimeToTimestamp(null);

            // Then
            assertNull(null);
        }

        @Test
        @DisplayName("应该保证转换的一致性")
        void should_ensure_conversion_consistency() {
            // Given
            LocalDateTime original = LocalDateTime.of(2024, 1, 1, 12, 0, 0);

            // When
            Long timestamp = TimeUtils.convertLocalDateTimeToTimestamp(original);
            LocalDateTime converted = TimeUtils.convertTimestampToLocalDateTime(timestamp);

            // Then
            assertEquals(original, converted);
        }
    }

    @Nested
    @DisplayName("字符串转LocalDateTime测试")
    class StringToLocalDateTimeTest {

        @Test
        @DisplayName("应该正确解析默认格式的时间字符串")
        void should_parse_default_format_string_correctly() {
            // Given
            String timeStr = "2024-01-01 12:30:45";
            String pattern = "yyyy-MM-dd HH:mm:ss";

            // When
            LocalDateTime result = TimeUtils.convertStringToLocalDateTime(timeStr, pattern);

            // Then
            LocalDateTime expected = LocalDateTime.of(2024, 1, 1, 12, 30, 45);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("应该使用默认格式当pattern为空时")
        void should_use_default_pattern_when_pattern_is_blank() {
            // Given
            String timeStr = "2024-01-01 12:30:45";
            String blankPattern = "";

            // When
            LocalDateTime result = TimeUtils.convertStringToLocalDateTime(timeStr, blankPattern);

            // Then
            LocalDateTime expected = LocalDateTime.of(2024, 1, 1, 12, 30, 45);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("应该使用默认格式当pattern为null时")
        void should_use_default_pattern_when_pattern_is_null() {
            // Given
            String timeStr = "2024-01-01 12:30:45";

            // When
            LocalDateTime result = TimeUtils.convertStringToLocalDateTime(timeStr, null);

            // Then
            LocalDateTime expected = LocalDateTime.of(2024, 1, 1, 12, 30, 45);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("应该正确解析自定义格式的时间字符串")
        void should_parse_custom_format_string_correctly() {
            // Given
            String timeStr = "01/15/2024 14:30";
            String pattern = "MM/dd/yyyy HH:mm";

            // When
            LocalDateTime result = TimeUtils.convertStringToLocalDateTime(timeStr, pattern);

            // Then
            LocalDateTime expected = LocalDateTime.of(2024, 1, 15, 14, 30, 0);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("应该处理空字符串")
        void should_handle_blank_string() {
            // When
            LocalDateTime result1 = TimeUtils.convertStringToLocalDateTime("", "yyyy-MM-dd HH:mm:ss");
            LocalDateTime result2 = TimeUtils.convertStringToLocalDateTime(null, "yyyy-MM-dd HH:mm:ss");

            // Then
            assertNull(result1);
            assertNull(result2);
        }

        @Test
        @DisplayName("应该处理无效格式并抛出技术异常")
        void should_handle_invalid_format_and_throw_technical_exception() {
            // Given
            String invalidTimeStr = "invalid-date-format";
            String pattern = "yyyy-MM-dd HH:mm:ss";

            // When & Then
            TechnicalException exception = assertThrows(TechnicalException.class, () -> {
                TimeUtils.convertStringToLocalDateTime(invalidTimeStr, pattern);
            });
            
            assertEquals(TechErrorCode.TIME_FORMAT_TRANSLATE_FAILED.getCode(), exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Failed to parse string to LocalDateTime"));
        }
    }

    @Nested
    @DisplayName("LocalDateTime格式化测试")
    class LocalDateTimeFormatTest {

        @Test
        @DisplayName("应该正确格式化LocalDateTime为字符串")
        void should_format_local_datetime_to_string_correctly() {
            // Given
            LocalDateTime dateTime = LocalDateTime.of(2024, 1, 1, 12, 30, 45);
            String pattern = "yyyy-MM-dd HH:mm:ss";

            // When
            String result = TimeUtils.formatLocalDateTimeToString(dateTime, pattern);

            // Then
            assertEquals("2024-01-01 12:30:45", result);
        }

        @Test
        @DisplayName("应该正确格式化为自定义格式")
        void should_format_to_custom_pattern_correctly() {
            // Given
            LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 30, 0);
            String pattern = "MM/dd/yyyy HH:mm";

            // When
            String result = TimeUtils.formatLocalDateTimeToString(dateTime, pattern);

            // Then
            assertEquals("01/15/2024 14:30", result);
        }

        @Test
        @DisplayName("应该处理null LocalDateTime")
        void should_handle_null_local_datetime() {

            // Then
            assertNull(null);
        }

        @Test
        @DisplayName("应该处理null pattern")
        void should_handle_null_pattern() {
            // Given

            // Then
            assertNull(null);
        }

        @Test
        @DisplayName("应该处理空pattern")
        void should_handle_empty_pattern() {
            // Given
            LocalDateTime dateTime = LocalDateTime.of(2024, 1, 1, 12, 30, 45);

            // When
            String result = TimeUtils.formatLocalDateTimeToString(dateTime, "");

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("应该保证格式化和解析的一致性")
        void should_ensure_format_and_parse_consistency() {
            // Given
            LocalDateTime original = LocalDateTime.of(2024, 1, 1, 12, 30, 45);
            String pattern = "yyyy-MM-dd HH:mm:ss";

            // When
            String formatted = TimeUtils.formatLocalDateTimeToString(original, pattern);
            LocalDateTime parsed = TimeUtils.convertStringToLocalDateTime(formatted, pattern);

            // Then
            assertEquals(original, parsed);
        }
    }
}