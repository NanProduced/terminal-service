package com.colorlight.terminal.infrastructure.record;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.application.dto.cache.DeviceTimeZoneCache;
import com.colorlight.terminal.application.port.outbound.repository.MediaPlayRecordRepository;
import com.colorlight.terminal.application.port.outbound.status.DeviceTimeZonePort;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DeviceMediaPlayRecordService 单元测试
 * 
 * 业务逻辑总结：
 * DeviceMediaPlayRecordService是Application层DeviceMediaPlayRecordPort接口的Infrastructure层实现，
 * 负责处理设备上报的素材播放记录，包括时间校准和数据存储功能。
 * 
 * 核心功能：
 * 1. handleMediaPlayRecordReport - 处理素材播放记录上报数据的主入口
 * 2. calibrateDeviceTime - 校准播放记录的开始播放时间（私有方法）
 * 
 * 时间校准逻辑：
 * - 检查配置是否启用时间校准功能
 * - 获取设备时区偏差信息
 * - 如果存在时间偏差，则校准所有记录的开始播放时间
 * 
 * 依赖：DeviceTimeZonePort、MediaPlayRecordRepository、TerminalStatsConfigProperties
 * 业务场景：设备上报素材播放数据时的时间校准和存储
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceMediaPlayRecordService - 素材播放记录服务测试")
class DeviceMediaPlayRecordServiceTest {

    @Mock
    private DeviceTimeZonePort deviceTimeZonePort;
    
    @Mock
    private MediaPlayRecordRepository mediaPlayRecordRepository;
    
    @Mock
    private TerminalStatsConfigProperties statsConfigProperties;
    
    @Mock
    private TerminalStatsConfigProperties.MediaPlayRecord mediaPlayRecordConfig;

    private DeviceMediaPlayRecordService deviceMediaPlayRecordService;

    @BeforeEach
    void setUp() {
        deviceMediaPlayRecordService = new DeviceMediaPlayRecordService(
            deviceTimeZonePort, 
            mediaPlayRecordRepository, 
            statsConfigProperties
        );
        
        // 使用lenient()避免严格模式报错，某些测试可能不会调用所有mock方法
        lenient().when(statsConfigProperties.getMediaPlayRecord()).thenReturn(mediaPlayRecordConfig);
        lenient().when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
    }

    @Nested
    @DisplayName("handleMediaPlayRecordReport - 处理素材播放记录上报")
    class HandleMediaPlayRecordReportTests {

        @Test
        @DisplayName("应该在时间校准启用时成功处理记录并进行时间校准")
        void should_handle_records_with_time_calibration_when_enabled() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            // 设置时间校准启用
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            // 设置设备时区信息（有偏差）
            Duration deviation = Duration.ofMinutes(5); // 5分钟偏差
            DeviceTimeZoneCache deviceTimeZone = createDeviceTimeZoneCache(deviceId, deviation);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证时间校准和存储
            verify(deviceTimeZonePort).getDeviceTimeZone(deviceId);
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId, reports);
            
