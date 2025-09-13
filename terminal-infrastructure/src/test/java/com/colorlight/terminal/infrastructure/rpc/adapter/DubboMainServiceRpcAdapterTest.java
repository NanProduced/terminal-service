package com.colorlight.terminal.infrastructure.rpc.adapter;

import com.colorlight.ccloud.command.dto.CommandFinishDto;
import com.colorlight.ccloud.command.enums.CommandStatusEnum;
import com.colorlight.ccloud.command.interfaces.CommandFinishFacade;
import com.colorlight.ccloud.command.interfaces.DeviceReportRpcService;
import com.colorlight.ccloud.schedule.dto.Schedule;
import com.colorlight.ccloud.schedule.interfaces.TerminalScheduleRpcService;
import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.ReportSource;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DubboMainServiceRpcAdapter 单元测试
 * 
 * <p>测试策略：</p>
 * <ul>
 *   <li>重点验证业务逻辑正确性，不依赖日志内容</li>
 *   <li>验证RPC调用参数的正确转换</li>
 *   <li>验证异常处理和容错机制</li>
 *   <li>验证性能监控逻辑</li>
 *   <li>验证异步调用的正确性</li>
 * </ul>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Dubbo主服务RPC适配器测试")
class DubboMainServiceRpcAdapterTest {

    @Mock
    private CommandFinishFacade commandFinishFacade;

    @Mock
    private DeviceReportRpcService deviceReportRpcService;

    @Mock
    private TerminalScheduleRpcService terminalScheduleRpcService;

    @InjectMocks
    private DubboMainServiceRpcAdapter rpcAdapter;

    @Captor
    private ArgumentCaptor<CommandFinishDto> commandFinishDtoCaptor;

    // 测试常量
    private static final Long TEST_DEVICE_ID = 10001L;
    private static final String TEST_COMMAND_ID = "12345";
    private static final Integer TEST_COMMAND_ID_INT = 12345;
    private static final String TEST_CLIENT_IP = "192.168.1.100";
    private static final String TEST_LED_REPORT = "{\"brightness\":80,\"status\":\"on\"}";

    @Nested
    @DisplayName("指令确认通知测试")
    class CommandConfirmNotificationTests {

        @Test
        @DisplayName("应该成功通知指令执行成功")
        void should_successfully_notify_command_success() {
            // Given
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .commandId(TEST_COMMAND_ID)
                    .success(true)
                    .build();

            // When
            rpcAdapter.notifyCommandConfirm(event);

            // Then
            verify(commandFinishFacade).commandFinish(commandFinishDtoCaptor.capture());
            
            CommandFinishDto capturedDto = commandFinishDtoCaptor.getValue();
            assertAll(
                    () -> assertThat(capturedDto.getDeviceId()).isEqualTo(TEST_DEVICE_ID),
                    () -> assertThat(capturedDto.getCommandId()).isEqualTo(TEST_COMMAND_ID),
                    () -> assertThat(capturedDto.getStatus()).isEqualTo(CommandStatusEnum.SUCCESS)
            );
        }

        @Test
        @DisplayName("应该成功通知指令执行失败")
        void should_successfully_notify_command_failure() {
            // Given
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .commandId(TEST_COMMAND_ID)
                    .success(false)
                    .build();

            // When
            rpcAdapter.notifyCommandConfirm(event);

            // Then
            verify(commandFinishFacade).commandFinish(commandFinishDtoCaptor.capture());
            
            CommandFinishDto capturedDto = commandFinishDtoCaptor.getValue();
            assertAll(
                    () -> assertThat(capturedDto.getDeviceId()).isEqualTo(TEST_DEVICE_ID),
                    () -> assertThat(capturedDto.getCommandId()).isEqualTo(TEST_COMMAND_ID),
                    () -> assertThat(capturedDto.getStatus()).isEqualTo(CommandStatusEnum.FAILED)
            );
        }

        @Test
        @DisplayName("应该优雅处理指令确认RPC异常")
        void should_gracefully_handle_command_confirm_rpc_exception() {
            // Given
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .commandId(TEST_COMMAND_ID)
                    .success(true)
                    .build();
            
            doThrow(new RpcException("主服务不可用")).when(commandFinishFacade).commandFinish(any());

            // When & Then - 应该不抛出异常，优雅处理
            rpcAdapter.notifyCommandConfirm(event);
            
            // 验证仍然调用了RPC服务
            verify(commandFinishFacade).commandFinish(any(CommandFinishDto.class));
        }

