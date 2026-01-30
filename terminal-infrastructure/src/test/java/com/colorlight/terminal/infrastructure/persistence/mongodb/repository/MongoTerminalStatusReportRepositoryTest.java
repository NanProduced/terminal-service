package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalStatusReportDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

/**
 * MongoTerminalStatusReportRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：保存和查询终端状态上报数据
 * 2. 保存逻辑：支持基于原始JSON字段的增量upsert
 * 3. 数据合并：原始JSON缺失时回退到兼容保存逻辑
 * 4. 时间管理：自动设置updateTime为当前时间
 * 5. 查询功能：根据设备ID查询最新状态报告
 * 6. 异常处理：查询异常时返回空Optional并记录日志
 * <p>
 * 测试策略：
 * - 保存新记录场景测试
 * - 更新现有记录场景测试
 * - 查询成功场景测试
 * - 查询不存在记录场景测试
 * - 查询异常处理测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoTerminalStatusReportRepository单元测试")
class MongoTerminalStatusReportRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MongoTerminalStatusReportRepository repository;

    private Long deviceId;
    private TerminalStatusReport report;
    private TerminalStatusReportDocument existingDocument;

    @BeforeEach
    void setUp() {
        deviceId = 12345L;
        
        // 创建测试用的状态报告
        report = new TerminalStatusReport();
        // 设置电源状态
        TerminalStatusReport.PowerStatus powerStatus = new TerminalStatusReport.PowerStatus();
        powerStatus.setPowerstatus(1); // 1表示开启
        powerStatus.setReportTime(System.currentTimeMillis());
        report.setPowerstatus(powerStatus);
        
        // 设置终端基本信息
        TerminalStatusReport.Terminal terminal = new TerminalStatusReport.Terminal();
        terminal.setName("测试终端");
        terminal.setLeddescription("测试LED屏");
        terminal.setReportTime(System.currentTimeMillis());
        report.setTerminal(terminal);
        
        // 创建测试用的已存在文档
        existingDocument = new TerminalStatusReportDocument();
        existingDocument.setDeviceId(deviceId);
        existingDocument.setTerminalStatusReport(new TerminalStatusReport());
        existingDocument.setUpdateTime(LocalDateTime.now().minusHours(1));
    }

    @Nested
    @DisplayName("保存终端状态报告测试")
    class SaveTerminalStatusReportTest {

        @Test
        @DisplayName("应该按原始JSON字段进行增量upsert")
        void should_upsert_with_raw_json_fields() {
            // Given
            TerminalStatusReport.BrightnessAndColorTemp bright = new TerminalStatusReport.BrightnessAndColorTemp();
            bright.setBrightness(120);
            bright.setColortemperature(6500);
            bright.setReportTime(111L);

            TerminalStatusReport partialReport = new TerminalStatusReport();
            partialReport.setBrightnessandcolortemp(bright);
            partialReport.setClientIp("127.0.0.1");

            String rawJson = "{\"brightnessandcolortemp\":{\"brightness\":120,\"colortemperature\":6500}}";

            // When
            repository.saveTerminalStatusReport(deviceId, partialReport, rawJson);

            // Then
            ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
            then(mongoTemplate).should().upsert(any(Query.class), updateCaptor.capture(), eq(TerminalStatusReportDocument.class));

            Document updateObject = updateCaptor.getValue().getUpdateObject();
            Document setDoc = (Document) updateObject.get("$set");
            Document setOnInsertDoc = (Document) updateObject.get("$setOnInsert");

            assertNotNull(setDoc);
            assertEquals(120, setDoc.get("terminalStatusReport.brightnessandcolortemp.brightness"));
            assertEquals(6500, setDoc.get("terminalStatusReport.brightnessandcolortemp.colortemperature"));
            assertEquals(111L, setDoc.get("terminalStatusReport.brightnessandcolortemp._report_time"));
            assertEquals("127.0.0.1", setDoc.get("terminalStatusReport.clientIp"));
            assertNotNull(setDoc.get("updateTime"));
            assertEquals(deviceId, setOnInsertDoc.get("deviceId"));

            assertFalse(setDoc.containsKey("terminalStatusReport.powerstatus"));
            then(mongoTemplate).should(never()).save(any());
        }

        @Test
        @DisplayName("当原始JSON缺失时应回退到兼容保存逻辑")
        void should_fallback_to_legacy_save_when_raw_json_missing() {
            // Given
            TerminalStatusReport existingReport = new TerminalStatusReport();
            // 设置现有报告的一些属性
            TerminalStatusReport.PowerStatus existingPowerStatus = new TerminalStatusReport.PowerStatus();
            existingPowerStatus.setPowerstatus(0); // 0表示关闭
            existingReport.setPowerstatus(existingPowerStatus);
            
            existingDocument.setTerminalStatusReport(existingReport);
            LocalDateTime originalUpdateTime = existingDocument.getUpdateTime();
            
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalStatusReportDocument.class)))
                    .willReturn(existingDocument);

            // When
            repository.saveTerminalStatusReport(deviceId, report, null);

            // Then
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalStatusReportDocument.class));
            then(mongoTemplate).should().save(existingDocument);
            
            // 验证时间被更新
            assertTrue(existingDocument.getUpdateTime().isAfter(originalUpdateTime));
            
            // 验证BeanUtils.copyNonNullProperties的效果
            // 新的非空属性应该被复制到现有报告中
            assertNotNull(existingDocument.getTerminalStatusReport().getPowerstatus());
            assertEquals(1, existingDocument.getTerminalStatusReport().getPowerstatus().getPowerstatus());
            assertNotNull(existingDocument.getTerminalStatusReport().getTerminal());
            assertEquals("测试终端", existingDocument.getTerminalStatusReport().getTerminal().getName());
        }

        @Test
        @DisplayName("应该正确处理null报告数据")
        void should_handle_null_report_data_correctly() {
            // Given
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalStatusReportDocument.class)))
                    .willReturn(null);

            // When
            repository.saveTerminalStatusReport(deviceId, null, null);

            // Then
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalStatusReportDocument.class));
            then(mongoTemplate).should().save(argThat(doc -> {
                TerminalStatusReportDocument document = (TerminalStatusReportDocument) doc;
                return document.getDeviceId().equals(deviceId) &&
                       document.getTerminalStatusReport() == null &&
                       document.getUpdateTime() != null;
            }));
        }
    }

    @Nested
    @DisplayName("查询报告数据测试")
    class GetReportDataTest {

        @Test
        @DisplayName("应该成功查询到报告数据")
        void should_get_report_data_successfully() {
            // Given
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalStatusReportDocument.class)))
                    .willReturn(existingDocument);

            // When
            Optional<TerminalStatusReport> result = repository.getReportData(deviceId);

            // Then
            assertTrue(result.isPresent());
            assertEquals(existingDocument.getTerminalStatusReport(), result.get());
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalStatusReportDocument.class));
        }

        @Test
        @DisplayName("应该在记录不存在时返回空Optional")
        void should_return_empty_optional_when_record_not_exists() {
            // Given
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalStatusReportDocument.class)))
                    .willReturn(null);

            // When
            Optional<TerminalStatusReport> result = repository.getReportData(deviceId);

            // Then
            assertFalse(result.isPresent());
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalStatusReportDocument.class));
        }

        @Test
        @DisplayName("应该在查询异常时返回空Optional")
        void should_return_empty_optional_when_query_throws_exception() {
            // Given
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalStatusReportDocument.class)))
                    .willThrow(new RuntimeException("Database connection error"));

            // When
            Optional<TerminalStatusReport> result = repository.getReportData(deviceId);

            // Then
            assertFalse(result.isPresent());
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalStatusReportDocument.class));
        }

        @Test
        @DisplayName("应该正确处理文档存在但报告为null的情况")
        void should_handle_document_exists_but_report_is_null() {
            // Given
            existingDocument.setTerminalStatusReport(null);
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalStatusReportDocument.class)))
                    .willReturn(existingDocument);

            // When
            Optional<TerminalStatusReport> result = repository.getReportData(deviceId);

            // Then
            assertFalse(result.isPresent());
            then(mongoTemplate).should().findOne(any(Query.class), eq(TerminalStatusReportDocument.class));
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("应该正确处理负数设备ID")
        void should_handle_negative_device_id() {
            // Given
            Long negativeDeviceId = -123L;
            given(mongoTemplate.findOne(any(Query.class), eq(TerminalStatusReportDocument.class)))
                    .willReturn(null);

            // When & Then
            assertDoesNotThrow(() -> {
                repository.saveTerminalStatusReport(negativeDeviceId, report, null);
                repository.getReportData(negativeDeviceId);
            });
        }
    }
}
