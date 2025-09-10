package com.colorlight.terminal.application.domain.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DeviceOnlineStatus 领域模型单元测试
 *
 * <p>测试范围：</p>
 * <ul>
 *   <li>构造函数和Getter/Setter方法</li>
 *   <li>静态工厂方法</li>
 *   <li>业务方法测试</li>
 * </ul>
 *
 * @author Nan
 */
@DisplayName("DeviceOnlineStatus领域模型测试")
class DeviceOnlineStatusTest {

    private static final Long TEST_DEVICE_ID = 10001L;
    private static final String TEST_CLIENT_IP = "192.168.1.100";
    private static final String TEST_VERSION = "2.0";
    private static final long TEST_TIMEOUT_THRESHOLD = 60_000L; // 60秒

    private DeviceOnlineStatus deviceOnlineStatus;

    @BeforeEach
    void setUp() {
        deviceOnlineStatus = DeviceOnlineStatus.builder()
                .deviceId(TEST_DEVICE_ID)
                .lastReportTime(System.currentTimeMillis())
                .lastReportSource(ReportSource.HTTP)
                .status(OnlineStatus.ONLINE)
                .statusChangeTime(System.currentTimeMillis())
                .onlineStartTime(System.currentTimeMillis())
                .clientIp(TEST_CLIENT_IP)
                .version(TEST_VERSION)
                .build();
    }


    @Nested
    @DisplayName("markOffline方法测试")
    class MarkOfflineMethodTests {

        @Test
        @DisplayName("应该将非离线状态标记为离线")
        void should_mark_non_offline_status_as_offline() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.ONLINE);
            Long originalStatusChangeTime = deviceOnlineStatus.getStatusChangeTime();
            
            // 手动设置一个新的状态更改时间
            long newStatusChangeTime = originalStatusChangeTime - 1000; // 假设时间为1秒前
            deviceOnlineStatus.setStatusChangeTime(newStatusChangeTime);
            
            // When
            deviceOnlineStatus.markOffline();

