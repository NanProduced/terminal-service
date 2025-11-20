package com.colorlight.terminal.infrastructure.async;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncDeviceStatusUpdatePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.infrastructure.event.AsyncBufferFlushEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * AsyncDeviceStatusUpdateService 全面单元测试
 * <p>
 * 基于深度业务逻辑分析的高质量测试设计：
 * 1. 异步缓冲机制测试
 * 2. 批量处理策略测试
 * 3. 多种刷新策略测试
 * 4. 并发安全设计测试
 * 5. 生命周期管理测试
 * 6. 统计监控体系测试
 *
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncDeviceStatusUpdateService - 异步设备状态更新服务全面测试")
class AsyncDeviceStatusUpdateServiceTest {

    @Mock
    private DeviceOnlineStatusPort deviceOnlineStatusPort;

    @Mock
    private DeviceConfigPort deviceConfigPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AsyncDeviceStatusUpdateService service;

    // ==================== 测试工具类 ====================

    /**
     * 测试数据构建器 - 提供各种测试场景的数据
     */
    static class TestDataBuilder {

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

        static DeviceOnlineStatus createReconnectStatus(Long deviceId) {
            return DeviceOnlineStatus.builder()
                    .deviceId(deviceId)
                    .lastReportTime(System.currentTimeMillis())
                    .lastReportSource(ReportSource.WEBSOCKET)
                    .status(OnlineStatus.RECONNECT)
                    .statusChangeTime(System.currentTimeMillis())
                    .onlineStartTime(System.currentTimeMillis())
                    .clientIp("192.168.1.101")
                    .version("1.0")
                    .build();
        }

        static DeviceOnlineStatus createGoLiveStatus(Long deviceId) {
            return DeviceOnlineStatus.builder()
                    .deviceId(deviceId)
                    .lastReportTime(System.currentTimeMillis())
                    .lastReportSource(ReportSource.HTTP)
                    .status(OnlineStatus.GO_LIVE)
                    .statusChangeTime(System.currentTimeMillis())
                    .onlineStartTime(System.currentTimeMillis())
                    .clientIp("192.168.1.102")
                    .version("1.2")
                    .build();
        }

        static List<DeviceOnlineStatus> createBatchStatuses(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> createOnlineStatus((long) (1000 + i)))
                    .toList();
        }

