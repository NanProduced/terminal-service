package com.colorlight.terminal.infrastructure.cleanup.cleaner;

import com.colorlight.terminal.infrastructure.persistence.mongodb.document.*;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MongoDataStoreCleaner 单元测试
 * 测试MongoDB数据存储清理器的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MongoDB数据存储清理器测试")
class MongoDataStoreCleanerTest {

    @Mock
    private MongoTemplate mongoTemplate;
    
    private MongoDataStoreCleaner cleaner;
    
    // 测试数据
    private static final Long TEST_DEVICE_ID = 12345L;
    
    @BeforeEach
    void setUp() {
        cleaner = new MongoDataStoreCleaner(mongoTemplate);
    }
    
    @Nested
    @DisplayName("基础功能测试")
    class BasicFunctionTest {
        
        @Test
        @DisplayName("应该返回正确的存储类型")
        void should_return_correct_storage_type() {
            // When & Then
            assertThat(cleaner.getStorageType()).isEqualTo("MongoDB");
        }
        
        @Test
        @DisplayName("应该支持MongoDB数据类型")
        void should_support_mongodb_data_types() {
            // Given - MongoDB数据类型
            DataType[] mongodbTypes = {
                DataType.GPS_RECORD,
                DataType.STATUS_REPORT,
                DataType.TERMINAL_LOG,
                DataType.MEDIA_PLAY_RECORD,
                DataType.PROGRAM_PLAY_RECORD,
                DataType.ONLINE_TIME,
                DataType.ABNORMAL_RECONNECT
            };
            
            // When & Then - 验证支持MongoDB数据类型
            for (DataType dataType : mongodbTypes) {
                assertThat(cleaner.supports(dataType))
                    .as("应该支持MongoDB数据类型: " + dataType)
                    .isTrue();
            }
        }
        
        @Test
        @DisplayName("应该不支持非MongoDB数据类型")
        void should_not_support_non_mongodb_data_types() {
            // Given - 非MongoDB数据类型
            DataType[] nonMongodbTypes = {
                DataType.SCREENSHOT_RECORD,
                DataType.SWITCH_RECORD,
                DataType.DEVICE_ACCOUNT,
                DataType.REDIS_CACHE
            };
            
            // When & Then - 验证不支持非MongoDB数据类型
            for (DataType dataType : nonMongodbTypes) {
                assertThat(cleaner.supports(dataType))
                    .as("不应该支持非MongoDB数据类型: " + dataType)
                    .isFalse();
            }
        }
    }
    
    @Nested
    @DisplayName("单个集合清理测试")
    class SingleCollectionCleanupTest {
        
        @Test
        @DisplayName("应该成功清理GPS记录")
        void should_cleanup_gps_records_successfully() {
            // Given - Mock删除结果
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(10L);
            when(mongoTemplate.remove(any(Query.class), eq(GpsRecordDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.GPS_RECORD));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(10);
            verify(mongoTemplate).remove(any(Query.class), eq(GpsRecordDocument.class));
        }
        
        @Test
        @DisplayName("应该成功清理状态上报记录")
        void should_cleanup_status_report_records_successfully() {
            // Given - Mock删除结果
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(5L);
            when(mongoTemplate.remove(any(Query.class), eq(TerminalStatusReportDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.STATUS_REPORT));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(5);
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalStatusReportDocument.class));
        }
        
        @Test
        @DisplayName("应该成功清理终端日志")
        void should_cleanup_terminal_log_successfully() {
            // Given - Mock删除结果
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(20L);
            when(mongoTemplate.remove(any(Query.class), eq(TerminalLogDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.TERMINAL_LOG));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(20);
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalLogDocument.class));
        }
        
