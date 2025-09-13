package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.dto.record.TerminalReconnectRecord;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.TerminalRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalAbnormalReconnectDocument;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * MongoTerminalReconnectRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：保存终端异常重连记录到MongoDB
 * 2. 数据转换：使用TerminalRecordConverter将记录转换为文档
 * 3. 保存操作：使用MongoTemplate.save进行单条记录保存
 * 4. 异常处理：MongoDB操作异常时抛出TechnicalException
 * 5. 简单设计：只有一个保存方法，没有查询功能
 * <p>
 * 测试策略：
 * - 正常保存场景测试
 * - 边界条件测试（null记录）
 * - 转换器异常处理测试
 * - MongoDB保存操作异常测试
 * - 验证转换器和模板的正确调用
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoTerminalReconnectRepository单元测试")
class MongoTerminalReconnectRepositoryTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private TerminalRecordConverter terminalRecordConverter;

    @InjectMocks
    private MongoTerminalReconnectRepository repository;

    private TerminalReconnectRecord reconnectRecord;
    private TerminalAbnormalReconnectDocument document;

    @BeforeEach
    void setUp() {
        // 创建测试用的终端重连记录
        reconnectRecord = new TerminalReconnectRecord();
        reconnectRecord.setDeviceId(12345L);
        reconnectRecord.setStartOnlineTime(LocalDateTime.now().minusHours(2));
        reconnectRecord.setLastReportTime(LocalDateTime.now().minusMinutes(30));
        reconnectRecord.setReconnectTime(LocalDateTime.now());
        reconnectRecord.setReconnectIp("192.168.1.100");
        reconnectRecord.setReconnectSource("websocket");
        
        // 创建测试用的文档
        document = TerminalAbnormalReconnectDocument.builder()
                .deviceId(12345L)
                .startOnlineTime(LocalDateTime.now().minusHours(2))
                .lastReportTime(LocalDateTime.now().minusMinutes(30))
                .reconnectTime(LocalDateTime.now())
                .reconnectIp("192.168.1.100")
                .reconnectSource("websocket")
                .build();
    }

    @Nested
    @DisplayName("保存重连记录测试")
    class SaveReconnectRecordTest {

        @Test
        @DisplayName("应该成功保存终端重连记录")
        void should_save_reconnect_record_successfully() {
            // Given
            given(terminalRecordConverter.convertToTerminalReconnectDocument(reconnectRecord))
                    .willReturn(document);

            // When
            repository.saveReconnectRecord(reconnectRecord);

            // Then
            then(terminalRecordConverter).should().convertToTerminalReconnectDocument(reconnectRecord);
            then(mongoTemplate).should().save(document);
        }

        @Test
        @DisplayName("应该正确处理包含完整信息的重连记录")
        void should_handle_complete_reconnect_record_correctly() {
            // Given - 创建包含所有信息的记录
            TerminalReconnectRecord completeRecord = new TerminalReconnectRecord();
            completeRecord.setDeviceId(67890L);
            completeRecord.setStartOnlineTime(LocalDateTime.now().minusHours(3));
            completeRecord.setLastReportTime(LocalDateTime.now().minusHours(2));
            completeRecord.setReconnectTime(LocalDateTime.now());
            completeRecord.setReconnectIp("192.168.1.200");
            completeRecord.setReconnectSource("tcp");
            
            TerminalAbnormalReconnectDocument completeDocument = TerminalAbnormalReconnectDocument.builder()
                    .deviceId(67890L)
                    .startOnlineTime(completeRecord.getStartOnlineTime())
                    .lastReportTime(completeRecord.getLastReportTime())
                    .reconnectTime(completeRecord.getReconnectTime())
                    .reconnectIp("192.168.1.200")
                    .reconnectSource("tcp")
                    .build();
            
            given(terminalRecordConverter.convertToTerminalReconnectDocument(completeRecord))
                    .willReturn(completeDocument);

            // When
            repository.saveReconnectRecord(completeRecord);

            // Then
            then(terminalRecordConverter).should().convertToTerminalReconnectDocument(completeRecord);
            then(mongoTemplate).should().save(completeDocument);
        }

        @Test
        @DisplayName("应该正确处理包含部分信息的重连记录")
        void should_handle_partial_reconnect_record_correctly() {
            // Given - 创建只包含基本信息的记录
            TerminalReconnectRecord partialRecord = new TerminalReconnectRecord();
            partialRecord.setDeviceId(11111L);
            partialRecord.setReconnectTime(LocalDateTime.now());
            // 其他字段为null
            
            TerminalAbnormalReconnectDocument partialDocument = TerminalAbnormalReconnectDocument.builder()
                    .deviceId(11111L)
                    .reconnectTime(partialRecord.getReconnectTime())
                    .build();
            
            given(terminalRecordConverter.convertToTerminalReconnectDocument(partialRecord))
                    .willReturn(partialDocument);

            // When
            repository.saveReconnectRecord(partialRecord);

            // Then
            then(terminalRecordConverter).should().convertToTerminalReconnectDocument(partialRecord);
            then(mongoTemplate).should().save(partialDocument);
        }

    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("应该在MongoDB操作异常时抛出TechnicalException")
        void should_throw_technical_exception_when_mongodb_operation_fails() {
            // Given
            given(terminalRecordConverter.convertToTerminalReconnectDocument(reconnectRecord))
                    .willReturn(document);
            given(mongoTemplate.save(document))
                    .willThrow(new RuntimeException("Database connection error"));

            // When & Then
            TechnicalException exception = assertThrows(TechnicalException.class, () -> {
                repository.saveReconnectRecord(reconnectRecord);
            });
            
            assertEquals(TechErrorCode.MONGO_DB_ERROR.getCode(), exception.getErrorCode());
            assertInstanceOf(RuntimeException.class, exception.getCause());
            assertEquals("Database connection error", exception.getCause().getMessage());
            
            then(terminalRecordConverter).should().convertToTerminalReconnectDocument(reconnectRecord);
            then(mongoTemplate).should().save(document);
        }

    }

    @Nested
    @DisplayName("数据完整性测试")
    class DataIntegrityTest {

        @Test
        @DisplayName("应该确保转换器被正确调用")
        void should_ensure_converter_is_called_correctly() {
            // Given
            given(terminalRecordConverter.convertToTerminalReconnectDocument(reconnectRecord))
                    .willReturn(document);

            // When
            repository.saveReconnectRecord(reconnectRecord);

            // Then
            then(terminalRecordConverter).should(times(1))
                    .convertToTerminalReconnectDocument(reconnectRecord);
            then(mongoTemplate).should(times(1)).save(document);
        }

        @Test
        @DisplayName("应该确保转换结果被正确传递给MongoDB")
        void should_ensure_conversion_result_is_passed_to_mongodb() {
            // Given
            TerminalAbnormalReconnectDocument customDocument = TerminalAbnormalReconnectDocument.builder()
                    .deviceId(99999L)
                    .reconnectIp("192.168.1.300")
                    .reconnectSource("custom")
                    .build();
            
            given(terminalRecordConverter.convertToTerminalReconnectDocument(reconnectRecord))
                    .willReturn(customDocument);

            // When
            repository.saveReconnectRecord(reconnectRecord);

            // Then
            then(mongoTemplate).should().save(eq(customDocument));
            then(mongoTemplate).should(never()).save(eq(document)); // 不应该使用原始的document
        }

        @Test
        @DisplayName("应该正确处理多次连续保存")
        void should_handle_multiple_consecutive_saves() {
            // Given
            TerminalReconnectRecord record1 = new TerminalReconnectRecord();
            record1.setDeviceId(1L);
            TerminalReconnectRecord record2 = new TerminalReconnectRecord();
            record2.setDeviceId(2L);
            
            TerminalAbnormalReconnectDocument doc1 = TerminalAbnormalReconnectDocument.builder()
                    .deviceId(1L)
                    .build();
            TerminalAbnormalReconnectDocument doc2 = TerminalAbnormalReconnectDocument.builder()
                    .deviceId(2L)
                    .build();
            
            given(terminalRecordConverter.convertToTerminalReconnectDocument(record1))
                    .willReturn(doc1);
            given(terminalRecordConverter.convertToTerminalReconnectDocument(record2))
                    .willReturn(doc2);

            // When
            repository.saveReconnectRecord(record1);
            repository.saveReconnectRecord(record2);

            // Then
            then(terminalRecordConverter).should().convertToTerminalReconnectDocument(record1);
            then(terminalRecordConverter).should().convertToTerminalReconnectDocument(record2);
            then(mongoTemplate).should().save(doc1);
            then(mongoTemplate).should().save(doc2);
        }
    }

    @Nested
    @DisplayName("业务场景测试")
    class BusinessScenarioTest {

        @Test
        @DisplayName("应该正确处理网络中断重连场景")
        void should_handle_network_interruption_reconnect_scenario() {
            // Given - 网络中断重连场景
            TerminalReconnectRecord networkIssueRecord = new TerminalReconnectRecord();
            networkIssueRecord.setDeviceId(12345L);
            networkIssueRecord.setReconnectTime(LocalDateTime.now());
            networkIssueRecord.setStartOnlineTime(LocalDateTime.now().minusMinutes(30));
            networkIssueRecord.setLastReportTime(LocalDateTime.now().minusMinutes(10));
            networkIssueRecord.setReconnectIp("192.168.1.100");
            networkIssueRecord.setReconnectSource("network_recovery");
            
            TerminalAbnormalReconnectDocument networkDocument = TerminalAbnormalReconnectDocument.builder()
                    .deviceId(12345L)
                    .reconnectTime(LocalDateTime.now())
                    .startOnlineTime(LocalDateTime.now().minusMinutes(30))
                    .lastReportTime(LocalDateTime.now().minusMinutes(10))
                    .reconnectIp("192.168.1.100")
                    .reconnectSource("network_recovery")
                    .build();
            
            given(terminalRecordConverter.convertToTerminalReconnectDocument(networkIssueRecord))
                    .willReturn(networkDocument);

            // When
            repository.saveReconnectRecord(networkIssueRecord);

            // Then
            then(terminalRecordConverter).should().convertToTerminalReconnectDocument(networkIssueRecord);
            then(mongoTemplate).should().save(networkDocument);
        }

        @Test
        @DisplayName("应该正确处理服务器重启重连场景")
        void should_handle_server_restart_reconnect_scenario() {
            // Given - 服务器重启重连场景
            TerminalReconnectRecord serverRestartRecord = new TerminalReconnectRecord();
            serverRestartRecord.setDeviceId(67890L);
            serverRestartRecord.setReconnectTime(LocalDateTime.now());
            serverRestartRecord.setStartOnlineTime(LocalDateTime.now().minusHours(2));
            serverRestartRecord.setLastReportTime(LocalDateTime.now().minusHours(1));
            serverRestartRecord.setReconnectIp("192.168.1.200");
            serverRestartRecord.setReconnectSource("server_restart");
            
            TerminalAbnormalReconnectDocument serverDocument = TerminalAbnormalReconnectDocument.builder()
                    .deviceId(67890L)
                    .reconnectTime(LocalDateTime.now())
                    .startOnlineTime(LocalDateTime.now().minusHours(2))
                    .lastReportTime(LocalDateTime.now().minusHours(1))
                    .reconnectIp("192.168.1.200")
                    .reconnectSource("server_restart")
                    .build();
            
            given(terminalRecordConverter.convertToTerminalReconnectDocument(serverRestartRecord))
                    .willReturn(serverDocument);

            // When
            repository.saveReconnectRecord(serverRestartRecord);

            // Then
            then(terminalRecordConverter).should().convertToTerminalReconnectDocument(serverRestartRecord);
            then(mongoTemplate).should().save(serverDocument);
        }
    }
}