            // 验证时间校准是否正确应用
            LocalDateTime originalTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
            LocalDateTime expectedAdjustedTime = originalTime.plus(deviation);
            assertThat(reports.get(0).getAdjustStartTime()).isEqualTo(expectedAdjustedTime);
        }

        @Test
        @DisplayName("应该在时间校准禁用时跳过校准直接存储记录")
        void should_skip_calibration_and_save_records_when_time_calibration_disabled() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            // 设置时间校准禁用
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(false);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证跳过校准直接存储
            verify(deviceTimeZonePort, never()).getDeviceTimeZone(anyLong());
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId, reports);
        }

        @Test
        @DisplayName("应该正确处理空记录列表")
        void should_handle_empty_records_list_correctly() {
            // Given - 准备空记录列表
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> emptyReports = Collections.emptyList();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, emptyReports);

            // Then - 验证正常处理空列表
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId, emptyReports);
        }

        @Test
        @DisplayName("应该在单条记录时正确处理时间校准")
        void should_handle_single_record_with_time_calibration_correctly() {
            // Given - 准备单条记录
            Long deviceId = 12345L;
            MediaPlayRecordReport singleRecord = createSingleMediaPlayRecord();
            List<MediaPlayRecordReport> reports = Collections.singletonList(singleRecord);
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            Duration deviation = Duration.ofHours(-2); // 负偏差
            DeviceTimeZoneCache deviceTimeZone = createDeviceTimeZoneCache(deviceId, deviation);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证单条记录的时间校准
            LocalDateTime originalTime = singleRecord.getStartUtcTime();
            LocalDateTime expectedAdjustedTime = originalTime.plus(deviation);
            assertThat(singleRecord.getAdjustStartTime()).isEqualTo(expectedAdjustedTime);
            
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId, reports);
        }
    }

    @Nested
    @DisplayName("时间校准功能测试")
    class TimeCalibrationTests {

        @Test
        @DisplayName("应该在设备时区缓存为null时跳过校准")
        void should_skip_calibration_when_device_timezone_cache_is_null() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(null);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证跳过校准但仍存储记录
            verify(deviceTimeZonePort).getDeviceTimeZone(deviceId);
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId, reports);
            
            // 验证时间未被校准（保持原始值）
            assertThat(reports.get(0).getAdjustStartTime()).isNull();
        }

        @Test
        @DisplayName("应该在设备时区偏差为null时跳过校准")
        void should_skip_calibration_when_deviation_is_null() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            // 设置时区缓存但偏差为null
            DeviceTimeZoneCache deviceTimeZone = new DeviceTimeZoneCache();
            deviceTimeZone.setDeviceId(deviceId);
            deviceTimeZone.setDeviceZoneId(ZoneId.of("Asia/Shanghai"));
            deviceTimeZone.setDeviation(null);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证跳过校准
            verify(deviceTimeZonePort).getDeviceTimeZone(deviceId);
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId, reports);
        }

        @Test
        @DisplayName("应该在时间偏差为零时跳过校准")
        void should_skip_calibration_when_deviation_is_zero() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            // 设置零偏差
            DeviceTimeZoneCache deviceTimeZone = createDeviceTimeZoneCache(deviceId, Duration.ZERO);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证跳过校准
            verify(deviceTimeZonePort).getDeviceTimeZone(deviceId);
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId, reports);
            
            // 验证时间未被校准
            assertThat(reports.get(0).getAdjustStartTime()).isNull();
        }

        @Test
        @DisplayName("应该正确处理正偏差的时间校准")
        void should_handle_positive_deviation_calibration_correctly() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            // 设置正偏差（设备时间快了）
            Duration positiveDeviation = Duration.ofMinutes(30);
            DeviceTimeZoneCache deviceTimeZone = createDeviceTimeZoneCache(deviceId, positiveDeviation);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证正偏差校准
            LocalDateTime originalTime = reports.get(0).getStartUtcTime();
            LocalDateTime expectedAdjustedTime = originalTime.plus(positiveDeviation);
            assertThat(reports.get(0).getAdjustStartTime()).isEqualTo(expectedAdjustedTime);
        }

        @Test
        @DisplayName("应该正确处理负偏差的时间校准")
        void should_handle_negative_deviation_calibration_correctly() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            // 设置负偏差（设备时间慢了）
            Duration negativeDeviation = Duration.ofMinutes(-15);
            DeviceTimeZoneCache deviceTimeZone = createDeviceTimeZoneCache(deviceId, negativeDeviation);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证负偏差校准
            LocalDateTime originalTime = reports.get(0).getStartUtcTime();
            LocalDateTime expectedAdjustedTime = originalTime.plus(negativeDeviation);
            assertThat(reports.get(0).getAdjustStartTime()).isEqualTo(expectedAdjustedTime);
        }

        @Test
        @DisplayName("应该对多条记录都进行时间校准")
        void should_calibrate_all_records_in_batch() {
            // Given - 准备多条记录
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createMultipleMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            Duration deviation = Duration.ofMinutes(10);
            DeviceTimeZoneCache deviceTimeZone = createDeviceTimeZoneCache(deviceId, deviation);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证所有记录都被校准
            for (MediaPlayRecordReport report : reports) {
                LocalDateTime originalTime = report.getStartUtcTime();
                LocalDateTime expectedAdjustedTime = originalTime.plus(deviation);
                assertThat(report.getAdjustStartTime()).isEqualTo(expectedAdjustedTime);
            }
            
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId, reports);
        }
    }

    @Nested
    @DisplayName("边界情况和异常测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("应该正确处理大偏差的时间校准")
        void should_handle_large_deviation_calibration_correctly() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            // 设置大偏差（12小时）
            Duration largeDeviation = Duration.ofHours(12);
            DeviceTimeZoneCache deviceTimeZone = createDeviceTimeZoneCache(deviceId, largeDeviation);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证大偏差校准
            LocalDateTime originalTime = reports.get(0).getStartUtcTime();
            LocalDateTime expectedAdjustedTime = originalTime.plus(largeDeviation);
            assertThat(reports.get(0).getAdjustStartTime()).isEqualTo(expectedAdjustedTime);
        }

        @Test
        @DisplayName("应该正确处理微小偏差的时间校准")
        void should_handle_small_deviation_calibration_correctly() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            List<MediaPlayRecordReport> reports = createTestMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            // 设置微小偏差（1秒）
            Duration smallDeviation = Duration.ofSeconds(1);
            DeviceTimeZoneCache deviceTimeZone = createDeviceTimeZoneCache(deviceId, smallDeviation);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId)).thenReturn(deviceTimeZone);

            // When - 执行业务方法
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId, reports);

            // Then - 验证微小偏差校准
            LocalDateTime originalTime = reports.get(0).getStartUtcTime();
            LocalDateTime expectedAdjustedTime = originalTime.plus(smallDeviation);
            assertThat(reports.get(0).getAdjustStartTime()).isEqualTo(expectedAdjustedTime);
        }

        @Test
        @DisplayName("应该正确处理不同设备ID的记录")
        void should_handle_different_device_ids_correctly() {
            // Given - 准备不同设备的测试数据
            Long deviceId1 = 11111L;
            Long deviceId2 = 22222L;
            List<MediaPlayRecordReport> reports1 = createTestMediaPlayRecords();
            List<MediaPlayRecordReport> reports2 = createTestMediaPlayRecords();
            
            when(mediaPlayRecordConfig.isTimeCalibrationEnabled()).thenReturn(true);
            
            // 设置不同的时区偏差
            DeviceTimeZoneCache deviceTimeZone1 = createDeviceTimeZoneCache(deviceId1, Duration.ofMinutes(5));
            DeviceTimeZoneCache deviceTimeZone2 = createDeviceTimeZoneCache(deviceId2, Duration.ofMinutes(-10));
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId1)).thenReturn(deviceTimeZone1);
            when(deviceTimeZonePort.getDeviceTimeZone(deviceId2)).thenReturn(deviceTimeZone2);

            // When - 分别处理两个设备的记录
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId1, reports1);
            deviceMediaPlayRecordService.handleMediaPlayRecordReport(deviceId2, reports2);

            // Then - 验证不同设备使用不同的校准参数
            verify(deviceTimeZonePort).getDeviceTimeZone(deviceId1);
            verify(deviceTimeZonePort).getDeviceTimeZone(deviceId2);
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId1, reports1);
            verify(mediaPlayRecordRepository).saveMediaPlayRecords(deviceId2, reports2);
        }
    }

    // 测试数据构建方法
    private List<MediaPlayRecordReport> createTestMediaPlayRecords() {
        MediaPlayRecordReport report = createSingleMediaPlayRecord();
        return Collections.singletonList(report);
    }

    private MediaPlayRecordReport createSingleMediaPlayRecord() {
        MediaPlayRecordReport report = new MediaPlayRecordReport();
        report.setProgramName("测试节目");
        report.setResOriginName("测试素材.mp4");
        report.setResMd5Name("abc123def456");
        report.setPageName("页面1");
        report.setPageIndex(1);
        report.setRegionName("区域1");
        report.setRegionIndex(1);
        report.setStartUtcTime(LocalDateTime.of(2024, 1, 1, 10, 0, 0));
        report.setStartLocalTime(LocalDateTime.of(2024, 1, 1, 18, 0, 0));
        report.setEndUtcTime(LocalDateTime.of(2024, 1, 1, 10, 5, 0));
        report.setEndLocalTime(LocalDateTime.of(2024, 1, 1, 18, 5, 0));
        report.setDuration(300L);
        report.setItemType("video");
        return report;
    }

    private List<MediaPlayRecordReport> createMultipleMediaPlayRecords() {
        MediaPlayRecordReport report1 = createSingleMediaPlayRecord();
        
        MediaPlayRecordReport report2 = new MediaPlayRecordReport();
        report2.setProgramName("测试节目2");
        report2.setResOriginName("测试素材2.jpg");
        report2.setResMd5Name("def456ghi789");
        report2.setPageName("页面2");
        report2.setPageIndex(2);
        report2.setRegionName("区域2");
        report2.setRegionIndex(2);
        report2.setStartUtcTime(LocalDateTime.of(2024, 1, 1, 11, 0, 0));
        report2.setStartLocalTime(LocalDateTime.of(2024, 1, 1, 19, 0, 0));
        report2.setEndUtcTime(LocalDateTime.of(2024, 1, 1, 11, 2, 0));
        report2.setEndLocalTime(LocalDateTime.of(2024, 1, 1, 19, 2, 0));
        report2.setDuration(120L);
        report2.setItemType("image");
        
        return Arrays.asList(report1, report2);
    }

    private DeviceTimeZoneCache createDeviceTimeZoneCache(Long deviceId, Duration deviation) {
        DeviceTimeZoneCache cache = new DeviceTimeZoneCache();
        cache.setDeviceId(deviceId);
        cache.setDeviceZoneId(ZoneId.of("Asia/Shanghai"));
        cache.setTimeZoneOffset(8.0);
        cache.setDeviation(deviation);
        return cache;
    }
}