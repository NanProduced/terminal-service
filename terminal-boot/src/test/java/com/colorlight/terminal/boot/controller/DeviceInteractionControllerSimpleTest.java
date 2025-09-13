package com.colorlight.terminal.boot.controller;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.application.port.inbound.program.TerminalProgramUseCase;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.boot.converter.CommandConverter;
import com.colorlight.terminal.boot.converter.TerminalLogConverter;
import com.colorlight.terminal.dto.command.DeviceApiCommand;
import com.colorlight.terminal.dto.command.DeviceApiCommandConfirm;
import com.colorlight.terminal.dto.log.DeviceApiTerminalLog;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceInteractionController简单单元测试
 * 
 * 使用纯Mockito测试，不依赖Spring上下文
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备交互控制器简单测试")
class DeviceInteractionControllerSimpleTest {

    @Mock
    private TerminalCommandUseCase terminalCommandUseCase;

    @Mock
    private TerminalReportUseCase terminalReportUseCase;

    @Mock
    private TerminalProgramUseCase terminalProgramUseCase;

    @Mock
    private CommandConverter commandConverter;

    @Mock
    private TerminalLogConverter terminalLogConverter;

    @InjectMocks
    private DeviceInteractionController controller;

    private final Long DEVICE_ID = 12345L;

    @BeforeEach
    void setUp() {
        // 创建模拟的认证主体
        TerminalPrincipal mockPrincipal = new TerminalPrincipal(DEVICE_ID, TerminalAccountStatus.ENABLE);
        
        // 设置Security上下文
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(mockPrincipal, null, mockPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("应该成功上报终端状态")
    void should_report_terminal_status_successfully() {
        // Given
        String reportData = "{\"status\":\"online\",\"brightness\":80}";

        // When
        ResponseEntity<Void> response = controller.reportTerminalStatus(reportData);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(terminalReportUseCase).asyncSaveStatusReport(DEVICE_ID, reportData);
    }

    @Test
    @DisplayName("应该成功返回待执行指令列表")
    void should_return_pending_commands_successfully() {
        // Given
        List<TerminalCommand> mockCommands = List.of(mock(TerminalCommand.class));
        List<DeviceApiCommand> mockApiCommands = List.of(createMockDeviceApiCommand(1, "api/brightness", "{\"value\":80}"));

        when(terminalCommandUseCase.getPendingCommands(DEVICE_ID)).thenReturn(mockCommands);
        when(commandConverter.convert2DeviceApiCommandList(mockCommands)).thenReturn(mockApiCommands);

        // When
        List<DeviceApiCommand> result = controller.getCommands("terminal", "12345");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1);
        assertThat(result.get(0).getAuthorUrl()).isEqualTo("api/brightness");
        
        verify(terminalCommandUseCase).getPendingCommands(DEVICE_ID);
        verify(commandConverter).convert2DeviceApiCommandList(mockCommands);
    }

    @Test
    @DisplayName("应该在发生异常时返回空列表")
    void should_return_empty_list_when_exception_occurs() {
        // Given
        when(terminalCommandUseCase.getPendingCommands(DEVICE_ID))
            .thenThrow(new RuntimeException("Database error"));

        // When
        List<DeviceApiCommand> result = controller.getCommands("terminal", "12345");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该成功确认指令执行")
    void should_confirm_command_successfully() {
        // Given
        DeviceApiCommandConfirm confirmRequest = new DeviceApiCommandConfirm();
        confirmRequest.setParent(123);
        confirmRequest.setContent("执行成功");

        // When
        ResponseEntity<Void> response = controller.confirmCommand(1, confirmRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(terminalCommandUseCase).confirmCommandExecution(DEVICE_ID, 123, "执行成功");
    }

    @Test
    @DisplayName("应该在parent为null时抛出异常")
    void should_throw_exception_when_parent_is_null() {
        // Given
        DeviceApiCommandConfirm confirmRequest = new DeviceApiCommandConfirm();
        confirmRequest.setParent(null);
        confirmRequest.setContent("执行成功");

        // When & Then
        assertThatThrownBy(() -> controller.confirmCommand(1, confirmRequest))
            .isInstanceOf(Exception.class);

        verify(terminalCommandUseCase, never()).confirmCommandExecution(any(), any(), any());
    }

    @Test
    @DisplayName("应该成功返回排程信息")
    void should_return_schedule_successfully() {
        // Given
        String scheduleJson = "{\"schedule\":[{\"time\":\"10:00\",\"program\":\"test\"}]}";
        when(terminalProgramUseCase.getSchedule(DEVICE_ID)).thenReturn(scheduleJson);

        // When
        String result = controller.getSchedule();

        // Then
        assertThat(result).isEqualTo(scheduleJson);
        verify(terminalProgramUseCase).getSchedule(DEVICE_ID);
    }

    @Test
    @DisplayName("应该成功上报传感器数据")
    void should_report_sensor_data_successfully() {
        // Given
        String sensorData = "{\"temperature\":25.5,\"humidity\":60}";

        // When
        ResponseEntity<Void> response = controller.reportSensorData(sensorData);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(terminalReportUseCase).asyncHandleSensorReport(eq(DEVICE_ID), any(), eq(sensorData));
    }

    @Test
    @DisplayName("应该在空白传感器数据时返回201状态")
    void should_return_201_when_blank_sensor_data() {
        // When
        ResponseEntity<Void> response = controller.reportSensorData("");

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(terminalReportUseCase, never()).asyncHandleSensorReport(any(), any(), any());
    }

    @Test
    @DisplayName("应该成功上报终端日志")
    void should_report_terminal_log_successfully() {
        // Given
        List<DeviceApiTerminalLog> logList = List.of(createMockDeviceApiTerminalLog());
        List<TerminalLog> convertedLogs = List.of(mock(TerminalLog.class));
        
        when(terminalLogConverter.convertToTerminalLog(logList)).thenReturn(convertedLogs);

        // When
        ResponseEntity<Void> response = controller.reportTerminalLog(logList);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(terminalLogConverter).convertToTerminalLog(logList);
        verify(terminalReportUseCase).asyncSaveTerminalLog(DEVICE_ID, convertedLogs);
    }

    @Test
    @DisplayName("应该成功上报下载进度")
    void should_report_downloading_successfully() {
        // Given
        String downloadReport = "{\"fileId\":123,\"progress\":75}";

        // When
        ResponseEntity<Void> response = controller.reportDownloading(downloadReport);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(terminalReportUseCase).asyncSaveDownloadingReport(DEVICE_ID, downloadReport);
    }

    @Test
    @DisplayName("应该在空白下载进度数据时抛出异常")
    void should_throw_exception_when_blank_downloading_report() {
        // When & Then
        assertThatThrownBy(() -> controller.reportDownloading(""))
            .isInstanceOf(Exception.class);

        verify(terminalReportUseCase, never()).asyncSaveDownloadingReport(any(), any());
    }

    // 辅助方法：创建模拟数据
    private DeviceApiCommand createMockDeviceApiCommand(Integer id, String authorUrl, String content) {
        DeviceApiCommand command = new DeviceApiCommand();
        command.setId(id);
        command.setAuthorUrl(authorUrl);
        command.setContent(new DeviceApiCommand.Content(content));
        return command;
    }

    private DeviceApiTerminalLog createMockDeviceApiTerminalLog() {
        DeviceApiTerminalLog log = new DeviceApiTerminalLog();
        log.setDeviceId(DEVICE_ID.intValue());
        log.setDeviceName("Test Device");
        log.setLogType("runtime");
        log.setDescription("Test log");
        log.setLevel(1);
        return log;
    }
}