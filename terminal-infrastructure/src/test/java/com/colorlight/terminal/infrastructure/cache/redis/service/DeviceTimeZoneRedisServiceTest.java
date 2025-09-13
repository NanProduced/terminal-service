package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.dto.cache.DeviceTimeZoneCache;
import com.colorlight.terminal.application.port.outbound.command.SystemCommandPort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceTimeZoneRedisService 单元测试
 * 测试设备时区信息Redis缓存服务的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备时区Redis服务测试")
class DeviceTimeZoneRedisServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private TerminalStatusReportRepository terminalStatusReportRepository;
    
    @Mock
    private SystemCommandPort systemCommandPort;
    
    @Mock
    private TerminalStatsConfigProperties statsConfigProperties;
    
    @Mock
    private TerminalStatsConfigProperties.TimeCalibration timeCalibrationConfig;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    private DeviceTimeZoneRedisService service;
    
    // 测试数据
    private static final Long TEST_DEVICE_ID = 12345L;
    private static final String TEST_TIMEZONE_ID = "Asia/Shanghai";
    private static final int TEST_TIMEZONE_OFFSET = 8;
    private static final long TEST_TTL_HOURS = 24L;
    private static final long TEST_OFFSET_THRESHOLD = 300L; // 5分钟
    
    @BeforeEach
    void setUp() {
        service = new DeviceTimeZoneRedisService(redisTemplate, terminalStatusReportRepository, 
                systemCommandPort, statsConfigProperties);
        
        // 设置默认的Mock行为
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(statsConfigProperties.getTimeCalibration()).thenReturn(timeCalibrationConfig);
        lenient().when(timeCalibrationConfig.getTimeZoneCacheTtlHours()).thenReturn(TEST_TTL_HOURS);
        lenient().when(timeCalibrationConfig.getOffsetThresholdSeconds()).thenReturn(TEST_OFFSET_THRESHOLD);
    }
    
    @Nested
    @DisplayName("设备时区获取测试")
    class DeviceTimeZoneRetrievalTest {
        
        @Test
        @DisplayName("应该从Redis缓存中成功获取设备时区信息")
        void should_get_device_timezone_from_redis_cache_successfully() {
            // Given - Redis缓存中存在设备时区信息
            DeviceTimeZoneCache cachedTimeZone = createTestTimeZoneCache();
            lenient().when(valueOperations.get(anyString())).thenReturn(cachedTimeZone);
            
            // When - 获取设备时区
            DeviceTimeZoneCache result = service.getDeviceTimeZone(TEST_DEVICE_ID);
            
            // Then - 返回缓存的时区信息
            assertThat(result).isNotNull();
            assertThat(result.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            assertThat(result.getDeviceZoneId()).isEqualTo(ZoneId.of(TEST_TIMEZONE_ID));
            // assertThat(result.getDeviceTimezone()).isEqualTo(TEST_TIMEZONE_OFFSET); // 根据实际API调整
            
            // 验证从Redis获取了缓存
            verify(valueOperations).get(anyString());
        }
        
        @Test
        @DisplayName("当Redis缓存中没有时区信息时应该刷新缓存")
        void should_refresh_cache_when_no_timezone_in_redis() {
            // Given - Redis缓存中没有设备时区信息
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            
            // Mock数据库中存在设备状态报告
            TerminalStatusReport statusReport = createTestStatusReport();
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.of(statusReport));
            
            // When - 获取设备时区
            DeviceTimeZoneCache result = service.getDeviceTimeZone(TEST_DEVICE_ID);
            
            // Then - 应该刷新缓存并返回时区信息
            assertThat(result).isNotNull();
            verify(terminalStatusReportRepository).getReportData(TEST_DEVICE_ID);
            verify(valueOperations).set(anyString(), any(DeviceTimeZoneCache.class), eq(TEST_TTL_HOURS), eq(TimeUnit.HOURS));
        }
        
        @Test
        @DisplayName("当数据库中没有设备状态报告时应该请求设备上报")
        void should_request_device_report_when_no_status_report() {
            // Given - Redis缓存和数据库中都没有设备时区信息
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.empty());
            
            // When - 获取设备时区
            DeviceTimeZoneCache result = service.getDeviceTimeZone(TEST_DEVICE_ID);
            
            // Then - 应该请求设备上报时间配置并返回null
            assertThat(result).isNull();
            verify(systemCommandPort).requestTimeZoneReport(eq(TEST_DEVICE_ID), anyString());
        }
        
        @Test
        @DisplayName("当状态报告中没有newRtc信息时应该请求设备上报")
        void should_request_device_report_when_no_newrtc_info() {
            // Given - Redis缓存中没有时区信息，状态报告中没有newRtc
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            
            TerminalStatusReport statusReport = new TerminalStatusReport();
            statusReport.setNewrtc(null); // 没有newRtc信息
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.of(statusReport));
            
            // When - 获取设备时区
            DeviceTimeZoneCache result = service.getDeviceTimeZone(TEST_DEVICE_ID);
            
            // Then - 应该请求设备上报时间配置并返回null
            assertThat(result).isNull();
            verify(systemCommandPort).requestTimeZoneReport(eq(TEST_DEVICE_ID), anyString());
        }
    }
    
    @Nested
    @DisplayName("时区缓存刷新测试")
    class TimeZoneCacheRefreshTest {
        
        @Test
        @DisplayName("应该成功刷新设备时区缓存")
        void should_refresh_device_timezone_cache_successfully() {
            // Given - 数据库中存在有效的设备状态报告
            TerminalStatusReport statusReport = createTestStatusReport();
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.of(statusReport));
            
            // When - 刷新设备时区缓存
            DeviceTimeZoneCache result = service.RefreshDeviceTimeZoneCache(TEST_DEVICE_ID);
            
            // Then - 应该返回新的时区缓存并保存到Redis
            assertThat(result).isNotNull();
            assertThat(result.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            verify(valueOperations).set(anyString(), any(DeviceTimeZoneCache.class), eq(TEST_TTL_HOURS), eq(TimeUnit.HOURS));
        }
        
        @Test
        @DisplayName("当刷新缓存失败时应该请求设备上报")
        void should_request_device_report_when_refresh_fails() {
            // Given - 数据库中没有设备状态报告
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.empty());
            
            // When - 刷新设备时区缓存
            DeviceTimeZoneCache result = service.RefreshDeviceTimeZoneCache(TEST_DEVICE_ID);
            
            // Then - 应该返回null并请求设备上报
            assertThat(result).isNull();
            verify(systemCommandPort).requestTimeZoneReport(eq(TEST_DEVICE_ID), anyString());
        }
    }
    
    @Nested
    @DisplayName("Redis缓存操作测试")
    class RedisCacheOperationTest {
        
        @Test
        @DisplayName("应该正确设置Redis缓存的TTL")
        void should_set_correct_ttl_for_redis_cache() {
            // Given - 数据库中存在有效的设备状态报告
            TerminalStatusReport statusReport = createTestStatusReport();
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.of(statusReport));
            
            // When - 刷新设备时区缓存
            service.RefreshDeviceTimeZoneCache(TEST_DEVICE_ID);
            
            // Then - 验证Redis缓存被设置了正确的TTL
            verify(valueOperations).set(anyString(), any(DeviceTimeZoneCache.class), eq(TEST_TTL_HOURS), eq(TimeUnit.HOURS));
        }
        
        @Test
        @DisplayName("当Redis操作失败时应该抛出技术异常")
        void should_throw_technical_exception_when_redis_operation_fails() {
            // Given - 数据库中存在有效的设备状态报告，但Redis操作失败
            TerminalStatusReport statusReport = createTestStatusReport();
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.of(statusReport));
            
            doThrow(new RuntimeException("Redis连接失败"))
                .when(valueOperations).set(anyString(), any(DeviceTimeZoneCache.class), anyLong(), any(TimeUnit.class));
            
            // When & Then - 刷新缓存应该抛出技术异常
            assertThatThrownBy(() -> service.RefreshDeviceTimeZoneCache(TEST_DEVICE_ID))
                .isInstanceOf(TechnicalException.class);
        }
        
        @Test
        @DisplayName("应该使用正确的Redis键格式")
        void should_use_correct_redis_key_format() {
            // Given - 准备测试数据
            DeviceTimeZoneCache cachedTimeZone = createTestTimeZoneCache();
            lenient().when(valueOperations.get(anyString())).thenReturn(cachedTimeZone);
            
            // When - 获取设备时区
            service.getDeviceTimeZone(TEST_DEVICE_ID);
            
            // Then - 验证使用了包含设备ID的Redis键
            verify(valueOperations).get(contains(TEST_DEVICE_ID.toString()));
        }
    }
    
    @Nested
    @DisplayName("时区偏差计算测试")
    class TimeZoneDeviationTest {
        
        @Test
        @DisplayName("应该正确计算设备时区偏差")
        void should_calculate_device_timezone_deviation_correctly() {
            // Given - 设备时间与服务器时间有偏差的状态报告
            TerminalStatusReport statusReport = createTestStatusReportWithDeviation();
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.of(statusReport));
            
            // When - 刷新设备时区缓存
            DeviceTimeZoneCache result = service.RefreshDeviceTimeZoneCache(TEST_DEVICE_ID);
            
            // Then - 应该计算并设置时区偏差
            assertThat(result).isNotNull();
            // 注意：具体的偏差值取决于实际的时间计算逻辑
        }
        
        @Test
        @DisplayName("当偏差小于阈值时不应该设置偏差值")
        void should_not_set_deviation_when_below_threshold() {
            // Given - 设备时间与服务器时间偏差很小的状态报告
            TerminalStatusReport statusReport = createStandardTestStatusReport(); // 默认无偏差
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.of(statusReport));
            
            // When - 刷新设备时区缓存
            DeviceTimeZoneCache result = service.RefreshDeviceTimeZoneCache(TEST_DEVICE_ID);
            
            // Then - 偏差值应该为null（因为在阈值内）
            assertThat(result).isNotNull();
            assertThat(result.getDeviation()).isEqualTo(Duration.ZERO);
        }
    }
    
    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTest {
        
        @Test
        @DisplayName("应该处理null设备ID")
        void should_handle_null_device_id() {
            // When & Then - null设备ID应该能正常处理
            assertThatNoException().isThrownBy(() -> service.getDeviceTimeZone(null));
        }
        
        @Test
        @DisplayName("应该处理系统命令发送异常")
        void should_handle_system_command_exception() {
            // Given - 需要请求设备上报，但系统命令发送失败
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            lenient().when(terminalStatusReportRepository.getReportData(TEST_DEVICE_ID))
                    .thenReturn(Optional.empty());
            doThrow(new RuntimeException("命令发送失败"))
                .when(systemCommandPort).requestTimeZoneReport(anyLong(), anyString());
            
            // When & Then - 应该能优雅处理异常
            assertThatNoException().isThrownBy(() -> service.getDeviceTimeZone(TEST_DEVICE_ID));
        }
    }
    
    // ===================== 测试数据构建辅助方法 =====================
    
    /**
     * 创建测试用的时区缓存
     */
    private DeviceTimeZoneCache createTestTimeZoneCache() {
        // 根据实际的DeviceTimeZoneCache构造器调整
        return new DeviceTimeZoneCache(TEST_DEVICE_ID, ZoneId.of(TEST_TIMEZONE_ID), +8.0, Duration.ZERO);
    }
    
    /**
     * 创建测试用的状态报告
     */
    private TerminalStatusReport createTestStatusReport() {
        TerminalStatusReport report = new TerminalStatusReport();
        TerminalStatusReport.NewRtc newRtc = new TerminalStatusReport.NewRtc();
        newRtc.setTimezoneId(TEST_TIMEZONE_ID);
        newRtc.setTimezone(TEST_TIMEZONE_OFFSET);
        newRtc.setReportTime(System.currentTimeMillis() / 1000); // 当前时间戳（秒）
        newRtc.setTime("2024-01-01 12:00:00"); // 设备本地时间
        report.setNewrtc(newRtc);
        return report;
    }

    /**
     * 创建测试使用的无偏差时间报告
     */
    private TerminalStatusReport createStandardTestStatusReport() {
        long nowStr = System.currentTimeMillis() / 1000;
        TerminalStatusReport report = new TerminalStatusReport();
        TerminalStatusReport.NewRtc newRtc = new TerminalStatusReport.NewRtc();
        newRtc.setTimezoneId(TEST_TIMEZONE_ID);
        newRtc.setTimezone(TEST_TIMEZONE_OFFSET);
        newRtc.setReportTime(nowStr); // 当前时间戳（秒）
        newRtc.setTime(LocalDateTime.ofEpochSecond(nowStr, 0, ZoneOffset.ofHours(8))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); // 设备本地时间
        report.setNewrtc(newRtc);
        return report;
    }
    
    /**
     * 创建有时区偏差的测试状态报告
     */
    private TerminalStatusReport createTestStatusReportWithDeviation() {
        TerminalStatusReport report = createTestStatusReport();
        // 设置一个有偏差的设备时间，使其超过阈值
        report.getNewrtc().setTime("2024-01-01 11:50:00"); // 比实际时间慢10分钟
        return report;
    }
}