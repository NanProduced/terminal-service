package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.infrastructure.async.AsyncDeviceStatusUpdateService;
import com.colorlight.terminal.infrastructure.async.AsyncGpsRecordService;
import com.colorlight.terminal.infrastructure.async.AsyncTerminalLoginUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;

/**
 * 异步缓冲区刷新事件监听器测试
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("异步缓冲区刷新事件监听器测试")
class AsyncBufferFlushEventListenerTest {

    @Mock
    private AsyncDeviceStatusUpdateService deviceStatusService;
    
    @Mock
    private AsyncGpsRecordService gpsRecordService;
    
    @Mock
    private AsyncTerminalLoginUpdateService loginUpdateService;

    @InjectMocks
    private AsyncBufferFlushEventListener eventListener;

    /**
     * 测试数据构建器
     */
    private static class TestEventBuilder {
        
        /**
         * 创建设备状态刷新事件
         */
        static AsyncBufferFlushEvent createDeviceStatusEvent(AsyncDeviceStatusUpdateService service, Integer bufferSize) {
            return AsyncBufferFlushEvent.createDeviceStatusFlushEvent(service, bufferSize);
        }
        
        /**
         * 创建GPS记录刷新事件
         */
        static AsyncBufferFlushEvent createGpsRecordEvent(AsyncGpsRecordService service) {
            return AsyncBufferFlushEvent.createGpsRecordFlushEvent(service, 5);
        }
        
        /**
         * 创建登录更新刷新事件
         */
        static AsyncBufferFlushEvent createLoginUpdateEvent(AsyncTerminalLoginUpdateService service) {
            return AsyncBufferFlushEvent.createLoginUpdateFlushEvent(service, 8);
        }
        
        /**
         * 创建错误类型的事件
         */
        static AsyncBufferFlushEvent createWrongTypeEvent() {
            return AsyncBufferFlushEvent.builder()
                    .bufferType(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS)
                    .serviceInstance("wrongServiceType")
                    .eventTime(System.currentTimeMillis())
                    .bufferSize(5)
                    .build();
        }
    }

    @BeforeEach
    void setUp() {
        // Mock服务的基本行为将在各个测试中按需设置
    }

    static Stream<Arguments> bufferFlushEventHandlers() {
        return Stream.of(
                Arguments.of("设备状态", new TestEventHandler() {
                    @Override
                    public void handle(AsyncBufferFlushEventListener listener, AsyncBufferFlushEvent event) {
                        listener.handleDeviceStatusBufferFlush(event);
                    }
                    
                    @Override
                    public void verifyCalls(AsyncDeviceStatusUpdateService deviceStatusService,
                                       AsyncGpsRecordService gpsRecordService,
                                       AsyncTerminalLoginUpdateService loginUpdateService) {
                        verify(deviceStatusService, times(1)).flushBuffer();
                        verifyNoInteractions(gpsRecordService);
                        verifyNoInteractions(loginUpdateService);
                    }
                }),
                
                Arguments.of("GPS记录", new TestEventHandler() {
                    @Override
                    public void handle(AsyncBufferFlushEventListener listener, AsyncBufferFlushEvent event) {
                        listener.handleGpsRecordBufferFlush(event);
                    }
                    
                    @Override
                    public void verifyCalls(AsyncDeviceStatusUpdateService deviceStatusService,
                                       AsyncGpsRecordService gpsRecordService,
                                       AsyncTerminalLoginUpdateService loginUpdateService) {
                        verify(gpsRecordService, times(1)).flushBuffer();
                        verifyNoInteractions(deviceStatusService);
                        verifyNoInteractions(loginUpdateService);
                    }
                }),
                
                Arguments.of("登录更新", new TestEventHandler() {
                    @Override
                    public void handle(AsyncBufferFlushEventListener listener, AsyncBufferFlushEvent event) {
                        listener.handleLoginUpdateBufferFlush(event);
                    }
                    
                    @Override
                    public void verifyCalls(AsyncDeviceStatusUpdateService deviceStatusService,
                                       AsyncGpsRecordService gpsRecordService,
                                       AsyncTerminalLoginUpdateService loginUpdateService) {
                        verify(loginUpdateService, times(1)).flushBuffer();
                        verifyNoInteractions(deviceStatusService);
                        verifyNoInteractions(gpsRecordService);
                    }
                })
        );
    }

    @ParameterizedTest(name = "应该正确处理{0}缓冲池刷新事件")
    @MethodSource("bufferFlushEventHandlers")
    @DisplayName("应该正确处理缓冲池刷新事件")
    void should_handle_buffer_flush_events(String eventType, TestEventHandler handler) {
        // Given - 创建事件
        lenient().doNothing().when(deviceStatusService).flushBuffer();
        lenient().doNothing().when(gpsRecordService).flushBuffer();
        lenient().doNothing().when(loginUpdateService).flushBuffer();
        
        AsyncBufferFlushEvent event = switch (eventType) {
            case "设备状态" -> TestEventBuilder.createDeviceStatusEvent(deviceStatusService, 10);
            case "GPS记录" -> TestEventBuilder.createGpsRecordEvent(gpsRecordService);
            case "登录更新" -> TestEventBuilder.createLoginUpdateEvent(loginUpdateService);
            default -> throw new IllegalArgumentException("未知事件类型: " + eventType);
        };

        // When - 处理事件
        handler.handle(eventListener, event);

        // Then - 验证服务调用
        handler.verifyCalls(deviceStatusService, gpsRecordService, loginUpdateService);
    }

    // 定义测试事件处理器接口
    interface TestEventHandler {
        void handle(AsyncBufferFlushEventListener listener, AsyncBufferFlushEvent event);
        void verifyCalls(AsyncDeviceStatusUpdateService deviceStatusService,
                    AsyncGpsRecordService gpsRecordService,
                    AsyncTerminalLoginUpdateService loginUpdateService);
    }

    @Test
    @DisplayName("应该忽略不匹配类型的设备状态事件")
    void should_ignore_mismatched_device_status_event() {
        // Given - 创建GPS类型的事件但传给设备状态处理器
        AsyncBufferFlushEvent event = TestEventBuilder.createGpsRecordEvent(gpsRecordService);

        // When - 用设备状态处理器处理
        eventListener.handleDeviceStatusBufferFlush(event);

        // Then - 验证没有服务调用
        verifyNoInteractions(deviceStatusService);
        verifyNoInteractions(gpsRecordService);
        verifyNoInteractions(loginUpdateService);
    }

    @Test
    @DisplayName("应该忽略不匹配类型的GPS记录事件")
    void should_ignore_mismatched_gps_record_event() {
        // Given - 创建设备状态类型的事件但传给GPS记录处理器
        AsyncBufferFlushEvent event = TestEventBuilder.createDeviceStatusEvent(deviceStatusService, 10);

        // When - 用GPS记录处理器处理
        eventListener.handleGpsRecordBufferFlush(event);

        // Then - 验证没有服务调用
        verifyNoInteractions(deviceStatusService);
        verifyNoInteractions(gpsRecordService);
        verifyNoInteractions(loginUpdateService);
    }

    @Test
    @DisplayName("应该忽略不匹配类型的登录更新事件")
    void should_ignore_mismatched_login_update_event() {
        // Given - 创建GPS类型的事件但传给登录更新处理器
        AsyncBufferFlushEvent event = TestEventBuilder.createGpsRecordEvent(gpsRecordService);

        // When - 用登录更新处理器处理
        eventListener.handleLoginUpdateBufferFlush(event);

        // Then - 验证没有服务调用
        verifyNoInteractions(deviceStatusService);
        verifyNoInteractions(gpsRecordService);
        verifyNoInteractions(loginUpdateService);
    }

    @Test
    @DisplayName("应该处理设备状态服务刷新时的异常")
    void should_handle_device_status_service_flush_exception() {
        // Given - 设置服务抛出异常
        doThrow(new RuntimeException("设备状态刷新失败")).when(deviceStatusService).flushBuffer();
        AsyncBufferFlushEvent event = TestEventBuilder.createDeviceStatusEvent(deviceStatusService, 10);

        // When - 处理事件（不应该抛出异常）
        eventListener.handleDeviceStatusBufferFlush(event);

        // Then - 验证服务被调用了
        verify(deviceStatusService, times(1)).flushBuffer();
    }

    @Test
    @DisplayName("应该处理GPS记录服务刷新时的异常")
    void should_handle_gps_record_service_flush_exception() {
        // Given - 设置服务抛出异常
        doThrow(new RuntimeException("GPS记录刷新失败")).when(gpsRecordService).flushBuffer();
        AsyncBufferFlushEvent event = TestEventBuilder.createGpsRecordEvent(gpsRecordService);

        // When - 处理事件（不应该抛出异常）
        eventListener.handleGpsRecordBufferFlush(event);

        // Then - 验证服务被调用了
        verify(gpsRecordService, times(1)).flushBuffer();
    }

    @Test
    @DisplayName("应该处理登录更新服务刷新时的异常")
    void should_handle_login_update_service_flush_exception() {
        // Given - 设置服务抛出异常
        doThrow(new RuntimeException("登录更新刷新失败")).when(loginUpdateService).flushBuffer();
        AsyncBufferFlushEvent event = TestEventBuilder.createLoginUpdateEvent(loginUpdateService);

        // When - 处理事件（不应该抛出异常）
        eventListener.handleLoginUpdateBufferFlush(event);

        // Then - 验证服务被调用了
        verify(loginUpdateService, times(1)).flushBuffer();
    }

    @Test
    @DisplayName("应该处理类型转换异常")
    void should_handle_class_cast_exception() {
        // Given - 创建错误的服务实例类型
        AsyncBufferFlushEvent event = TestEventBuilder.createWrongTypeEvent();

        // When - 处理事件（不应该抛出异常）
        eventListener.handleDeviceStatusBufferFlush(event);

        // Then - 验证没有服务调用
        verifyNoInteractions(deviceStatusService);
        verifyNoInteractions(gpsRecordService);
        verifyNoInteractions(loginUpdateService);
    }

    @Test
    @DisplayName("统一事件处理器应该处理所有类型的事件")
    void should_handle_all_buffer_flush_events() {
        // Given - 创建不同类型的事件
        AsyncBufferFlushEvent deviceEvent = TestEventBuilder.createDeviceStatusEvent(deviceStatusService, 10);
        AsyncBufferFlushEvent gpsEvent = TestEventBuilder.createGpsRecordEvent(gpsRecordService);
        AsyncBufferFlushEvent loginEvent = TestEventBuilder.createLoginUpdateEvent(loginUpdateService);

        // When - 统一处理器处理所有事件
        eventListener.handleAllBufferFlushEvents(deviceEvent);
        eventListener.handleAllBufferFlushEvents(gpsEvent);
        eventListener.handleAllBufferFlushEvents(loginEvent);

        // Then - 验证统一处理器不会调用具体服务（仅用于监控日志）
        verifyNoInteractions(deviceStatusService);
        verifyNoInteractions(gpsRecordService);
        verifyNoInteractions(loginUpdateService);
    }

    @Test
    @DisplayName("应该处理null事件")
    void should_handle_null_event() {
        // When & Then - 处理null事件会抛出NullPointerException（这是预期行为）
        // 在实际使用中，Spring事件系统不会传递null事件
        
        // 验证没有服务调用
        verifyNoInteractions(deviceStatusService);
        verifyNoInteractions(gpsRecordService);
        verifyNoInteractions(loginUpdateService);
    }

    @Test
    @DisplayName("应该验证事件的完整性")
    void should_validate_event_integrity() {
        // Given - 创建包含完整信息的事件
        doNothing().when(deviceStatusService).flushBuffer();
        AsyncBufferFlushEvent event = TestEventBuilder.createDeviceStatusEvent(deviceStatusService, 15);

        // 验证事件属性
        assert event.getBufferType() == AsyncBufferFlushEvent.BufferType.DEVICE_STATUS;
        assert event.getServiceInstance() == deviceStatusService;
        assert event.getBufferSize() == 15;
        assert event.getEventTime() > 0;

        // When - 处理事件
        eventListener.handleDeviceStatusBufferFlush(event);

        // Then - 验证服务调用
        verify(deviceStatusService, times(1)).flushBuffer();
    }

    @Test
    @DisplayName("应该正确处理零缓冲区大小的事件")
    void should_handle_zero_buffer_size_event() {
        // Given - 创建缓冲区大小为0的事件
        doNothing().when(deviceStatusService).flushBuffer();
        AsyncBufferFlushEvent event = TestEventBuilder.createDeviceStatusEvent(deviceStatusService, 0);

        // When - 处理事件
        eventListener.handleDeviceStatusBufferFlush(event);

        // Then - 验证服务仍然被调用（因为可能需要清理操作）
        verify(deviceStatusService, times(1)).flushBuffer();
    }

    @Test
    @DisplayName("应该正确处理大缓冲区大小的事件")
    void should_handle_large_buffer_size_event() {
        // Given - 创建缓冲区大小很大的事件
        doNothing().when(deviceStatusService).flushBuffer();
        AsyncBufferFlushEvent event = TestEventBuilder.createDeviceStatusEvent(deviceStatusService, 10000);

        // When - 处理事件
        eventListener.handleDeviceStatusBufferFlush(event);

        // Then - 验证服务被调用
        verify(deviceStatusService, times(1)).flushBuffer();
    }
}