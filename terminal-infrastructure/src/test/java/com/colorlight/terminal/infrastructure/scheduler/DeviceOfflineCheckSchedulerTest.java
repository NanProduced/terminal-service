package com.colorlight.terminal.infrastructure.scheduler;

import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceOfflineCheckScheduler 单元测试
 * 测试设备离线检测定时任务的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备离线检测定时任务测试")
class DeviceOfflineCheckSchedulerTest {

    @Mock
    private DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;
    
    @Mock
    private DeviceConfigPort deviceConfigPort;
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    
    @Mock
    private SetOperations<String, Object> setOperations;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    @Mock
    private Cursor<String> cursor;
    
    private DeviceOfflineCheckScheduler scheduler;
    
    // 测试数据
    private static final long TEST_OFFLINE_THRESHOLD = 60000L; // 1分钟
    
    @BeforeEach
    void setUp() {
        scheduler = new DeviceOfflineCheckScheduler(deviceOnlineStatusUseCase, deviceConfigPort, redisTemplate);
        
        // Mock Redis操作
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    
    @Nested
    @DisplayName("离线设备检查测试")
    class OfflineDeviceCheckTest {
        
        @Test
        @DisplayName("应该成功处理离线设备并记录性能指标")
        void should_process_offline_devices_successfully_and_log_performance() {
            // Given - 模拟处理5个离线设备
            when(deviceOnlineStatusUseCase.processOfflineDevices()).thenReturn(5);
            
            // When - 执行离线设备检查
            assertThatNoException().isThrownBy(() -> scheduler.checkOfflineDevices());
            
            // Then - 验证调用了离线设备处理
            verify(deviceOnlineStatusUseCase).processOfflineDevices();
        }
        
        @Test
        @DisplayName("当无离线设备时应该正常完成")
        void should_complete_normally_when_no_offline_devices() {
            // Given - 无离线设备
            when(deviceOnlineStatusUseCase.processOfflineDevices()).thenReturn(0);
            
            // When - 执行离线设备检查
            assertThatNoException().isThrownBy(() -> scheduler.checkOfflineDevices());
            
            // Then - 验证调用了离线设备处理
            verify(deviceOnlineStatusUseCase).processOfflineDevices();
        }
        
        @Test
        @DisplayName("当离线设备处理抛出异常时应该捕获并记录日志")
        void should_catch_and_log_exception_when_offline_processing_fails() {
            // Given - 离线设备处理抛出异常
            RuntimeException exception = new RuntimeException("数据库连接失败");
            when(deviceOnlineStatusUseCase.processOfflineDevices()).thenThrow(exception);
            
            // When - 执行离线设备检查，应该不抛出异常
            assertThatNoException().isThrownBy(() -> scheduler.checkOfflineDevices());
            
            // Then - 验证仍然尝试处理离线设备
            verify(deviceOnlineStatusUseCase).processOfflineDevices();
        }
        
        @Test
        @DisplayName("应该能够处理大量离线设备")
        void should_handle_large_number_of_offline_devices() {
            // Given - 大量离线设备
            int largeOfflineCount = 1000;
            when(deviceOnlineStatusUseCase.processOfflineDevices()).thenReturn(largeOfflineCount);
            
            // When - 执行离线设备检查
            assertThatNoException().isThrownBy(() -> scheduler.checkOfflineDevices());
            
            // Then - 验证处理了大量离线设备
            verify(deviceOnlineStatusUseCase).processOfflineDevices();
        }
    }
    
    @Nested
    @DisplayName("设备统计和校准测试")
    class DeviceStatisticsAndCalibrationTest {
        
        @Test
        @DisplayName("应该成功获取在线设备统计并执行校准")
        void should_get_online_statistics_and_perform_calibration_successfully() {
            // Given - 模拟在线设备统计
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(150);
            
            // Mock校准相关的Redis操作
            setupCalibrationMocks();
            
            // When - 执行统计和校准
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证调用了统计方法
            verify(deviceOnlineStatusUseCase).getOnlineDeviceCount();
        }
        
        @Test
        @DisplayName("当获取统计信息失败时应该捕获异常")
        void should_catch_exception_when_statistics_fails() {
            // Given - 统计信息获取失败
            RuntimeException exception = new RuntimeException("Redis连接失败");
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenThrow(exception);
            
            // When - 执行统计和校准，应该不抛出异常
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证仍然尝试获取统计信息
            verify(deviceOnlineStatusUseCase).getOnlineDeviceCount();
        }
        
        @Test
        @DisplayName("应该处理零在线设备的情况")
        void should_handle_zero_online_devices() {
            // Given - 无在线设备
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(0);
            
            // Mock校准相关的Redis操作
            setupCalibrationMocks();
            
            // When - 执行统计和校准
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证调用了统计方法
            verify(deviceOnlineStatusUseCase).getOnlineDeviceCount();
        }
    }
    
    @Nested
    @DisplayName("Redis扫描和校准测试")
    class RedisScanAndCalibrationTest {
        
        @Test
        @DisplayName("应该正确扫描和过滤在线设备")
        void should_scan_and_filter_online_devices_correctly() {
            // Given - 设置Redis扫描和校准的Mock
            setupFullCalibrationMocks();
            
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(3);
            
            // When - 执行统计和校准
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证Redis扫描操作
            verify(redisTemplate).scan(any(ScanOptions.class));
        }
        
        @Test
        @DisplayName("当Redis扫描失败时应该优雅处理")
        void should_handle_redis_scan_failure_gracefully() {
            // Given - Redis扫描失败
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(10);
            when(redisTemplate.scan(any(ScanOptions.class))).thenThrow(new RuntimeException("Redis扫描失败"));
            when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(TEST_OFFLINE_THRESHOLD);
            
            // When - 执行统计和校准，应该不抛出异常
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证仍然尝试获取统计信息
            verify(deviceOnlineStatusUseCase).getOnlineDeviceCount();
        }
        
        @Test
        @DisplayName("应该正确处理设备ID提取")
        void should_extract_device_id_correctly() {
            // Given - 设置包含有效和无效设备ID的扫描结果
            List<String> keys = Arrays.asList(
                "device:status:12345",     // 有效设备ID
                "device:status:67890",     // 有效设备ID
                "device:status:index",     // 索引键，应跳过
                "device:status:abc123",    // 无效格式，应跳过
                "other:key:12345"          // 其他键，应跳过
            );
            
            setupScanWithKeys(keys);
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(2);
            when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(TEST_OFFLINE_THRESHOLD);
            
            // Mock在线设备的lastReportTime
            long currentTime = System.currentTimeMillis();
            when(hashOperations.get("device:status:12345", "lastReportTime"))
                .thenReturn(String.valueOf(currentTime - 30000)); // 30秒前，在线
            when(hashOperations.get("device:status:67890", "lastReportTime"))
                .thenReturn(String.valueOf(currentTime - 90000)); // 90秒前，离线
            
            // When - 执行统计和校准
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证正确处理了设备ID提取
            verify(hashOperations).get("device:status:12345", "lastReportTime");
            verify(hashOperations).get("device:status:67890", "lastReportTime");
            verify(hashOperations, never()).get("device:status:index", "lastReportTime");
        }
        
        @Test
        @DisplayName("应该正确执行索引校准")
        void should_perform_index_calibration_correctly() {
            // Given - 设置校准Mock
            setupFullCalibrationMocks();
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(2);
            
            // When - 执行统计和校准
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证索引校准操作
            verify(redisTemplate).delete(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY);
            }
        
        @Test
        @DisplayName("应该正确执行计数器校准")
        void should_perform_counter_calibration_correctly() {
            // Given - 设置校准Mock
            setupFullCalibrationMocks();
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(5);
            
            // When - 执行统计和校准
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证计数器校准操作
            verify(valueOperations).set(eq(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY), anyInt());
        }
        
        @Test
        @DisplayName("当校准过程中出现异常时应该优雅处理")
        void should_handle_calibration_exceptions_gracefully() {
            // Given - 统计正常，但校准失败
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(10);
            when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
            when(cursor.hasNext()).thenReturn(false);
            doThrow(new RuntimeException("Redis连接关闭失败")).when(cursor).close();
            when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(TEST_OFFLINE_THRESHOLD);
            
            // Mock索引校准失败
            doThrow(new RuntimeException("索引校准失败")).when(redisTemplate).delete(anyString());
            
            // When - 执行统计和校准，应该不抛出异常
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证仍然尝试获取统计信息
            verify(deviceOnlineStatusUseCase).getOnlineDeviceCount();
        }
    }
    
    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTest {
        
        @Test
        @DisplayName("应该处理空的扫描结果")
        void should_handle_empty_scan_results() {
            // Given - 空的扫描结果
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(0);
            when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
            when(cursor.hasNext()).thenReturn(false);
            when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(TEST_OFFLINE_THRESHOLD);
            
            // When - 执行统计和校准
            assertThatNoException().isThrownBy(() -> scheduler.calibrationAndStatistic());
            
            // Then - 验证处理了空结果
            verify(redisTemplate).scan(any(ScanOptions.class));
            verify(cursor).hasNext();
        }
    }
    
    // ===================== 测试辅助方法 =====================
    
    /**
     * 设置基本校准相关的Mock
     */
    private void setupCalibrationMocks() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(TEST_OFFLINE_THRESHOLD);
    }
    
