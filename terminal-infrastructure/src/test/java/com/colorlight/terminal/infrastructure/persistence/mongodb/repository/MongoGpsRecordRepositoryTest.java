package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.infrastructure.generator.GpsIndexesGenerator;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.GpsRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.GpsRecordDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

/**
 * MongoGpsRecordRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：批量保存GPS记录到MongoDB
 * 2. 数据转换：使用GpsRecordConverter将GpsReport转换为GpsRecordDocument
 * 3. 性能优化：使用MongoDB的BulkOperations.UNORDERED模式批量插入
 * 4. 异常处理：空数据检查、转换失败处理、批量操作异常重抛
 * 5. 日志记录：操作结果记录和异常日志
 * <p>
 * 测试策略：
 * - 正常批量保存场景测试
 * - 边界条件测试（null、空列表）
 * - 转换器异常处理测试
 * - MongoDB批量操作异常测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoGpsRecordRepository单元测试")
class MongoGpsRecordRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;
    
    @Mock
    private GpsIndexesGenerator gpsIndexesGenerator;
    
    @Mock
    private GpsRecordConverter gpsRecordConverter;
    
    @Mock
    private BulkOperations bulkOperations;
    
    @InjectMocks
    private MongoGpsRecordRepository mongoGpsRecordRepository;
    
    private GpsReport sampleGpsReport;
    private GpsRecordDocument sampleDocument;
    private List<GpsReport> gpsReportList;
    private List<GpsRecordDocument> documentList;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        sampleGpsReport = new GpsReport();
        // 设置GpsReport的基本属性（根据实际字段调整）
        
        sampleDocument = new GpsRecordDocument();
        // 设置GpsRecordDocument的基本属性
        
        gpsReportList = Collections.singletonList(sampleGpsReport);
        documentList = List.of(sampleDocument);
        
        // 配置BulkOperations的默认行为（宽松模式）
        lenient().when(mongoTemplate.bulkOps(any(BulkOperations.BulkMode.class), any(Class.class)))
                .thenReturn(bulkOperations);
        lenient().when(bulkOperations.insert(anyList())).thenReturn(bulkOperations);
    }

    @Test
    @DisplayName("批量保存GPS记录 - 成功场景")
    void batchSaveGpsRecord_Success() {
        // Given: 准备正常的GPS报告列表和转换结果
        given(gpsRecordConverter.convertToGpsDocumentList(gpsReportList, gpsIndexesGenerator))
                .willReturn(documentList);
        
        // 模拟批量操作成功结果
        var bulkWriteResult = mock(com.mongodb.bulk.BulkWriteResult.class);
        given(bulkWriteResult.getInsertedCount()).willReturn(1);
        given(bulkOperations.execute()).willReturn(bulkWriteResult);

        // When: 执行批量保存
        assertDoesNotThrow(() -> mongoGpsRecordRepository.batchSaveGpsRecord(gpsReportList));

        // Then: 验证调用链
        then(gpsRecordConverter).should().convertToGpsDocumentList(gpsReportList, gpsIndexesGenerator);
        then(mongoTemplate).should().bulkOps(BulkOperations.BulkMode.UNORDERED, GpsRecordDocument.class);
        then(bulkOperations).should().insert(documentList);
        then(bulkOperations).should().execute();
    }

    @Test
    @DisplayName("批量保存GPS记录 - 空列表处理")
    void batchSaveGpsRecord_EmptyList() {
        // Given: 空的GPS报告列表
        List<GpsReport> emptyList = Collections.emptyList();

        // When: 执行批量保存
        mongoGpsRecordRepository.batchSaveGpsRecord(emptyList);

        // Then: 验证不进行任何数据库操作
        then(gpsRecordConverter).should(never()).convertToGpsDocumentList(any(), any());
        then(mongoTemplate).should(never()).bulkOps(any(BulkOperations.BulkMode.class), any(Class.class));
    }

    @Test
    @DisplayName("批量保存GPS记录 - null列表处理")
    void batchSaveGpsRecord_NullList() {
        // When: 传入null
        mongoGpsRecordRepository.batchSaveGpsRecord(null);

        // Then: 验证不进行任何数据库操作
        then(gpsRecordConverter).should(never()).convertToGpsDocumentList(any(), any());
        then(mongoTemplate).should(never()).bulkOps(any(BulkOperations.BulkMode.class), any(Class.class));
    }

    @Test
    @DisplayName("批量保存GPS记录 - 转换结果为空")
    void batchSaveGpsRecord_EmptyConversionResult() {
        // Given: 转换器返回空列表
        given(gpsRecordConverter.convertToGpsDocumentList(gpsReportList, gpsIndexesGenerator))
                .willReturn(Collections.emptyList());

        // When: 执行批量保存
        mongoGpsRecordRepository.batchSaveGpsRecord(gpsReportList);

        // Then: 验证转换调用但不执行数据库操作
        then(gpsRecordConverter).should().convertToGpsDocumentList(gpsReportList, gpsIndexesGenerator);
        then(mongoTemplate).should(never()).bulkOps(any(BulkOperations.BulkMode.class), any(Class.class));
    }

    @Test
    @DisplayName("批量保存GPS记录 - 部分保存成功")
    void batchSaveGpsRecord_PartialSuccess() {
        // Given: 2条记录但只有1条成功插入
        List<GpsReport> twoReports = Arrays.asList(sampleGpsReport, sampleGpsReport);
        List<GpsRecordDocument> twoDocuments = Arrays.asList(sampleDocument, sampleDocument);
        
        given(gpsRecordConverter.convertToGpsDocumentList(twoReports, gpsIndexesGenerator))
                .willReturn(twoDocuments);
        
        var bulkWriteResult = mock(com.mongodb.bulk.BulkWriteResult.class);
        given(bulkWriteResult.getInsertedCount()).willReturn(1); // 只成功插入1条
        given(bulkOperations.execute()).willReturn(bulkWriteResult);

        // When: 执行批量保存
        assertDoesNotThrow(() -> mongoGpsRecordRepository.batchSaveGpsRecord(twoReports));

        // Then: 验证正常完成（仅记录警告日志）
        then(bulkOperations).should().execute();
    }

    @Test
    @DisplayName("批量保存GPS记录 - 转换器异常")
    void batchSaveGpsRecord_ConverterException() {
        // Given: 转换器抛出异常
        given(gpsRecordConverter.convertToGpsDocumentList(gpsReportList, gpsIndexesGenerator))
                .willThrow(new RuntimeException("转换失败"));

        // When & Then: 验证异常被重抛并包装
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> mongoGpsRecordRepository.batchSaveGpsRecord(gpsReportList));
        
        assertEquals("GPS数据批量保存失败", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("转换失败", exception.getCause().getMessage());
        
        // 验证没有执行数据库操作
        then(mongoTemplate).should(never()).bulkOps(any(BulkOperations.BulkMode.class), any(Class.class));
    }

    @Test
    @DisplayName("批量保存GPS记录 - MongoDB批量操作异常")
    void batchSaveGpsRecord_BulkOperationException() {
        // Given: 正常转换，但批量操作失败
        given(gpsRecordConverter.convertToGpsDocumentList(gpsReportList, gpsIndexesGenerator))
                .willReturn(documentList);
        given(bulkOperations.execute()).willThrow(new RuntimeException("数据库连接失败"));

        // When & Then: 验证异常被重抛并包装
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> mongoGpsRecordRepository.batchSaveGpsRecord(gpsReportList));
        
        assertEquals("GPS数据批量保存失败", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("数据库连接失败", exception.getCause().getMessage());
    }
}