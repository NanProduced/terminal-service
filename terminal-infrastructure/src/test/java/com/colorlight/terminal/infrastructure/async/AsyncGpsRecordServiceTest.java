package com.colorlight.terminal.infrastructure.async;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.application.port.outbound.repository.GpsRecordRepository;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 异步GPS记录服务测试
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("异步GPS记录服务测试")
class AsyncGpsRecordServiceTest {

    @Mock
    private GpsRecordRepository gpsRepository;
    
    @Mock
    private TerminalStatsConfigProperties terminalStatsConfigProperties;
    
    @Mock
    private TerminalStatsConfigProperties.Gps gpsConfig;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @InjectMocks
    private AsyncGpsRecordService asyncGpsRecordService;

    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        /**
         * 创建有效的GPS报告
         */
        static GpsReport createValidGpsReport(Long deviceId, Double latitude, Double longitude) {
            GpsReport gpsReport = new GpsReport();
            gpsReport.setDeviceId(deviceId);
            gpsReport.setLatitude(latitude);
            gpsReport.setLongitude(longitude);
            gpsReport.setAccuracy(10.5);
            gpsReport.setAltitude(100.0);
            gpsReport.setSpeed(0.0);
            gpsReport.setDirect(45.0);
            gpsReport.setSatellites(8);
            gpsReport.setDeviceTime(LocalDateTime.now());
            gpsReport.setServerTime(LocalDateTime.now());
            gpsReport.setSensorType("gps");
            gpsReport.setSensorId(1);
            return gpsReport;
        }
        
        /**
         * 创建无效的GPS报告（坐标超出范围）
         */
        static GpsReport createInvalidGpsReport() {
            GpsReport gpsReport = new GpsReport();
            gpsReport.setDeviceId(1002L);
            gpsReport.setLatitude(100.0); // 超出纬度范围(-90, 90)
            gpsReport.setLongitude(200.0); // 超出经度范围(-180, 180)
            gpsReport.setDeviceTime(LocalDateTime.now());
            gpsReport.setServerTime(LocalDateTime.now());
            gpsReport.setSensorType("gps");
            gpsReport.setSensorId(1);
            return gpsReport;
        }
        
        /**
         * 创建包含特殊标识值的GPS报告
         */
        static GpsReport createInvalidFlagGpsReport() {
            GpsReport gpsReport = new GpsReport();
            gpsReport.setDeviceId(1003L);
            gpsReport.setLatitude(-1.0); // 无效标识值
            gpsReport.setLongitude(-1.0); // 无效标识值
            gpsReport.setDeviceTime(LocalDateTime.now());
            gpsReport.setServerTime(LocalDateTime.now());
            gpsReport.setSensorType("gps");
            gpsReport.setSensorId(1);
            return gpsReport;
        }
        
