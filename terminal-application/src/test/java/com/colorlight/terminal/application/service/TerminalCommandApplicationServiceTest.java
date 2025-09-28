package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.application.dto.result.CommandFetchResult;
import com.colorlight.terminal.application.dto.result.CommandSendResult;
import com.colorlight.terminal.application.port.outbound.command.CommandCachePort;
import com.colorlight.terminal.application.port.outbound.command.CommandWebSocketPort;
import com.colorlight.terminal.application.port.outbound.config.CommandConfigPort;
import com.colorlight.terminal.application.port.outbound.generator.CommandIdGeneratorPort;
import com.colorlight.terminal.application.port.outbound.status.CommandConfirmEventPort;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 终端指令应用服务单元测试
 * 
 * <p>测试策略：</p>
 * <ul>
 *   <li>验证指令下发的完整流程</li>
 *   <li>测试WebSocket和缓存的降级策略</li>
 *   <li>验证指令生命周期管理</li>
 *   <li>测试事件发布机制</li>
 *   <li>验证异常处理和边界条件</li>
 * </ul>
 * 
 * @author Nan
 */
@DisplayName("终端指令管理服务测试")
class TerminalCommandApplicationServiceTest extends BaseApplicationServiceTest {
    
    @Mock
    private CommandWebSocketPort commandWebSocketPort;
    
    @Mock
    private CommandCachePort commandCachePort;
    
    @Mock
    private CommandIdGeneratorPort commandIdGeneratorPort;
    
    @Mock
    private CommandConfigPort commandConfigPort;
    
    @Mock
    private CommandConfirmEventPort commandConfirmEventPort;
    
    @InjectMocks
    private TerminalCommandApplicationService service;
    
    @Captor
    private ArgumentCaptor<TerminalCommand> commandCaptor;
    
    @Captor
    private ArgumentCaptor<CommandConfirmEvent> eventCaptor;
    
    // 测试常量
    private static final Integer TEST_COMMAND_ID = 12345;
    private static final String TEST_AUTHOR_URL = "api/brightness";
    private static final String TEST_CONTENT_RAW = "{\"brightness\":80}";
    private static final Integer TEST_KARMA = 1; // POST方式
    private static final Long TEST_EXPIRE_HOURS = 24L;
    private static final String TEST_RESULT = "success";
    
    @BeforeEach
    void setUp() {
        // 配置默认Mock行为
        lenient().when(commandIdGeneratorPort.generateCommandId()).thenReturn(TEST_COMMAND_ID);
        lenient().when(commandConfigPort.getCommandExpireHours()).thenReturn(TEST_EXPIRE_HOURS);
    }
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        /**
         * 创建标准的指令发送请求
         */
        static SendCommandRequest createStandardRequest() {
            return SendCommandRequest.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .authorUrl(TEST_AUTHOR_URL)
                    .contentRaw(TEST_CONTENT_RAW)
                    .karma(TEST_KARMA)
                    .build();
        }
        
        /**
         * 创建自定义的指令发送请求
         */
        static SendCommandRequest createCustomRequest(Long deviceId, String authorUrl) {
            return SendCommandRequest.builder()
                    .deviceId(deviceId)
                    .authorUrl(authorUrl)
                    .contentRaw(TEST_CONTENT_RAW)
                    .karma(TEST_KARMA)
                    .build();
        }
        
