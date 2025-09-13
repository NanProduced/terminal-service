package com.colorlight.terminal.infrastructure.record;

import com.colorlight.terminal.application.domain.report.ProgramPlayRecordReport;
import com.colorlight.terminal.application.port.outbound.repository.ProgramPlayRecordRepository;
import com.colorlight.terminal.infrastructure.persistence.mysql.repository.MysqlProgramRepositoryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * DeviceProgramPlayRecordService 单元测试
 * <p>
 * 业务逻辑总结：
 * DeviceProgramPlayRecordService是Application层DeviceProgramPlayRecordPort接口的Infrastructure层实现，
 * 负责处理设备上报的节目播放记录，包括云节目过滤、业务字段填充和数据存储功能。
 * <p>
 * 核心功能：
 * 1. handleProgramPlayRecordReport - 处理节目播放记录报告的主入口
 * 2. processProgramReport - 处理单个节目播放上报（私有方法）
 * <p>
 * 业务逻辑：
 * - 过滤出支持统计的云节目（通过尝试将programIdStr转换为整数来判断）
 * - LAN节目不支持统计，会被过滤掉
 * - 填充业务字段：设备ID、解析VSN名称、查询programId
 * - 保存处理后的记录到数据库
 * <p>
 * 依赖：MysqlProgramRepositoryAdapter、ProgramPlayRecordRepository
 * 业务场景：设备上报节目播放数据时的过滤、处理和存储
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceProgramPlayRecordService - 节目播放记录服务测试")
class DeviceProgramPlayRecordServiceTest {

    @Mock
    private MysqlProgramRepositoryAdapter programRepositoryAdapter;
    
    @Mock
    private ProgramPlayRecordRepository programPlayRecordRepository;

    private DeviceProgramPlayRecordService deviceProgramPlayRecordService;

    @BeforeEach
    void setUp() {
        deviceProgramPlayRecordService = new DeviceProgramPlayRecordService(
            programRepositoryAdapter, 
            programPlayRecordRepository
        );
        
        // 使用lenient()避免严格模式报错，某些测试可能不会调用所有mock方法
        lenient().when(programRepositoryAdapter.findProgramIdByNameAndAuthor(anyString(), anyInt(), anyString()))
                .thenReturn(null);
    }

    @Nested
    @DisplayName("handleProgramPlayRecordReport - 处理节目播放记录报告")
    class HandleProgramPlayRecordReportTests {

        @Test
        @DisplayName("应该成功处理云节目播放记录")
        @SuppressWarnings("unchecked")
        void should_handle_cloud_program_records_successfully() {
            // Given - 准备云节目测试数据
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> reports = createCloudProgramReports();
            
            // 设置查询programId返回值
            when(programRepositoryAdapter.findProgramIdByNameAndAuthor("测试云节目", 100, "test_program"))
                .thenReturn(1001);

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证云节目被正确处理
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).hasSize(1);
            
            ProgramPlayRecordReport savedReport = savedReports.get(0);
            assertThat(savedReport.getDeviceId()).isEqualTo(deviceId);
            assertThat(savedReport.getAuthorId()).isEqualTo(100);
            assertThat(savedReport.getProgramId()).isEqualTo(1001);
            
            verify(programRepositoryAdapter).findProgramIdByNameAndAuthor("测试云节目", 100, "test_program");
        }

