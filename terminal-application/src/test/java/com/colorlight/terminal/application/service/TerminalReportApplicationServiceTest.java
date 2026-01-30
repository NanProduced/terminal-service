package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.report.*;
import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.application.domain.sensor.SensorReport;
import com.colorlight.terminal.application.dto.record.ScreenshotUploadRecord;
import com.colorlight.terminal.application.enums.TerminalLogType;
import com.colorlight.terminal.application.handler.ReportTimePopulator;
import com.colorlight.terminal.application.port.outbound.repository.DownloadingRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalLogRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceGpsHandlePort;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceMediaPlayRecordPort;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceProgramPlayRecordPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceDownloadingPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceSwitchRecordPort;
import com.colorlight.terminal.application.port.outbound.storage.ScreenshotStoragePort;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 终端报告应用服务单元测试
 * 
 * <p>测试策略：</p>
 * <ul>
 *   <li>验证异步报告处理的完整流程</li>
 *   <li>测试JSON反序列化和数据转换</li>
 *   <li>验证多种报告类型的处理逻辑</li>
 *   <li>测试传感器数据分类处理机制</li>
 *   <li>验证异常处理和错误传播</li>
 * </ul>
 * 
 * @author Nan
 */
@DisplayName("终端报告管理服务测试")
class TerminalReportApplicationServiceTest extends BaseApplicationServiceTest {
    
    @Mock
    private TerminalStatusReportRepository terminalStatusReportRepository;
    
    @Mock
    private TerminalLogRepository terminalLogRepository;
    
    @Mock
    private DeviceSwitchRecordPort deviceSwitchRecordPort;
    
    @Mock
    private DeviceMediaPlayRecordPort deviceMediaPlayRecordPort;
    
    @Mock
    private DeviceProgramPlayRecordPort deviceProgramPlayRecordPort;
    
    @Mock
    private DeviceGpsHandlePort deviceGpsHandlePort;
    
    @Mock
    private ScreenshotStoragePort screenshotStoragePort;
    
    @Mock
    private DeviceDownloadingPort deviceDownloadingPort;
    
    @Mock
    private DownloadingRepository downloadingRepository;
    
    @Mock
    private MainServerRpcPort mainServerRpcPort;
    
    @InjectMocks
    private TerminalReportApplicationService service;

    @Captor
    private ArgumentCaptor<List<TerminalLog>> terminalLogsCaptor;
    
    @Captor
    private ArgumentCaptor<GpsReport> gpsReportCaptor;

    // 静态工具类Mock
    private MockedStatic<JsonUtils> jsonUtilsMock;
    private MockedStatic<ReportTimePopulator> reportTimePopulatorMock;

    // 测试常量
    private static final String TEST_REPORT_STR = "{\"terminal\":{\"name\":\"test\"}}";
    private static final String TEST_MEDIA_REPORT_STR = "[{\"mediaId\":1}]";
    private static final String TEST_PROGRAM_REPORT_STR = "[{\"programId\":1}]";
    private static final String TEST_SENSOR_REPORT_STR = "[{\"sensorType\":\"gps\"}]";
    private static final String TEST_DOWNLOADING_REPORT_STR = "{\"what\":\"program\"}";
    private static final String TEST_CLIENT_IP = "192.168.1.100";
    private static final LocalDateTime TEST_REPORT_TIME = LocalDateTime.of(2023, 1, 1, 12, 0);
    private static final byte[] TEST_SCREENSHOT_DATA = "test-screenshot-data".getBytes();

    // ObjectMapper用于创建JsonNode
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @BeforeEach
    void setUp() {
        jsonUtilsMock = mockStatic(JsonUtils.class);
        reportTimePopulatorMock = mockStatic(ReportTimePopulator.class);
    }
    
    @AfterEach
    void tearDown() {
        jsonUtilsMock.close();
        reportTimePopulatorMock.close();
    }
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        /**
         * 创建简化的终端状态报告
         */
        static TerminalStatusReport createSimpleStatusReport() {
            return TerminalStatusReport.builder()
                    .terminal(TerminalStatusReport.Terminal.builder()
                            .name("test-terminal")
                            .reportTime(1234567890L)
                            .build())
                    .info(TerminalStatusReport.InfoWrapper.builder()
                            .info(TerminalStatusReport.Info.builder()
                                    .vername("v1.0.0")
                                    .serialno("TEST123456")
                                    .up(3600L)
                                    .build())
                            .reportTime(1234567890L)
                            .build())
                    .build();
        }
        
