package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.enums.TerminalLogType;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.TerminalLogDocConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalLogDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

/**
 * MongoTerminalLogRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：保存终端日志到MongoDB
 * 2. 单条保存：使用mongoTemplate.save()
 * 3. 批量保存：使用mongoTemplate.insertAll()
 * 4. 数据转换：使用TerminalLogDocConverter进行转换
 * <p>
 * 测试策略：
 * - 单条保存测试
 * - 批量保存测试
 * - null值处理测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoTerminalLogRepository单元测试")
class MongoTerminalLogRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;
    
    @Mock
    private TerminalLogDocConverter terminalLogDocConverter;
    
    @InjectMocks
    private MongoTerminalLogRepository mongoTerminalLogRepository;
    
    private TerminalLog sampleLog;
    private TerminalLogDocument sampleDocument;

    @BeforeEach
    void setUp() {
        sampleLog = TerminalLog.builder()
                .deviceId(12345L)
                .operation(TerminalLogType.MEMORY_REPORT)
                .logType("INFO")
                .logArg1("测试日志参数1")
                .logArg2("测试日志参数2")
                .deviceTime("2024-01-01 10:00:00")
                .serverTime(LocalDateTime.of(2024, 1, 1, 10, 0, 0))
                .build();
        
        sampleDocument = new TerminalLogDocument();
        sampleDocument.setDeviceId(12345L);
        sampleDocument.setOperation(1); // TerminalLogType转换为Integer
        sampleDocument.setDescription("测试日志描述");
        sampleDocument.setLogArg1("测试日志参数1");
        sampleDocument.setLogArg2("测试日志参数2");
        sampleDocument.setDeviceTime("2024-01-01 10:00:00");
        sampleDocument.setServerTime(LocalDateTime.of(2024, 1, 1, 10, 0, 0));
        
        // 配置默认Mock行为（宽松模式）
        lenient().when(terminalLogDocConverter.convertToTerminalLogDocument(any(TerminalLog.class)))
                .thenReturn(sampleDocument);
    }

    @Test
    @DisplayName("保存终端日志 - 成功场景")
    void saveTerminalLog_Success() {
        // Given: 准备日志和转换结果
        given(terminalLogDocConverter.convertToTerminalLogDocument(sampleLog))
                .willReturn(sampleDocument);

        // When: 执行保存
        assertDoesNotThrow(() -> mongoTerminalLogRepository.saveTerminalLog(sampleLog));

        // Then: 验证调用链
        then(terminalLogDocConverter).should().convertToTerminalLogDocument(sampleLog);
        then(mongoTemplate).should().save(sampleDocument);
    }

    @Test
    @DisplayName("批量保存终端日志 - 成功场景")
    void batchSaveTerminalLog_Success() {
        // Given: 准备日志列表
        TerminalLog log1 = TerminalLog.builder()
                .deviceId(11111L)
                .operation(TerminalLogType.ACCOUNT_CHANGED)
                .logType("INFO")
                .logArg1("日志1")
                .build();
        TerminalLog log2 = TerminalLog.builder()
                .deviceId(22222L)
                .operation(TerminalLogType.CURRENT_BRIGHTNESS)
                .logType("ERROR")
                .logArg1("日志2")
                .build();
        
        List<TerminalLog> logs = Arrays.asList(log1, log2);
        
        TerminalLogDocument doc1 = new TerminalLogDocument();
        doc1.setDeviceId(11111L);
        doc1.setOperation(1);
        doc1.setLogArg1("日志1");
        
        TerminalLogDocument doc2 = new TerminalLogDocument();
        doc2.setDeviceId(22222L);
        doc2.setOperation(2);
        doc2.setLogArg1("日志2");
        
        List<TerminalLogDocument> documents = Arrays.asList(doc1, doc2);
        
        given(terminalLogDocConverter.convertToTerminalLogDocumentList(logs))
                .willReturn(documents);

        // When: 执行批量保存
        assertDoesNotThrow(() -> mongoTerminalLogRepository.batchSaveTerminalLog(logs));

        // Then: 验证调用链
        then(terminalLogDocConverter).should().convertToTerminalLogDocumentList(logs);
        then(mongoTemplate).should().insertAll(documents);
    }

    @Test
    @DisplayName("批量保存终端日志 - 空列表处理")
    void batchSaveTerminalLog_EmptyList() {
        // Given: 空列表
        List<TerminalLog> emptyLogs = Collections.emptyList();
        List<TerminalLogDocument> emptyDocuments = Collections.emptyList();
        
        given(terminalLogDocConverter.convertToTerminalLogDocumentList(emptyLogs))
                .willReturn(emptyDocuments);

        // When: 执行批量保存
        mongoTerminalLogRepository.batchSaveTerminalLog(emptyLogs);

        // Then: 验证转换器被调用，但insertAll传入空列表
        then(terminalLogDocConverter).should().convertToTerminalLogDocumentList(emptyLogs);
        then(mongoTemplate).should().insertAll(emptyDocuments);
    }

    @Test
    @DisplayName("单条保存终端日志 - 转换异常")
    void saveTerminalLog_ConversionException() {
        // Given: 转换器抛出异常
        RuntimeException conversionException = new RuntimeException("转换失败");
        given(terminalLogDocConverter.convertToTerminalLogDocument(sampleLog))
                .willThrow(conversionException);

        // When & Then: 验证异常被传播
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> mongoTerminalLogRepository.saveTerminalLog(sampleLog));
        
        assertEquals(conversionException, exception);
        
        // 验证没有执行保存操作
        then(mongoTemplate).should(never()).save(any());
    }

    @Test
    @DisplayName("批量保存终端日志 - MongoDB异常")
    void batchSaveTerminalLog_MongoException() {
        // Given: 正常转换，但MongoDB操作失败
        List<TerminalLog> logs = Collections.singletonList(sampleLog);
        List<TerminalLogDocument> documents = Collections.singletonList(sampleDocument);
        
        given(terminalLogDocConverter.convertToTerminalLogDocumentList(logs))
                .willReturn(documents);
        
        RuntimeException mongoException = new RuntimeException("MongoDB插入失败");
        willThrow(mongoException).given(mongoTemplate).insertAll(documents);

        // When & Then: 验证异常被传播
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> mongoTerminalLogRepository.batchSaveTerminalLog(logs));
        
        assertEquals(mongoException, exception);
        
        // 验证转换器被调用
        then(terminalLogDocConverter).should().convertToTerminalLogDocumentList(logs);
        then(mongoTemplate).should().insertAll(documents);
    }
}