        @Test
        @DisplayName("应该成功清理素材播放记录")
        void should_cleanup_media_play_records_successfully() {
            // Given - Mock删除结果
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(8L);
            when(mongoTemplate.remove(any(Query.class), eq(MediaPlayRecordDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.MEDIA_PLAY_RECORD));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(8);
            verify(mongoTemplate).remove(any(Query.class), eq(MediaPlayRecordDocument.class));
        }
        
        @Test
        @DisplayName("应该成功清理节目播放记录")
        void should_cleanup_program_play_records_successfully() {
            // Given - Mock删除结果
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(3L);
            when(mongoTemplate.remove(any(Query.class), eq(ProgramPlayRecordDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.PROGRAM_PLAY_RECORD));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(3);
            verify(mongoTemplate).remove(any(Query.class), eq(ProgramPlayRecordDocument.class));
        }
        
        @Test
        @DisplayName("应该成功清理在线时长记录")
        void should_cleanup_online_time_records_successfully() {
            // Given - Mock删除结果
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(12L);
            when(mongoTemplate.remove(any(Query.class), eq(TerminalOnlineTimeDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.ONLINE_TIME));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(12);
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalOnlineTimeDocument.class));
        }
        
        @Test
        @DisplayName("应该成功清理异常重连记录")
        void should_cleanup_abnormal_reconnect_records_successfully() {
            // Given - Mock删除结果
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(2L);
            when(mongoTemplate.remove(any(Query.class), eq(TerminalAbnormalReconnectDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.ABNORMAL_RECONNECT));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(2);
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalAbnormalReconnectDocument.class));
        }
        
