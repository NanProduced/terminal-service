package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.BDDMockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CommandConfirmEventPublisher单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：发布设备指令确认事件到Spring事件系统
 * 2. 异步处理：使用@Async("deviceEventExecutor")进行异步事件发布
 * 3. 异常处理：捕获发布异常并记录日志，不抛出异常
 * 4. 日志记录：记录事件发布的调试信息和错误信息
 * <p>
 * 测试策略：
 * - 成功发布事件场景
 * - 事件发布异常处理场景
 * - 不同类型的CommandConfirmEvent处理
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommandConfirmEventPublisher单元测试")
class CommandConfirmEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    
    @InjectMocks
    private CommandConfirmEventPublisher commandConfirmEventPublisher;
    
    private CommandConfirmEvent successEvent;
    private CommandConfirmEvent failedEvent;

    @BeforeEach
    void setUp() {
        successEvent = CommandConfirmEvent.success(12345L, 100);
        failedEvent = CommandConfirmEvent.failed(67890L, 200);
    }

    @Test
    @DisplayName("发布指令确认事件 - 成功场景")
    void publishCommandConfirmEvent_Success() {
        // When: 发布成功确认事件
        assertDoesNotThrow(() -> commandConfirmEventPublisher.publishCommandConfirmEvent(successEvent));

        // Then: 验证事件被发布
        then(applicationEventPublisher).should().publishEvent(successEvent);
    }

    @Test
    @DisplayName("发布指令确认事件 - 失败事件")
    void publishCommandConfirmEvent_FailedEvent() {
        // When: 发布失败确认事件
        assertDoesNotThrow(() -> commandConfirmEventPublisher.publishCommandConfirmEvent(failedEvent));

        // Then: 验证事件被发布
        then(applicationEventPublisher).should().publishEvent(failedEvent);
    }

    @Test
    @DisplayName("发布指令确认事件 - ApplicationEventPublisher异常")
    void publishCommandConfirmEvent_PublisherException() {
        // Given: ApplicationEventPublisher抛出异常
        RuntimeException publishException = new RuntimeException("事件发布失败");
        willThrow(publishException).given(applicationEventPublisher).publishEvent(successEvent);

        // When: 发布事件 - 应该捕获异常而不抛出
        assertDoesNotThrow(() -> commandConfirmEventPublisher.publishCommandConfirmEvent(successEvent));

        // Then: 验证尝试发布事件
        then(applicationEventPublisher).should().publishEvent(successEvent);
    }

    @Test
    @DisplayName("发布指令确认事件 - 验证事件内容")
    void publishCommandConfirmEvent_VerifyEventContent() {
        // Given: 创建具体内容的事件
        CommandConfirmEvent customEvent = CommandConfirmEvent.builder()
                .deviceId(99999L)
                .commandId("CUSTOM_CMD_123")
                .success(true)
                .build();

        // When: 发布事件
        commandConfirmEventPublisher.publishCommandConfirmEvent(customEvent);

        // Then: 验证事件被正确发布
        then(applicationEventPublisher).should().publishEvent(customEvent);
    }

    @Test
    @DisplayName("发布指令确认事件 - 批量发布不同事件")
    void publishCommandConfirmEvent_MultipleEvents() {
        // Given: 多个不同的事件
        CommandConfirmEvent event1 = CommandConfirmEvent.success(11111L, 301);
        CommandConfirmEvent event2 = CommandConfirmEvent.failed(22222L, 302);
        CommandConfirmEvent event3 = CommandConfirmEvent.builder()
                .deviceId(33333L)
                .commandId("BATCH_CMD")
                .success(false)
                .build();

        // When: 依次发布多个事件
        commandConfirmEventPublisher.publishCommandConfirmEvent(event1);
        commandConfirmEventPublisher.publishCommandConfirmEvent(event2);
        commandConfirmEventPublisher.publishCommandConfirmEvent(event3);

        // Then: 验证所有事件都被发布
        then(applicationEventPublisher).should().publishEvent(event1);
        then(applicationEventPublisher).should().publishEvent(event2);
        then(applicationEventPublisher).should().publishEvent(event3);
    }

    @Test
    @DisplayName("发布指令确认事件 - 静态工厂方法创建的事件")
    void publishCommandConfirmEvent_StaticFactoryMethods() {
        // Given: 使用静态工厂方法创建事件
        CommandConfirmEvent successFactoryEvent = CommandConfirmEvent.success(55555L, 500);
        CommandConfirmEvent failedFactoryEvent = CommandConfirmEvent.failed(66666L, 600);

        // When: 发布工厂方法创建的事件
        commandConfirmEventPublisher.publishCommandConfirmEvent(successFactoryEvent);
        commandConfirmEventPublisher.publishCommandConfirmEvent(failedFactoryEvent);

        // Then: 验证工厂方法创建的事件被正确发布
        then(applicationEventPublisher).should().publishEvent(successFactoryEvent);
        then(applicationEventPublisher).should().publishEvent(failedFactoryEvent);
        
        // 验证事件内容
        assertTrue(successFactoryEvent.isSuccess());
        assertFalse(failedFactoryEvent.isSuccess());
        assertEquals("500", successFactoryEvent.getCommandId());
        assertEquals("600", failedFactoryEvent.getCommandId());
    }
}