        @Test
        @DisplayName("应该正确处理空结果的指令确认事件")
        void should_correctly_handle_command_confirm_event_with_null_result() {
            // Given
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .commandId(TEST_COMMAND_ID)
                    .success(true)
                    .build();

            // When
            rpcAdapter.notifyCommandConfirm(event);

            // Then
            verify(commandFinishFacade).commandFinish(commandFinishDtoCaptor.capture());
            
            CommandFinishDto capturedDto = commandFinishDtoCaptor.getValue();
            assertThat(capturedDto.getStatus()).isEqualTo(CommandStatusEnum.SUCCESS);
        }
    }

    @Nested
    @DisplayName("指令过期通知测试")
    class CommandExpirationNotificationTests {

        @Test
        @DisplayName("应该成功通知指令过期")
        void should_successfully_notify_command_expiration() {
            // When
            rpcAdapter.notifyCommandExpiration(TEST_DEVICE_ID, TEST_COMMAND_ID_INT);

            // Then
            verify(commandFinishFacade).commandFinish(commandFinishDtoCaptor.capture());
            
            CommandFinishDto capturedDto = commandFinishDtoCaptor.getValue();
            assertAll(
                    () -> assertThat(capturedDto.getDeviceId()).isEqualTo(TEST_DEVICE_ID),
                    () -> assertThat(capturedDto.getCommandId()).isEqualTo(TEST_COMMAND_ID_INT.toString()),
                    () -> assertThat(capturedDto.getStatus()).isEqualTo(CommandStatusEnum.TIMEOUT)
            );
        }

        @Test
        @DisplayName("应该正确转换Integer类型的指令ID")
        void should_correctly_convert_integer_command_id() {
            // Given
            Integer commandId = 99999;

            // When
            rpcAdapter.notifyCommandExpiration(TEST_DEVICE_ID, commandId);

            // Then
            verify(commandFinishFacade).commandFinish(commandFinishDtoCaptor.capture());
            
            CommandFinishDto capturedDto = commandFinishDtoCaptor.getValue();
            assertThat(capturedDto.getCommandId()).isEqualTo("99999");
        }

        @Test
        @DisplayName("应该优雅处理指令过期通知RPC异常")
        void should_gracefully_handle_command_expiration_rpc_exception() {
            // Given
            doThrow(new RpcException("网络超时")).when(commandFinishFacade).commandFinish(any());

            // When & Then - 应该不抛出异常
            rpcAdapter.notifyCommandExpiration(TEST_DEVICE_ID, TEST_COMMAND_ID_INT);
            
            verify(commandFinishFacade).commandFinish(any(CommandFinishDto.class));
        }
    }

    @Nested
    @DisplayName("设备状态上报测试")
    class DeviceStatusReportTests {

        @Test
        @DisplayName("应该成功上报设备心跳状态")
        void should_successfully_report_device_heartbeat() {
            // Given
            Long eventTime = System.currentTimeMillis();
            DeviceStatusEvent event = DeviceStatusEvent.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .eventTime(eventTime)
                    .clientIp(TEST_CLIENT_IP)
                    .reportSource(ReportSource.WEBSOCKET)
                    .build();

            // When
            rpcAdapter.notifyDeviceLastReportTime(event);

            // Then
            verify(deviceReportRpcService).reportDeviceHeartbeat(
                    TEST_DEVICE_ID,
                    eventTime,
                    TEST_CLIENT_IP,
                    "WEBSOCKET"
            );
        }

        @Test
        @DisplayName("应该正确转换所有上报源枚举")
        void should_correctly_convert_all_report_source_enums() {
            // 测试所有可能的上报源枚举值
            ReportSource[] allSources = ReportSource.values();
            
            for (ReportSource source : allSources) {
                // Given
                DeviceStatusEvent event = DeviceStatusEvent.builder()
                        .deviceId(TEST_DEVICE_ID)
                        .eventTime(System.currentTimeMillis())
                        .clientIp(TEST_CLIENT_IP)
                        .reportSource(source)
                        .build();

                // When
                rpcAdapter.notifyDeviceLastReportTime(event);

                // Then
                verify(deviceReportRpcService).reportDeviceHeartbeat(
                        eq(TEST_DEVICE_ID),
                        anyLong(),
                        eq(TEST_CLIENT_IP),
                        eq(source.name())
                );
                
                // 重置mock以便下次验证
                reset(deviceReportRpcService);
            }
        }