        @Test
        @DisplayName("当集合无数据时应该返回0")
        void should_return_zero_when_no_records_exist() {
            // Given - Mock删除结果为0
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(0L);
            when(mongoTemplate.remove(any(Query.class), eq(GpsRecordDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.GPS_RECORD));
            
            // Then - 验证清理结果
            assertThat(result).isZero();
            verify(mongoTemplate).remove(any(Query.class), eq(GpsRecordDocument.class));
        }
    }
    
    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTest {
        
        @Test
        @DisplayName("单个集合清理失败时应该继续清理其他集合")
        void should_continue_cleanup_when_one_collection_fails() {
            // Given - GPS记录清理失败，但终端日志清理成功
            when(mongoTemplate.remove(any(Query.class), eq(GpsRecordDocument.class)))
                .thenThrow(new RuntimeException("MongoDB连接失败"));
            
            DeleteResult successResult = mock(DeleteResult.class);
            when(successResult.getDeletedCount()).thenReturn(15L);
            when(mongoTemplate.remove(any(Query.class), eq(TerminalLogDocument.class)))
                .thenReturn(successResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(
                DataType.GPS_RECORD,
                DataType.TERMINAL_LOG
            ));
            
            // Then - 验证只统计成功清理的记录
            assertThat(result).isEqualTo(15);
            verify(mongoTemplate).remove(any(Query.class), eq(GpsRecordDocument.class));
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalLogDocument.class));
        }
        
        @Test
        @DisplayName("所有集合清理失败时应该返回0")
        void should_return_zero_when_all_collections_fail() {
            // Given - 所有集合清理都失败
            when(mongoTemplate.remove(any(Query.class), any(Class.class)))
                .thenThrow(new RuntimeException("MongoDB服务不可用"));
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(
                DataType.GPS_RECORD,
                DataType.TERMINAL_LOG,
                DataType.STATUS_REPORT
            ));
            
            // Then - 验证清理结果
            assertThat(result).isZero();
            verify(mongoTemplate, times(3)).remove(any(Query.class), any(Class.class));
        }
    }
    
    @Nested
    @DisplayName("批量清理测试")
    class BatchCleanupTest {
        
        @Test
        @DisplayName("应该成功清理多个集合")
        void should_cleanup_multiple_collections_successfully() {
            // Given - Mock多个集合的删除结果
            DeleteResult gpsResult = mock(DeleteResult.class);
            when(gpsResult.getDeletedCount()).thenReturn(25L);
            when(mongoTemplate.remove(any(Query.class), eq(GpsRecordDocument.class)))
                .thenReturn(gpsResult);
            
            DeleteResult logResult = mock(DeleteResult.class);
            when(logResult.getDeletedCount()).thenReturn(30L);
            when(mongoTemplate.remove(any(Query.class), eq(TerminalLogDocument.class)))
                .thenReturn(logResult);
            
            DeleteResult statusResult = mock(DeleteResult.class);
            when(statusResult.getDeletedCount()).thenReturn(10L);
            when(mongoTemplate.remove(any(Query.class), eq(TerminalStatusReportDocument.class)))
                .thenReturn(statusResult);
            
            DeleteResult mediaResult = mock(DeleteResult.class);
            when(mediaResult.getDeletedCount()).thenReturn(5L);
            when(mongoTemplate.remove(any(Query.class), eq(MediaPlayRecordDocument.class)))
                .thenReturn(mediaResult);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(
                DataType.GPS_RECORD,
                DataType.TERMINAL_LOG,
                DataType.STATUS_REPORT,
                DataType.MEDIA_PLAY_RECORD
            ));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(70); // 25 + 30 + 10 + 5
            
            // 验证所有集合都被清理
            verify(mongoTemplate).remove(any(Query.class), eq(GpsRecordDocument.class));
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalLogDocument.class));
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalStatusReportDocument.class));
            verify(mongoTemplate).remove(any(Query.class), eq(MediaPlayRecordDocument.class));
        }
        
        @Test
        @DisplayName("应该跳过不支持的数据类型")
        void should_skip_unsupported_data_types() {
            // Given - Mock MongoDB集合删除结果
            DeleteResult result = mock(DeleteResult.class);
            when(result.getDeletedCount()).thenReturn(10L);
            when(mongoTemplate.remove(any(Query.class), eq(GpsRecordDocument.class)))
                .thenReturn(result);
            
            // When - 执行清理操作，包含不支持的数据类型
            int cleanupResult = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(
                DataType.GPS_RECORD,       // MongoDB支持
                DataType.SCREENSHOT_RECORD, // MySQL类型，不支持
                DataType.DEVICE_ACCOUNT,   // MySQL类型，不支持
                DataType.REDIS_CACHE       // Redis类型，不支持
            ));
            
            // Then - 验证只清理了支持的数据类型
            assertThat(cleanupResult).isEqualTo(10);
            verify(mongoTemplate, times(1)).remove(any(Query.class), any(Class.class));
            verify(mongoTemplate).remove(any(Query.class), eq(GpsRecordDocument.class));
        }
        
        @Test
        @DisplayName("当没有支持的数据类型时应该返回0")
        void should_return_zero_when_no_supported_data_types() {
            // When - 执行清理操作，只包含不支持的数据类型
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(
                DataType.SCREENSHOT_RECORD,
                DataType.DEVICE_ACCOUNT,
                DataType.REDIS_CACHE
            ));
            
            // Then - 验证清理结果
            assertThat(result).isZero();
            verify(mongoTemplate, never()).remove(any(Query.class), any(Class.class));
        }
        
        @Test
        @DisplayName("应该正确处理部分成功的批量清理")
        void should_handle_partial_success_in_batch_cleanup() {
            // Given - 部分集合清理成功，部分失败
            DeleteResult successResult1 = mock(DeleteResult.class);
            when(successResult1.getDeletedCount()).thenReturn(15L);
            when(mongoTemplate.remove(any(Query.class), eq(GpsRecordDocument.class)))
                .thenReturn(successResult1);
            
            // 终端日志清理失败
            when(mongoTemplate.remove(any(Query.class), eq(TerminalLogDocument.class)))
                .thenThrow(new RuntimeException("集合清理失败"));
            
            DeleteResult successResult2 = mock(DeleteResult.class);
            when(successResult2.getDeletedCount()).thenReturn(8L);
            when(mongoTemplate.remove(any(Query.class), eq(TerminalStatusReportDocument.class)))
                .thenReturn(successResult2);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(
                DataType.GPS_RECORD,
                DataType.TERMINAL_LOG,
                DataType.STATUS_REPORT
            ));
            
            // Then - 验证只统计成功清理的记录
            assertThat(result).isEqualTo(23); // 15 + 8
            verify(mongoTemplate).remove(any(Query.class), eq(GpsRecordDocument.class));
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalLogDocument.class));
            verify(mongoTemplate).remove(any(Query.class), eq(TerminalStatusReportDocument.class));
        }
    }
    
    @Nested
    @DisplayName("查询条件验证测试")
    class QueryConditionTest {
        
        @Test
        @DisplayName("应该使用正确的查询条件")
        void should_use_correct_query_condition() {
            // Given - Mock删除结果
            DeleteResult deleteResult = mock(DeleteResult.class);
            when(deleteResult.getDeletedCount()).thenReturn(5L);
            when(mongoTemplate.remove(any(Query.class), eq(GpsRecordDocument.class)))
                .thenReturn(deleteResult);
            
            // When - 执行清理操作
            cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.GPS_RECORD));
            
            // Then - 验证查询条件
            verify(mongoTemplate).remove(argThat(query -> {
                // 验证查询条件包含正确的deviceId
                String queryString = query.toString();
                return queryString.contains("deviceId") && queryString.contains(TEST_DEVICE_ID.toString());
            }), eq(GpsRecordDocument.class));
        }
    }
}