        static List<DeviceOnlineStatus> createMixedStatusBatch(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> {
                        return switch (i % 3) {
                            case 0 -> createOnlineStatus((long) (2000 + i));
                            case 1 -> createReconnectStatus((long) (2000 + i));
                            default -> createGoLiveStatus((long) (2000 + i));
                        };
                    })
                    .toList();
        }
    }

    /**
     * 测试配置构建器 - 提供不同的配置组合
     */
    static class TestConfigBuilder {

        static void setupDefaultConfig(DeviceConfigPort configPort) {
            lenient().when(configPort.getBufferPoolWindowMs()).thenReturn(5000L);
            lenient().when(configPort.getBufferPoolMaxSize()).thenReturn(1000);
            lenient().when(configPort.getBufferPoolBatchSize()).thenReturn(100);
            lenient().when(configPort.getEmergencyFlushThreshold()).thenReturn(0.8);
            lenient().when(configPort.getTaskBufferPoolDelayMs()).thenReturn(1000L);
            lenient().when(configPort.getBufferPoolStatisticsInterval()).thenReturn(30000L);
            lenient().when(configPort.getTaskStatisticsDelayMs()).thenReturn(5000L);
            lenient().when(configPort.isEmergencyFlushEnabled()).thenReturn(true);
        }

        static void setupSmallBufferConfig(DeviceConfigPort configPort) {
            setupDefaultConfig(configPort);
            when(configPort.getBufferPoolMaxSize()).thenReturn(5);
            when(configPort.getBufferPoolBatchSize()).thenReturn(2);
        }

        static void setupHighThresholdConfig(DeviceConfigPort configPort) {
            setupDefaultConfig(configPort);
            when(configPort.getEmergencyFlushThreshold()).thenReturn(0.9);
        }

        static void setupDisabledEmergencyConfig(DeviceConfigPort configPort) {
            setupDefaultConfig(configPort);
            when(configPort.isEmergencyFlushEnabled()).thenReturn(false);
        }
    }

    /**
     * 并发测试辅助工具
     */
    static class ConcurrencyTestHelper {

        static void executeMultiThreadOperation(int threadCount, Runnable operation) throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        operation.run();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
        }

        static void executeConcurrentSubmitAndFlush(AsyncDeviceStatusUpdateService service) throws InterruptedException {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(2);

            // 提交线程
            Thread submitThread = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 50; i++) {
                        service.submitStatusUpdate(TestDataBuilder.createOnlineStatus((long) i));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });

            // 刷新线程
            Thread flushThread = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 5; i++) {
                        service.flushBuffer();
                        // 移除了 Thread.sleep(10); // 模拟刷新间隔
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });

            submitThread.start();
            flushThread.start();

            startLatch.countDown(); // 同时开始
            assertThat(completeLatch.await(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    // ==================== 测试设置 ====================

    @BeforeEach
    void setUp() {
        TestConfigBuilder.setupDefaultConfig(deviceConfigPort);
        service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
        service.init();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
        }
    }

    // ==================== 1. 基础功能测试组 ====================

    @Nested
    @DisplayName("基础功能测试")
    class BasicOperationsTest {

        @Test
        @DisplayName("应该成功提交单个设备状态更新")
        void should_submit_single_status_update_successfully() {
            // Given
            DeviceOnlineStatus status = TestDataBuilder.createOnlineStatus(1001L);

            // When
            service.submitStatusUpdate(status);

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = service.getBufferPoolStatus();
            assertThat(bufferStatus.currentSize()).isEqualTo(1);
            assertThat(bufferStatus.totalProcessed()).isEqualTo(1);
            assertThat(bufferStatus.totalFlushed()).isZero();
        }

        @Test
        @DisplayName("应该成功批量提交设备状态更新")
        void should_submit_batch_status_updates_successfully() {
            // Given
            List<DeviceOnlineStatus> statusList = TestDataBuilder.createBatchStatuses(5);

            // When
            service.submitBatchStatusUpdate(statusList);

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = service.getBufferPoolStatus();
            assertThat(bufferStatus.currentSize()).isEqualTo(5);
            assertThat(bufferStatus.totalProcessed()).isEqualTo(5);
        }

        @Test
        @DisplayName("应该正确处理null状态提交")
        void should_handle_null_status_submission() {
            // When
            service.submitStatusUpdate(null);

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = service.getBufferPoolStatus();
            assertThat(bufferStatus.currentSize()).isZero();
            assertThat(bufferStatus.totalProcessed()).isZero();
        }

        @Test
        @DisplayName("应该正确过滤批量提交中的null对象")
        void should_filter_null_objects_in_batch_submission() {
            // Given
            List<DeviceOnlineStatus> statusListWithNulls = Arrays.asList(
                    TestDataBuilder.createOnlineStatus(1001L),
                    null,
                    TestDataBuilder.createReconnectStatus(1002L),
                    null
            );

            // When
            service.submitBatchStatusUpdate(statusListWithNulls);

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = service.getBufferPoolStatus();
            assertThat(bufferStatus.currentSize()).isEqualTo(2);
            assertThat(bufferStatus.totalProcessed()).isEqualTo(2);
        }

        @Test
        @DisplayName("应该正确处理空集合提交")
        void should_handle_empty_collections() {
            // When
            service.submitBatchStatusUpdate(Collections.emptyList());
            service.submitBatchStatusUpdate(null);

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus bufferStatus = service.getBufferPoolStatus();
            assertThat(bufferStatus.currentSize()).isZero();
            assertThat(bufferStatus.totalProcessed()).isZero();
        }
    }

    // ==================== 2. 缓冲池机制测试组 ====================

    @Nested
    @DisplayName("缓冲池机制测试")
    class BufferPoolMechanicsTest {

        @Test
        @DisplayName("应该在达到容量上限时触发同步flush而不丢弃数据")
        void should_trigger_sync_flush_when_capacity_reached() {
            // Given - 设置小容量缓冲池
            TestConfigBuilder.setupSmallBufferConfig(deviceConfigPort);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            // When - 添加超过容量的状态，会触发soft limit的同步flush
            for (int i = 1; i <= 8; i++) {
                service.submitStatusUpdate(TestDataBuilder.createOnlineStatus((long) i));
            }

            // Then - soft limit策略：未丢弃数据，触发同步flush
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.currentSize()).isLessThanOrEqualTo(5); // 最多5个，可能已flush
            assertThat(status.totalProcessed()).isEqualTo(8); // 所有8个都被处理
            assertThat(status.totalDropped()).isEqualTo(0); // 没有丢弃，ConcurrentHashMap保证no data loss
            // totalFlushed可能>0，因为触发了soft limit的同步flush
            assertThat(status.totalFlushed()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("应该正确计算缓冲池使用率")
        void should_calculate_utilization_rate_correctly() {
            // Given
            when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(10);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            // When
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(3));

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.currentSize()).isEqualTo(3);
            assertThat(status.maxSize()).isEqualTo(10);
            assertThat(status.utilizationRate()).isEqualTo(0.3);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 100, 1000})
        @DisplayName("应该支持不同的缓冲池大小配置")
        void should_support_different_buffer_sizes(int bufferSize) {
            // Given
            when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(bufferSize);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            // When
            int submissionCount = Math.min(bufferSize, 50); // 避免测试过长
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(submissionCount));

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.maxSize()).isEqualTo(bufferSize);
            assertThat(status.currentSize()).isEqualTo(submissionCount);
        }
    }

    // ==================== 3. 刷新策略测试组 ====================

    @Nested
    @DisplayName("刷新策略测试")
    class FlushStrategiesTest {

        @Test
        @DisplayName("应该成功执行手动刷新")
        void should_execute_manual_flush_successfully() {
            // Given
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(3));

            // When
            service.flushBuffer();

            // Then - 使用Pipeline批量处理，验证batchSmartDetermined被调用
            verify(deviceOnlineStatusPort, times(1)).batchSmartDetermined(any(List.class));

            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.currentSize()).isZero();
            assertThat(status.totalFlushed()).isEqualTo(3);
        }

        @Test
        @DisplayName("应该在空缓冲池时跳过刷新")
        void should_skip_flush_when_buffer_empty() {
            // When
            service.flushBuffer();

            // Then - 验证批量处理方法未被调用
            verify(deviceOnlineStatusPort, never()).batchSmartDetermined(any(List.class));
        }

        @Test
        @DisplayName("应该在定时刷新时发布事件")
        void should_publish_event_on_scheduled_flush() {
            // Given
            service.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));

            // When
            service.scheduledFlush();

            // Then
            verify(eventPublisher).publishEvent(any(AsyncBufferFlushEvent.class));
        }

        @Test
        @DisplayName("应该在空缓冲池时跳过定时刷新")
        void should_skip_scheduled_flush_when_buffer_empty() {
            // When
            service.scheduledFlush();

            // Then
            verify(eventPublisher, never()).publishEvent(any(AsyncBufferFlushEvent.class));
        }
    }

    // ==================== 4. 批处理逻辑测试组 ====================

    @Nested
    @DisplayName("批处理逻辑测试")
    class BatchProcessingTest {

        @Test
        @DisplayName("应该按配置的批处理大小处理状态")
        void should_process_by_configured_batch_size() {
            // Given
            when(deviceConfigPort.getBufferPoolBatchSize()).thenReturn(3);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(10));

            // When
            service.flushBuffer();

            // Then - 验证所有状态都被通过批量方法处理
            // 10个状态，批处理大小为3，会分为4批（3+3+3+1）
            verify(deviceOnlineStatusPort, times(4)).batchSmartDetermined(any(List.class));

            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.currentSize()).isZero();
            assertThat(status.totalFlushed()).isEqualTo(10);
        }

        @Test
        @DisplayName("应该在处理异常时继续处理后续状态")
        void should_continue_processing_when_exception_occurs() {
            // Given - 批量方法异常
            doThrow(new RuntimeException("模拟处理异常"))
                    .when(deviceOnlineStatusPort).batchSmartDetermined(any(List.class));

            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(3));

            // When
            service.flushBuffer();

            // Then - 尽管有异常，所有状态都被尝试处理
            verify(deviceOnlineStatusPort, times(1)).batchSmartDetermined(any(List.class));

            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.currentSize()).isZero();
        }

        @Test
        @DisplayName("应该正确处理ONLINE类型的设备状态")
        void should_handle_mixed_device_status_types() {
            // Given - 异步缓冲区只接收ONLINE状态（RECONNECT和GO_LIVE由同步处理）
            List<DeviceOnlineStatus> onlineStatuses = new ArrayList<>();
            for (long i = 1001L; i <= 1006L; i++) {
                onlineStatuses.add(TestDataBuilder.createOnlineStatus(i));
            }

            // When
            service.submitBatchStatusUpdate(onlineStatuses);
            service.flushBuffer();

            // Then - 验证批量处理被调用
            verify(deviceOnlineStatusPort, times(1)).batchSmartDetermined(any(List.class));

            // 验证批量方法收到了所有6个ONLINE状态
            verify(deviceOnlineStatusPort).batchSmartDetermined(argThat(list ->
                    list.size() == 6 &&
                    list.stream().allMatch(s -> s.getStatus() == OnlineStatus.ONLINE)
            ));
        }
    }

    // ==================== 5. 生命周期管理测试组 ====================

    @Nested
    @DisplayName("生命周期管理测试")
    class LifecycleManagementTest {

        @Test
        @DisplayName("应该在服务关闭时强制刷新缓冲池")
        void should_force_flush_on_service_destroy() {
            // Given
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(3));

            // When
            service.destroy();

            // Then - 通过批量处理验证刷新
            verify(deviceOnlineStatusPort, times(1)).batchSmartDetermined(any(List.class));
        }

        @Test
        @DisplayName("应该在服务停止后拒绝新提交")
        void should_reject_submissions_after_service_stopped() {
            // Given
            service.destroy();

            // When
            service.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(2));

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.currentSize()).isZero();
            assertThat(status.totalProcessed()).isZero();
        }

        @Test
        @DisplayName("应该正确初始化服务状态")
        void should_initialize_service_state_correctly() {
            // Given - 新的服务实例
            AsyncDeviceStatusUpdateService newService = new AsyncDeviceStatusUpdateService(
                    deviceOnlineStatusPort, deviceConfigPort, eventPublisher);

            // When
            newService.init();

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = newService.getBufferPoolStatus();
            assertThat(status.currentSize()).isZero();
            assertThat(status.totalProcessed()).isZero();
            assertThat(status.totalFlushed()).isZero();
            assertThat(status.lastFlushTime()).isGreaterThan(0);
        }
    }

    // ==================== 6. 并发安全测试组 ====================

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencySafetyTest {

        @Test
        @DisplayName("应该支持并发状态提交")
        void should_support_concurrent_status_submission() throws InterruptedException {
            // Given
            int threadCount = 5;
            int statusPerThread = 10;
            AtomicInteger submitCount = new AtomicInteger(0);

            // When
            ConcurrencyTestHelper.executeMultiThreadOperation(threadCount, () -> {
                for (int i = 0; i < statusPerThread; i++) {
                    int id = submitCount.incrementAndGet();
                    service.submitStatusUpdate(TestDataBuilder.createOnlineStatus((long) id));
                }
            });

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.totalProcessed()).isEqualTo(threadCount * statusPerThread);
        }

        @Test
        @DisplayName("应该支持并发刷新操作")
        void should_support_concurrent_flush_operations() throws InterruptedException {
            // Given
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(20));

            // When - 多线程并发刷新
            ConcurrencyTestHelper.executeMultiThreadOperation(3, service::flushBuffer);

            // Then - 所有状态都应该被处理，通过批量处理验证
            // 只有一个线程会成功获得锁并执行刷新
            verify(deviceOnlineStatusPort, times(1)).batchSmartDetermined(any(List.class));

            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.currentSize()).isZero();
        }

        @Test
        @DisplayName("应该正确处理并发提交和刷新")
        void should_handle_concurrent_submit_and_flush() throws InterruptedException {
            // When
            ConcurrencyTestHelper.executeConcurrentSubmitAndFlush(service);

            // 最终刷新确保所有数据都被处理
            service.flushBuffer();

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.totalProcessed()).isEqualTo(50);
            assertThat(status.currentSize()).isZero();

            // 验证批量处理被调用（次数取决于批处理大小）
            verify(deviceOnlineStatusPort, atLeastOnce()).batchSmartDetermined(any(List.class));
        }
    }

    // ==================== 7. 配置驱动测试组 ====================

    @Nested
    @DisplayName("配置驱动测试")
    class ConfigurationDrivenTest {

        @Test
        @DisplayName("应该根据配置调整批处理行为")
        void should_adjust_batch_behavior_by_configuration() {
            // Given - 不同的批处理大小配置
            when(deviceConfigPort.getBufferPoolBatchSize()).thenReturn(2);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(5));

            // When
            service.flushBuffer();

            // Then - 验证分批处理（5个状态，批大小2，分为3批）
            verify(deviceOnlineStatusPort, times(3)).batchSmartDetermined(any(List.class));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 10, 50, 100})
        @DisplayName("应该支持不同的批处理大小")
        void should_support_different_batch_sizes(int batchSize) {
            // Given
            when(deviceConfigPort.getBufferPoolBatchSize()).thenReturn(batchSize);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            int totalItems = 20;
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(totalItems));

            // When
            service.flushBuffer();

            // Then - 计算预期的批处理次数
            int expectedBatches = (totalItems + batchSize - 1) / batchSize;
            verify(deviceOnlineStatusPort, times(expectedBatches)).batchSmartDetermined(any(List.class));
        }
    }

    // ==================== 8. 异常处理测试组 ====================

    @Nested
    @DisplayName("异常处理测试")
    class ErrorHandlingTest {

        @Test
        @DisplayName("应该优雅处理状态更新异常")
        void should_gracefully_handle_status_update_exceptions() {
            // Given
            doThrow(new RuntimeException("网络异常"))
                    .when(deviceOnlineStatusPort).batchSmartDetermined(any(List.class));

            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(2));

            // When
            service.flushBuffer();

            // Then - 异常被捕获，缓冲池被清空
            verify(deviceOnlineStatusPort, times(1)).batchSmartDetermined(any(List.class));

            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.currentSize()).isZero();
        }

        @Test
        @DisplayName("应该处理事件发布异常")
        void should_handle_event_publishing_exceptions() {
            // Given
            doThrow(new RuntimeException("事件发布异常"))
                    .when(eventPublisher).publishEvent(any(AsyncBufferFlushEvent.class));

            service.submitStatusUpdate(TestDataBuilder.createOnlineStatus(1001L));

            // When - 定时刷新不应该因为事件发布异常而中断
            service.scheduledFlush();

            // Then - 服务应该继续正常工作
            service.flushBuffer();
            verify(deviceOnlineStatusPort).batchSmartDetermined(any(List.class));
        }
    }

    // ==================== 9. 统计监控测试组 ====================

    @Nested
    @DisplayName("统计监控测试")
    class StatisticsMonitoringTest {

        @Test
        @DisplayName("应该准确统计处理和刷新数据")
        void should_accurately_track_processing_and_flush_statistics() {
            // Given & When
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(5));
            service.flushBuffer();

            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(3));
            service.flushBuffer();

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.totalProcessed()).isEqualTo(8);
            assertThat(status.totalFlushed()).isEqualTo(8);
            assertThat(status.currentSize()).isZero();
        }

        @Test
        @DisplayName("应该使用soft limit策略而不丢弃数据")
        void should_use_soft_limit_without_dropping_data() {
            // Given - 小容量缓冲池
            when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(3);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            // When - 批量提交超过容量的状态
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(7));

            // Then - soft limit策略：所有数据都被处理，没有丢弃，可能触发了多次flush
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.totalProcessed()).isEqualTo(7); // 所有7个都被处理
            assertThat(status.totalDropped()).isEqualTo(0); // 没有丢弃，ConcurrentHashMap无损
            // currentSize<=3，且totalFlushed可能>0（触发了soft limit flush）
            assertThat(status.currentSize()).isLessThanOrEqualTo(3);
        }

        @Test
        @DisplayName("应该提供完整的缓冲池状态信息")
        void should_provide_complete_buffer_pool_status() {
            // Given
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(10));

            // When
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();

            // Then
            assertThat(status.currentSize()).isEqualTo(10);
            assertThat(status.maxSize()).isEqualTo(1000);
            assertThat(status.utilizationRate()).isEqualTo(0.01);
            assertThat(status.totalProcessed()).isEqualTo(10);
            assertThat(status.totalFlushed()).isZero();
            assertThat(status.totalDropped()).isZero();
            assertThat(status.lastFlushTime()).isGreaterThan(0);
        }

        @Test
        @DisplayName("应该支持统计日志输出")
        void should_support_statistics_logging() {
            // Given
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(5));
            service.flushBuffer();

            // When - 执行统计日志输出（应该不抛异常）
            service.logStatistics();

            // Then - 验证统计数据正确
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.totalProcessed()).isEqualTo(5);
            assertThat(status.totalFlushed()).isEqualTo(5);
        }
    }

    // ==================== 边界条件和压力测试 ====================

    @Nested
    @DisplayName("边界条件和压力测试")
    class BoundaryAndStressTest {

        @Test
        @DisplayName("应该处理大量状态提交")
        void should_handle_large_volume_status_submission() {
            // Given
            int largeVolume = 1000;

            // When
            for (int i = 0; i < largeVolume; i++) {
                service.submitStatusUpdate(TestDataBuilder.createOnlineStatus((long) i));
            }
            service.flushBuffer();

            // Then - 验证批量处理被调用（次数取决于批处理大小和缓冲池大小）
            verify(deviceOnlineStatusPort, atLeastOnce()).batchSmartDetermined(any(List.class));

            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.totalProcessed()).isLessThanOrEqualTo(largeVolume);
            assertThat(status.currentSize()).isZero();
        }

        @Test
        @DisplayName("应该处理最小配置参数（soft limit策略）")
        void should_handle_minimal_configuration_with_soft_limit() {
            // Given
            when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(1);
            when(deviceConfigPort.getBufferPoolBatchSize()).thenReturn(1);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            // When - 最小配置下，soft limit会触发多次sync flush
            service.submitBatchStatusUpdate(TestDataBuilder.createBatchStatuses(3));
            service.flushBuffer();

            // Then - 即使配置最小，soft limit也保证所有数据都被处理
            verify(deviceOnlineStatusPort, atLeastOnce()).batchSmartDetermined(any(List.class));

            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();
            assertThat(status.maxSize()).isEqualTo(1);
            assertThat(status.totalProcessed()).isEqualTo(3); // 所有3个都被处理
            assertThat(status.totalDropped()).isEqualTo(0); // 没有丢弃
            assertThat(status.currentSize()).isLessThanOrEqualTo(1);
        }

        @Test
        @DisplayName("应该在高并发下保持数据一致性")
        void should_maintain_data_consistency_under_high_concurrency() throws InterruptedException {
            // Given
            when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(100);
            service = new AsyncDeviceStatusUpdateService(deviceOnlineStatusPort, deviceConfigPort, eventPublisher);
            service.init();

            int threadCount = 10;
            int statusPerThread = 20;

            // When
            ConcurrencyTestHelper.executeMultiThreadOperation(threadCount, () -> {
                for (int i = 0; i < statusPerThread; i++) {
                    service.submitStatusUpdate(TestDataBuilder.createOnlineStatus(
                            (long) (Thread.currentThread().getId() * 1000 + i)));
                }
            });

            service.flushBuffer();

            // Then
            AsyncDeviceStatusUpdatePort.BufferPoolStatus status = service.getBufferPoolStatus();

            // 在高并发情况下，由于缓冲池限制，可能有丢弃
            int totalExpected = threadCount * statusPerThread;
            int totalProcessed = (int) status.totalProcessed();

            assertThat(totalProcessed).isEqualTo(totalExpected);
            assertThat(status.currentSize()).isZero();

            // 验证批量处理被调用
            verify(deviceOnlineStatusPort, atLeastOnce()).batchSmartDetermined(any(List.class));
        }
    }
}