            // Then
            assertThat(deviceOnlineStatus.getStatus()).isEqualTo(OnlineStatus.OFFLINE);
            assertThat(deviceOnlineStatus.getStatusChangeTime()).isNotNull();
            assertThat(deviceOnlineStatus.getStatusChangeTime()).isNotEqualTo(originalStatusChangeTime);
        }

        @Test
        @DisplayName("应该保持已经是离线状态的对象不变")
        void should_keep_already_offline_status_unchanged() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.OFFLINE);
            Long originalStatusChangeTime = deviceOnlineStatus.getStatusChangeTime();

            // When
            deviceOnlineStatus.markOffline();

            // Then
            assertThat(deviceOnlineStatus.getStatus()).isEqualTo(OnlineStatus.OFFLINE);
            assertThat(deviceOnlineStatus.getStatusChangeTime()).isEqualTo(originalStatusChangeTime);
        }
    }

    @Nested
    @DisplayName("isOnline方法测试")
    class IsOnlineMethodTests {

        @Test
        @DisplayName("应该在lastReportTime为null时返回false")
        void should_return_false_when_last_report_time_is_null() {
            // Given
            deviceOnlineStatus.setLastReportTime(null);

            // When
            boolean result = deviceOnlineStatus.isOnline();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该在状态为OFFLINE时返回false")
        void should_return_false_when_status_is_offline() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.OFFLINE);

            // When
            boolean result = deviceOnlineStatus.isOnline();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该在GO_LIVE状态且在超时阈值内时返回true")
        void should_return_true_when_go_live_status_and_within_timeout() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.GO_LIVE);
            deviceOnlineStatus.setLastReportTime(System.currentTimeMillis());

            // When
            boolean result = deviceOnlineStatus.isOnline();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("应该在ONLINE状态且在超时阈值内时返回true")
        void should_return_true_when_online_status_and_within_timeout() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.ONLINE);
            deviceOnlineStatus.setLastReportTime(System.currentTimeMillis());

            // When
            boolean result = deviceOnlineStatus.isOnline();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("应该在RECONNECT状态且在超时阈值内时返回true")
        void should_return_true_when_reconnect_status_and_within_timeout() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.RECONNECT);
            deviceOnlineStatus.setLastReportTime(System.currentTimeMillis());

            // When
            boolean result = deviceOnlineStatus.isOnline();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("应该在UNKNOWN状态时返回false")
        void should_return_false_when_unknown_status() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.UNKNOWN);

            // When
            boolean result = deviceOnlineStatus.isOnline();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该在超时阈值外时返回false")
        void should_return_false_when_beyond_timeout_threshold() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.ONLINE);
            deviceOnlineStatus.setLastReportTime(System.currentTimeMillis() - TEST_TIMEOUT_THRESHOLD - 1000);

            // When
            boolean result = deviceOnlineStatus.isOnline(TEST_TIMEOUT_THRESHOLD);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该在超时阈值内时返回true")
        void should_return_true_when_within_timeout_threshold() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.ONLINE);
            deviceOnlineStatus.setLastReportTime(System.currentTimeMillis() - TEST_TIMEOUT_THRESHOLD + 1000);

            // When
            boolean result = deviceOnlineStatus.isOnline(TEST_TIMEOUT_THRESHOLD);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getCurrentOnlineDuration方法测试")
    class GetCurrentOnlineDurationMethodTests {

        @Test
        @DisplayName("应该在在线相关状态且onlineStartTime不为null时返回在线时长")
        void should_return_online_duration_when_online_related_status_and_online_start_time_not_null() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.ONLINE);
            long startTime = System.currentTimeMillis() - 5000; // 5秒前
            deviceOnlineStatus.setOnlineStartTime(startTime);

            // When
            long duration = deviceOnlineStatus.getCurrentOnlineDuration();

            // Then
            assertThat(duration).isPositive();
        }

        @Test
        @DisplayName("应该在非在线相关状态时返回0")
        void should_return_zero_when_not_online_related_status() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.OFFLINE);

            // When
            long duration = deviceOnlineStatus.getCurrentOnlineDuration();

            // Then
            assertThat(duration).isZero();
        }

        @Test
        @DisplayName("应该在onlineStartTime为null时返回0")
        void should_return_zero_when_online_start_time_is_null() {
            // Given
            deviceOnlineStatus.setStatus(OnlineStatus.ONLINE);
            deviceOnlineStatus.setOnlineStartTime(null);

            // When
            long duration = deviceOnlineStatus.getCurrentOnlineDuration();

            // Then
            assertThat(duration).isZero();
        }
    }

    @Nested
    @DisplayName("getEffectiveVersion方法测试")
    class GetEffectiveVersionMethodTests {

        @Test
        @DisplayName("应该在version不为null时返回实际版本")
        void should_return_actual_version_when_version_not_null() {
            // Given
            deviceOnlineStatus.setVersion(TEST_VERSION);

            // When
            String effectiveVersion = deviceOnlineStatus.getEffectiveVersion();

            // Then
            assertThat(effectiveVersion).isEqualTo(TEST_VERSION);
        }

        @Test
        @DisplayName("应该在version为null时返回默认版本1.0")
        void should_return_default_version_when_version_is_null() {
            // Given
            deviceOnlineStatus.setVersion(null);

            // When
            String effectiveVersion = deviceOnlineStatus.getEffectiveVersion();

            // Then
            assertThat(effectiveVersion).isEqualTo("1.0");
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("应该正确处理所有构造函数")
        void should_handle_all_constructors_correctly() {
            // When & Then
            assertThatCode(DeviceOnlineStatus::new)
                    .doesNotThrowAnyException();

            assertThatCode(() -> new DeviceOnlineStatus(
                    TEST_DEVICE_ID,
                    System.currentTimeMillis(),
                    ReportSource.HTTP,
                    OnlineStatus.ONLINE,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    TEST_CLIENT_IP,
                    TEST_VERSION))
                    .doesNotThrowAnyException();
        }
    }
}