        /**
         * 创建期望的终端指令对象
         */
        static TerminalCommand createExpectedCommand() {
            LocalDateTime now = LocalDateTime.now();
            return TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID)
                    .deviceId(TEST_DEVICE_ID)
                    .authorUrl(TEST_AUTHOR_URL)
                    .contentRaw(TEST_CONTENT_RAW)
                    .karma(TEST_KARMA)
                    .createTime(now)
                    .expireTime(now.plusHours(TEST_EXPIRE_HOURS))
                    .status(TerminalCommand.CommandStatus.PENDING)
                    .build();
        }
        
        /**
         * 创建待执行指令列表
         */
        static List<TerminalCommand> createPendingCommandsList() {
            return List.of(
                createExpectedCommand(),
                TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID + 1)
                    .deviceId(TEST_DEVICE_ID)
                    .authorUrl("api/volume")
                    .contentRaw("{\"volume\":50}")
                    .karma(1)
                    .createTime(LocalDateTime.now())
                    .expireTime(LocalDateTime.now().plusHours(TEST_EXPIRE_HOURS))
                    .status(TerminalCommand.CommandStatus.PENDING)
                    .build()
            );
        }
    }
    
    @Nested
    @DisplayName("指令下发测试")
    class SendCommandToDeviceTests {
        
        @Test
        @DisplayName("应该成功下发指令 - 设备在线，WebSocket下发成功")
        void should_send_command_successfully_via_websocket_when_device_online() {
            // Given - 设备在线，WebSocket下发成功
            SendCommandRequest request = TestDataBuilder.createStandardRequest();
            when(commandCachePort.saveCommand(any(TerminalCommand.class))).thenReturn(true);
            when(commandWebSocketPort.isDeviceOnline(TEST_DEVICE_ID)).thenReturn(true);
            when(commandWebSocketPort.sendCommandViaWebSocket(any(TerminalCommand.class))).thenReturn(true);
            
            // When - 执行指令下发
            CommandSendResult result = service.sendCommandToDevice(request);
            
            // Then - 验证结果
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCommandId()).isEqualTo(TEST_COMMAND_ID.toString());
            assertThat(result.getSendMethod()).isEqualTo(CommandSendResult.SendMethod.WEBSOCKET);
            assertThat(result.getMessage()).isEqualTo("指令已缓存并实时下发");
            
            // 验证调用顺序和参数
            var inOrder = inOrder(commandCachePort, commandWebSocketPort);
            inOrder.verify(commandCachePort).saveCommand(commandCaptor.capture());
            inOrder.verify(commandWebSocketPort).isDeviceOnline(TEST_DEVICE_ID);
            inOrder.verify(commandWebSocketPort).sendCommandViaWebSocket(commandCaptor.capture());
            
            // 验证创建的指令对象
            TerminalCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.getCommandId()).isEqualTo(TEST_COMMAND_ID);
            assertThat(capturedCommand.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            assertThat(capturedCommand.getAuthorUrl()).isEqualTo(TEST_AUTHOR_URL);
            assertThat(capturedCommand.getContentRaw()).isEqualTo(TEST_CONTENT_RAW);
            assertThat(capturedCommand.getKarma()).isEqualTo(TEST_KARMA);
            assertThat(capturedCommand.getStatus()).isEqualTo(TerminalCommand.CommandStatus.PENDING);
        }
        
        @Test
        @DisplayName("应该成功下发指令 - 设备在线，WebSocket下发失败，回退到缓存")
        void should_fallback_to_cache_when_websocket_fails() {
            // Given - 设备在线，但WebSocket下发失败
            SendCommandRequest request = TestDataBuilder.createStandardRequest();
            when(commandCachePort.saveCommand(any(TerminalCommand.class))).thenReturn(true);
            when(commandWebSocketPort.isDeviceOnline(TEST_DEVICE_ID)).thenReturn(true);
            when(commandWebSocketPort.sendCommandViaWebSocket(any(TerminalCommand.class))).thenReturn(false);
            
            // When - 执行指令下发
            CommandSendResult result = service.sendCommandToDevice(request);
            
            // Then - 验证结果
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCommandId()).isEqualTo(TEST_COMMAND_ID.toString());
            assertThat(result.getSendMethod()).isEqualTo(CommandSendResult.SendMethod.REDIS_CACHE);
            assertThat(result.getMessage()).isEqualTo("指令已缓存，WebSocket下发失败，等待轮询");
            
            // 验证WebSocket尝试过但失败
            verify(commandWebSocketPort).sendCommandViaWebSocket(any(TerminalCommand.class));
        }
        
        @Test
        @DisplayName("应该成功下发指令 - 设备离线，仅缓存等待轮询")
        void should_cache_only_when_device_offline() {
            // Given - 设备离线
            SendCommandRequest request = TestDataBuilder.createStandardRequest();
            when(commandCachePort.saveCommand(any(TerminalCommand.class))).thenReturn(true);
            when(commandWebSocketPort.isDeviceOnline(TEST_DEVICE_ID)).thenReturn(false);
            
            // When - 执行指令下发
            CommandSendResult result = service.sendCommandToDevice(request);
            
            // Then - 验证结果
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCommandId()).isEqualTo(TEST_COMMAND_ID.toString());
            assertThat(result.getSendMethod()).isEqualTo(CommandSendResult.SendMethod.REDIS_CACHE);
            assertThat(result.getMessage()).isEqualTo("指令已缓存，等待设备轮询");
            
            // 验证没有尝试WebSocket下发
            verify(commandWebSocketPort, never()).sendCommandViaWebSocket(any());
        }
        
        @Test
        @DisplayName("应该在指令缓存失败时返回失败结果")
        void should_return_failure_when_cache_fails() {
            // Given - 指令缓存失败
            SendCommandRequest request = TestDataBuilder.createStandardRequest();
            when(commandCachePort.saveCommand(any(TerminalCommand.class))).thenReturn(false);
            
            // When - 执行指令下发
            CommandSendResult result = service.sendCommandToDevice(request);
            
            // Then - 验证失败结果
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getSendMethod()).isEqualTo(CommandSendResult.SendMethod.FAILED);
            assertThat(result.getErrorCode()).isEqualTo("TM0006");
            assertThat(result.getMessage()).isEqualTo("指令缓存失败");
            
            // 验证没有进行后续操作
            verify(commandWebSocketPort, never()).isDeviceOnline(any());
            verify(commandWebSocketPort, never()).sendCommandViaWebSocket(any());
        }
        
        @Test
        @DisplayName("应该在发生异常时返回系统错误")
        void should_return_system_error_when_exception_occurs() {
            // Given - 创建指令时发生异常
            SendCommandRequest request = TestDataBuilder.createStandardRequest();
            when(commandIdGeneratorPort.generateCommandId()).thenThrow(new RuntimeException("ID生成服务异常"));
            
            // When - 执行指令下发
            CommandSendResult result = service.sendCommandToDevice(request);
            
            // Then - 验证系统错误结果
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getSendMethod()).isEqualTo(CommandSendResult.SendMethod.FAILED);
            assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode());
            assertThat(result.getMessage()).contains("系统内部错误");
            assertThat(result.getMessage()).contains("ID生成服务异常");
        }
        
        @Test
        @DisplayName("应该正确处理指令创建参数")
        void should_handle_command_creation_parameters_correctly() {
            // Given - 自定义参数的请求
            Long customDeviceId = 99999L;
            String customAuthorUrl = "api/custom";
            SendCommandRequest request = TestDataBuilder.createCustomRequest(customDeviceId, customAuthorUrl);
            when(commandCachePort.saveCommand(any(TerminalCommand.class))).thenReturn(true);
            when(commandWebSocketPort.isDeviceOnline(customDeviceId)).thenReturn(false);
            
            // When - 执行指令下发
            service.sendCommandToDevice(request);
            
            // Then - 验证创建的指令对象参数
            verify(commandCachePort).saveCommand(commandCaptor.capture());
            TerminalCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.getDeviceId()).isEqualTo(customDeviceId);
            assertThat(capturedCommand.getAuthorUrl()).isEqualTo(customAuthorUrl);
            
            // 验证配置服务调用
            verify(commandIdGeneratorPort).generateCommandId();
            verify(commandConfigPort).getCommandExpireHours();
        }
    }
    
    @Nested
    @DisplayName("获取待执行指令测试")
    class GetPendingCommandsTests {

        @Test
        @DisplayName("应该成功返回待执行指令并清理过期指令（使用整合方法）")
        void should_return_pending_commands_with_integrated_cleanup() {
            // Given - 有待执行指令和过期指令
            List<TerminalCommand> expectedCommands = TestDataBuilder.createPendingCommandsList();
            CommandFetchResult fetchResult = CommandFetchResult.success(
                    expectedCommands, 3, 8, 150L, true);
            when(commandCachePort.getPendingCommandsWithCleanup(TEST_DEVICE_ID)).thenReturn(fetchResult);

            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);

            // Then - 验证结果
            assertThat(result).isEqualTo(expectedCommands);

            // 验证调用了整合方法
            verify(commandCachePort).getPendingCommandsWithCleanup(TEST_DEVICE_ID);
            // 验证不再调用旧的分步方法
            verify(commandCachePort, never()).cleanExpiredCommands(any());
            verify(commandCachePort, never()).getPendingCommands(any());
        }

        @Test
        @DisplayName("应该在无待执行指令时返回空列表")
        void should_return_empty_list_when_no_pending_commands() {
            // Given - 无待执行指令但有清理统计
            CommandFetchResult fetchResult = CommandFetchResult.success(
                    Collections.emptyList(), 2, 2, 80L, true);
            when(commandCachePort.getPendingCommandsWithCleanup(TEST_DEVICE_ID)).thenReturn(fetchResult);

            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);

            // Then - 验证空列表
            assertThat(result).isEmpty();
            verify(commandCachePort).getPendingCommandsWithCleanup(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("应该在整合方法异常时返回空列表")
        void should_return_empty_list_when_integrated_method_fails() {
            // Given - 整合方法异常
            when(commandCachePort.getPendingCommandsWithCleanup(TEST_DEVICE_ID))
                    .thenThrow(new RuntimeException("整合方法异常"));

            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);

            // Then - 验证返回空列表（因为整个方法会捕获异常）
            assertThat(result).isEmpty();
            verify(commandCachePort).getPendingCommandsWithCleanup(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("应该记录性能和清理统计")
        void should_log_performance_and_cleanup_statistics() {
            // Given - 有清理统计的结果
            List<TerminalCommand> commands = TestDataBuilder.createPendingCommandsList();
            CommandFetchResult fetchResult = CommandFetchResult.success(
                    commands, 5, 10, 200L, true);
            when(commandCachePort.getPendingCommandsWithCleanup(TEST_DEVICE_ID)).thenReturn(fetchResult);

            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);

            // Then - 验证结果和调用
            assertThat(result).isEqualTo(commands);
            verify(commandCachePort).getPendingCommandsWithCleanup(TEST_DEVICE_ID);
            // 注意：日志验证通常通过日志框架的测试工具完成，这里主要验证业务逻辑
        }

        @Test
        @DisplayName("应该处理批量优化降级的情况")
        void should_handle_batch_optimization_fallback() {
            // Given - 使用降级方案的结果
            List<TerminalCommand> commands = TestDataBuilder.createPendingCommandsList();
            CommandFetchResult fetchResult = CommandFetchResult.success(
                    commands, 1, 3, 300L, false); // 未使用批量优化
            when(commandCachePort.getPendingCommandsWithCleanup(TEST_DEVICE_ID)).thenReturn(fetchResult);

            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);

            // Then - 验证结果正确
            assertThat(result).isEqualTo(commands);
            verify(commandCachePort).getPendingCommandsWithCleanup(TEST_DEVICE_ID);
        }
    }
    
    @Nested
    @DisplayName("指令执行确认测试")
    class ConfirmCommandExecutionTests {
        
        @Test
        @DisplayName("应该成功确认指令执行并发布成功事件")
        void should_confirm_command_execution_successfully() {
            // Given - 指令确认成功，缓存移除成功
            when(commandCachePort.removeCommand(TEST_DEVICE_ID, TEST_COMMAND_ID)).thenReturn(true);
            
            // When - 确认指令执行
            service.confirmCommandExecution(TEST_DEVICE_ID, TEST_COMMAND_ID, TEST_RESULT);
            
            // Then - 验证缓存移除和事件发布
            verify(commandCachePort).removeCommand(TEST_DEVICE_ID, TEST_COMMAND_ID);
            verify(commandConfirmEventPort).publishCommandConfirmEvent(eventCaptor.capture());
            
            CommandConfirmEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            assertThat(capturedEvent.getCommandId()).isEqualTo(TEST_COMMAND_ID.toString());
            assertThat(capturedEvent.isSuccess()).isTrue();
        }
        
        @Test
        @DisplayName("应该在缓存移除失败时仍发布成功事件")
        void should_publish_success_event_even_when_cache_removal_fails() {
            // Given - 指令确认成功，但缓存移除失败
            when(commandCachePort.removeCommand(TEST_DEVICE_ID, TEST_COMMAND_ID)).thenReturn(false);
            
            // When - 确认指令执行
            service.confirmCommandExecution(TEST_DEVICE_ID, TEST_COMMAND_ID, TEST_RESULT);
            
            // Then - 验证仍发布成功事件
            verify(commandConfirmEventPort).publishCommandConfirmEvent(eventCaptor.capture());
            
            CommandConfirmEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.isSuccess()).isTrue();
        }
        
        @Test
        @DisplayName("应该在确认过程异常时发布失败事件")
        void should_publish_failure_event_when_confirmation_fails() {
            // Given - 确认过程发生异常
            when(commandCachePort.removeCommand(TEST_DEVICE_ID, TEST_COMMAND_ID))
                    .thenThrow(new RuntimeException("缓存移除异常"));
            
            // When - 确认指令执行
            service.confirmCommandExecution(TEST_DEVICE_ID, TEST_COMMAND_ID, TEST_RESULT);
            
            // Then - 验证发布失败事件
            verify(commandConfirmEventPort).publishCommandConfirmEvent(eventCaptor.capture());
            
            CommandConfirmEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            assertThat(capturedEvent.getCommandId()).isEqualTo(TEST_COMMAND_ID.toString());
            assertThat(capturedEvent.isSuccess()).isFalse();
        }
    }
}