package com.colorlight.terminal.infrastructure.cleanup;

import com.colorlight.terminal.application.port.outbound.cache.TerminalAuthCachePort;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.infrastructure.cleanup.cleaner.DataStoreCleaner;
import com.colorlight.terminal.infrastructure.config.properties.DeviceConfigProperties;
import com.colorlight.terminal.infrastructure.event.AsyncBufferFlushEvent;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceDeletionRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceDeletionRecordMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.TerminalAccountMapper;
import com.colorlight.terminal.rpc.dto.config.DataCleanupConfigDTO;
import com.colorlight.terminal.rpc.dto.enums.CleanupMode;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DeviceDataCleanupService 单元测试
 * 测试设备数据清理服务的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备数据清理服务测试")
class DeviceDataCleanupServiceTest {

    @Mock
    private DeviceConfigProperties deviceConfig;
    
    @Mock
    private DeviceDeletionRecordMapper deletionRecordMapper;
    
    @Mock
    private List<DataStoreCleaner> cleaners;
    
    @Mock
    private DataStoreCleaner mysqlCleaner;
    
    @Mock
    private DataStoreCleaner mongoCleaner;
    
    @Mock
    private DataStoreCleaner redisCleaner;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private TerminalAuthCachePort terminalAuthCachePort;
    
    @Mock
    private TerminalAccountMapper terminalAccountMapper;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    private DeviceDataCleanupService cleanupService;
    
    // 测试数据
    private static final Long TEST_DEVICE_ID = 12345L;
    private static final Long TEST_RECORD_ID = 100L;
    
    @BeforeEach
    void setUp() {
        // 创建清理器列表
        List<DataStoreCleaner> cleanersList = Arrays.asList(mysqlCleaner, mongoCleaner, redisCleaner);
        lenient().when(cleaners.iterator()).thenReturn(cleanersList.iterator());
        lenient().when(cleaners.size()).thenReturn(3);
        
        // 设置清理器类型
        lenient().when(mysqlCleaner.getStorageType()).thenReturn("MySQL");
        lenient().when(mongoCleaner.getStorageType()).thenReturn("MongoDB");
        lenient().when(redisCleaner.getStorageType()).thenReturn("Redis");
        
        cleanupService = new DeviceDataCleanupService(
            deviceConfig, deletionRecordMapper, cleanersList, objectMapper, eventPublisher, 
            terminalAuthCachePort, terminalAccountMapper);
    }
    
    @Nested
    @DisplayName("单个设备数据清理测试")
    class SingleDeviceCleanupTest {
        