    /**
     * 设置完整校准相关的Mock
     */
    private void setupFullCalibrationMocks() {
        List<String> keys = Arrays.asList("device:status:12345", "device:status:67890");
        setupScanWithKeys(keys);
        
        when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(TEST_OFFLINE_THRESHOLD);
        
        // Mock在线设备
        long currentTime = System.currentTimeMillis();
        when(hashOperations.get("device:status:12345", "lastReportTime"))
            .thenReturn(String.valueOf(currentTime - 30000)); // 在线
        when(hashOperations.get("device:status:67890", "lastReportTime"))
            .thenReturn(String.valueOf(currentTime - 30000)); // 在线
    }
    
    /**
     * 设置带指定键的扫描Mock
     */
    private void setupScanWithKeys(List<String> keys) {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        
        // 模拟cursor遍历
        if (keys.isEmpty()) {
            when(cursor.hasNext()).thenReturn(false);
        } else {
            Boolean[] hasNextResults = new Boolean[keys.size() + 1];
            for (int i = 0; i < keys.size(); i++) {
                hasNextResults[i] = true;
            }
            hasNextResults[keys.size()] = false; // 最后一次返回false
            
            when(cursor.hasNext()).thenReturn(true, hasNextResults);
            when(cursor.next())
                .thenReturn(keys.get(0), keys.subList(1, keys.size()).toArray(new String[0]));
        }
    }
}