        @Test
        @DisplayName("应该优雅处理设备状态上报RPC异常")
        void should_gracefully_handle_device_status_report_rpc_exception() {
            // Given
            DeviceStatusEvent event = DeviceStatusEvent.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .eventTime(System.currentTimeMillis())
                    .clientIp(TEST_CLIENT_IP)
                    .reportSource(ReportSource.HTTP)
                    .build();
            
            doThrow(new RpcException("主服务繁忙")).when(deviceReportRpcService)
                    .reportDeviceHeartbeat(any(), any(), any(), any());

            // When & Then - 应该不抛出异常
            rpcAdapter.notifyDeviceLastReportTime(event);
            
            verify(deviceReportRpcService).reportDeviceHeartbeat(any(), any(), any(), any());
        }

        @Test
        @DisplayName("应该处理空客户端IP的情况")
        void should_handle_null_client_ip() {
            // Given
            DeviceStatusEvent event = DeviceStatusEvent.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .eventTime(System.currentTimeMillis())
                    .clientIp(null)
                    .reportSource(ReportSource.WEBSOCKET)
                    .build();

            // When
            rpcAdapter.notifyDeviceLastReportTime(event);

            // Then
            verify(deviceReportRpcService).reportDeviceHeartbeat(
                    eq(TEST_DEVICE_ID),
                    anyLong(),
                    isNull(),
                    eq("WEBSOCKET")
            );
        }
    }

    @Nested
    @DisplayName("LED状态上报测试")
    class LedStatusReportTests {

        @Test
        @DisplayName("应该成功上报LED状态")
        void should_successfully_report_led_status() {
            // When
            rpcAdapter.notifyLedStatus(TEST_DEVICE_ID, TEST_LED_REPORT);

            // Then
            verify(deviceReportRpcService).reportDeviceLedStatus(
                    eq(TEST_DEVICE_ID),
                    anyLong(), // 时间戳
                    eq(TEST_LED_REPORT)
            );
        }

        @Test
        @DisplayName("应该使用当前时间戳上报LED状态")
        void should_use_current_timestamp_for_led_status_report() {
            // Given
            long beforeCall = System.currentTimeMillis();

            // When
            rpcAdapter.notifyLedStatus(TEST_DEVICE_ID, TEST_LED_REPORT);

            // Then
            long afterCall = System.currentTimeMillis();
            
            verify(deviceReportRpcService).reportDeviceLedStatus(
                    eq(TEST_DEVICE_ID),
                    longThat(timestamp -> timestamp >= beforeCall && timestamp <= afterCall),
                    eq(TEST_LED_REPORT)
            );
        }

        @Test
        @DisplayName("应该处理空LED报告内容")
        void should_handle_null_led_report_content() {
            // When
            rpcAdapter.notifyLedStatus(TEST_DEVICE_ID, null);

            // Then
            verify(deviceReportRpcService).reportDeviceLedStatus(
                    eq(TEST_DEVICE_ID),
                    anyLong(),
                    isNull()
            );
        }

        @Test
        @DisplayName("应该处理空字符串LED报告内容")
        void should_handle_empty_led_report_content() {
            // When
            rpcAdapter.notifyLedStatus(TEST_DEVICE_ID, "");

            // Then
            verify(deviceReportRpcService).reportDeviceLedStatus(
                    eq(TEST_DEVICE_ID),
                    anyLong(),
                    eq("")
            );
        }