        /**
         * 创建带Info的终端状态报告
         */
        static TerminalStatusReport createStatusReportWithInfo() {
            return TerminalStatusReport.builder()
                    .info(TerminalStatusReport.InfoWrapper.builder()
                            .info(TerminalStatusReport.Info.builder()
                                    .vername("v1.0.0")
                                    .up(7200L)
                                    .build())
                            .reportTime(1234567890L)
                            .build())
                    .build();
        }
        
        /**
         * 创建终端日志列表
         */
        static List<TerminalLog> createTerminalLogs() {
            return List.of(
                    TerminalLog.builder()
                            .operation(TerminalLogType.MEMORY_REPORT)
                            .logType("runtime")
                            .logSubtype1("memory")
                            .logArg1("test-arg1")
                            .deviceTime("2023-01-01 12:00:00")
                            .build(),
                    TerminalLog.builder()
                            .operation(TerminalLogType.REDIAL)
                            .logType("connectivity")
                            .logSubtype1("4g")
                            .logSubtype2("redial")
                            .logArg1("network-error")
                            .deviceTime("2023-01-01 12:01:00")
                            .build()
            );
        }
        
        /**
         * 创建媒体播放记录报告列表
         */
        static List<MediaPlayRecordReport> createMediaPlayRecords() {
            MediaPlayRecordReport mediaPlayRecord = new MediaPlayRecordReport();
            mediaPlayRecord.setProgramName("test-program");
            mediaPlayRecord.setResOriginName("test-media");
            mediaPlayRecord.setResMd5Name("test-md5");
            mediaPlayRecord.setPageName("test-page");
            mediaPlayRecord.setPageIndex(0);
            mediaPlayRecord.setRegionName("test-region");
            mediaPlayRecord.setRegionIndex(0);
            mediaPlayRecord.setStartUtcTime(LocalDateTime.now());
            mediaPlayRecord.setStartLocalTime(LocalDateTime.now());
            mediaPlayRecord.setDuration(30L);
            mediaPlayRecord.setItemType("video");
            return List.of(mediaPlayRecord);
        }
        
        /**
         * 创建节目播放记录报告列表
         */
        static List<ProgramPlayRecordReport> createProgramPlayRecords() {
            ProgramPlayRecordReport programPlayRecord = new ProgramPlayRecordReport();
            programPlayRecord.setProgramVsn("test-vsn");
            programPlayRecord.setProgramName("test-program");
            programPlayRecord.setProgramIdStr("201");
            programPlayRecord.setPlayTimes(1);
            programPlayRecord.setSingleDuration(120L);
            programPlayRecord.setPlayDuration(120L);
            programPlayRecord.setProgramId(201);
            programPlayRecord.setAuthorId(1001);
            return List.of(programPlayRecord);
        }
        
        /**
         * 创建GPS传感器报告列表
         */
        static List<SensorReport> createGpsSensorReports() {
            GpsReport gpsReport = new GpsReport();
            gpsReport.setSensorType("gps");
            gpsReport.setLatitude(39.9042);
            gpsReport.setLongitude(116.4074);
            gpsReport.setAccuracy(5.0);
            gpsReport.setDeviceTime(LocalDateTime.now());
            return List.of(gpsReport);
        }
        
        /**
         * 创建有效的GPS报告
         */
        static GpsReport createValidGpsReport() {
            GpsReport gpsReport = new GpsReport();
            gpsReport.setSensorType("gps");
            gpsReport.setLatitude(39.9042);
            gpsReport.setLongitude(116.4074);
            gpsReport.setAccuracy(5.0);
            gpsReport.setDeviceTime(LocalDateTime.now());
            return gpsReport;
        }
        
        /**
         * 创建无效的GPS报告
         */
        static GpsReport createInvalidGpsReport() {
            GpsReport gpsReport = new GpsReport();
            gpsReport.setSensorType("gps");
            gpsReport.setLatitude(-1.0); // 无效GPS标识值
            gpsReport.setLongitude(-1.0); // 无效GPS标识值
            return gpsReport;
        }
        
        /**
         * 创建其他类型传感器报告
         */
        static List<SensorReport> createOtherSensorReports() {
            SensorReport otherReport = new SensorReport();
            otherReport.setSensorType("temperature");
            otherReport.setDeviceTime(LocalDateTime.now());
            return List.of(otherReport);
        }
        
        /**
         * 创建截图上传记录
         */
        static ScreenshotUploadRecord createScreenshotUploadRecord() {
            ScreenshotUploadRecord screenshotRecord = new ScreenshotUploadRecord();
            screenshotRecord.setDeviceId(TEST_DEVICE_ID);
            screenshotRecord.setScreenshotData(TEST_SCREENSHOT_DATA);
            screenshotRecord.setContentLength((long) TEST_SCREENSHOT_DATA.length);
            screenshotRecord.setUploadTime(LocalDateTime.now());
            return screenshotRecord;
        }
        
