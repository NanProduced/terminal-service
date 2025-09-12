package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.dto.record.TerminalOnlineTimeRecord;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.TerminalRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalOnlineTimeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * MongoTerminalOnlineTimeRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：保存终端在线时长记录到MongoDB
 * 2. 数据转换：使用TerminalRecordConverter将记录转换为文档
 * 3. 单条保存：使用mongoTemplate.save()保存单个文档
 * 4. 异常处理：将任何异常包装为TechnicalException并重抛
 * <p>
 * 测试策略：
 * - 正常保存场景验证
 * - 转换器异常处理测试
 * - MongoDB保存异常处理测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoTerminalOnlineTimeRepository单元测试")
class MongoTerminalOnlineTimeRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;
    
    @Mock
    private TerminalRecordConverter terminalRecordConverter;
    
    @InjectMocks
    private MongoTerminalOnlineTimeRepository mongoTerminalOnlineTimeRepository;
    
    private TerminalOnlineTimeRecord sampleRecord;
    private TerminalOnlineTimeDocument sampleDocument;

    @BeforeEach
    void setUp() {
        sampleRecord = TerminalOnlineTimeRecord.builder()
                .deviceId(12345L)
                .build();
        
        sampleDocument = new TerminalOnlineTimeDocument();
        
        // 配置默认Mock行为（宽松模式）
        lenient().when(terminalRecordConverter.convertToTerminalOnlineTimeDocument(any(TerminalOnlineTimeRecord.class)))
                .thenReturn(sampleDocument);
    }

    @Test
    @DisplayName("保存在线时长记录 - 成功场景")
    void saveTerminalOnlineTime_Success() {
        // Given: 准备记录和转换结果
        given(terminalRecordConverter.convertToTerminalOnlineTimeDocument(sampleRecord))
                .willReturn(sampleDocument);

        // When: 执行保存
        assertDoesNotThrow(() -> mongoTerminalOnlineTimeRepository.saveTerminalOnlineTime(sampleRecord));

        // Then: 验证调用链
        then(terminalRecordConverter).should().convertToTerminalOnlineTimeDocument(sampleRecord);
        then(mongoTemplate).should().save(sampleDocument);
    }

    @Test
    @DisplayName("保存在线时长记录 - 转换器异常")
    void saveTerminalOnlineTime_ConverterException() {
        // Given: 转换器抛出异常
        RuntimeException conversionException = new RuntimeException("转换失败");
        given(terminalRecordConverter.convertToTerminalOnlineTimeDocument(sampleRecord))
                .willThrow(conversionException);

        // When & Then: 验证异常被包装为TechnicalException
        TechnicalException exception = assertThrows(TechnicalException.class, 
                () -> mongoTerminalOnlineTimeRepository.saveTerminalOnlineTime(sampleRecord));
        
        assertEquals(conversionException, exception.getCause());
        
        // 验证没有执行保存操作
        then(mongoTemplate).should(never()).save(any());
    }

    @Test
    @DisplayName("保存在线时长记录 - MongoDB保存异常")
    void saveTerminalOnlineTime_MongoException() {
        // Given: 正常转换，但MongoDB保存失败
        given(terminalRecordConverter.convertToTerminalOnlineTimeDocument(sampleRecord))
                .willReturn(sampleDocument);
        
        RuntimeException mongoException = new RuntimeException("MongoDB连接失败");
        willThrow(mongoException).given(mongoTemplate).save(sampleDocument);

        // When & Then: 验证异常被包装为TechnicalException
        TechnicalException exception = assertThrows(TechnicalException.class, 
                () -> mongoTerminalOnlineTimeRepository.saveTerminalOnlineTime(sampleRecord));
        
        assertEquals(mongoException, exception.getCause());
        
        // 验证转换器被调用
        then(terminalRecordConverter).should().convertToTerminalOnlineTimeDocument(sampleRecord);
        then(mongoTemplate).should().save(sampleDocument);
    }
}