package com.colorlight.terminal.infrastructure.persistence.mongodb.converter;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.infrastructure.generator.GpsIndexesGenerator;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.GpsRecordDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * GpsRecordConverter单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：GPS报告转换为MongoDB文档
 * 2. 字段映射：基本字段直接映射，objectId忽略
 * 3. 地理索引生成：使用GpsIndexesGenerator生成GeoJsonPoint和S2CellId
 * 4. 表达式计算：point、cellId、cellLevel通过表达式动态计算
 * 5. 批量转换：支持列表转换
 * <p>
 * 测试策略：
 * - 单个GPS报告转换测试
 * - 批量GPS报告转换测试  
 * - 地理索引生成逻辑验证
 * - null值处理测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GpsRecordConverter单元测试")
class GpsRecordConverterTest {

    private GpsRecordConverter converter;
    
    @Mock
    private GpsIndexesGenerator gpsIndexesGenerator;
    
    private GpsReport sampleGpsReport;
    private GeoJsonPoint sampleGeoPoint;

    @BeforeEach
    void setUp() {
        converter = Mappers.getMapper(GpsRecordConverter.class);
        
        // 准备测试数据
        sampleGpsReport = new GpsReport();
        sampleGpsReport.setDeviceId(12345L);
        sampleGpsReport.setLatitude(39.9042);
        sampleGpsReport.setLongitude(116.4074);
        sampleGpsReport.setDeviceTime(LocalDateTime.now());
        sampleGpsReport.setServerTime(LocalDateTime.now());
        sampleGpsReport.setSensorId(1);
        sampleGpsReport.setSensorType("gps");
        sampleGpsReport.setAccuracy(5.0);
        sampleGpsReport.setAltitude(45.0);
        sampleGpsReport.setSpeed(0.0);
        sampleGpsReport.setDirect(180.0);
        sampleGpsReport.setSatellites(8);
        
        sampleGeoPoint = new GeoJsonPoint(116.4074, 39.9042);
        
        // 配置默认Mock行为（宽松模式）
        lenient().when(gpsIndexesGenerator.createGeoJsonPoint(any(GpsReport.class)))
                .thenReturn(sampleGeoPoint);
        lenient().when(gpsIndexesGenerator.generateS2CellId(any(GpsReport.class)))
                .thenReturn("1234567890abcdef");
        lenient().when(gpsIndexesGenerator.getConfiguredCellLevel())
                .thenReturn(15);
    }

    @Test
    @DisplayName("转换GPS报告为文档 - 成功场景")
    void convertToGpsDocument_Success() {
        // Given: 配置mock行为
        given(gpsIndexesGenerator.createGeoJsonPoint(sampleGpsReport))
                .willReturn(sampleGeoPoint);
        given(gpsIndexesGenerator.generateS2CellId(sampleGpsReport))
                .willReturn("1234567890abcdef");
        given(gpsIndexesGenerator.getConfiguredCellLevel())
                .willReturn(15);

        // When: 执行转换
        GpsRecordDocument result = converter.convertToGpsDocument(sampleGpsReport, gpsIndexesGenerator);

        // Then: 验证转换结果
        assertNotNull(result);
        assertEquals(12345L, result.getDeviceId());
        assertEquals(39.9042, result.getLatitude());
        assertEquals(116.4074, result.getLongitude());
        assertEquals(sampleGpsReport.getDeviceTime(), result.getDeviceTime());
        assertEquals(sampleGpsReport.getServerTime(), result.getServerTime());
        assertEquals(Integer.valueOf(1), result.getSensorId());
        assertEquals(5.0, result.getAccuracy());
        assertEquals(45.0, result.getAltitude());
        assertEquals(0.0, result.getSpeed());
        assertEquals(180.0, result.getDirect());
        assertEquals(Integer.valueOf(8), result.getSatellites());
        
        // 验证地理索引字段
        assertEquals(sampleGeoPoint, result.getPoint());
        assertEquals("1234567890abcdef", result.getCellId());
        assertEquals(Integer.valueOf(15), result.getCellLevel());
        
        // 验证objectId被忽略（应该为null）
        assertNull(result.getObjectId());
        
        // 验证Generator调用
        then(gpsIndexesGenerator).should().createGeoJsonPoint(sampleGpsReport);
        then(gpsIndexesGenerator).should().generateS2CellId(sampleGpsReport);
        then(gpsIndexesGenerator).should().getConfiguredCellLevel();
    }

