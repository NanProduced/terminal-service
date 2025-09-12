package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.SystemCommandEvent;
import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.BDDMockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemCommandPublisher单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：发布系统内部指令事件到Spring事件系统
 * 2. 时区信息请求：请求设备上报时间和时区配置信息
 * 3. 指令构造：使用SystemCommandHelper生成标准化的指令请求
 * 4. 事件发布：将构造的SystemCommandEvent发布到事件总线
 * 5. 日志记录：记录指令事件发布的信息
 * <p>
 * 测试策略：
 * - 时区信息请求功能测试
 * - 事件构造和发布验证
 * - 指令内容正确性验证
 * - 日志记录验证
 * - 异常处理测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemCommandPublisher单元测试")
class SystemCommandPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    
    @InjectMocks
    private SystemCommandPublisher systemCommandPublisher;
    
    private Long sampleDeviceId;
    private String sampleTrigger;

    @BeforeEach
    void setUp() {
        sampleDeviceId = 12345L;
        sampleTrigger = "timeZoneService";
    }

    @Test
    @DisplayName("请求时区信息上报 - 成功场景")
    void requestTimeZoneReport_Success() {
        // When: 请求时区信息上报
        assertDoesNotThrow(() -> systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, sampleTrigger));

        // Then: 验证事件被发布
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        
        SystemCommandEvent publishedEvent = eventCaptor.getValue();
        assertNotNull(publishedEvent);
        assertEquals(sampleTrigger, publishedEvent.getTriggerBeanName());
        assertEquals(SystemCommandEvent.BusinessScene.DEVICE_META_DATA, publishedEvent.getBusinessScene());
        
        // 验证生成的指令内容
        SendCommandRequest command = publishedEvent.getCommand();
        assertNotNull(command);
        assertEquals(sampleDeviceId, command.getDeviceId());
        assertEquals("api/newrtc.json", command.getAuthorUrl());
        assertEquals("", command.getContentRaw());
        assertEquals(Integer.valueOf(0), command.getKarma()); // GET请求
    }

    @Test
    @DisplayName("请求时区信息上报 - 验证指令构造")
    void requestTimeZoneReport_VerifyCommandConstruction() {
        // When: 请求时区信息上报
        systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, sampleTrigger);

        // Then: 验证事件中的指令被正确构造
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        
        SystemCommandEvent event = eventCaptor.getValue();
        SendCommandRequest command = event.getCommand();
        
        // 验证指令的各个属性
        assertEquals(sampleDeviceId, command.getDeviceId());
        assertEquals("api/newrtc.json", command.getAuthorUrl());
        assertEquals("", command.getContentRaw());
        assertEquals(Integer.valueOf(0), command.getKarma());
    }

    @Test
    @DisplayName("请求时区信息上报 - 不同设备ID")
    void requestTimeZoneReport_DifferentDeviceIds() {
        // Given: 不同的设备ID
        Long deviceId1 = 11111L;
        Long deviceId2 = 22222L;
        Long deviceId3 = 33333L;
        String trigger = "batchTimeZoneService";

        // When: 为不同设备请求时区信息
        systemCommandPublisher.requestTimeZoneReport(deviceId1, trigger);
        systemCommandPublisher.requestTimeZoneReport(deviceId2, trigger);
        systemCommandPublisher.requestTimeZoneReport(deviceId3, trigger);

        // Then: 验证三个事件都被发布
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should(times(3)).publishEvent(eventCaptor.capture());
        
        // 验证每个事件的设备ID正确
        var capturedEvents = eventCaptor.getAllValues();
        assertEquals(3, capturedEvents.size());
        
        assertEquals(deviceId1, capturedEvents.get(0).getCommand().getDeviceId());
        assertEquals(deviceId2, capturedEvents.get(1).getCommand().getDeviceId());
        assertEquals(deviceId3, capturedEvents.get(2).getCommand().getDeviceId());
        
        // 验证所有事件的trigger都正确
        capturedEvents.forEach(event -> assertEquals(trigger, event.getTriggerBeanName()));
    }

    @Test
    @DisplayName("请求时区信息上报 - 不同触发器")
    void requestTimeZoneReport_DifferentTriggers() {
        // Given: 不同的触发器名称
        String trigger1 = "schedulerService";
        String trigger2 = "deviceHealthService"; 
        String trigger3 = "configurationService";

        // When: 使用不同触发器请求时区信息
        systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, trigger1);
        systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, trigger2);
        systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, trigger3);

        // Then: 验证三个事件都被发布且触发器正确
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should(times(3)).publishEvent(eventCaptor.capture());
        
        var capturedEvents = eventCaptor.getAllValues();
        assertEquals(trigger1, capturedEvents.get(0).getTriggerBeanName());
        assertEquals(trigger2, capturedEvents.get(1).getTriggerBeanName());
        assertEquals(trigger3, capturedEvents.get(2).getTriggerBeanName());
    }

    @Test
    @DisplayName("请求时区信息上报 - 事件发布异常")
    void requestTimeZoneReport_PublishException() {
        // Given: ApplicationEventPublisher抛出异常
        RuntimeException publishException = new RuntimeException("事件发布失败");
        willThrow(publishException).given(applicationEventPublisher).publishEvent(any(SystemCommandEvent.class));

        // When & Then: 验证异常被抛出（因为没有异常处理）
        assertThrows(RuntimeException.class, () -> 
                systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, sampleTrigger));
        
        // 验证尝试发布事件
        then(applicationEventPublisher).should().publishEvent(any(SystemCommandEvent.class));
    }

    @Test
    @DisplayName("请求时区信息上报 - 边界值设备ID")
    void requestTimeZoneReport_BoundaryDeviceIds() {
        // Given: 边界值设备ID
        Long minDeviceId = 1L;
        Long maxDeviceId = Long.MAX_VALUE;
        String trigger = "boundaryTestService";

        // When: 使用边界值设备ID
        systemCommandPublisher.requestTimeZoneReport(minDeviceId, trigger);
        systemCommandPublisher.requestTimeZoneReport(maxDeviceId, trigger);

        // Then: 验证两个事件都被正确发布
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should(times(2)).publishEvent(eventCaptor.capture());
        
        var capturedEvents = eventCaptor.getAllValues();
        assertEquals(minDeviceId, capturedEvents.get(0).getCommand().getDeviceId());
        assertEquals(maxDeviceId, capturedEvents.get(1).getCommand().getDeviceId());
    }

    @Test
    @DisplayName("请求时区信息上报 - 验证业务场景")
    void requestTimeZoneReport_VerifyBusinessScene() {
        // When: 请求时区信息上报
        systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, sampleTrigger);

        // Then: 验证业务场景设置正确
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        
        SystemCommandEvent event = eventCaptor.getValue();
        assertEquals(SystemCommandEvent.BusinessScene.DEVICE_META_DATA, event.getBusinessScene());
    }

    @Test
    @DisplayName("请求时区信息上报 - 特殊字符触发器")
    void requestTimeZoneReport_SpecialCharacterTrigger() {
        // Given: 包含特殊字符的触发器名称
        String specialTrigger = "time-zone_service.v1";

        // When: 使用特殊字符触发器
        systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, specialTrigger);

        // Then: 验证事件正确发布
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        
        SystemCommandEvent event = eventCaptor.getValue();
        assertEquals(specialTrigger, event.getTriggerBeanName());
    }

    @Test
    @DisplayName("请求时区信息上报 - 空触发器处理")
    void requestTimeZoneReport_EmptyTrigger() {
        // Given: 空字符串触发器
        String emptyTrigger = "";

        // When: 使用空触发器
        systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, emptyTrigger);

        // Then: 验证事件仍然被发布（业务层应该处理验证）
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        
        SystemCommandEvent event = eventCaptor.getValue();
        assertEquals(emptyTrigger, event.getTriggerBeanName());
    }

    @Test
    @DisplayName("请求时区信息上报 - 验证完整的事件结构")
    void requestTimeZoneReport_VerifyCompleteEventStructure() {
        // When: 请求时区信息上报
        systemCommandPublisher.requestTimeZoneReport(sampleDeviceId, sampleTrigger);

        // Then: 验证完整的事件结构
        ArgumentCaptor<SystemCommandEvent> eventCaptor = ArgumentCaptor.forClass(SystemCommandEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        
        SystemCommandEvent event = eventCaptor.getValue();
        
        // 验证事件的所有必要属性都被设置
        assertNotNull(event);
        assertNotNull(event.getTriggerBeanName());
        assertNotNull(event.getBusinessScene());
        assertNotNull(event.getCommand());
        
        // 验证指令的所有必要属性都被设置
        SendCommandRequest command = event.getCommand();
        assertNotNull(command.getDeviceId());
        assertNotNull(command.getAuthorUrl());
        assertNotNull(command.getContentRaw());
        assertNotNull(command.getKarma());
    }
}