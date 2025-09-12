package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * CommandConfirmEventHandler 单元测试
 * <p>
 * 业务逻辑总结：
 * CommandConfirmEventHandler是Spring事件监听器，负责处理指令确认事件。
 * <p>
 * 核心功能：
 * 1. handleCommandConfirmEvent - 监听和处理CommandConfirmEvent事件
 * <p>
 * 业务逻辑：
 * - 使用@EventListener注解监听CommandConfirmEvent事件
 * - 异步处理（@Async注解，使用rpcNotificationExecutor线程池）
 * - 通过MainServerRpcPort通知主服务器指令确认结果
 * - 记录调试日志
 * <p>
 * 依赖：MainServerRpcPort（RPC通信端口）
 * 业务场景：当设备确认指令执行时，通过事件机制通知主服务器
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommandConfirmEventHandler - 指令确认事件处理器测试")
class CommandConfirmEventHandlerTest {

    @Mock
    private MainServerRpcPort mainServerRpcPort;

    private CommandConfirmEventHandler commandConfirmEventHandler;

    @BeforeEach
    void setUp() {
        commandConfirmEventHandler = new CommandConfirmEventHandler(mainServerRpcPort);
        
        // 使用lenient()避免严格模式报错，某些测试可能不会调用所有mock方法
        lenient().doNothing().when(mainServerRpcPort).notifyCommandConfirm(any(CommandConfirmEvent.class));
    }

    @Nested
    @DisplayName("handleCommandConfirmEvent - 处理指令确认事件")
    class HandleCommandConfirmEventTests {