    @Test
    @DisplayName("转换GPS报告为文档 - null输入处理")
    void convertToGpsDocument_NullInput() {
        // When: 传入null
        GpsRecordDocument result = converter.convertToGpsDocument(null, gpsIndexesGenerator);

        // Then: 返回null
        assertNull(result);
        
        // 验证Generator不被调用
        then(gpsIndexesGenerator).should(never()).createGeoJsonPoint(any());
        then(gpsIndexesGenerator).should(never()).generateS2CellId(any());
        then(gpsIndexesGenerator).should(never()).getConfiguredCellLevel();
    }

    @Test
    @DisplayName("批量转换GPS报告列表 - 成功场景")
    void convertToGpsDocumentList_Success() {
        // Given: 准备GPS报告列表
        GpsReport report1 = new GpsReport();
        report1.setDeviceId(11111L);
        report1.setLatitude(31.2304);
        report1.setLongitude(121.4737);
        report1.setDeviceTime(LocalDateTime.of(2024, 1, 1, 10, 0, 0));
        report1.setServerTime(LocalDateTime.of(2024, 1, 1, 10, 0, 0));
        
        GpsReport report2 = new GpsReport();
        report2.setDeviceId(22222L);
        report2.setLatitude(22.3193);
        report2.setLongitude(114.1694);
        report2.setDeviceTime(LocalDateTime.of(2024, 1, 1, 11, 0, 0));
        report2.setServerTime(LocalDateTime.of(2024, 1, 1, 11, 0, 0));
        
        List<GpsReport> reports = Arrays.asList(report1, report2);
        
        // 配置不同的mock返回值
        given(gpsIndexesGenerator.createGeoJsonPoint(report1))
                .willReturn(new GeoJsonPoint(121.4737, 31.2304));
        given(gpsIndexesGenerator.createGeoJsonPoint(report2))
                .willReturn(new GeoJsonPoint(114.1694, 22.3193));
        given(gpsIndexesGenerator.generateS2CellId(report1))
                .willReturn("shanghai_cell_id");
        given(gpsIndexesGenerator.generateS2CellId(report2))
                .willReturn("shenzhen_cell_id");

        // When: 执行批量转换
        List<GpsRecordDocument> results = converter.convertToGpsDocumentList(reports, gpsIndexesGenerator);

        // Then: 验证转换结果
        assertNotNull(results);
        assertEquals(2, results.size());
        
        // 验证第一个文档
        GpsRecordDocument doc1 = results.get(0);
        assertEquals(11111L, doc1.getDeviceId());
        assertEquals(31.2304, doc1.getLatitude());
        assertEquals(121.4737, doc1.getLongitude());
        assertEquals("shanghai_cell_id", doc1.getCellId());
        
        // 验证第二个文档
        GpsRecordDocument doc2 = results.get(1);
        assertEquals(22222L, doc2.getDeviceId());
        assertEquals(22.3193, doc2.getLatitude());
        assertEquals(114.1694, doc2.getLongitude());
        assertEquals("shenzhen_cell_id", doc2.getCellId());
        
        // 验证Generator被正确调用
        then(gpsIndexesGenerator).should().createGeoJsonPoint(report1);
        then(gpsIndexesGenerator).should().createGeoJsonPoint(report2);
        then(gpsIndexesGenerator).should().generateS2CellId(report1);
        then(gpsIndexesGenerator).should().generateS2CellId(report2);
        then(gpsIndexesGenerator).should(times(2)).getConfiguredCellLevel();
    }

    @Test
    @DisplayName("批量转换GPS报告列表 - 空列表处理")
    void convertToGpsDocumentList_EmptyList() {
        // Given: 空列表
        List<GpsReport> emptyList = Arrays.asList();

        // When: 执行批量转换
        List<GpsRecordDocument> results = converter.convertToGpsDocumentList(emptyList, gpsIndexesGenerator);

        // Then: 返回空列表
        assertNotNull(results);
        assertTrue(results.isEmpty());
        
        // 验证Generator不被调用
        then(gpsIndexesGenerator).should(never()).createGeoJsonPoint(any());
        then(gpsIndexesGenerator).should(never()).generateS2CellId(any());
        then(gpsIndexesGenerator).should(never()).getConfiguredCellLevel();
    }

    @Test
    @DisplayName("批量转换GPS报告列表 - null列表处理")
    void convertToGpsDocumentList_NullList() {
        // When: 传入null列表
        List<GpsRecordDocument> results = converter.convertToGpsDocumentList(null, gpsIndexesGenerator);

        // Then: 返回null
        assertNull(results);
    }

