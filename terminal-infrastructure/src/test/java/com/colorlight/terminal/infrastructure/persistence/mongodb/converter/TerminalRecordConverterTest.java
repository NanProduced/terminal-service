package com.colorlight.terminal.infrastructure.persistence.mongodb.converter;

import com.colorlight.terminal.application.dto.record.TerminalOnlineTimeRecord;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalOnlineTimeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TerminalRecordConverter单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：终端记录对象转换为MongoDB文档
 * 2. 字段映射：基本字段直接映射，objectId忽略
 * 3. 时长计算：使用自定义calculateDuration方法计算在线时长（秒）
 * 4. 时间戳生成：createAt字段使用表达式自动设置为当前时间
 * 5. 空值处理：时间为null时duration返回0L
 * <p>
 * 测试策略：
 * - 正常转换场景测试
 * - 时长计算逻辑验证
 * - null值处理测试
 * - 边界时间计算测试
 * 
 * @author Generated Test
 */
@DisplayName("TerminalRecordConverter单元测试")
class TerminalRecordConverterTest {

    private TerminalRecordConverter converter;

    @BeforeEach
    void setUp() {
        converter = Mappers.getMapper(TerminalRecordConverter.class);
    }

    @Test
    @DisplayName("转换在线时长记录 - 正常场景")
    void convertToTerminalOnlineTimeDocument_Success() {
        // Given: 准备在线时长记录
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 12, 30, 15);
        
        TerminalOnlineTimeRecord onlineTimeRecord = TerminalOnlineTimeRecord.builder()
                .deviceId(12345L)
                .startTime(startTime)
                .endTime(endTime)
                .build();

        // When: 执行转换
        TerminalOnlineTimeDocument document = converter.convertToTerminalOnlineTimeDocument(onlineTimeRecord);

        // Then: 验证转换结果
        assertNotNull(document);
        assertEquals(12345L, document.getDeviceId());
        assertEquals(startTime, document.getStartTime());
        assertEquals(endTime, document.getEndTime());
        
        // 验证时长计算（2小时30分15秒 = 9015秒）
        assertEquals(9015L, document.getDuration());
        
        // 验证createAt被设置为当前时间（允许1秒误差）
        assertNotNull(document.getCreateAt());
        assertTrue(Duration.between(document.getCreateAt(), LocalDateTime.now()).abs().getSeconds() <= 1);
        
        // 验证objectId被忽略
        assertNull(document.getObjectId());
    }

    @Test
    @DisplayName("转换在线时长记录 - null输入处理")
    void convertToTerminalOnlineTimeDocument_NullInput() {
        // When: 传入null
        TerminalOnlineTimeDocument result = converter.convertToTerminalOnlineTimeDocument(null);

        // Then: 返回null
        assertNull(result);
    }

    @Test
    @DisplayName("时长计算 - 正常时间范围")
    void calculateDuration_NormalTimeRange() {
        // Given: 正常的开始和结束时间
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 14, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 15, 30, 45);

        // When: 计算时长
        Long duration = converter.calculateDuration(startTime, endTime);

        // Then: 验证计算结果（1小时30分45秒 = 5445秒）
        assertEquals(5445L, duration);
    }

    @Test
    @DisplayName("时长计算 - 开始时间为null")
    void calculateDuration_StartTimeNull() {
        // Given: 开始时间为null
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 15, 30, 45);

        // When: 计算时长
        Long duration = converter.calculateDuration(null, endTime);

        // Then: 返回0
        assertEquals(0L, duration);
    }

    @Test
    @DisplayName("时长计算 - 结束时间为null")
    void calculateDuration_EndTimeNull() {
        // Given: 结束时间为null
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 14, 0, 0);

        // When: 计算时长
        Long duration = converter.calculateDuration(startTime, null);

        // Then: 返回0
        assertEquals(0L, duration);
    }

    @Test
    @DisplayName("时长计算 - 两个时间都为null")
    void calculateDuration_BothTimesNull() {
        // When: 两个时间都为null
        Long duration = converter.calculateDuration(null, null);

        // Then: 返回0
        assertEquals(0L, duration);
    }

    @Test
    @DisplayName("时长计算 - 零时长")
    void calculateDuration_ZeroDuration() {
        // Given: 相同的开始和结束时间
        LocalDateTime sameTime = LocalDateTime.of(2024, 1, 1, 14, 0, 0);

        // When: 计算时长
        Long duration = converter.calculateDuration(sameTime, sameTime);

        // Then: 返回0
        assertEquals(0L, duration);
    }

    @Test
    @DisplayName("时长计算 - 跨天时间范围")
    void calculateDuration_CrossDayTimeRange() {
        // Given: 跨天的时间范围
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 23, 30, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 2, 1, 15, 30);

        // When: 计算时长
        Long duration = converter.calculateDuration(startTime, endTime);

        // Then: 验证计算结果（1小时45分30秒 = 6330秒）
        assertEquals(6330L, duration);
    }

    @Test
    @DisplayName("时长计算 - 负时长")
    void calculateDuration_NegativeDuration() {
        // Given: 结束时间早于开始时间
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 15, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 14, 0, 0);

        // When: 计算时长
        Long duration = converter.calculateDuration(startTime, endTime);

        // Then: 返回负数（-3600秒）
        assertEquals(-3600L, duration);
    }

    @Test
    @DisplayName("时长计算 - 毫秒级精度")
    void calculateDuration_MillisecondPrecision() {
        // Given: 包含毫秒的时间
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 14, 0, 0, 500_000_000);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 14, 0, 2, 300_000_000);

        // When: 计算时长
        Long duration = converter.calculateDuration(startTime, endTime);

        // Then: 验证计算结果（1.8秒，向下取整为1秒）
        assertEquals(1L, duration);
    }

    @Test
    @DisplayName("转换在线时长记录 - 部分字段为null")
    void convertToTerminalOnlineTimeDocument_PartialNullFields() {
        // Given: 部分字段为null的记录
        TerminalOnlineTimeRecord onlineTimeRecord = TerminalOnlineTimeRecord.builder()
                .deviceId(12345L)
                .startTime(null)
                .endTime(LocalDateTime.of(2024, 1, 1, 15, 0, 0))
                .build();

        // When: 执行转换
        TerminalOnlineTimeDocument document = converter.convertToTerminalOnlineTimeDocument(onlineTimeRecord);

        // Then: 验证转换结果
        assertNotNull(document);
        assertEquals(12345L, document.getDeviceId());
        assertNull(document.getStartTime());
        assertEquals(LocalDateTime.of(2024, 1, 1, 15, 0, 0), document.getEndTime());
        
        // 由于startTime为null，duration应该为0
        assertEquals(0L, document.getDuration());
        
        // createAt仍然应该被设置
        assertNotNull(document.getCreateAt());
    }

    @Test
    @DisplayName("时长计算 - 长时间范围")
    void calculateDuration_LongTimeRange() {
        // Given: 跨月的长时间范围
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 2, 1, 0, 0, 0);

        // When: 计算时长
        Long duration = converter.calculateDuration(startTime, endTime);

        // Then: 验证计算结果（31天 = 31 * 24 * 3600 = 2,678,400秒）
        assertEquals(2_678_400L, duration);
    }
}