package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.SystemCommandEvent;
import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.application.dto.result.CommandSendResult;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;

/**
 * SystemCommandEventHandler单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：处理系统内部指令下发事件
 * 2. 异步处理：使用@Async("deviceEventExecutor")异步执行指令下发
 * 3. 指令下发：委托给TerminalCommandUseCase执行实际的指令发送
 * 4. 结果记录：记录指令下发的结果日志
 * 5. 事件监听：通过@EventListener监听SystemCommandEvent
 * <p>
 * 测试策略：
 * - 正常指令下发场景
 * - 不同业务场景的指令处理
 * - 指令下发成功和失败的结果处理
 * - UseCase异常处理测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemCommandEventHandler单元测试")
class SystemCommandEventHandlerTest {

    @Mock
    private TerminalCommandUseCase terminalCommandUseCase;
    
    @InjectMocks
    private SystemCommandEventHandler systemCommandEventHandler;
    
    private SendCommandRequest sampleCommand;
    private SystemCommandEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleCommand = SendCommandRequest.builder()
                .deviceId(12345L)
                .authorUrl("api/brightness")
                .contentRaw("{\"brightness\": 80}")
                .karma(1) // POST请求
                .build();
        
        sampleEvent = SystemCommandEvent.builder()
                .triggerBeanName("brightnessController")
                .command(sampleCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();
    }

    @Test
    @DisplayName("处理系统指令事件 - 指令发送成功")
    void handleSystemCommandEvent_Success() {
        // Given: 指令发送成功的结果
        CommandSendResult successResult = CommandSendResult.success(
                "CMD_12345", 
                CommandSendResult.SendMethod.WEBSOCKET, 
                "指令发送成功"
        );
        given(terminalCommandUseCase.sendCommandToDevice(sampleCommand)).willReturn(successResult);

        // When: 处理系统指令事件
        assertDoesNotThrow(() -> systemCommandEventHandler.handleSystemCommandEvent(sampleEvent));

        // Then: 验证指令发送被调用
        then(terminalCommandUseCase).should().sendCommandToDevice(sampleCommand);
    }

    @Test
    @DisplayName("处理系统指令事件 - 指令发送失败")
    void handleSystemCommandEvent_Failed() {
        // Given: 指令发送失败的结果
        CommandSendResult failedResult = CommandSendResult.failed("E001", "设备离线，指令发送失败");
        given(terminalCommandUseCase.sendCommandToDevice(sampleCommand)).willReturn(failedResult);

        // When: 处理系统指令事件
        assertDoesNotThrow(() -> systemCommandEventHandler.handleSystemCommandEvent(sampleEvent));

        // Then: 验证指令发送被调用
        then(terminalCommandUseCase).should().sendCommandToDevice(sampleCommand);
    }

    @Test
    @DisplayName("处理系统指令事件 - 不同下发方式的结果")
    void handleSystemCommandEvent_DifferentSendMethods() {
        // Given: 准备不同下发方式的指令和结果
        SendCommandRequest websocketCommand = SendCommandRequest.builder()
                .deviceId(11111L)
                .authorUrl("api/volume")
                .contentRaw("{\"volume\": 50}")
                .karma(1)
                .build();
        
        SendCommandRequest cacheCommand = SendCommandRequest.builder()
                .deviceId(22222L)
                .authorUrl("api/reboot")
                .contentRaw("{}")
                .karma(1)
                .build();

        SystemCommandEvent websocketEvent = SystemCommandEvent.builder()
                .triggerBeanName("volumeController")
                .command(websocketCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();

        SystemCommandEvent cacheEvent = SystemCommandEvent.builder()
                .triggerBeanName("rebootController")
                .command(cacheCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();

        // Mock不同的下发结果
        CommandSendResult websocketResult = CommandSendResult.success(
                "WS_CMD_001", 
                CommandSendResult.SendMethod.WEBSOCKET, 
                "WebSocket实时下发成功"
        );
        CommandSendResult cacheResult = CommandSendResult.success(
                "CACHE_CMD_002", 
                CommandSendResult.SendMethod.REDIS_CACHE, 
                "Redis缓存等待轮询"
        );

        given(terminalCommandUseCase.sendCommandToDevice(websocketCommand)).willReturn(websocketResult);
        given(terminalCommandUseCase.sendCommandToDevice(cacheCommand)).willReturn(cacheResult);

        // When: 处理不同的系统指令事件
        systemCommandEventHandler.handleSystemCommandEvent(websocketEvent);
        systemCommandEventHandler.handleSystemCommandEvent(cacheEvent);

        // Then: 验证两个指令都被发送
        then(terminalCommandUseCase).should().sendCommandToDevice(websocketCommand);
        then(terminalCommandUseCase).should().sendCommandToDevice(cacheCommand);
    }

    @Test
    @DisplayName("处理系统指令事件 - 不同HTTP方法的指令")
    void handleSystemCommandEvent_DifferentHttpMethods() {
        // Given: 不同HTTP方法的指令
        SendCommandRequest getCommand = SendCommandRequest.builder()
                .deviceId(33333L)
                .authorUrl("api/status")
                .contentRaw(null)
                .karma(0) // GET请求
                .build();

        SendCommandRequest putCommand = SendCommandRequest.builder()
                .deviceId(44444L)
                .authorUrl("api/config")
                .contentRaw("{\"config\": \"updated\"}")
                .karma(2) // PUT请求
                .build();

        SendCommandRequest deleteCommand = SendCommandRequest.builder()
                .deviceId(55555L)
                .authorUrl("api/cache")
                .contentRaw(null)
                .karma(3) // DELETE请求
                .build();

        SystemCommandEvent getEvent = SystemCommandEvent.builder()
                .triggerBeanName("statusController")
                .command(getCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();

        SystemCommandEvent putEvent = SystemCommandEvent.builder()
                .triggerBeanName("configController")
                .command(putCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();

        SystemCommandEvent deleteEvent = SystemCommandEvent.builder()
                .triggerBeanName("cacheController")
                .command(deleteCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();

        // Mock结果
        CommandSendResult getResult = CommandSendResult.success("GET_001", CommandSendResult.SendMethod.WEBSOCKET, "GET请求成功");
        CommandSendResult putResult = CommandSendResult.success("PUT_002", CommandSendResult.SendMethod.REDIS_CACHE, "PUT请求成功");
        CommandSendResult deleteResult = CommandSendResult.success("DEL_003", CommandSendResult.SendMethod.WEBSOCKET, "DELETE请求成功");

        given(terminalCommandUseCase.sendCommandToDevice(getCommand)).willReturn(getResult);
        given(terminalCommandUseCase.sendCommandToDevice(putCommand)).willReturn(putResult);
        given(terminalCommandUseCase.sendCommandToDevice(deleteCommand)).willReturn(deleteResult);

        // When: 处理不同HTTP方法的事件
        systemCommandEventHandler.handleSystemCommandEvent(getEvent);
        systemCommandEventHandler.handleSystemCommandEvent(putEvent);
        systemCommandEventHandler.handleSystemCommandEvent(deleteEvent);

        // Then: 验证所有指令都被发送
        then(terminalCommandUseCase).should().sendCommandToDevice(getCommand);
        then(terminalCommandUseCase).should().sendCommandToDevice(putCommand);
        then(terminalCommandUseCase).should().sendCommandToDevice(deleteCommand);
    }

    @Test
    @DisplayName("处理系统指令事件 - UseCase抛出异常")
    void handleSystemCommandEvent_UseCaseException() {
        // Given: UseCase抛出异常
        RuntimeException useCaseException = new RuntimeException("指令处理异常");
        given(terminalCommandUseCase.sendCommandToDevice(sampleCommand)).willThrow(useCaseException);

        // When: 处理系统指令事件
        // Note: 由于方法没有显式异常处理，异常会被抛出，但在异步环境中被框架处理
        assertThrows(RuntimeException.class, () -> systemCommandEventHandler.handleSystemCommandEvent(sampleEvent));

        // Then: 验证UseCase被调用
        then(terminalCommandUseCase).should().sendCommandToDevice(sampleCommand);
    }

    @Test
    @DisplayName("处理系统指令事件 - 复杂指令内容")
    void handleSystemCommandEvent_ComplexCommandContent() {
        // Given: 复杂的指令内容
        String complexContent = """
                {
                  "operation": "multi_config",
                  "parameters": {
                    "brightness": 75,
                    "volume": 60,
                    "schedule": {
                      "start": "08:00",
                      "end": "18:00"
                    }
                  }
                }""";

        SendCommandRequest complexCommand = SendCommandRequest.builder()
                .deviceId(99999L)
                .authorUrl("api/multi_config")
                .contentRaw(complexContent)
                .karma(1)
                .build();

        SystemCommandEvent complexEvent = SystemCommandEvent.builder()
                .triggerBeanName("multiConfigController")
                .command(complexCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();

        CommandSendResult complexResult = CommandSendResult.success(
                "COMPLEX_CMD_999", 
                CommandSendResult.SendMethod.WEBSOCKET, 
                "复杂配置指令下发成功"
        );
        given(terminalCommandUseCase.sendCommandToDevice(complexCommand)).willReturn(complexResult);

        // When: 处理复杂指令事件
        systemCommandEventHandler.handleSystemCommandEvent(complexEvent);

        // Then: 验证复杂指令被正确处理
        then(terminalCommandUseCase).should().sendCommandToDevice(complexCommand);
    }

    @Test
    @DisplayName("处理系统指令事件 - 空指令内容")
    void handleSystemCommandEvent_EmptyContent() {
        // Given: 空指令内容的指令（如重启指令）
        SendCommandRequest emptyContentCommand = SendCommandRequest.builder()
                .deviceId(66666L)
                .authorUrl("api/restart")
                .contentRaw("")
                .karma(1)
                .build();

        SystemCommandEvent emptyContentEvent = SystemCommandEvent.builder()
                .triggerBeanName("restartController")
                .command(emptyContentCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();

        CommandSendResult emptyContentResult = CommandSendResult.success(
                "RESTART_CMD", 
                CommandSendResult.SendMethod.WEBSOCKET, 
                "重启指令下发成功"
        );
        given(terminalCommandUseCase.sendCommandToDevice(emptyContentCommand)).willReturn(emptyContentResult);

        // When: 处理空内容指令事件
        systemCommandEventHandler.handleSystemCommandEvent(emptyContentEvent);

        // Then: 验证空内容指令被正确处理
        then(terminalCommandUseCase).should().sendCommandToDevice(emptyContentCommand);
    }

    @Test
    @DisplayName("处理系统指令事件 - 验证事件属性传递")
    void handleSystemCommandEvent_VerifyEventProperties() {
        // Given: 具有完整属性的事件
        SystemCommandEvent fullEvent = SystemCommandEvent.builder()
                .triggerBeanName("testController")
                .command(sampleCommand)
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .build();

        CommandSendResult result = CommandSendResult.success("TEST_CMD", CommandSendResult.SendMethod.WEBSOCKET, "测试成功");
        given(terminalCommandUseCase.sendCommandToDevice(sampleCommand)).willReturn(result);

        // When: 处理事件
        systemCommandEventHandler.handleSystemCommandEvent(fullEvent);

        // Then: 验证指令对象被正确传递到UseCase
        then(terminalCommandUseCase).should().sendCommandToDevice(sampleCommand);
        
        // 验证传递的指令包含正确的属性
        then(terminalCommandUseCase).should().sendCommandToDevice(argThat(command -> 
                command.getDeviceId().equals(12345L) &&
                command.getAuthorUrl().equals("api/brightness") &&
                command.getContentRaw().equals("{\"brightness\": 80}") &&
                command.getKarma().equals(1)
        ));
    }
}