    @Test
    @DisplayName("转换GPS报告为文档 - 地理索引生成异常处理")
    void convertToGpsDocument_IndexGenerationException() {
        // Given: GPS索引生成器抛出异常
        given(gpsIndexesGenerator.createGeoJsonPoint(sampleGpsReport))
                .willThrow(new RuntimeException("地理索引生成失败"));

        // When & Then: 验证异常被传播
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> converter.convertToGpsDocument(sampleGpsReport, gpsIndexesGenerator));
        
        assertEquals("地理索引生成失败", exception.getMessage());
    }

    @Test
    @DisplayName("转换GPS报告为文档 - 部分字段为null")
    void convertToGpsDocument_PartialNullFields() {
        // Given: 部分字段为null的GPS报告
        GpsReport partialReport = new GpsReport();
        partialReport.setDeviceId(12345L);
        partialReport.setLatitude(39.9042);
        // longitude和其他字段为null
        
        given(gpsIndexesGenerator.createGeoJsonPoint(partialReport))
                .willReturn(sampleGeoPoint);
        given(gpsIndexesGenerator.generateS2CellId(partialReport))
                .willReturn("partial_cell_id");
        given(gpsIndexesGenerator.getConfiguredCellLevel())
                .willReturn(15);

        // When: 执行转换
        GpsRecordDocument result = converter.convertToGpsDocument(partialReport, gpsIndexesGenerator);

        // Then: 验证转换结果
        assertNotNull(result);
        assertEquals(12345L, result.getDeviceId());
        assertEquals(39.9042, result.getLatitude());
        assertNull(result.getLongitude()); // null值应该被保留
        assertNull(result.getDeviceTime()); // null值应该被保留
        assertNull(result.getServerTime()); // null值应该被保留
        
        // 地理索引字段仍然正确生成
        assertEquals(sampleGeoPoint, result.getPoint());
        assertEquals("partial_cell_id", result.getCellId());
        assertEquals(Integer.valueOf(15), result.getCellLevel());
    }

    @Test
    @DisplayName("转换GPS报告为文档 - 验证所有字段映射")
    void convertToGpsDocument_AllFieldsMapping() {
        // Given: 包含所有字段的完整GPS报告
        GpsReport completeReport = new GpsReport();
        completeReport.setDeviceId(99999L);
        completeReport.setLatitude(40.0);
        completeReport.setLongitude(120.0);
        completeReport.setDeviceTime(LocalDateTime.of(2024, 2, 1, 12, 0, 0));
        completeReport.setServerTime(LocalDateTime.of(2024, 2, 1, 12, 0, 0));
        completeReport.setSensorId(2);
        completeReport.setSensorType("gps");
        completeReport.setAccuracy(3.0);
        completeReport.setAltitude(100.0);
        completeReport.setSpeed(60.0);
        completeReport.setDirect(90.0);
        completeReport.setSatellites(12);
        
        given(gpsIndexesGenerator.createGeoJsonPoint(completeReport))
                .willReturn(new GeoJsonPoint(120.0, 40.0));
        given(gpsIndexesGenerator.generateS2CellId(completeReport))
                .willReturn("complete_cell_id");
        given(gpsIndexesGenerator.getConfiguredCellLevel())
                .willReturn(18);

        // When: 执行转换
        GpsRecordDocument result = converter.convertToGpsDocument(completeReport, gpsIndexesGenerator);

        // Then: 验证所有字段正确映射
        assertNotNull(result);
        assertEquals(99999L, result.getDeviceId());
        assertEquals(40.0, result.getLatitude());
        assertEquals(120.0, result.getLongitude());
        assertEquals(LocalDateTime.of(2024, 2, 1, 12, 0, 0), result.getDeviceTime());
        assertEquals(LocalDateTime.of(2024, 2, 1, 12, 0, 0), result.getServerTime());
        assertEquals(Integer.valueOf(2), result.getSensorId());
        assertEquals(3.0, result.getAccuracy());
        assertEquals(100.0, result.getAltitude());
        assertEquals(60.0, result.getSpeed());
        assertEquals(90.0, result.getDirect());
        assertEquals(Integer.valueOf(12), result.getSatellites());
        
        // 验证表达式生成的字段
        assertEquals(new GeoJsonPoint(120.0, 40.0), result.getPoint());
        assertEquals("complete_cell_id", result.getCellId());
        assertEquals(Integer.valueOf(18), result.getCellLevel());
        
        // 验证objectId被忽略
        assertNull(result.getObjectId());
    }
}