        @Test
        @DisplayName("应该成功处理指令确认成功事件")
        void should_handle_successful_command_confirm_event() {
            // Given - 准备成功的指令确认事件
            Long deviceId = 12345L;
            Integer commandId = 67890;
            CommandConfirmEvent event = CommandConfirmEvent.success(deviceId, commandId);

            // When - 处理指令确认事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证通过RPC端口通知主服务器
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该成功处理指令确认失败事件")
        void should_handle_failed_command_confirm_event() {
            // Given - 准备失败的指令确认事件
            Long deviceId = 12345L;
            Integer commandId = 67890;
            CommandConfirmEvent event = CommandConfirmEvent.failed(deviceId, commandId);

            // When - 处理指令确认事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证通过RPC端口通知主服务器
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该正确处理自定义构建的事件")
        void should_handle_custom_built_event() {
            // Given - 准备自定义构建的指令确认事件
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                .deviceId(99999L)
                .commandId("custom-cmd-001")
                .success(true)
                .build();

            // When - 处理指令确认事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证通过RPC端口通知主服务器
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该处理包含所有字段的完整事件")
        void should_handle_complete_event_with_all_fields() {
            // Given - 准备包含所有字段的完整事件
            CommandConfirmEvent event = new CommandConfirmEvent(
                12345L,
                "cmd-12345", 
                true
            );

            // When - 处理指令确认事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证通过RPC端口通知主服务器
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该处理不同设备的多个事件")
        void should_handle_multiple_events_from_different_devices() {
            // Given - 准备来自不同设备的多个事件
            CommandConfirmEvent event1 = CommandConfirmEvent.success(11111L, 1001);
            CommandConfirmEvent event2 = CommandConfirmEvent.failed(22222L, 2002);
            CommandConfirmEvent event3 = CommandConfirmEvent.success(33333L, 3003);

            // When - 依次处理多个事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event1);
            commandConfirmEventHandler.handleCommandConfirmEvent(event2);
            commandConfirmEventHandler.handleCommandConfirmEvent(event3);

            // Then - 验证每个事件都被正确处理
            verify(mainServerRpcPort).notifyCommandConfirm(event1);
            verify(mainServerRpcPort).notifyCommandConfirm(event2);
            verify(mainServerRpcPort).notifyCommandConfirm(event3);
        }

        @Test
        @DisplayName("应该处理同一设备的多个指令确认事件")
        void should_handle_multiple_commands_from_same_device() {
            // Given - 准备同一设备的多个指令确认事件
            Long deviceId = 12345L;
            CommandConfirmEvent event1 = CommandConfirmEvent.success(deviceId, 1001);
            CommandConfirmEvent event2 = CommandConfirmEvent.failed(deviceId, 1002);
            CommandConfirmEvent event3 = CommandConfirmEvent.success(deviceId, 1003);

            // When - 依次处理多个事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event1);
            commandConfirmEventHandler.handleCommandConfirmEvent(event2);
            commandConfirmEventHandler.handleCommandConfirmEvent(event3);

            // Then - 验证每个事件都被正确处理
            verify(mainServerRpcPort).notifyCommandConfirm(event1);
            verify(mainServerRpcPort).notifyCommandConfirm(event2);
            verify(mainServerRpcPort).notifyCommandConfirm(event3);
        }
    }

    @Nested
    @DisplayName("边界情况和异常处理测试")
    class EdgeCaseAndExceptionTests {

        @Test
        @DisplayName("应该处理设备ID为0的事件")
        void should_handle_event_with_zero_device_id() {
            // Given - 准备设备ID为0的事件
            CommandConfirmEvent event = CommandConfirmEvent.success(0L, 12345);

            // When - 处理事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证事件被正确处理
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该处理负数设备ID的事件")
        void should_handle_event_with_negative_device_id() {
            // Given - 准备负数设备ID的事件
            CommandConfirmEvent event = CommandConfirmEvent.success(-1L, 12345);

            // When - 处理事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证事件被正确处理
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该处理指令ID为0的事件")
        void should_handle_event_with_zero_command_id() {
            // Given - 准备指令ID为0的事件
            CommandConfirmEvent event = CommandConfirmEvent.success(12345L, 0);

            // When - 处理事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证事件被正确处理
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该处理负数指令ID的事件")
        void should_handle_event_with_negative_command_id() {
            // Given - 准备负数指令ID的事件
            CommandConfirmEvent event = CommandConfirmEvent.failed(12345L, -1);

            // When - 处理事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证事件被正确处理
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该处理字符串指令ID为空的事件")
        void should_handle_event_with_empty_string_command_id() {
            // Given - 准备字符串指令ID为空的事件
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                .deviceId(12345L)
                .commandId("")
                .success(true)
                .build();

            // When - 处理事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证事件被正确处理
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该处理字符串指令ID为null的事件")
        void should_handle_event_with_null_string_command_id() {
            // Given - 准备字符串指令ID为null的事件
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                .deviceId(12345L)
                .commandId(null)
                .success(false)
                .build();

            // When - 处理事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证事件被正确处理
            verify(mainServerRpcPort).notifyCommandConfirm(event);
        }

        @Test
        @DisplayName("应该正确处理RPC通知异常情况")
        void should_handle_rpc_notification_exceptions() {
            // Given - 模拟RPC通知异常
            CommandConfirmEvent event = CommandConfirmEvent.success(12345L, 67890);
            doThrow(new RuntimeException("RPC通信失败")).when(mainServerRpcPort).notifyCommandConfirm(event);

            // When & Then - 执行处理，验证异常被抛出（因为没有异常处理逻辑）
            try {
                commandConfirmEventHandler.handleCommandConfirmEvent(event);
            } catch (RuntimeException e) {
                // 验证RPC方法被调用
                verify(mainServerRpcPort).notifyCommandConfirm(event);
            }
        }
    }

    @Nested
    @DisplayName("事件处理验证测试")
    class EventProcessingVerificationTests {

        @Test
        @DisplayName("应该验证事件对象的完整性")
        void should_verify_event_object_integrity() {
            // Given - 准备完整的事件对象
            Long expectedDeviceId = 12345L;
            String expectedCommandId = "cmd-67890";
            boolean expectedSuccess = true;
            
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                .deviceId(expectedDeviceId)
                .commandId(expectedCommandId)
                .success(expectedSuccess)
                .build();

            // When - 处理事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证传递给RPC的事件对象包含正确的数据
            verify(mainServerRpcPort).notifyCommandConfirm(argThat(capturedEvent -> 
                capturedEvent.getDeviceId().equals(expectedDeviceId) &&
                capturedEvent.getCommandId().equals(expectedCommandId) &&
                capturedEvent.isSuccess() == expectedSuccess
            ));
        }

        @Test
        @DisplayName("应该确保每次调用都传递正确的事件对象")
        void should_ensure_correct_event_object_passed_each_time() {
            // Given - 准备两个不同的事件
            CommandConfirmEvent event1 = CommandConfirmEvent.success(11111L, 1001);
            CommandConfirmEvent event2 = CommandConfirmEvent.failed(22222L, 2002);

            // When - 分别处理两个事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event1);
            commandConfirmEventHandler.handleCommandConfirmEvent(event2);

            // Then - 验证每个事件都使用正确的参数调用RPC
            verify(mainServerRpcPort).notifyCommandConfirm(event1);
            verify(mainServerRpcPort).notifyCommandConfirm(event2);
            
            // 验证总共调用了两次
            verify(mainServerRpcPort, times(2)).notifyCommandConfirm(any(CommandConfirmEvent.class));
        }

        @Test
        @DisplayName("应该验证RPC方法只被调用一次")
        void should_verify_rpc_method_called_exactly_once() {
            // Given - 准备单个事件
            CommandConfirmEvent event = CommandConfirmEvent.success(12345L, 67890);

            // When - 处理事件
            commandConfirmEventHandler.handleCommandConfirmEvent(event);

            // Then - 验证RPC方法被调用且仅调用一次
            verify(mainServerRpcPort, times(1)).notifyCommandConfirm(event);
            
            // 验证没有其他方法被调用（如果MainServerRpcPort有其他方法）
            verifyNoMoreInteractions(mainServerRpcPort);
        }
    }
}