        /**
         * 创建下载报告
         */
        static DownloadingReport createDownloadingReport() {
            DownloadingReport report = new DownloadingReport();
            report.setWhat("program_status");
            return report;
        }
    }
    
    @Nested
    @DisplayName("状态报告测试")
    class StatusReportTests {
        
        @Test
        @DisplayName("应该成功异步保存状态报告")
        void should_save_status_report_successfully() {
            // Given - 正常状态报告
            TerminalStatusReport expectedReport = TestDataBuilder.createSimpleStatusReport();
            jsonUtilsMock.when(() -> JsonUtils.fromJson(TEST_REPORT_STR, TerminalStatusReport.class))
                         .thenReturn(expectedReport);
            
            // When - 执行异步保存
            service.asyncSaveStatusReport(TEST_DEVICE_ID, TEST_REPORT_STR, TEST_CLIENT_IP);
            
            // Then - 验证调用流程
            verify(mainServerRpcPort).notifyLedStatus(TEST_DEVICE_ID, TEST_REPORT_STR);
            jsonUtilsMock.verify(() -> JsonUtils.fromJson(eq(TEST_REPORT_STR), eq(TerminalStatusReport.class)));
            reportTimePopulatorMock.verify(() -> ReportTimePopulator.populateReportTime(eq(expectedReport), anyLong()));
            assertThat(expectedReport.getClientIp()).isEqualTo(TEST_CLIENT_IP);
            verify(terminalStatusReportRepository).saveTerminalStatusReport(TEST_DEVICE_ID, expectedReport, TEST_REPORT_STR);
        }
        
        @Test
        @DisplayName("应该处理状态报告中的开机时间戳")
        void should_handle_switch_on_record_when_info_exists() {
            // Given - 包含Info信息的状态报告
            TerminalStatusReport reportWithInfo = TestDataBuilder.createStatusReportWithInfo();
            jsonUtilsMock.when(() -> JsonUtils.fromJson(TEST_REPORT_STR, TerminalStatusReport.class))
                         .thenReturn(reportWithInfo);
            
            // When - 执行异步保存
            service.asyncSaveStatusReport(TEST_DEVICE_ID, TEST_REPORT_STR, TEST_CLIENT_IP);
            
            // Then - 验证开机记录处理
            verify(deviceSwitchRecordPort).asyncHandlerSwitchOnRecord(TEST_DEVICE_ID, reportWithInfo.getInfo());
        }
        
        @Test
        @DisplayName("应该在JSON解析失败时记录调试日志")
        void should_log_debug_when_json_parsing_fails() {
            // Given - JSON解析失败
            jsonUtilsMock.when(() -> JsonUtils.fromJson(TEST_REPORT_STR, TerminalStatusReport.class))
                         .thenThrow(new RuntimeException("JSON解析失败"));
            
            // When - 执行异步保存（不应抛出异常）
            service.asyncSaveStatusReport(TEST_DEVICE_ID, TEST_REPORT_STR, TEST_CLIENT_IP);
            
            // Then - 验证主服务通知仍被调用，但异常被捕获
            verify(mainServerRpcPort).notifyLedStatus(TEST_DEVICE_ID, TEST_REPORT_STR);
            verify(terminalStatusReportRepository, never()).saveTerminalStatusReport(any(), any(), any());
        }
    }
    
    @Nested
    @DisplayName("终端日志测试")
    class LogTests {
        
        @Test
        @DisplayName("应该成功异步保存终端日志")
        void should_save_terminal_log_successfully() {
            // Given - 终端日志列表
            List<TerminalLog> logs = TestDataBuilder.createTerminalLogs();
            
            // When - 执行异步保存
            service.asyncSaveTerminalLog(TEST_DEVICE_ID, logs);
            
            // Then - 验证设备ID设置和批量保存
            verify(terminalLogRepository).batchSaveTerminalLog(terminalLogsCaptor.capture());
            
            List<TerminalLog> capturedLogs = terminalLogsCaptor.getValue();
            assertThat(capturedLogs).hasSize(2).allMatch(log -> log.getDeviceId().equals(TEST_DEVICE_ID));
        }
        
        @Test
        @DisplayName("应该在保存终端日志失败时抛出业务异常")
        void should_throw_business_exception_when_save_terminal_log_fails() {
            // Given - 批量保存失败
            List<TerminalLog> logs = TestDataBuilder.createTerminalLogs();
            doThrow(new RuntimeException("批量保存失败"))
                    .when(terminalLogRepository).batchSaveTerminalLog(any());
            
            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> service.asyncSaveTerminalLog(TEST_DEVICE_ID, logs))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "TM0004")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }
    
    @Nested
    @DisplayName("播放记录测试")
    class PlayRecordTests {
        
        @Test
        @DisplayName("应该成功处理媒体播放记录报告")
        void should_handle_media_play_record_successfully() {
            // Given - 媒体播放记录
            List<MediaPlayRecordReport> expectedRecords = TestDataBuilder.createMediaPlayRecords();
            jsonUtilsMock.when(() -> JsonUtils.fromJson(eq(TEST_MEDIA_REPORT_STR), any(TypeReference.class)))
                         .thenReturn(expectedRecords);
            
            // When - 处理媒体播放记录
            service.asyncHandleMediaPlayRecordReport(TEST_DEVICE_ID, TEST_MEDIA_REPORT_STR);
            
            // Then - 验证处理流程
            jsonUtilsMock.verify(() -> JsonUtils.fromJson(eq(TEST_MEDIA_REPORT_STR), any(TypeReference.class)));
            verify(deviceMediaPlayRecordPort).handleMediaPlayRecordReport(TEST_DEVICE_ID, expectedRecords);
        }
        
        @Test
        @DisplayName("应该成功处理节目播放记录报告")
        void should_handle_program_play_record_successfully() {
            // Given - 节目播放记录
            List<ProgramPlayRecordReport> expectedRecords = TestDataBuilder.createProgramPlayRecords();
            jsonUtilsMock.when(() -> JsonUtils.fromJson(eq(TEST_PROGRAM_REPORT_STR), any(TypeReference.class)))
                         .thenReturn(expectedRecords);
            
            // When - 处理节目播放记录
            service.asyncHandleProgramPlayRecordReport(TEST_DEVICE_ID, TEST_PROGRAM_REPORT_STR);
            
            // Then - 验证处理流程
            jsonUtilsMock.verify(() -> JsonUtils.fromJson(eq(TEST_PROGRAM_REPORT_STR), any(TypeReference.class)));
            verify(deviceProgramPlayRecordPort).handleProgramPlayRecordReport(TEST_DEVICE_ID, expectedRecords);
        }
    }
    
    @Nested
    @DisplayName("传感器报告测试")
    class SensorReportTests {
        
        @Test
        @DisplayName("应该成功处理GPS传感器报告")
        void should_handle_gps_sensor_report_successfully() {
            // Given - GPS传感器报告
            List<SensorReport> gpsReports = TestDataBuilder.createGpsSensorReports();

            // When - 处理传感器报告
            service.asyncHandleSensorReport(TEST_DEVICE_ID, TEST_REPORT_TIME, gpsReports);

            // Then - 验证GPS处理
            verify(deviceGpsHandlePort).receiveGpsRecord(gpsReportCaptor.capture());

            GpsReport capturedGpsReport = gpsReportCaptor.getValue();
            assertThat(capturedGpsReport.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            assertThat(capturedGpsReport.getServerTime()).isEqualTo(TEST_REPORT_TIME);
            assertThat(capturedGpsReport.getSensorType()).isEqualTo("gps");
        }
        
        @Test
        @DisplayName("应该跳过无效的GPS传感器报告")
        void should_skip_invalid_gps_sensor_report() {
            // Given - 无效GPS报告
            List<SensorReport> invalidGpsReports = List.of(TestDataBuilder.createInvalidGpsReport());

            // When - 处理传感器报告
            service.asyncHandleSensorReport(TEST_DEVICE_ID, TEST_REPORT_TIME, invalidGpsReports);

            // Then - 验证无效GPS报告被跳过
            verify(deviceGpsHandlePort, never()).receiveGpsRecord(any());
        }
        
        @Test
        @DisplayName("应该处理其他类型传感器报告")
        void should_handle_other_sensor_reports() {
            // Given - 其他类型传感器报告
            List<SensorReport> otherReports = TestDataBuilder.createOtherSensorReports();

            // When - 处理传感器报告
            service.asyncHandleSensorReport(TEST_DEVICE_ID, TEST_REPORT_TIME, otherReports);

            // Then - 验证其他类型传感器报告被处理（仅debug日志）
            verify(deviceGpsHandlePort, never()).receiveGpsRecord(any());
        }
        
        @Test
        @DisplayName("应该在传感器报告为空时返回")
        void should_return_early_when_sensor_reports_empty() {
            // Given - 空传感器报告列表

            // When - 处理传感器报告
            service.asyncHandleSensorReport(TEST_DEVICE_ID, TEST_REPORT_TIME, Collections.emptyList());

            // Then - 验证提前返回，没有进一步处理
            verify(deviceGpsHandlePort, never()).receiveGpsRecord(any());
        }
        
        @Test
        @DisplayName("应该在传感器报告处理异常时抛出业务异常")
        void should_throw_business_exception_when_sensor_report_processing_fails() {
            // Given - GPS处理失败
            List<SensorReport> gpsReports = TestDataBuilder.createGpsSensorReports();
            doThrow(new RuntimeException("GPS处理失败"))
                    .when(deviceGpsHandlePort).receiveGpsRecord(any());

            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> service.asyncHandleSensorReport(TEST_DEVICE_ID, TEST_REPORT_TIME, gpsReports))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "TM0004")
                    .hasMessageContaining("传感器数据处理失败")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }
    
    @Nested
    @DisplayName("截图上传测试")
    class ScreenshotTests {
        
        @Test
        @DisplayName("应该成功处理设备截图上传")
        void should_handle_device_screenshot_upload_successfully() {
            // Given - 截图上传记录
            ScreenshotUploadRecord uploadRecord = TestDataBuilder.createScreenshotUploadRecord();
            
            // When - 处理截图上传
            service.asyncSaveDeviceScreenshot(uploadRecord);
            
            // Then - 验证上传处理
            verify(screenshotStoragePort).uploadScreenshot(
                    eq(TEST_DEVICE_ID),
                    eq(TEST_SCREENSHOT_DATA),
                    eq((long) TEST_SCREENSHOT_DATA.length),
                    eq(uploadRecord.getUploadTime())
            );
        }
        
        @Test
        @DisplayName("应该在截图数据为空时抛出业务异常")
        void should_throw_business_exception_when_screenshot_data_is_null() {
            // Given - 空截图数据
            ScreenshotUploadRecord uploadRecord = TestDataBuilder.createScreenshotUploadRecord();
            uploadRecord.setScreenshotData(null);
            
            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> service.asyncSaveDeviceScreenshot(uploadRecord))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "TM0002")
                    .hasMessageContaining("截图数据不能为空");
            
            verify(screenshotStoragePort, never()).uploadScreenshot(any(), any(), anyLong(), any());
        }
        
        @Test
        @DisplayName("应该在截图上传失败时抛出业务异常")
        void should_throw_business_exception_when_screenshot_upload_fails() {
            // Given - 截图上传失败
            ScreenshotUploadRecord uploadRecord = TestDataBuilder.createScreenshotUploadRecord();
            doThrow(new RuntimeException("存储服务异常"))
                    .when(screenshotStoragePort).uploadScreenshot(any(), any(), anyLong(), any());
            
            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> service.asyncSaveDeviceScreenshot(uploadRecord))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "TM0004")
                    .hasMessageContaining("截图上传失败")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }
    
    @Nested
    @DisplayName("下载进度上报测试")
    class DownloadingReportTests {
        
        @Test
        @DisplayName("应该成功保存下载进度上报")
        void should_save_downloading_report_successfully() {
            // Given - 下载报告
            DownloadingReport expectedReport = TestDataBuilder.createDownloadingReport();
            jsonUtilsMock.when(() -> JsonUtils.fromJson(TEST_DOWNLOADING_REPORT_STR, DownloadingReport.class))
                         .thenReturn(expectedReport);
            
            // When - 保存下载报告
            service.asyncSaveDownloadingReport(TEST_DEVICE_ID, TEST_DOWNLOADING_REPORT_STR);
            
            // Then - 验证缓存和持久化
            verify(deviceDownloadingPort).saveDownloadingStatus(TEST_DEVICE_ID, expectedReport);
            verify(downloadingRepository).saveDeviceDownloadingStatus(TEST_DEVICE_ID, expectedReport);
        }
        
        @Test
        @DisplayName("应该在下载进度上报保存失败时抛出业务异常")
        void should_throw_business_exception_when_downloading_report_save_fails() {
            // Given - JSON解析失败
            jsonUtilsMock.when(() -> JsonUtils.fromJson(TEST_DOWNLOADING_REPORT_STR, DownloadingReport.class))
                         .thenThrow(new RuntimeException("JSON解析失败"));
            
            // When & Then - 验证抛出业务异常
            assertThatThrownBy(() -> service.asyncSaveDownloadingReport(TEST_DEVICE_ID, TEST_DOWNLOADING_REPORT_STR))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "TM0004")
                    .hasMessageContaining("下载进度保存失败")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }
}
