package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * DeviceStatusEventPublisher单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：发布设备状态变更事件到Spring事件系统
 * 2. 配置化支持：根据DeviceConfigPort配置选择同步或异步发布
 * 3. 单个事件发布：支持同步和异步两种模式
 * 4. 批量事件发布：支持批量发布多个事件
 * 5. 异常处理：捕获发布异常并记录日志
 * 6. 条件启用：通过@ConditionalOnProperty控制组件启用
 * <p>
 * 测试策略：
 * - 同步发布模式测试
 * - 异步发布模式测试
 * - 批量发布测试
 * - 异常处理测试
 * - 配置影响测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceStatusEventPublisher单元测试")
class DeviceStatusEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    
    @Mock
    private DeviceConfigPort deviceConfigPort;
    
    @InjectMocks
    private DeviceStatusEventPublisher deviceStatusEventPublisher;
    
    private DeviceStatusEvent sampleEvent;
    private Long currentTime;

    @BeforeEach
    void setUp() {
        currentTime = System.currentTimeMillis();
        sampleEvent = DeviceStatusEvent.builder()
                .deviceId(12345L)
                .eventType(DeviceStatusEvent.EventType.DEVICE_GO_LIVE)
                .reportSource(ReportSource.HTTP)
                .clientIp("192.168.1.100")
                .eventTime(currentTime)
                .build();
    }

    @Test
    @DisplayName("发布设备状态事件 - 同步模式")
    void publishStatusEvent_SyncMode() {
        // Given: 配置为同步模式
        given(deviceConfigPort.isSpringEventAsync()).willReturn(false);

        // When: 发布事件
        assertDoesNotThrow(() -> deviceStatusEventPublisher.publishStatusEvent(sampleEvent));

        // Then: 验证同步发布
        then(applicationEventPublisher).should().publishEvent(sampleEvent);
        then(deviceConfigPort).should(times(2)).isSpringEventAsync(); // 调用2次：一次日志，一次条件判断
    }

    @Test
    @DisplayName("发布设备状态事件 - 异步模式")
    void publishStatusEvent_AsyncMode() {
        // Given: 配置为异步模式
        given(deviceConfigPort.isSpringEventAsync()).willReturn(true);

        // When: 发布事件
        assertDoesNotThrow(() -> deviceStatusEventPublisher.publishStatusEvent(sampleEvent));

        // Then: 验证异步发布（通过spy验证异步方法被调用）
        then(deviceConfigPort).should(times(2)).isSpringEventAsync(); // 调用2次：一次日志，一次条件判断
        // 注意：由于异步方法是protected的，我们主要验证配置被正确读取
    }

    @Test
    @DisplayName("发布设备状态事件 - 发布异常处理")
    void publishStatusEvent_PublishException() {
        // Given: 同步模式下发布事件抛出异常
        given(deviceConfigPort.isSpringEventAsync()).willReturn(false);
        RuntimeException publishException = new RuntimeException("事件发布失败");
        willThrow(publishException).given(applicationEventPublisher).publishEvent(sampleEvent);

        // When: 发布事件 - 应该捕获异常
        assertDoesNotThrow(() -> deviceStatusEventPublisher.publishStatusEvent(sampleEvent));

        // Then: 验证尝试发布事件
        then(applicationEventPublisher).should().publishEvent(sampleEvent);
        then(deviceConfigPort).should(times(2)).isSpringEventAsync(); // 调用2次：一次日志，一次条件判断
    }

    @Test
    @DisplayName("批量发布设备状态事件 - 同步模式")
    void batchPublishStatusEvents_SyncMode() {
        // Given: 批量事件和同步模式配置
        DeviceStatusEvent event1 = DeviceStatusEvent.builder()
                .deviceId(11111L)
                .eventType(DeviceStatusEvent.EventType.DEVICE_HEARTBEAT)
                .eventTime(currentTime)
                .build();
        DeviceStatusEvent event2 = DeviceStatusEvent.builder()
                .deviceId(22222L)
                .eventType(DeviceStatusEvent.EventType.DEVICE_RECONNECT)
                .eventTime(currentTime)
                .build();
        
        List<DeviceStatusEvent> events = Arrays.asList(event1, event2);
        given(deviceConfigPort.isSpringEventAsync()).willReturn(false);

        // When: 批量发布事件
        deviceStatusEventPublisher.batchPublishStatusEvents(events);

        // Then: 验证每个事件都被同步发布
        then(applicationEventPublisher).should().publishEvent(event1);
        then(applicationEventPublisher).should().publishEvent(event2);
        then(deviceConfigPort).should(times(2)).isSpringEventAsync();
    }

    @Test
    @DisplayName("批量发布设备状态事件 - 异步模式")
    void batchPublishStatusEvents_AsyncMode() {
        // Given: 批量事件和异步模式配置
        List<DeviceStatusEvent> events = Collections.singletonList(sampleEvent);
        given(deviceConfigPort.isSpringEventAsync()).willReturn(true);

        // When: 批量发布事件
        deviceStatusEventPublisher.batchPublishStatusEvents(events);

        // Then: 验证异步配置被读取
        then(deviceConfigPort).should(times(2)).isSpringEventAsync();
    }

    @Test
    @DisplayName("批量发布设备状态事件 - 空列表处理")
    void batchPublishStatusEvents_EmptyList() {
        // Given: 空事件列表
        List<DeviceStatusEvent> emptyList = Collections.emptyList();

        // When: 批量发布空列表
        deviceStatusEventPublisher.batchPublishStatusEvents(emptyList);

        // Then: 验证没有调用任何发布操作
        then(applicationEventPublisher).should(never()).publishEvent(any());
        then(deviceConfigPort).should(never()).isSpringEventAsync();
    }

    @Test
    @DisplayName("批量发布设备状态事件 - null列表处理")
    void batchPublishStatusEvents_NullList() {
        // When: 批量发布null列表
        deviceStatusEventPublisher.batchPublishStatusEvents(null);

        // Then: 验证没有调用任何发布操作
        then(applicationEventPublisher).should(never()).publishEvent(any());
        then(deviceConfigPort).should(never()).isSpringEventAsync();
    }

    @Test
    @DisplayName("批量发布设备状态事件 - 批量发布异常")
    void batchPublishStatusEvents_BatchException() {
        // Given: 同步模式下批量发布抛出异常
        List<DeviceStatusEvent> events = Collections.singletonList(sampleEvent);
        given(deviceConfigPort.isSpringEventAsync()).willReturn(false);
        RuntimeException batchException = new RuntimeException("批量发布失败");
        willThrow(batchException).given(applicationEventPublisher).publishEvent(sampleEvent);

        // When: 批量发布事件 - 应该捕获异常
        assertDoesNotThrow(() -> deviceStatusEventPublisher.batchPublishStatusEvents(events));

        // Then: 验证尝试发布事件
        then(applicationEventPublisher).should().publishEvent(sampleEvent);
    }

    @Test
    @DisplayName("发布不同类型的设备状态事件")
    void publishStatusEvent_DifferentEventTypes() {
        // Given: 不同类型的设备状态事件
        DeviceStatusEvent goLiveEvent = DeviceStatusEvent.createGoLiveEvent(11111L, ReportSource.HTTP, "192.168.1.101");
        DeviceStatusEvent heartbeatEvent = DeviceStatusEvent.createHeartbeatEvent(22222L, ReportSource.WEBSOCKET, "192.168.1.102");
        DeviceStatusEvent offlineEvent = DeviceStatusEvent.createDetectedOfflineEvent(33333L, currentTime - 3600000L, currentTime - 300000L);
        DeviceStatusEvent confirmOfflineEvent = DeviceStatusEvent.createConfirmOfflineEvent(44444L);
        
        given(deviceConfigPort.isSpringEventAsync()).willReturn(false);

        // When: 发布不同类型的事件
        deviceStatusEventPublisher.publishStatusEvent(goLiveEvent);
        deviceStatusEventPublisher.publishStatusEvent(heartbeatEvent);
        deviceStatusEventPublisher.publishStatusEvent(offlineEvent);
        deviceStatusEventPublisher.publishStatusEvent(confirmOfflineEvent);

        // Then: 验证所有事件都被发布
        then(applicationEventPublisher).should().publishEvent(goLiveEvent);
        then(applicationEventPublisher).should().publishEvent(heartbeatEvent);
        then(applicationEventPublisher).should().publishEvent(offlineEvent);
        then(applicationEventPublisher).should().publishEvent(confirmOfflineEvent);
    }

    @Test
    @DisplayName("大批量发布设备状态事件")
    void batchPublishStatusEvents_LargeBatch() {
        // Given: 大批量事件列表
        List<DeviceStatusEvent> largeEventList = Arrays.asList(
                DeviceStatusEvent.createGoLiveEvent(1L, ReportSource.HTTP, "192.168.1.1"),
                DeviceStatusEvent.createHeartbeatEvent(2L, ReportSource.WEBSOCKET, "192.168.1.2"),
                DeviceStatusEvent.createDetectedOfflineEvent(3L, currentTime - 7200000L, currentTime - 600000L),
                DeviceStatusEvent.createConfirmOfflineEvent(4L),
                DeviceStatusEvent.createReconnectEvent(5L, ReportSource.HTTP, "192.168.1.5", currentTime - 1800000L, currentTime - 300000L)
        );
        
        given(deviceConfigPort.isSpringEventAsync()).willReturn(false);

        // When: 批量发布大量事件
        deviceStatusEventPublisher.batchPublishStatusEvents(largeEventList);

        // Then: 验证所有事件都被发布
        for (DeviceStatusEvent event : largeEventList) {
            then(applicationEventPublisher).should().publishEvent(event);
        }
        then(deviceConfigPort).should(times(2)).isSpringEventAsync();
    }

    @Test
    @DisplayName("配置读取验证")
    void configurationReading() {
        // Given: 不同的配置值
        given(deviceConfigPort.isSpringEventAsync()).willReturn(true, false, true);

        // When: 多次发布事件，每次检查配置
        deviceStatusEventPublisher.publishStatusEvent(sampleEvent);
        deviceStatusEventPublisher.publishStatusEvent(sampleEvent);
        deviceStatusEventPublisher.publishStatusEvent(sampleEvent);

        // Then: 验证配置被正确读取
        then(deviceConfigPort).should(times(6)).isSpringEventAsync();
    }

    @Test
    @DisplayName("混合发布模式测试")
    void mixedPublishMode() {
        // Given: 单个事件和批量事件
        List<DeviceStatusEvent> eventList = Collections.singletonList(sampleEvent);
        given(deviceConfigPort.isSpringEventAsync()).willReturn(false);

        // When: 同时进行单个发布和批量发布
        deviceStatusEventPublisher.publishStatusEvent(sampleEvent);
        deviceStatusEventPublisher.batchPublishStatusEvents(eventList);

        // Then: 验证事件被正确发布
        then(applicationEventPublisher).should(times(2)).publishEvent(sampleEvent);
        then(deviceConfigPort).should(times(4)).isSpringEventAsync();
    }
}