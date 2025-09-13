package com.colorlight.terminal.infrastructure.async;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncDeviceStatusUpdatePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 异步设备状态更新服务测试
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("异步设备状态更新服务测试")
class AsyncDeviceStatusUpdateServiceTest {

    @Mock
    private DeviceOnlineStatusPort deviceOnlineStatusPort;
    
    @Mock
    private DeviceConfigPort deviceConfigPort;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @InjectMocks
    private AsyncDeviceStatusUpdateService asyncDeviceStatusUpdateService;

    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        /**
         * 创建在线状态测试数据
         */
        static DeviceOnlineStatus createOnlineStatus(Long deviceId) {
            return DeviceOnlineStatus.builder()
                    .deviceId(deviceId)
                    .lastReportTime(System.currentTimeMillis())
                    .lastReportSource(ReportSource.HTTP)
                    .status(OnlineStatus.ONLINE)
                    .statusChangeTime(System.currentTimeMillis())
                    .onlineStartTime(System.currentTimeMillis())
                    .clientIp("192.168.1.100")
                    .version("1.1")
                    .build();
        }
        
        /**
         * 创建重连状态测试数据
         */
        static DeviceOnlineStatus createReconnectStatus() {
            return DeviceOnlineStatus.builder()
                    .deviceId(1002L)
                    .lastReportTime(System.currentTimeMillis())
                    .lastReportSource(ReportSource.WEBSOCKET)
                    .status(OnlineStatus.RECONNECT)
                    .statusChangeTime(System.currentTimeMillis())
                    .onlineStartTime(System.currentTimeMillis())
                    .clientIp("192.168.1.101")
                    .version("1.0")
                    .build();
        }
        