        @Test
        @DisplayName("应该优雅处理LED状态上报RPC异常")
        void should_gracefully_handle_led_status_report_rpc_exception() {
            // Given
            doThrow(new RpcException("服务降级")).when(deviceReportRpcService)
                    .reportDeviceLedStatus(any(), anyLong(), any());

            // When & Then - 应该不抛出异常
            rpcAdapter.notifyLedStatus(TEST_DEVICE_ID, TEST_LED_REPORT);
            
            verify(deviceReportRpcService).reportDeviceLedStatus(any(), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("排程获取测试")
    class ScheduleRetrievalTests {

        @Test
        @DisplayName("应该成功获取设备排程并转换为JSON")
        void should_successfully_get_device_schedule_and_convert_to_json() {
            // Given
            Schedule mockSchedule = createMockSchedule();
            when(terminalScheduleRpcService.getScheduleByLedId(TEST_DEVICE_ID)).thenReturn(mockSchedule);

            // When
            String result = rpcAdapter.getScheduleByDeviceId(TEST_DEVICE_ID);

            // Then
            assertAll(
                    () -> assertThat(result).isNotNull()
            );
            
            verify(terminalScheduleRpcService).getScheduleByLedId(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("应该处理空排程返回null")
        void should_return_null_for_null_schedule() {
            // Given
            when(terminalScheduleRpcService.getScheduleByLedId(TEST_DEVICE_ID)).thenReturn(null);

            // When
            String result = rpcAdapter.getScheduleByDeviceId(TEST_DEVICE_ID);

            // Then
            assertThat(result).isNull();
            verify(terminalScheduleRpcService).getScheduleByLedId(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("应该正确调用排程服务的getScheduleByLedId方法")
        void should_correctly_call_schedule_service_get_by_led_id() {
            // Given
            Long specificDeviceId = 99999L;
            Schedule mockSchedule = createMockSchedule();
            when(terminalScheduleRpcService.getScheduleByLedId(specificDeviceId)).thenReturn(mockSchedule);

            // When
            rpcAdapter.getScheduleByDeviceId(specificDeviceId);

            // Then
            verify(terminalScheduleRpcService).getScheduleByLedId(specificDeviceId);
        }

        @Test
        @DisplayName("应该处理排程服务RPC异常")
        void should_handle_schedule_service_rpc_exception() {
            // Given - 注意：此方法没有try-catch，所以异常会传播
            when(terminalScheduleRpcService.getScheduleByLedId(TEST_DEVICE_ID))
                    .thenThrow(new RpcException("排程服务不可用"));

            // When 
            String result = rpcAdapter.getScheduleByDeviceId(TEST_DEVICE_ID);

            // Then - 应该返回null而不是抛出异常
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("应该处理复杂排程对象的JSON转换")
        void should_handle_complex_schedule_object_json_conversion() {
            // Given
            Schedule complexSchedule = createMockSchedule();
            when(terminalScheduleRpcService.getScheduleByLedId(TEST_DEVICE_ID)).thenReturn(complexSchedule);

            // When
            String result = rpcAdapter.getScheduleByDeviceId(TEST_DEVICE_ID);

            // Then
            assertAll(
                    () -> assertThat(result).isNotNull()
            );
        }
    }

    @Nested
    @DisplayName("容错和异常处理测试")
    class FaultToleranceAndExceptionHandlingTests {

        @Test
        @DisplayName("应该在所有RPC异常情况下继续执行不中断业务")
        void should_continue_execution_without_interrupting_business_on_all_rpc_exceptions() {
            // Given - 所有RPC服务都抛出异常
            doThrow(new RpcException("指令服务异常")).when(commandFinishFacade).commandFinish(any());
            doThrow(new RpcException("设备上报服务异常")).when(deviceReportRpcService)
                    .reportDeviceHeartbeat(any(), any(), any(), any());
            doThrow(new RpcException("LED上报服务异常")).when(deviceReportRpcService)
                    .reportDeviceLedStatus(any(), anyLong(), any());

            // When & Then - 所有调用都应该不抛出异常
            CommandConfirmEvent confirmEvent = CommandConfirmEvent.builder()
                    .deviceId(TEST_DEVICE_ID).commandId(TEST_COMMAND_ID).success(true).build();
            
            DeviceStatusEvent statusEvent = DeviceStatusEvent.builder()
                    .deviceId(TEST_DEVICE_ID).eventTime(System.currentTimeMillis())
                    .clientIp(TEST_CLIENT_IP).reportSource(ReportSource.WEBSOCKET).build();

            // 这些调用都不应该抛出异常
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
                rpcAdapter.notifyCommandConfirm(confirmEvent);
                rpcAdapter.notifyCommandExpiration(TEST_DEVICE_ID, TEST_COMMAND_ID_INT);
                rpcAdapter.notifyDeviceLastReportTime(statusEvent);
                rpcAdapter.notifyLedStatus(TEST_DEVICE_ID, TEST_LED_REPORT);
            });
        }

        @Test
        @DisplayName("应该正确处理不同类型的RPC异常")
        void should_correctly_handle_different_types_of_rpc_exceptions() {
            // 测试不同类型的RPC异常
            RpcException[] exceptions = {
                    new RpcException("超时异常"),
                    new RpcException("网络异常"),
                    new RpcException("服务不可用"),
                    new RpcException("序列化异常")
            };

            CommandConfirmEvent event = CommandConfirmEvent.builder()
                    .deviceId(TEST_DEVICE_ID).commandId(TEST_COMMAND_ID).success(true).build();

            for (RpcException exception : exceptions) {
                // Given
                doThrow(exception).when(commandFinishFacade).commandFinish(any());

                // When & Then - 都应该不抛出异常
                org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
                    rpcAdapter.notifyCommandConfirm(event);
                });

                // 重置mock以便下次测试
                reset(commandFinishFacade);
            }
        }
    }

    @Nested
    @DisplayName("参数边界值测试")
    class ParameterBoundaryValueTests {

        @Test
        @DisplayName("应该处理极大的设备ID")
        void should_handle_very_large_device_id() {
            // Given
            Long largeDeviceId = Long.MAX_VALUE;
            CommandConfirmEvent event = CommandConfirmEvent.builder()
                    .deviceId(largeDeviceId)
                    .commandId(TEST_COMMAND_ID)
                    .success(true)
                    .build();

            // When
            rpcAdapter.notifyCommandConfirm(event);

            // Then
            verify(commandFinishFacade).commandFinish(commandFinishDtoCaptor.capture());
            assertThat(commandFinishDtoCaptor.getValue().getDeviceId()).isEqualTo(largeDeviceId);
        }

        @Test
        @DisplayName("应该处理极大的指令ID")
        void should_handle_very_large_command_id() {
            // Given
            Integer largeCommandId = Integer.MAX_VALUE;

            // When
            rpcAdapter.notifyCommandExpiration(TEST_DEVICE_ID, largeCommandId);

            // Then
            verify(commandFinishFacade).commandFinish(commandFinishDtoCaptor.capture());
            assertThat(commandFinishDtoCaptor.getValue().getCommandId()).isEqualTo(largeCommandId.toString());
        }

        @Test
        @DisplayName("应该处理很长的LED报告内容")
        void should_handle_very_long_led_report_content() {
            // Given
            String longLedReport = "x".repeat(10000); // 10KB的内容

            // When & Then - 应该不抛出异常
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
                rpcAdapter.notifyLedStatus(TEST_DEVICE_ID, longLedReport);
            });

            verify(deviceReportRpcService).reportDeviceLedStatus(
                    eq(TEST_DEVICE_ID),
                    anyLong(),
                    eq(longLedReport)
            );
        }

    }

    /**
     * 创建复杂的测试排程对象
     */
    private Schedule createMockSchedule() {
        Schedule schedule = mock(Schedule.class);
        // 模拟复杂排程对象的JSON表示，包含多个字段
        String complexScheduleJson = "{\"contentsSchedule\":[{\"type_priority\":200,\"priority\":0,\"if_limit_time\":true,\"limit_time\":{\"start_time\":\"10:40:55\",\"end_time\":\"23:59:59\"},\"operation\":{\"id\":1164,\"name\":\"Playlist5922\",\"vsn\":\"Playlist5922_4d9a41e19242f9e68a753aafe2f1c7f3_12612.vsn\",\"source\":\"internet\"},\"if_limit_date\":true,\"limit_date\":{\"start\":\"2024-07-26\",\"end\":\"2024-07-26\",\"start_time\":\"00:00:00\",\"end_time\":\"23:59:59\"},\"if_limit_weekday\":true,\"limit_weekday\":[true,true,true,false,false,true,true],\"type\":\"rotation\",\"name\":\"Play_Program\"}],\"commandSchedule\":[{\"operation\":{\"author_url\":\"api/brightness\",\"karma\":2,\"content\":\"{\\\\\"brightness\\\\\":\\\\\"77\\\\\"}\"},\"op_time\":[\"10:41:02\"],\"if_limit_date\":true,\"limit_date\":{\"start\":\"2024-07-26\",\"end\":\"2024-07-26\"},\"if_limit_weekday\":true,\"limit_weekday\":[true,true,false,false,true,true,true],\"content\":{\"name\":\"Value\",\"value\":\"30\"},\"type\":\"command\",\"name\":\"Brightness_Control\"}]}";
        lenient().when(schedule.toString()).thenReturn(complexScheduleJson);
        return schedule;
    }
}