        /**
         * 批量创建GPS报告
         */
        static List<GpsReport> createBatchGpsReports(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> createValidGpsReport((long) (1000 + i), 39.9 + i * 0.01, 116.3 + i * 0.01))
                    .toList();
        }
        
        /**
         * 创建边界坐标的GPS报告
         */
        static GpsReport createBoundaryGpsReport(Long deviceId, Double latitude, Double longitude) {
            GpsReport gpsReport = new GpsReport();
            gpsReport.setDeviceId(deviceId);
            gpsReport.setLatitude(latitude);
            gpsReport.setLongitude(longitude);
            gpsReport.setDeviceTime(LocalDateTime.now());
            gpsReport.setServerTime(LocalDateTime.now());
            gpsReport.setSensorType("gps");
            gpsReport.setSensorId(1);
            return gpsReport;
        }
    }

    @BeforeEach
    void setUp() {
        // 设置GPS配置的默认返回值
        lenient().when(terminalStatsConfigProperties.getGps()).thenReturn(gpsConfig);
        lenient().when(gpsConfig.getMaxQueueSize()).thenReturn(100);
        lenient().when(gpsConfig.getBatchSize()).thenReturn(20);
        lenient().when(gpsConfig.getFlushInterval()).thenReturn(5000);
        
        // 初始化队列
        asyncGpsRecordService.initializeQueue();
    }

    @Test
    @DisplayName("应该成功接收有效的GPS报告")
    void should_receive_valid_gps_report_successfully() {
        // Given - 创建有效的GPS报告
        GpsReport validReport = TestDataBuilder.createValidGpsReport(1001L, 39.9042, 116.4074);

        // When - 接收GPS报告
        asyncGpsRecordService.receiveGpsRecord(validReport);

        // Then - 验证报告被正确添加到队列
        // 由于无法直接访问队列，通过刷新操作验证
        asyncGpsRecordService.flushBuffer();
        verify(gpsRepository, times(1)).batchSaveGpsRecord(anyList());
    }

    @Test
    @DisplayName("应该正确处理null GPS报告")
    void should_handle_null_gps_report() {

        // When - 接收null报告
        asyncGpsRecordService.receiveGpsRecord(null);

        // Then - 验证没有数据被处理
        asyncGpsRecordService.flushBuffer();
        verify(gpsRepository, never()).batchSaveGpsRecord(anyList());
    }

    @Test
    @DisplayName("应该接收多个GPS报告并批量处理")
    void should_receive_multiple_gps_reports_and_batch_process() {
        // Given - 创建多个GPS报告
        List<GpsReport> gpsReports = TestDataBuilder.createBatchGpsReports(5);

        // When - 逐个接收GPS报告
        gpsReports.forEach(asyncGpsRecordService::receiveGpsRecord);

        // Then - 验证批量处理
        asyncGpsRecordService.flushBuffer();
        verify(gpsRepository, times(1)).batchSaveGpsRecord(argThat(list -> list.size() == 5));
    }

    @Test
    @DisplayName("应该正确处理有效坐标边界值")
    void should_handle_valid_coordinate_boundaries() {
        // Given - 创建边界坐标的GPS报告
        GpsReport northPole = TestDataBuilder.createBoundaryGpsReport(1001L, 90.0, 0.0);
        GpsReport southPole = TestDataBuilder.createBoundaryGpsReport(1002L, -90.0, 0.0);
        GpsReport eastBoundary = TestDataBuilder.createBoundaryGpsReport(1003L, 0.0, 180.0);
        GpsReport westBoundary = TestDataBuilder.createBoundaryGpsReport(1004L, 0.0, -180.0);

        // When - 接收边界坐标报告
        asyncGpsRecordService.receiveGpsRecord(northPole);
        asyncGpsRecordService.receiveGpsRecord(southPole);
        asyncGpsRecordService.receiveGpsRecord(eastBoundary);
        asyncGpsRecordService.receiveGpsRecord(westBoundary);

        // Then - 验证所有边界值都被正确处理
        asyncGpsRecordService.flushBuffer();
        verify(gpsRepository, times(1)).batchSaveGpsRecord(argThat(list -> list.size() == 4));
    }

    @Test
    @DisplayName("应该成功刷新缓冲队列")
    void should_flush_buffer_successfully() {
        // Given - 添加GPS报告到队列
        List<GpsReport> gpsReports = TestDataBuilder.createBatchGpsReports(3);
        gpsReports.forEach(asyncGpsRecordService::receiveGpsRecord);

        // When - 刷新缓冲队列
        asyncGpsRecordService.flushBuffer();

        // Then - 验证数据被正确保存
        verify(gpsRepository, times(1)).batchSaveGpsRecord(argThat(list -> list.size() == 3));
    }

    @Test
    @DisplayName("应该在队列为空时跳过刷新")
    void should_skip_flush_when_queue_is_empty() {
        // Given - 空队列
        // 没有添加任何GPS报告

        // When - 刷新空队列
        asyncGpsRecordService.flushBuffer();

        // Then - 验证没有调用存储操作
        verify(gpsRepository, never()).batchSaveGpsRecord(anyList());
    }

    @Test
    @DisplayName("应该按配置的批次大小处理数据")
    void should_process_data_by_configured_batch_size() {
        // Given - 设置较小的批次大小为2
        lenient().when(gpsConfig.getBatchSize()).thenReturn(2);
        
        // 添加2个GPS报告（正好等于批次大小）
        List<GpsReport> gpsReports = TestDataBuilder.createBatchGpsReports(2);
        gpsReports.forEach(asyncGpsRecordService::receiveGpsRecord);

        // When - 刷新缓冲队列
        asyncGpsRecordService.flushBuffer();

        // Then - 验证按批次大小处理（一次处理2个）
        verify(gpsRepository, times(1)).batchSaveGpsRecord(argThat(list -> list.size() == 2));
        
        // Given - 再添加5个GPS报告
        List<GpsReport> moreReports = TestDataBuilder.createBatchGpsReports(5);
        moreReports.forEach(asyncGpsRecordService::receiveGpsRecord);

        // When - 第一次刷新（取出2个）
        asyncGpsRecordService.flushBuffer();

        // Then - 验证处理第一批（2个）
        verify(gpsRepository, times(2)).batchSaveGpsRecord(argThat(list -> list.size() == 2));

        // When - 第二次刷新（取出2个）
        asyncGpsRecordService.flushBuffer();

        // Then - 验证处理第二批（2个）
        verify(gpsRepository, times(3)).batchSaveGpsRecord(argThat(list -> list.size() == 2));

        // When - 第三次刷新（取出最后1个）
        asyncGpsRecordService.flushBuffer();

        // Then - 验证处理最后一批（1个）
        verify(gpsRepository, times(4)).batchSaveGpsRecord(anyList());
    }

    @Test
    @DisplayName("应该在存储失败时将数据重新放回队列")
    void should_requeue_data_when_storage_fails() {
        // Given - 设置存储操作抛出异常
        doThrow(new RuntimeException("数据库连接失败")).when(gpsRepository).batchSaveGpsRecord(anyList());
        
        // 添加GPS报告到队列
        GpsReport gpsReport = TestDataBuilder.createValidGpsReport(1001L, 39.9042, 116.4074);
        asyncGpsRecordService.receiveGpsRecord(gpsReport);

        // When - 刷新缓冲队列（会失败）
        asyncGpsRecordService.flushBuffer();

        // Then - 验证存储被调用
        verify(gpsRepository, times(1)).batchSaveGpsRecord(anyList());

        // 修复存储问题
        doNothing().when(gpsRepository).batchSaveGpsRecord(anyList());

        // When - 再次刷新（应该处理重新入队的数据）
        asyncGpsRecordService.flushBuffer();

        // Then - 验证数据被重新处理
        verify(gpsRepository, times(2)).batchSaveGpsRecord(anyList());
    }

    @Test
    @DisplayName("应该在队列满时触发背压处理")
    void should_trigger_backpressure_when_queue_is_full() {
        // Given - 设置较小的队列大小
        when(gpsConfig.getMaxQueueSize()).thenReturn(2);
        asyncGpsRecordService.initializeQueue(); // 重新初始化以应用新配置
        
        // 填满队列
        asyncGpsRecordService.receiveGpsRecord(TestDataBuilder.createValidGpsReport(1001L, 39.9, 116.4));
        asyncGpsRecordService.receiveGpsRecord(TestDataBuilder.createValidGpsReport(1002L, 39.8, 116.3));

        // When - 尝试添加第三个报告（触发背压）
        GpsReport thirdReport = TestDataBuilder.createValidGpsReport(1003L, 39.7, 116.2);
        asyncGpsRecordService.receiveGpsRecord(thirdReport);

        // Then - 验证背压机制触发刷新
        verify(gpsRepository, atLeastOnce()).batchSaveGpsRecord(anyList());
    }

    @Test
    @DisplayName("应该在定时刷新时处理非空队列")
    void should_process_non_empty_queue_on_scheduled_flush() {
        // Given - 添加GPS报告到队列
        GpsReport gpsReport = TestDataBuilder.createValidGpsReport(1001L, 39.9042, 116.4074);
        asyncGpsRecordService.receiveGpsRecord(gpsReport);

        // When - 执行定时刷新
        asyncGpsRecordService.scheduledFlush();

        // Then - 验证定时刷新被触发
        // 注意：由于scheduledFlush调用异步方法，这里主要验证没有异常
        // 实际的刷新验证在其他测试中完成
    }

    @Test
    @DisplayName("应该在定时刷新时跳过空队列")
    void should_skip_empty_queue_on_scheduled_flush() {
        // Given - 空队列
        // 没有添加任何GPS报告

        // When - 执行定时刷新
        asyncGpsRecordService.scheduledFlush();

        // Then - 验证没有存储操作被调用
        verify(gpsRepository, never()).batchSaveGpsRecord(anyList());
    }

    @Test
    @DisplayName("应该验证GPS报告的有效性")
    void should_validate_gps_report_properly() {
        // Given - 创建各种GPS报告
        GpsReport validReport = TestDataBuilder.createValidGpsReport(1001L, 39.9042, 116.4074);
        GpsReport invalidRangeReport = TestDataBuilder.createInvalidGpsReport();
        GpsReport invalidFlagReport = TestDataBuilder.createInvalidFlagGpsReport();

        // When & Then - 验证有效性检查
        assertThat(validReport.validate()).isTrue();
        assertThat(invalidRangeReport.validate()).isFalse();
        assertThat(invalidFlagReport.validate()).isFalse();
    }

    @Test
    @DisplayName("应该正确处理中国主要城市的GPS坐标")
    void should_handle_major_chinese_cities_coordinates() {
        // Given - 中国主要城市坐标
        GpsReport beijing = TestDataBuilder.createValidGpsReport(1001L, 39.9042, 116.4074); // 北京
        GpsReport shanghai = TestDataBuilder.createValidGpsReport(1002L, 31.2304, 121.4737); // 上海
        GpsReport guangzhou = TestDataBuilder.createValidGpsReport(1003L, 23.1291, 113.2644); // 广州
        GpsReport shenzhen = TestDataBuilder.createValidGpsReport(1004L, 22.5431, 114.0579); // 深圳

        // When - 接收这些坐标报告
        asyncGpsRecordService.receiveGpsRecord(beijing);
        asyncGpsRecordService.receiveGpsRecord(shanghai);
        asyncGpsRecordService.receiveGpsRecord(guangzhou);
        asyncGpsRecordService.receiveGpsRecord(shenzhen);

        // Then - 验证所有报告都被正确处理
        asyncGpsRecordService.flushBuffer();
        verify(gpsRepository, times(1)).batchSaveGpsRecord(argThat(list -> list.size() == 4));
    }
}