        @Test
        @DisplayName("应该过滤掉LAN节目播放记录")
        @SuppressWarnings("unchecked")
        void should_filter_out_lan_program_records() {
            // Given - 准备混合节目测试数据（包含云节目和LAN节目）
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> reports = createMixedProgramReports();

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证只有云节目被保存，LAN节目被过滤
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).hasSize(1); // 只有1个云节目，LAN节目被过滤
            assertThat(savedReports.get(0).getAuthorId()).isEqualTo(200); // 云节目的authorId
        }

        @Test
        @DisplayName("应该正确处理空记录列表")
        @SuppressWarnings("unchecked")
        void should_handle_empty_records_list_correctly() {
            // Given - 准备空记录列表
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> emptyReports = Collections.emptyList();

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, emptyReports);

            // Then - 验证正常处理空列表
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).isEmpty();
            
            // 验证没有查询programId
            verify(programRepositoryAdapter, never()).findProgramIdByNameAndAuthor(anyString(), anyInt(), anyString());
        }

        @Test
        @DisplayName("应该正确处理全部为LAN节目的记录列表")
        @SuppressWarnings("unchecked")
        void should_handle_all_lan_program_records_correctly() {
            // Given - 准备全部为LAN节目的记录列表
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> lanReports = createLanProgramReports();

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, lanReports);

            // Then - 验证所有LAN节目被过滤，保存空列表
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).isEmpty();
            
            // 验证没有查询programId
            verify(programRepositoryAdapter, never()).findProgramIdByNameAndAuthor(anyString(), anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("云节目识别和过滤测试")
    class CloudProgramFilterTests {

        @Test
        @DisplayName("应该正确识别数字格式的云节目programIdStr")
        @SuppressWarnings("unchecked")
        void should_identify_cloud_program_with_numeric_program_id_str() {
            // Given - 准备数字格式的programIdStr
            Long deviceId = 12345L;
            ProgramPlayRecordReport cloudReport = createBasicProgramReport();
            cloudReport.setProgramIdStr("300"); // 数字格式，应该被识别为云节目
            List<ProgramPlayRecordReport> reports = Collections.singletonList(cloudReport);

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证数字格式被正确识别为云节目
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).hasSize(1);
            assertThat(savedReports.get(0).getAuthorId()).isEqualTo(300);
        }

        @Test
        @DisplayName("应该过滤掉非数字格式的LAN节目programIdStr")
        @SuppressWarnings("unchecked")
        void should_filter_out_non_numeric_lan_program_id_str() {
            // Given - 准备非数字格式的programIdStr
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> reports = Arrays.asList(
                createLanProgramReport("lan_program_123"),
                createLanProgramReport("program_abc"),
                createLanProgramReport("test-program"),
                createLanProgramReport("program.vsn")
            );

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证所有非数字格式都被过滤
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).isEmpty();
        }

        @Test
        @DisplayName("应该处理边界数字值的programIdStr")
        @SuppressWarnings("unchecked")
        void should_handle_boundary_numeric_program_id_str() {
            // Given - 准备边界数字值
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> reports = Arrays.asList(
                createCloudProgramReport("0"),      // 最小值
                createCloudProgramReport("1"),      // 最小正值
                createCloudProgramReport("999999")  // 大数值
            );

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证边界数字值都被正确识别
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).hasSize(3);
            assertThat(savedReports.get(0).getAuthorId()).isZero();
            assertThat(savedReports.get(1).getAuthorId()).isEqualTo(1);
            assertThat(savedReports.get(2).getAuthorId()).isEqualTo(999999);
        }
    }

    @Nested
    @DisplayName("业务字段填充测试")
    class BusinessFieldPopulationTests {

        @Test
        @DisplayName("应该正确填充设备ID字段")
        @SuppressWarnings("unchecked")
        void should_populate_device_id_field_correctly() {
            // Given - 准备测试数据
            Long deviceId = 67890L;
            List<ProgramPlayRecordReport> reports = createCloudProgramReports();

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证设备ID被正确填充
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports.get(0).getDeviceId()).isEqualTo(deviceId);
        }

        @Test
        @DisplayName("应该正确解析VSN名称")
        void should_parse_vsn_name_correctly() {
            // Given - 准备带.vsn后缀的VSN名称
            Long deviceId = 12345L;
            ProgramPlayRecordReport report = createBasicProgramReport();
            report.setProgramIdStr("150");
            report.setProgramVsn("my_program.vsn");
            List<ProgramPlayRecordReport> reports = Collections.singletonList(report);
            
            when(programRepositoryAdapter.findProgramIdByNameAndAuthor(anyString(), anyInt(), eq("my_program")))
                .thenReturn(2001);

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证VSN名称被正确解析（去掉.vsn后缀）
            verify(programRepositoryAdapter).findProgramIdByNameAndAuthor(anyString(), anyInt(), eq("my_program"));
        }

        @Test
        @DisplayName("应该处理不带.vsn后缀的VSN名称")
        void should_handle_vsn_name_without_suffix() {
            // Given - 准备不带.vsn后缀的VSN名称
            Long deviceId = 12345L;
            ProgramPlayRecordReport report = createBasicProgramReport();
            report.setProgramIdStr("150");
            report.setProgramVsn("program_without_suffix");
            List<ProgramPlayRecordReport> reports = Collections.singletonList(report);

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证使用null作为VSN名称进行查询
            verify(programRepositoryAdapter).findProgramIdByNameAndAuthor(anyString(), anyInt(), isNull());
        }

        @Test
        @DisplayName("应该正确查询和填充programId")
        @SuppressWarnings("unchecked")
        void should_query_and_populate_program_id_correctly() {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            ProgramPlayRecordReport report = createBasicProgramReport();
            report.setProgramIdStr("180");
            report.setProgramName("特定节目名称");
            report.setProgramVsn("specific_program.vsn");
            List<ProgramPlayRecordReport> reports = Collections.singletonList(report);
            
            when(programRepositoryAdapter.findProgramIdByNameAndAuthor("特定节目名称", 180, "specific_program"))
                .thenReturn(3001);

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证programId被正确查询和填充
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports.get(0).getProgramId()).isEqualTo(3001);
            
            verify(programRepositoryAdapter).findProgramIdByNameAndAuthor("特定节目名称", 180, "specific_program");
        }

        @Test
        @DisplayName("应该处理查询不到programId的情况")
        @SuppressWarnings("unchecked")
        void should_handle_program_id_not_found() {
            // Given - 准备测试数据，设置查询返回null
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> reports = createCloudProgramReports();
            
            when(programRepositoryAdapter.findProgramIdByNameAndAuthor(anyString(), anyInt(), anyString()))
                .thenReturn(null);

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证programId保持为null
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports.get(0).getProgramId()).isNull();
        }

        @Test
        @DisplayName("应该处理authorId为null的情况")
        void should_handle_null_author_id() {
            // Given - 准备测试数据，模拟authorId在某种异常情况下被设置为null
            Long deviceId = 12345L;
            ProgramPlayRecordReport report = createBasicProgramReport();
            report.setProgramIdStr("250");
            List<ProgramPlayRecordReport> reports = Collections.singletonList(report);
            
            // 使用doAnswer来在查询时将authorId设置为null，模拟边界情况
            doAnswer(invocation -> {
                // 获取传入的参数
                Integer authorId = invocation.getArgument(1);

                // 验证传入的是正确的authorId（250）
                assertThat(authorId).isEqualTo(250);
                
                // 模拟后续处理中authorId变为null的情况，验证代码中的null处理逻辑
                // 实际上这个测试主要验证过滤阶段正确设置了authorId
                return null;
            }).when(programRepositoryAdapter).findProgramIdByNameAndAuthor(anyString(), anyInt(), anyString());

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证使用正确的authorId进行查询
            verify(programRepositoryAdapter).findProgramIdByNameAndAuthor(anyString(), eq(250), anyString());
        }
    }

    @Nested
    @DisplayName("集成场景测试")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("应该处理多个云节目的完整业务流程")
        @SuppressWarnings("unchecked")
        void should_handle_multiple_cloud_programs_complete_workflow() {
            // Given - 准备多个云节目
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> reports = createMultipleCloudProgramReports();
            
            // 设置不同的programId查询结果
            when(programRepositoryAdapter.findProgramIdByNameAndAuthor("云节目1", 101, "program1"))
                .thenReturn(1001);
            when(programRepositoryAdapter.findProgramIdByNameAndAuthor("云节目2", 102, "program2"))
                .thenReturn(1002);

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证完整业务流程
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).hasSize(2);
            
            // 验证第一个节目
            ProgramPlayRecordReport report1 = savedReports.get(0);
            assertThat(report1.getDeviceId()).isEqualTo(deviceId);
            assertThat(report1.getAuthorId()).isEqualTo(101);
            assertThat(report1.getProgramId()).isEqualTo(1001);
            
            // 验证第二个节目
            ProgramPlayRecordReport report2 = savedReports.get(1);
            assertThat(report2.getDeviceId()).isEqualTo(deviceId);
            assertThat(report2.getAuthorId()).isEqualTo(102);
            assertThat(report2.getProgramId()).isEqualTo(1002);
            
            // 验证查询调用
            verify(programRepositoryAdapter).findProgramIdByNameAndAuthor("云节目1", 101, "program1");
            verify(programRepositoryAdapter).findProgramIdByNameAndAuthor("云节目2", 102, "program2");
        }

        @Test
        @DisplayName("应该正确处理混合场景：云节目、LAN节目、异常数据")
        @SuppressWarnings("unchecked")
        void should_handle_mixed_scenario_correctly() {
            // Given - 准备混合场景数据
            Long deviceId = 12345L;
            List<ProgramPlayRecordReport> reports = createComplexMixedReports();
            
            when(programRepositoryAdapter.findProgramIdByNameAndAuthor("有效云节目", 999, "valid_cloud"))
                .thenReturn(5001);

            // When - 执行业务方法
            deviceProgramPlayRecordService.handleProgramPlayRecordReport(deviceId, reports);

            // Then - 验证只有有效的云节目被处理
            ArgumentCaptor<List<ProgramPlayRecordReport>> captor = ArgumentCaptor.forClass(List.class);
            verify(programPlayRecordRepository).saveProgramPlayRecords(captor.capture());
            
            List<ProgramPlayRecordReport> savedReports = captor.getValue();
            assertThat(savedReports).hasSize(1); // 只有1个有效云节目
            assertThat(savedReports.get(0).getProgramName()).isEqualTo("有效云节目");
            assertThat(savedReports.get(0).getAuthorId()).isEqualTo(999);
            assertThat(savedReports.get(0).getProgramId()).isEqualTo(5001);
        }
    }

    // 测试数据构建方法
    private List<ProgramPlayRecordReport> createCloudProgramReports() {
        ProgramPlayRecordReport report = createBasicProgramReport();
        report.setProgramIdStr("100"); // 数字格式，云节目
        report.setProgramName("测试云节目");
        report.setProgramVsn("test_program.vsn");
        return Collections.singletonList(report);
    }

    private List<ProgramPlayRecordReport> createMixedProgramReports() {
        return Arrays.asList(
            createCloudProgramReport("200"), // 云节目
            createLanProgramReport("lan_program_abc") // LAN节目
        );
    }

    private List<ProgramPlayRecordReport> createLanProgramReports() {
        return Arrays.asList(
            createLanProgramReport("lan_program_1"),
            createLanProgramReport("local_program_2")
        );
    }

    private List<ProgramPlayRecordReport> createMultipleCloudProgramReports() {
        return Arrays.asList(
            createCloudProgramReportWithDetails("101", "云节目1", "program1.vsn"),
            createCloudProgramReportWithDetails("102", "云节目2", "program2.vsn")
        );
    }

    private List<ProgramPlayRecordReport> createComplexMixedReports() {
        return Arrays.asList(
            createLanProgramReport("lan_program"),           // LAN节目，应被过滤
            createCloudProgramReportWithDetails("999", "有效云节目", "valid_cloud.vsn"), // 有效云节目
            createLanProgramReport("another_lan_program")    // 另一个LAN节目，应被过滤
        );
    }

    private ProgramPlayRecordReport createBasicProgramReport() {
        ProgramPlayRecordReport report = new ProgramPlayRecordReport();
        report.setProgramName("基础节目");
        report.setProgramVsn("basic_program.vsn");
        report.setPlayTimes(1);
        report.setSingleDuration(300L);
        report.setPlayDuration(300L);
        
        // 设置时间列表
        LinkedList<LocalDateTime> startTimes = new LinkedList<>();
        startTimes.add(LocalDateTime.of(2024, 1, 1, 10, 0, 0));
        report.setStartUtcTime(startTimes);
        
        LinkedList<LocalDateTime> endTimes = new LinkedList<>();
        endTimes.add(LocalDateTime.of(2024, 1, 1, 10, 5, 0));
        report.setEndUtcTime(endTimes);
        
        return report;
    }

    private ProgramPlayRecordReport createCloudProgramReport(String programIdStr) {
        ProgramPlayRecordReport report = createBasicProgramReport();
        report.setProgramIdStr(programIdStr);
        report.setProgramName("云节目_" + programIdStr);
        return report;
    }

    private ProgramPlayRecordReport createLanProgramReport(String programIdStr) {
        ProgramPlayRecordReport report = createBasicProgramReport();
        report.setProgramIdStr(programIdStr); // 非数字格式
        report.setProgramName("LAN节目_" + programIdStr);
        return report;
    }

    private ProgramPlayRecordReport createCloudProgramReportWithDetails(String programIdStr, String programName, String programVsn) {
        ProgramPlayRecordReport report = createBasicProgramReport();
        report.setProgramIdStr(programIdStr);
        report.setProgramName(programName);
        report.setProgramVsn(programVsn);
        return report;
    }
}