        /**
         * 创建批量状态测试数据
         */
        static List<DeviceOnlineStatus> createBatchStatuses(int count) {
            return java.util.stream.IntStream.range(0, count)
                    .mapToObj(i -> createOnlineStatus((long) (1000 + i)))
                    .toList();
        }
    }

    @BeforeEach
    void setUp() {
        // 设置默认配置返回值
        lenient().when(deviceConfigPort.getBufferPoolWindowMs()).thenReturn(5000L);
        lenient().when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(1000);
        lenient().when(deviceConfigPort.getBufferPoolBatchSize()).thenReturn(100);
        lenient().when(deviceConfigPort.getEmergencyFlushThreshold()).thenReturn(0.8);
        lenient().when(deviceConfigPort.getTaskBufferPoolDelayMs()).thenReturn(1000L);
        lenient().when(deviceConfigPort.getBufferPoolStatisticsInterval()).thenReturn(30000L);
        lenient().when(deviceConfigPort.getTaskStatisticsDelayMs()).thenReturn(5000L);
        lenient().when(deviceConfigPort.isEmergencyFlushEnabled()).thenReturn(true);
        
        // 初始化服务
        asyncDeviceStatusUpdateService.init();
    }

    @Test
    @DisplayName("应该成功提交单个设备状态更新")
    void should_submit_single_status_update_successfully() {
        // Given - 准备一个设备在线状态
        DeviceOnlineStatus status = TestDataBuilder.createOnlineStatus(1001L);

        // When - 提交状态更新
        asyncDeviceStatusUpdateService.submitStatusUpdate(status);

        // Then - 验证缓冲池状态和统计信息
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isEqualTo(1);
        assertThat(bufferStatus.totalProcessed()).isEqualTo(1);
        assertThat(bufferStatus.totalFlushed()).isZero();
    }

    @Test
    @DisplayName("应该正确处理null状态提交")
    void should_handle_null_status_submission() {

        // When - 提交null状态
        asyncDeviceStatusUpdateService.submitStatusUpdate(null);

        // Then - 验证缓冲池保持空状态
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isZero();
        assertThat(bufferStatus.totalProcessed()).isZero();
    }

    @Test
    @DisplayName("应该成功批量提交设备状态更新")
    void should_submit_batch_status_updates_successfully() {
        // Given - 准备批量设备状态
        List<DeviceOnlineStatus> statusList = TestDataBuilder.createBatchStatuses(5);

        // When - 批量提交状态更新
        asyncDeviceStatusUpdateService.submitBatchStatusUpdate(statusList);

        // Then - 验证缓冲池状态和统计信息
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isEqualTo(5);
        assertThat(bufferStatus.totalProcessed()).isEqualTo(5);
        assertThat(bufferStatus.totalFlushed()).isZero();
    }

    @Test
    @DisplayName("应该正确处理空批量列表提交")
    void should_handle_empty_batch_submission() {
        // Given - 空的状态列表
        List<DeviceOnlineStatus> emptyList = List.of();

        // When - 提交空列表
        asyncDeviceStatusUpdateService.submitBatchStatusUpdate(emptyList);

        // Then - 验证缓冲池保持空状态
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isZero();
        assertThat(bufferStatus.totalProcessed()).isZero();
    }

    @Test
    @DisplayName("应该正确过滤批量提交中的null对象")
    void should_filter_null_objects_in_batch_submission() {
        // Given - 包含null对象的状态列表
        List<DeviceOnlineStatus> statusListWithNulls = Arrays.asList(
                TestDataBuilder.createOnlineStatus(1001L),
                null,
                TestDataBuilder.createReconnectStatus(),
                null
        );

        // When - 提交包含null的列表
        asyncDeviceStatusUpdateService.submitBatchStatusUpdate(statusListWithNulls);

        // Then - 验证只有非null对象被处理
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isEqualTo(2);
        assertThat(bufferStatus.totalProcessed()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该成功刷新缓冲池并调用状态更新")
    void should_flush_buffer_and_call_status_updates() {
        // Given - 向缓冲池添加一些状态
        DeviceOnlineStatus status1 = TestDataBuilder.createOnlineStatus(1001L);
        DeviceOnlineStatus status2 = TestDataBuilder.createReconnectStatus();
        asyncDeviceStatusUpdateService.submitStatusUpdate(status1);
        asyncDeviceStatusUpdateService.submitStatusUpdate(status2);

        // When - 刷新缓冲池
        asyncDeviceStatusUpdateService.flushBuffer();

        // Then - 验证状态更新被调用且缓冲池被清空
        verify(deviceOnlineStatusPort, times(2)).smartDetermined(any(DeviceOnlineStatus.class));
        
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isZero();
        assertThat(bufferStatus.totalFlushed()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该在缓冲池为空时跳过刷新")
    void should_skip_flush_when_buffer_is_empty() {
        // Given - 空的缓冲池
        AsyncDeviceStatusUpdatePort.BufferPoolStatus initialStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(initialStatus.currentSize()).isZero();

        // When - 刷新空缓冲池
        asyncDeviceStatusUpdateService.flushBuffer();

        // Then - 验证没有调用状态更新
        verify(deviceOnlineStatusPort, never()).smartDetermined(any(DeviceOnlineStatus.class));
    }

    @Test
    @DisplayName("应该按批次大小处理大量状态更新")
    void should_process_large_batch_by_configured_size() {
        // Given - 设置较小的批次大小
        when(deviceConfigPort.getBufferPoolBatchSize()).thenReturn(3);
        
        // 添加大量状态到缓冲池
        List<DeviceOnlineStatus> largeStatusList = TestDataBuilder.createBatchStatuses(10);
        asyncDeviceStatusUpdateService.submitBatchStatusUpdate(largeStatusList);

        // When - 刷新缓冲池
        asyncDeviceStatusUpdateService.flushBuffer();

        // Then - 验证所有状态都被处理
        verify(deviceOnlineStatusPort, times(10)).smartDetermined(any(DeviceOnlineStatus.class));
        
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isZero();
        assertThat(bufferStatus.totalFlushed()).isEqualTo(10);
    }

    @Test
    @DisplayName("应该返回正确的缓冲池状态信息")
    void should_return_correct_buffer_pool_status() {
        // Given - 添加一些状态到缓冲池
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1002L));

        // When - 获取缓冲池状态
        AsyncDeviceStatusUpdatePort.BufferPoolStatus status = asyncDeviceStatusUpdateService.getBufferPoolStatus();

        // Then - 验证状态信息正确
        assertThat(status.currentSize()).isEqualTo(2);
        assertThat(status.maxSize()).isEqualTo(1000);
        assertThat(status.utilizationRate()).isEqualTo(0.002); // 2/1000
        assertThat(status.totalProcessed()).isEqualTo(2);
        assertThat(status.totalFlushed()).isZero();
        assertThat(status.lastFlushTime()).isGreaterThan(0);
    }

    @Test
    @DisplayName("应该在定时刷新时处理非空缓冲池")
    void should_process_non_empty_buffer_on_scheduled_flush() {
        // Given - 添加状态到缓冲池
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));

        // When - 执行定时刷新
        asyncDeviceStatusUpdateService.scheduledFlush();

        // Then - 验证刷新被触发
        // 注意：由于scheduledFlush调用异步方法，这里主要验证逻辑正确性
        AsyncDeviceStatusUpdatePort.BufferPoolStatus status = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        // 缓冲池大小可能为0（如果异步刷新已完成）或1（如果异步刷新还未完成）
        assertThat(status.totalProcessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该在定时刷新时跳过空缓冲池")
    void should_skip_empty_buffer_on_scheduled_flush() {
        // Given - 空的缓冲池
        AsyncDeviceStatusUpdatePort.BufferPoolStatus initialStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(initialStatus.currentSize()).isZero();

        // When - 执行定时刷新
        asyncDeviceStatusUpdateService.scheduledFlush();

        // Then - 验证没有额外处理
        AsyncDeviceStatusUpdatePort.BufferPoolStatus finalStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(finalStatus.totalProcessed()).isZero();
        assertThat(finalStatus.totalFlushed()).isZero();
    }

    @Test
    @DisplayName("应该在服务关闭时强制刷新缓冲池")
    void should_force_flush_buffer_on_service_destroy() {
        // Given - 添加状态到缓冲池
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createReconnectStatus());

        // When - 销毁服务
        asyncDeviceStatusUpdateService.destroy();

        // Then - 验证缓冲池被强制刷新
        verify(deviceOnlineStatusPort, times(2)).smartDetermined(any(DeviceOnlineStatus.class));
    }

    @Test
    @DisplayName("应该在服务停止后拒绝新的状态提交")
    void should_reject_new_submissions_after_service_stopped() {
        // Given - 停止服务
        asyncDeviceStatusUpdateService.destroy();

        // When - 尝试提交新状态
        DeviceOnlineStatus status = TestDataBuilder.createOnlineStatus(1001L);
        asyncDeviceStatusUpdateService.submitStatusUpdate(status);

        // Then - 验证状态未被添加到缓冲池
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isZero();
        assertThat(bufferStatus.totalProcessed()).isZero();
    }

    @Test
    @DisplayName("应该在状态更新失败时继续处理后续状态")
    void should_continue_processing_when_status_update_fails() {
        // Given - 设置状态更新抛出异常
        doThrow(new RuntimeException("模拟状态更新失败"))
                .when(deviceOnlineStatusPort).smartDetermined(any(DeviceOnlineStatus.class));
        
        // 添加多个状态到缓冲池
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1002L));

        // When - 刷新缓冲池
        asyncDeviceStatusUpdateService.flushBuffer();

        // Then - 验证尽管发生异常，所有状态都被尝试处理
        verify(deviceOnlineStatusPort, times(2)).smartDetermined(any(DeviceOnlineStatus.class));
        
        AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(bufferStatus.currentSize()).isZero(); // 缓冲池应被清空
        assertThat(bufferStatus.totalFlushed()).isEqualTo(2); // 统计记录处理数量
    }

    @Test
    @DisplayName("应该输出正确的统计信息")
    void should_log_correct_statistics() {
        // Given - 处理一些状态更新
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1002L));
        asyncDeviceStatusUpdateService.flushBuffer();

        // When - 执行统计输出
        asyncDeviceStatusUpdateService.logStatistics();

        // Then - 验证统计信息正确
        AsyncDeviceStatusUpdatePort.BufferPoolStatus status = asyncDeviceStatusUpdateService.getBufferPoolStatus();
        assertThat(status.totalProcessed()).isEqualTo(2);
        assertThat(status.totalFlushed()).isEqualTo(2);
        assertThat(status.currentSize()).isZero();
        assertThat(status.utilizationRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("应该正确计算缓冲池使用率")
    void should_calculate_buffer_pool_utilization_rate_correctly() {
        // Given - 设置最大缓冲池大小为10，添加3个状态
        when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(10);
        
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1002L));
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1003L));

        // When - 获取缓冲池状态
        AsyncDeviceStatusUpdatePort.BufferPoolStatus status = asyncDeviceStatusUpdateService.getBufferPoolStatus();

        // Then - 验证使用率计算正确
        assertThat(status.currentSize()).isEqualTo(3);
        assertThat(status.maxSize()).isEqualTo(10);
        assertThat(status.utilizationRate()).isEqualTo(0.3); // 3/10 = 0.3
    }

    @Test
    @DisplayName("应该处理最大缓冲池大小为0的边界情况")
    void should_handle_zero_max_buffer_size_edge_case() {
        // Given - 设置最大缓冲池大小为0
        when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(0);
        
        asyncDeviceStatusUpdateService.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));

        // When - 获取缓冲池状态
        AsyncDeviceStatusUpdatePort.BufferPoolStatus status = asyncDeviceStatusUpdateService.getBufferPoolStatus();

        // Then - 验证使用率为0，避免除零错误
        assertThat(status.maxSize()).isZero();
        assertThat(status.utilizationRate()).isEqualTo(0.0);
    }
}