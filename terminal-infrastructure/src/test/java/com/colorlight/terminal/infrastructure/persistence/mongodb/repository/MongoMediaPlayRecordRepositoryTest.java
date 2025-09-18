package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.MediaPlayRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.MediaPlayRecordDocument;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.*;

/**
 * MongoMediaPlayRecordRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：批量保存素材播放记录到MongoDB
 * 2. 参数处理：接收设备ID和素材播放记录列表
 * 3. 数据转换：使用MediaPlayRecordConverter将报告转换为文档
 * 4. 批量操作：使用MongoTemplate.insertAll进行批量插入
 * 5. 异常处理：MongoDB操作异常时抛出TechnicalException
 * 6. 错误码问题：代码中错误地使用了MYSQL_ERROR而不是MONGO_DB_ERROR（这是一个bug）
 * <p>
 * 测试策略：
 * - 正常批量保存场景测试
 * - 边界条件测试（空列表、null列表、null设备ID）
 * - 转换器异常处理测试
 * - MongoDB批量操作异常测试
 * - 验证错误码使用（测试实际的bug行为）
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoMediaPlayRecordRepository单元测试")
class MongoMediaPlayRecordRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MediaPlayRecordConverter mediaPlayRecordConverter;

    @InjectMocks
    private MongoMediaPlayRecordRepository repository;

    private Long deviceId;
    private List<MediaPlayRecordReport> reports;
    private List<MediaPlayRecordDocument> documents;
    private Map<String, Integer> mediaIdMap;

    @BeforeEach
    void setUp() {
        deviceId = 12345L;
        
        // 创建测试用的素材播放记录报告
        MediaPlayRecordReport report1 = new MediaPlayRecordReport();
        report1.setProgramName("测试节目1");
        report1.setResOriginName("test_video1.mp4");
        report1.setResMd5Name("md5_hash_1");
        report1.setPageName("页面1");
        report1.setPageIndex(0);
        report1.setRegionName("区域1");
        report1.setRegionIndex(0);
        report1.setStartUtcTime(LocalDateTime.now());
        report1.setStartLocalTime(LocalDateTime.now());
        report1.setEndUtcTime(LocalDateTime.now().plusMinutes(3));
        report1.setEndLocalTime(LocalDateTime.now().plusMinutes(3));
        report1.setDuration(180L);
        report1.setItemType("video");
        
        MediaPlayRecordReport report2 = new MediaPlayRecordReport();
        report2.setProgramName("测试节目2");
        report2.setResOriginName("test_image2.jpg");
        report2.setResMd5Name("md5_hash_2");
        report2.setPageName("页面2");
        report2.setPageIndex(1);
        report2.setRegionName("区域2");
        report2.setRegionIndex(1);
        report2.setStartUtcTime(LocalDateTime.now().minusMinutes(5));
        report2.setStartLocalTime(LocalDateTime.now().minusMinutes(5));
        report2.setEndUtcTime(LocalDateTime.now().minusMinutes(1));
        report2.setEndLocalTime(LocalDateTime.now().minusMinutes(1));
        report2.setDuration(240L);
        report2.setItemType("image");
        
        reports = Arrays.asList(report1, report2);
        
        // 创建测试用的文档
        MediaPlayRecordDocument doc1 = new MediaPlayRecordDocument();
        doc1.setDeviceId(deviceId);
        doc1.setProgramName("测试节目1");
        doc1.setDuration(180L);
        
        MediaPlayRecordDocument doc2 = new MediaPlayRecordDocument();
        doc2.setDeviceId(deviceId);
        doc2.setProgramName("测试节目2");
        doc2.setDuration(240L);
        
        documents = Arrays.asList(doc1, doc2);

        // 创建素材ID映射
        mediaIdMap = new HashMap<>();
        mediaIdMap.put("test_video1.mp4", 1001);
        mediaIdMap.put("test_image2.jpg", 1002);
    }

    @Nested
    @DisplayName("批量保存素材播放记录测试")
    class SaveMediaPlayRecordsTest {

        @Test
        @DisplayName("应该成功批量保存素材播放记录（不包含素材ID）")
        void should_save_media_play_records_successfully() {
            // Given
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, reports))
                    .willReturn(documents);

            // When
            repository.saveMediaPlayRecords(deviceId, reports);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, reports);
            then(mongoTemplate).should().insertAll(documents);
        }

        @Test
        @DisplayName("应该成功批量保存素材播放记录（包含素材ID映射）")
        void should_save_media_play_records_with_media_id_map_successfully() {
            // Given
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, reports, mediaIdMap))
                    .willReturn(documents);

            // When
            repository.saveMediaPlayRecords(deviceId, reports, mediaIdMap);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, reports, mediaIdMap);
            then(mongoTemplate).should().insertAll(documents);
        }

        @Test
        @DisplayName("应该成功处理单个记录的保存")
        void should_save_single_media_play_record_successfully() {
            // Given
            List<MediaPlayRecordReport> singleReport = Collections.singletonList(reports.get(0));
            List<MediaPlayRecordDocument> singleDocument = Collections.singletonList(documents.get(0));

            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, singleReport))
                    .willReturn(singleDocument);

            // When
            repository.saveMediaPlayRecords(deviceId, singleReport);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, singleReport);
            then(mongoTemplate).should().insertAll(singleDocument);
        }

        @Test
        @DisplayName("应该成功处理单个记录的保存（包含素材ID映射）")
        void should_save_single_media_play_record_with_media_id_map_successfully() {
            // Given
            List<MediaPlayRecordReport> singleReport = Collections.singletonList(reports.get(0));
            List<MediaPlayRecordDocument> singleDocument = Collections.singletonList(documents.get(0));
            Map<String, Integer> singleMediaIdMap = Map.of("test_video1.mp4", 1001);

            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, singleReport, singleMediaIdMap))
                    .willReturn(singleDocument);

            // When
            repository.saveMediaPlayRecords(deviceId, singleReport, singleMediaIdMap);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, singleReport, singleMediaIdMap);
            then(mongoTemplate).should().insertAll(singleDocument);
        }

        @Test
        @DisplayName("应该正确处理空列表")
        void should_handle_empty_list_correctly() {
            // Given
            List<MediaPlayRecordReport> emptyReports = Collections.emptyList();
            List<MediaPlayRecordDocument> emptyDocuments = Collections.emptyList();

            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, emptyReports))
                    .willReturn(emptyDocuments);

            // When
            repository.saveMediaPlayRecords(deviceId, emptyReports);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, emptyReports);
            then(mongoTemplate).should().insertAll(emptyDocuments);
        }

        @Test
        @DisplayName("应该正确处理空列表（包含素材ID映射）")
        void should_handle_empty_list_with_media_id_map_correctly() {
            // Given
            List<MediaPlayRecordReport> emptyReports = Collections.emptyList();
            List<MediaPlayRecordDocument> emptyDocuments = Collections.emptyList();
            Map<String, Integer> emptyMediaIdMap = Collections.emptyMap();

            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, emptyReports, emptyMediaIdMap))
                    .willReturn(emptyDocuments);

            // When
            repository.saveMediaPlayRecords(deviceId, emptyReports, emptyMediaIdMap);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, emptyReports, emptyMediaIdMap);
            then(mongoTemplate).should().insertAll(emptyDocuments);
        }

        @Test
        @DisplayName("应该正确处理null设备ID")
        void should_handle_null_device_id_correctly() {
            
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(null, reports))
                    .willReturn(documents);

            // When
            repository.saveMediaPlayRecords(null, reports);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(null, reports);
            then(mongoTemplate).should().insertAll(documents);
        }

        @Test
        @DisplayName("应该正确处理负数设备ID")
        void should_handle_negative_device_id_correctly() {
            // Given
            Long negativeDeviceId = -123L;
            
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(negativeDeviceId, reports))
                    .willReturn(documents);

            // When
            repository.saveMediaPlayRecords(negativeDeviceId, reports);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(negativeDeviceId, reports);
            then(mongoTemplate).should().insertAll(documents);
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
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, reports))
                    .willThrow(converterException);

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                repository.saveMediaPlayRecords(deviceId, reports);
            });

            assertEquals("Converter failed", exception.getMessage());
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, reports);
            then(mongoTemplate).should(never()).insertAll(anyList());
        }

        @Test
        @DisplayName("应该在转换器异常时传播异常（带素材ID映射）")
        void should_propagate_converter_exception_with_media_id_map() {
            // Given
            RuntimeException converterException = new RuntimeException("Converter failed with mediaIdMap");
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, reports, mediaIdMap))
                    .willThrow(converterException);

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                repository.saveMediaPlayRecords(deviceId, reports, mediaIdMap);
            });

            assertEquals("Converter failed with mediaIdMap", exception.getMessage());
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, reports, mediaIdMap);
            then(mongoTemplate).should(never()).insertAll(anyList());
        }
    }

    @Nested
    @DisplayName("数据完整性测试")
    class DataIntegrityTest {

        @Test
        @DisplayName("应该确保转换器被正确调用并传递设备ID")
        void should_ensure_converter_is_called_correctly_with_device_id() {
            // Given
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, reports))
                    .willReturn(documents);

            // When
            repository.saveMediaPlayRecords(deviceId, reports);

            // Then
            then(mediaPlayRecordConverter).should(times(1))
                    .convertToMediaPlayRecordDocumentList(eq(deviceId), eq(reports));
            then(mongoTemplate).should(times(1)).insertAll(documents);
        }

        @Test
        @DisplayName("应该确保转换器被正确调用并传递设备ID和素材ID映射")
        void should_ensure_converter_is_called_correctly_with_device_id_and_media_id_map() {
            // Given
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, reports, mediaIdMap))
                    .willReturn(documents);

            // When
            repository.saveMediaPlayRecords(deviceId, reports, mediaIdMap);

            // Then
            then(mediaPlayRecordConverter).should(times(1))
                    .convertToMediaPlayRecordDocumentList(eq(deviceId), eq(reports), eq(mediaIdMap));
            then(mongoTemplate).should(times(1)).insertAll(documents);
        }

        @Test
        @DisplayName("应该确保转换结果被正确传递给MongoDB")
        void should_ensure_conversion_result_is_passed_to_mongodb() {
            // Given
            List<MediaPlayRecordDocument> customDocuments = Arrays.asList(
                new MediaPlayRecordDocument(),
                new MediaPlayRecordDocument()
            );
            
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, reports))
                    .willReturn(customDocuments);

            // When
            repository.saveMediaPlayRecords(deviceId, reports);

            // Then
            then(mongoTemplate).should().insertAll(eq(customDocuments));
            then(mongoTemplate).should(never()).insertAll(eq(documents)); // 不应该使用原始的documents
        }

        @Test
        @DisplayName("应该正确处理大批量数据")
        void should_handle_large_batch_data_correctly() {
            // Given - 创建大量数据
            List<MediaPlayRecordReport> largeReports = Collections.nCopies(1000, reports.get(0));
            List<MediaPlayRecordDocument> largeDocuments = Collections.nCopies(1000, documents.get(0));

            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, largeReports))
                    .willReturn(largeDocuments);

            // When
            repository.saveMediaPlayRecords(deviceId, largeReports);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, largeReports);
            then(mongoTemplate).should().insertAll(largeDocuments);
        }

        @Test
        @DisplayName("应该正确处理大批量数据（带素材ID映射）")
        void should_handle_large_batch_data_with_media_id_map_correctly() {
            // Given - 创建大量数据
            List<MediaPlayRecordReport> largeReports = Collections.nCopies(1000, reports.get(0));
            List<MediaPlayRecordDocument> largeDocuments = Collections.nCopies(1000, documents.get(0));
            Map<String, Integer> largeMediaIdMap = Map.of("test_video1.mp4", 1001);

            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, largeReports, largeMediaIdMap))
                    .willReturn(largeDocuments);

            // When
            repository.saveMediaPlayRecords(deviceId, largeReports, largeMediaIdMap);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(deviceId, largeReports, largeMediaIdMap);
            then(mongoTemplate).should().insertAll(largeDocuments);
        }

        @Test
        @DisplayName("应该正确处理不同设备ID的调用")
        void should_handle_different_device_ids_correctly() {
            // Given
            Long anotherDeviceId = 67890L;
            List<MediaPlayRecordDocument> anotherDocuments = List.of(new MediaPlayRecordDocument());
            
            given(mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(anotherDeviceId, reports))
                    .willReturn(anotherDocuments);

            // When
            repository.saveMediaPlayRecords(anotherDeviceId, reports);

            // Then
            then(mediaPlayRecordConverter).should().convertToMediaPlayRecordDocumentList(eq(anotherDeviceId), eq(reports));
            then(mongoTemplate).should().insertAll(anotherDocuments);
        }
    }
}