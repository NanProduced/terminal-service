package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.ProgramPlayRecordReport;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.ProgramPlayRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.ProgramPlayRecordDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.*;

/**
 * MongoProgramPlayRecordRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：批量保存节目播放记录到MongoDB
 * 2. 数据转换：使用ProgramPlayRecordConverter将报告转换为文档
 * 3. 批量操作：使用MongoTemplate.insertAll进行批量插入
 * 4. 异常处理：MongoDB操作异常时抛出TechnicalException
 * 5. 数据验证：依赖转换器进行数据验证和转换
 * <p>
 * 测试策略：
 * - 正常批量保存场景测试
 * - 边界条件测试（空列表、null列表）
 * - 转换器异常处理测试
 * - MongoDB批量操作异常测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoProgramPlayRecordRepository单元测试")
class MongoProgramPlayRecordRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ProgramPlayRecordConverter programPlayRecordConverter;

    @InjectMocks
    private MongoProgramPlayRecordRepository repository;

    private List<ProgramPlayRecordReport> reports;
    private List<ProgramPlayRecordDocument> documents;

    @BeforeEach
    void setUp() {
        // 创建测试用的节目播放记录报告
        ProgramPlayRecordReport report1 = new ProgramPlayRecordReport();
        report1.setProgramId(1001);
        report1.setProgramName("测试节目1");
        report1.setProgramVsn("vsn_001");
        report1.setPlayTimes(5);
        report1.setSingleDuration(300L);
        report1.setPlayDuration(1500L); // 5次播放 * 300秒
        report1.setDeviceId(12345L);
        report1.setAuthorId(100);
        
        ProgramPlayRecordReport report2 = new ProgramPlayRecordReport();
        report2.setProgramId(1002);
        report2.setProgramName("测试节目2");
        report2.setProgramVsn("vsn_002");
        report2.setPlayTimes(3);
        report2.setSingleDuration(450L);
        report2.setPlayDuration(1350L); // 3次播放 * 450秒
        report2.setDeviceId(12345L);
        report2.setAuthorId(101);
        
        reports = Arrays.asList(report1, report2);
        
        // 创建测试用的文档
        ProgramPlayRecordDocument doc1 = new ProgramPlayRecordDocument();
        doc1.setProgramId(1001);
        doc1.setProgramName("测试节目1");
        doc1.setPlayTimes(5);
        doc1.setSingleDuration(300L);
        doc1.setPlayDuration(1500L);
        doc1.setDeviceId(12345L);
        doc1.setStartLocalTime(LocalDateTime.now());
        doc1.setEndLocalTime(LocalDateTime.now().plusMinutes(25));
        doc1.setStartUtcTime(LocalDateTime.now());
        doc1.setEndUtcTime(LocalDateTime.now().plusMinutes(25));
        
        ProgramPlayRecordDocument doc2 = new ProgramPlayRecordDocument();
        doc2.setProgramId(1002);
        doc2.setProgramName("测试节目2");
        doc2.setPlayTimes(3);
        doc2.setSingleDuration(450L);
        doc2.setPlayDuration(1350L);
        doc2.setDeviceId(12345L);
        doc2.setStartLocalTime(LocalDateTime.now().minusMinutes(30));
        doc2.setEndLocalTime(LocalDateTime.now().minusMinutes(7));
        doc2.setStartUtcTime(LocalDateTime.now().minusMinutes(30));
        doc2.setEndUtcTime(LocalDateTime.now().minusMinutes(7));
        
        documents = Arrays.asList(doc1, doc2);
    }

    @Nested
    @DisplayName("批量保存节目播放记录测试")
    class SaveProgramPlayRecordsTest {

        @Test
        @DisplayName("应该成功批量保存节目播放记录")
        void should_save_program_play_records_successfully() {
            // Given
            given(programPlayRecordConverter.convertToProgramRecordDocumentList(reports))
                    .willReturn(documents);

            // When
            repository.saveProgramPlayRecords(reports);

            // Then
            then(programPlayRecordConverter).should().convertToProgramRecordDocumentList(reports);
            then(mongoTemplate).should().insertAll(documents);
        }

        @Test
        @DisplayName("应该成功处理单个记录的保存")
        void should_save_single_program_play_record_successfully() {
            // Given
            List<ProgramPlayRecordReport> singleReport = Collections.singletonList(reports.get(0));
            List<ProgramPlayRecordDocument> singleDocument = Collections.singletonList(documents.get(0));
            
            given(programPlayRecordConverter.convertToProgramRecordDocumentList(singleReport))
                    .willReturn(singleDocument);

            // When
            repository.saveProgramPlayRecords(singleReport);

            // Then
            then(programPlayRecordConverter).should().convertToProgramRecordDocumentList(singleReport);
            then(mongoTemplate).should().insertAll(singleDocument);
        }

        @Test
        @DisplayName("应该正确处理空列表")
        void should_handle_empty_list_correctly() {
            // Given
            List<ProgramPlayRecordReport> emptyReports = Collections.emptyList();
            List<ProgramPlayRecordDocument> emptyDocuments = Collections.emptyList();
            
            given(programPlayRecordConverter.convertToProgramRecordDocumentList(emptyReports))
                    .willReturn(emptyDocuments);

            // When
            repository.saveProgramPlayRecords(emptyReports);

            // Then
            then(programPlayRecordConverter).should().convertToProgramRecordDocumentList(emptyReports);
            then(mongoTemplate).should().insertAll(emptyDocuments);
        }

    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("应该在转换器异常时传播异常")
        void should_propagate_converter_exception() {
            // Given
            RuntimeException converterException = new RuntimeException("Converter failed");
            given(programPlayRecordConverter.convertToProgramRecordDocumentList(reports))
                    .willThrow(converterException);

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                repository.saveProgramPlayRecords(reports);
            });
            
            assertEquals("Converter failed", exception.getMessage());
            then(programPlayRecordConverter).should().convertToProgramRecordDocumentList(reports);
            then(mongoTemplate).should(never()).insertAll(anyList());
        }

        @Test
        @DisplayName("应该在MongoDB操作异常时抛出TechnicalException")
        void should_throw_technical_exception_when_mongodb_operation_fails() {
            // Given
            given(programPlayRecordConverter.convertToProgramRecordDocumentList(reports))
                    .willReturn(documents);
            given(mongoTemplate.insertAll(documents))
                    .willThrow(new RuntimeException("Database connection error"));

            // When & Then
            TechnicalException exception = assertThrows(TechnicalException.class, () -> {
                repository.saveProgramPlayRecords(reports);
            });
            
            assertEquals(TechErrorCode.MONGO_DB_ERROR.getCode(), exception.getErrorCode());
            assertInstanceOf(RuntimeException.class, exception.getCause());
            assertEquals("Database connection error", exception.getCause().getMessage());
            
            then(programPlayRecordConverter).should().convertToProgramRecordDocumentList(reports);
            then(mongoTemplate).should().insertAll(documents);
        }
    }

    @Nested
    @DisplayName("数据完整性测试")
    class DataIntegrityTest {

        @Test
        @DisplayName("应该确保转换器被正确调用")
        void should_ensure_converter_is_called_correctly() {
            // Given
            given(programPlayRecordConverter.convertToProgramRecordDocumentList(reports))
                    .willReturn(documents);

            // When
            repository.saveProgramPlayRecords(reports);

            // Then
            then(programPlayRecordConverter).should(times(1))
                    .convertToProgramRecordDocumentList(reports);
            then(mongoTemplate).should(times(1)).insertAll(documents);
        }

        @Test
        @DisplayName("应该确保转换结果被正确传递给MongoDB")
        void should_ensure_conversion_result_is_passed_to_mongodb() {
            // Given
            List<ProgramPlayRecordDocument> customDocuments = Arrays.asList(
                new ProgramPlayRecordDocument(),
                new ProgramPlayRecordDocument()
            );
            
            given(programPlayRecordConverter.convertToProgramRecordDocumentList(reports))
                    .willReturn(customDocuments);

            // When
            repository.saveProgramPlayRecords(reports);

            // Then
            then(mongoTemplate).should().insertAll(eq(customDocuments));
            then(mongoTemplate).should(never()).insertAll(eq(documents)); // 不应该使用原始的documents
        }

        @Test
        @DisplayName("应该正确处理大批量数据")
        void should_handle_large_batch_data_correctly() {
            // Given - 创建大量数据
            List<ProgramPlayRecordReport> largeReports = Collections.nCopies(1000, reports.get(0));
            List<ProgramPlayRecordDocument> largeDocuments = Collections.nCopies(1000, documents.get(0));
            
            given(programPlayRecordConverter.convertToProgramRecordDocumentList(largeReports))
                    .willReturn(largeDocuments);

            // When
            repository.saveProgramPlayRecords(largeReports);

            // Then
            then(programPlayRecordConverter).should().convertToProgramRecordDocumentList(largeReports);
            then(mongoTemplate).should().insertAll(largeDocuments);
        }
    }
}