        @Test
        @DisplayName("应该使用默认配置成功清理设备数据")
        void should_cleanup_device_with_default_config_successfully() throws JsonProcessingException {
            // Given - 准备默认配置和测试数据
            DeviceConfigProperties.DataCleanup defaultCleanupConfig = createDefaultCleanupConfig();
            when(deviceConfig.getCleanup()).thenReturn(defaultCleanupConfig);
            
            DeviceDeletionRecordDO mockRecord = createMockDeletionRecord();
            mockRecord.setId(TEST_RECORD_ID);
            when(deletionRecordMapper.insert(any(DeviceDeletionRecordDO.class))).thenReturn(1);
            
            // Mock JSON转换
            when(objectMapper.writeValueAsString(any())).thenReturn("[\"TERMINAL_LOG\"]");
            
            // Mock清理器执行结果
            when(mysqlCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(10);
            when(mongoCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(50);
            when(redisCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(5);
            
            Map<String, Integer> expectedCounts = Map.of("MySQL", 10, "MongoDB", 50, "Redis", 5);
            when(objectMapper.writeValueAsString(expectedCounts)).thenReturn("{\"MySQL\":10,\"MongoDB\":50,\"Redis\":5}");
            
            // When - 执行清理操作
            cleanupService.cleanupDeviceDataAsync(TEST_DEVICE_ID, null);
            
            // Then - 验证执行结果
            // 验证插入删除记录
            ArgumentCaptor<DeviceDeletionRecordDO> insertCaptor = ArgumentCaptor.forClass(DeviceDeletionRecordDO.class);
            verify(deletionRecordMapper).insert(insertCaptor.capture());
            DeviceDeletionRecordDO insertedRecord = insertCaptor.getValue();
            assertThat(insertedRecord.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            assertThat(insertedRecord.getStatus()).isEqualTo("PENDING");
            assertThat(insertedRecord.getCleanupMode()).isEqualTo("EXCLUDE");
            
            // 验证状态更新：RUNNING -> SUCCESS
            ArgumentCaptor<DeviceDeletionRecordDO> updateCaptor = ArgumentCaptor.forClass(DeviceDeletionRecordDO.class);
            verify(deletionRecordMapper, times(2)).updateById(updateCaptor.capture());
            
            List<DeviceDeletionRecordDO> updates = updateCaptor.getAllValues();
            
            // 第一次更新：设置为RUNNING状态
            DeviceDeletionRecordDO runningUpdate = updates.get(0);
            assertThat(runningUpdate.getStatus()).isEqualTo("RUNNING");
            assertThat(runningUpdate.getStartTime()).isNotNull();
            
            // 第二次更新：设置为SUCCESS状态
            DeviceDeletionRecordDO successUpdate = updates.get(1);
            assertThat(successUpdate.getStatus()).isEqualTo("SUCCESS");
            assertThat(successUpdate.getEndTime()).isNotNull();
            assertThat(successUpdate.getDeletedCounts()).isEqualTo("{\"MySQL\":10,\"MongoDB\":50,\"Redis\":5}");
            
            // 验证所有清理器都被调用
            verify(mysqlCleaner).cleanup(eq(TEST_DEVICE_ID), any());
            verify(mongoCleaner).cleanup(eq(TEST_DEVICE_ID), any());
            verify(redisCleaner).cleanup(eq(TEST_DEVICE_ID), any());
        }
        
        @Test
        @DisplayName("应该使用自定义配置清理指定数据类型")
        void should_cleanup_device_with_custom_config_include_mode() throws JsonProcessingException {
            // Given - 准备自定义配置（仅清理GPS记录和终端日志）
            Set<DataType> includeTypes = EnumSet.of(DataType.GPS_RECORD, DataType.TERMINAL_LOG);
            DataCleanupConfigDTO customConfig = DataCleanupConfigDTO.builder()
                    .mode(CleanupMode.INCLUDE)
                    .dataTypes(includeTypes)
                    .build();
            
            DeviceDeletionRecordDO mockRecord = createMockDeletionRecord();
            mockRecord.setId(TEST_RECORD_ID);
            when(deletionRecordMapper.insert(any(DeviceDeletionRecordDO.class))).thenReturn(1);
            
            // Mock JSON转换
            when(objectMapper.writeValueAsString(any())).thenReturn("[\"GPS_RECORD\",\"TERMINAL_LOG\"]");
            
            // Mock清理器执行结果 - 只有MongoDB清理器有数据
            when(mysqlCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(0);
            when(mongoCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(25);
            when(redisCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(0);
            
            Map<String, Integer> expectedCounts = Map.of("MongoDB", 25);
            when(objectMapper.writeValueAsString(expectedCounts)).thenReturn("{\"MongoDB\":25}");
            
            // When - 执行清理操作
            cleanupService.cleanupDeviceDataAsync(TEST_DEVICE_ID, customConfig);
            
            // Then - 验证执行结果
            // 验证插入的记录使用了自定义配置
            ArgumentCaptor<DeviceDeletionRecordDO> insertCaptor = ArgumentCaptor.forClass(DeviceDeletionRecordDO.class);
            verify(deletionRecordMapper).insert(insertCaptor.capture());
            DeviceDeletionRecordDO insertedRecord = insertCaptor.getValue();
            assertThat(insertedRecord.getCleanupMode()).isEqualTo("INCLUDE");
            assertThat(insertedRecord.getDataTypes()).isEqualTo("[\"GPS_RECORD\",\"TERMINAL_LOG\"]");
            
            // 验证传递给清理器的数据类型包含DEVICE_ACCOUNT（安全要求）
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<DataType>> dataTypesCaptor = ArgumentCaptor.forClass(Set.class);
            verify(mysqlCleaner).cleanup(eq(TEST_DEVICE_ID), dataTypesCaptor.capture());
            Set<DataType> actualDataTypes = dataTypesCaptor.getValue();
            assertThat(actualDataTypes).contains(DataType.GPS_RECORD, DataType.TERMINAL_LOG, DataType.DEVICE_ACCOUNT);
        }
        
        @Test
        @DisplayName("应该使用排除模式清理除指定类型外的所有数据")
        void should_cleanup_device_with_exclude_mode() throws JsonProcessingException {
            // Given - 准备排除配置（排除终端日志）
            Set<DataType> excludeTypes = EnumSet.of(DataType.TERMINAL_LOG);
            DataCleanupConfigDTO customConfig = DataCleanupConfigDTO.builder()
                    .mode(CleanupMode.EXCLUDE)
                    .dataTypes(excludeTypes)
                    .build();
            
            DeviceDeletionRecordDO mockRecord = createMockDeletionRecord();
            mockRecord.setId(TEST_RECORD_ID);
            when(deletionRecordMapper.insert(any(DeviceDeletionRecordDO.class))).thenReturn(1);
            
            // Mock JSON转换
            when(objectMapper.writeValueAsString(any())).thenReturn("[\"TERMINAL_LOG\"]");
            
            // Mock清理器执行结果
            when(mysqlCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(15);
            when(mongoCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(30);
            when(redisCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(8);
            
            Map<String, Integer> expectedCounts = Map.of("MySQL", 15, "MongoDB", 30, "Redis", 8);
            when(objectMapper.writeValueAsString(expectedCounts)).thenReturn("{\"MySQL\":15,\"MongoDB\":30,\"Redis\":8}");
            
            // When - 执行清理操作
            cleanupService.cleanupDeviceDataAsync(TEST_DEVICE_ID, customConfig);
            
            // Then - 验证传递给清理器的数据类型不包含TERMINAL_LOG但包含DEVICE_ACCOUNT
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<DataType>> dataTypesCaptor = ArgumentCaptor.forClass(Set.class);
            verify(mysqlCleaner).cleanup(eq(TEST_DEVICE_ID), dataTypesCaptor.capture());
            Set<DataType> actualDataTypes = dataTypesCaptor.getValue();
            assertThat(actualDataTypes)
                    .doesNotContain(DataType.TERMINAL_LOG)
                    .contains(DataType.DEVICE_ACCOUNT)
                    .hasSizeGreaterThan(5);
        }
        
        @Test
        @DisplayName("应该使用ALL模式清理所有数据类型")
        void should_cleanup_device_with_all_mode() throws JsonProcessingException {
            // Given - 准备ALL模式配置
            DataCleanupConfigDTO customConfig = DataCleanupConfigDTO.builder()
                    .mode(CleanupMode.ALL)
                    .build();
            
            DeviceDeletionRecordDO mockRecord = createMockDeletionRecord();
            mockRecord.setId(TEST_RECORD_ID);
            when(deletionRecordMapper.insert(any(DeviceDeletionRecordDO.class))).thenReturn(1);
            
            // Mock JSON转换
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");
            
            // Mock清理器执行结果
            when(mysqlCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(20);
            when(mongoCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(100);
            when(redisCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenReturn(12);
            
            Map<String, Integer> expectedCounts = Map.of("MySQL", 20, "MongoDB", 100, "Redis", 12);
            when(objectMapper.writeValueAsString(expectedCounts)).thenReturn("{\"MySQL\":20,\"MongoDB\":100,\"Redis\":12}");
            
            // When - 执行清理操作
            cleanupService.cleanupDeviceDataAsync(TEST_DEVICE_ID, customConfig);
            
            // Then - 验证传递给清理器的数据类型包含所有枚举值
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<DataType>> dataTypesCaptor = ArgumentCaptor.forClass(Set.class);
            verify(mysqlCleaner).cleanup(eq(TEST_DEVICE_ID), dataTypesCaptor.capture());
            Set<DataType> actualDataTypes = dataTypesCaptor.getValue();
            assertThat(actualDataTypes).containsAll(EnumSet.allOf(DataType.class));
        }
        
        @Test
        @DisplayName("当清理器执行失败时应该继续执行并记录成功状态")
        void should_continue_when_cleanup_throws_exception() throws JsonProcessingException {
            // Given - 准备测试数据，清理过程中出现异常
            DeviceConfigProperties.DataCleanup defaultCleanupConfig = createDefaultCleanupConfig();
            when(deviceConfig.getCleanup()).thenReturn(defaultCleanupConfig);
            
            DeviceDeletionRecordDO mockRecord = createMockDeletionRecord();
            mockRecord.setId(TEST_RECORD_ID);
            when(deletionRecordMapper.insert(any(DeviceDeletionRecordDO.class))).thenReturn(1);
            
            // Mock JSON转换
            when(objectMapper.writeValueAsString(any())).thenReturn("[\"TERMINAL_LOG\"]");
            
            // Mock清理过程中出现异常
            RuntimeException cleanupException = new RuntimeException("清理过程出错");
            when(mysqlCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenThrow(cleanupException);
            when(mongoCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenThrow(cleanupException);
            when(redisCleaner.cleanup(eq(TEST_DEVICE_ID), any())).thenThrow(cleanupException);
            
            // When - 执行清理操作应该不会抛出异常
            // 验证不会抛出异常
            assertThatCode(() -> cleanupService.cleanupDeviceDataAsync(TEST_DEVICE_ID, null))
                    .doesNotThrowAnyException();
            
            // 验证记录了SUCCESS状态，因为异常被捕获且不会中断流程
            ArgumentCaptor<DeviceDeletionRecordDO> updateCaptor = ArgumentCaptor.forClass(DeviceDeletionRecordDO.class);
            verify(deletionRecordMapper, times(2)).updateById(updateCaptor.capture());
            
            List<DeviceDeletionRecordDO> updates = updateCaptor.getAllValues();
            
            // 第一次更新：设置为RUNNING状态
            DeviceDeletionRecordDO runningUpdate = updates.get(0);
            assertThat(runningUpdate.getStatus()).isEqualTo("RUNNING");
            
            // 第二次更新：设置为SUCCESS状态（异常被捕获，不中断流程）
            DeviceDeletionRecordDO successUpdate = updates.get(1);
            assertThat(successUpdate.getStatus()).isEqualTo("SUCCESS");
            assertThat(successUpdate.getErrorMessage()).isNull();
            assertThat(successUpdate.getEndTime()).isNotNull();
        }
        
        @Test
        @DisplayName("当删除记录插入失败时应该抛出异常")
        void should_throw_exception_when_deletion_record_insert_fails() {
            // Given - 准备默认配置，但删除记录插入失败
            DeviceConfigProperties.DataCleanup defaultCleanupConfig = createDefaultCleanupConfig();
            when(deviceConfig.getCleanup()).thenReturn(defaultCleanupConfig);
            
            when(deletionRecordMapper.insert(any(DeviceDeletionRecordDO.class))).thenThrow(new RuntimeException("数据库连接失败"));
            
            // When & Then - 执行清理操作应该抛出异常
            assertThatThrownBy(() -> cleanupService.cleanupDeviceDataAsync(TEST_DEVICE_ID, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("数据库连接失败");
            
            // 验证没有调用清理器
            verify(mysqlCleaner, never()).cleanup(any(), any());
            verify(mongoCleaner, never()).cleanup(any(), any());
            verify(redisCleaner, never()).cleanup(any(), any());
        }
    }
    
    @Nested
    @DisplayName("批量设备数据清理测试")
    class BatchDeviceCleanupTest {
        
        @Test
        @DisplayName("应该成功发布批量清理事件")
        void should_publish_batch_cleanup_events_successfully() {
            // Given - 准备批量设备ID和自定义配置
            List<Long> deviceIds = Arrays.asList(1001L, 1002L, 1003L);
            DataCleanupConfigDTO customConfig = DataCleanupConfigDTO.builder()
                    .mode(CleanupMode.ALL)
                    .build();
            
            // When - 执行批量清理
            cleanupService.batchCleanupDeviceDataAsync(deviceIds, customConfig);
            
            // Then - 验证为每个设备都发布了清理事件
            ArgumentCaptor<AsyncBufferFlushEvent> eventCaptor = ArgumentCaptor.forClass(AsyncBufferFlushEvent.class);
            verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());
            
            List<AsyncBufferFlushEvent> publishedEvents = eventCaptor.getAllValues();
            assertThat(publishedEvents).hasSize(3);
            
            // 验证事件内容（这里假设AsyncBufferFlushEvent有相应的getter方法）
            // 注意：由于AsyncBufferFlushEvent的具体实现细节不明确，这里只验证调用次数
        }
        
        @Test
        @DisplayName("当事件发布失败时应该抛出BusinessException")
        void should_throw_business_exception_when_event_publish_fails() {
            // Given - 准备测试数据，事件发布器会抛出异常
            List<Long> deviceIds = Arrays.asList(1001L, 1002L);
            DataCleanupConfigDTO customConfig = DataCleanupConfigDTO.builder()
                    .mode(CleanupMode.INCLUDE)
                    .addDataType(DataType.GPS_RECORD)
                    .build();
            
            RuntimeException publishException = new RuntimeException("事件发布失败");
            doThrow(publishException).when(eventPublisher).publishEvent(any(AsyncBufferFlushEvent.class));
            
            // When & Then - 执行批量清理应该抛出BusinessException
            assertThatThrownBy(() -> cleanupService.batchCleanupDeviceDataAsync(deviceIds, customConfig))
                    .isInstanceOf(BusinessException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
            
            // 验证至少尝试发布了一个事件
            verify(eventPublisher, atLeastOnce()).publishEvent(any(AsyncBufferFlushEvent.class));
        }
        
        @Test
        @DisplayName("应该处理空设备ID列表")
        void should_handle_empty_device_ids_list() {
            // Given - 空的设备ID列表
            List<Long> emptyDeviceIds = Collections.emptyList();
            
            // When - 执行批量清理
            cleanupService.batchCleanupDeviceDataAsync(emptyDeviceIds, null);
            
            // Then - 验证没有发布任何事件
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
    
    // ===================== 测试数据构建辅助方法 =====================
    
    /**
     * 创建默认的清理配置
     */
    private DeviceConfigProperties.DataCleanup createDefaultCleanupConfig() {
        DeviceConfigProperties.DataCleanup cleanupConfig = new DeviceConfigProperties.DataCleanup();
        cleanupConfig.setEnabled(true);
        cleanupConfig.setMode(CleanupMode.EXCLUDE);
        cleanupConfig.setDataTypes(EnumSet.of(DataType.TERMINAL_LOG));
        return cleanupConfig;
    }
    
    /**
     * 创建模拟的删除记录
     */
    private DeviceDeletionRecordDO createMockDeletionRecord() {
        DeviceDeletionRecordDO deletionRecordDO = new DeviceDeletionRecordDO();
        deletionRecordDO.setDeviceId(TEST_DEVICE_ID);
        deletionRecordDO.setStatus("PENDING");
        deletionRecordDO.setCreateTime(LocalDateTime.now());
        return deletionRecordDO;
    }
}