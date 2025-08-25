package com.colorlight.terminal.boot.controller;

import com.colorlight.terminal.api.DeviceInteractionApi;
import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.boot.converter.CommandConverter;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import com.colorlight.terminal.dto.command.DeviceApiCommand;
import com.colorlight.terminal.dto.command.DeviceApiCommandConfirm;
import com.colorlight.terminal.dto.media.DeviceApiMedia;
import com.colorlight.terminal.dto.program.DeviceApiProgram;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备交互控制器 - 实现设备二次开发文档中的必须接口
 * <p>由于老版本遗留问题使用Integer做设备Id，这里进行适配使用Auth认证中的Long Id</p>
 * 
 * @author Nan
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "设备交互API", description = "终端设备与服务器直接进行交互的API")
public class DeviceInteractionController implements DeviceInteractionApi {
    
    private final TerminalCommandUseCase terminalCommandUseCase;
    private final CommandConverter commandConverter;

    @Operation(
            summary = "上报终端信息",
            description = "设备上报led_status到服务器",
            tags = {"终端上报"}
    )
    @Override
    public void reportTerminalStatus(String report) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        // TODO: 实现设备状态处理逻辑
        log.info("DeviceReport - 收到上报消息: deviceId={}, report={}", principal.getDeviceId(), report);
    }

    @Operation(
            summary = "终端获取指令",
            description = "终端通过HTTP方式获取指令",
            tags = {"终端指令"}
    )
    @Override
    public List<DeviceApiCommand> getCommands(String cltType, String deviceNum) {
        // cltType、deviceNum这两个参数没用，历史问题
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            // 获取待执行指令
            List<TerminalCommand> commands = terminalCommandUseCase.getPendingCommands(principal.getDeviceId());
            
            // 转换为API格式
            List<DeviceApiCommand> apiCommands = commandConverter.convert2DeviceApiCommandList(commands);
            
            log.info("DeviceCommand - 返回 {} 条指令给设备: {}", apiCommands.size(), principal.getDeviceId());
            return apiCommands;
            
        } catch (Exception e) {
            log.error("DeviceCommand - 获取设备指令异常, deviceNum: {}", principal.getDeviceId(), e);
            return List.of(); // 返回空列表避免设备端异常
        }
    }

    @Operation(
            summary = "终端确认指令",
            description = "终端通过HTTP方式确认指令",
            tags = {"终端指令"}
    )
    @Override
    public void confirmCommand(Integer post, DeviceApiCommandConfirm commandConfirm) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long deviceId = principal.getDeviceId();
        log.info("DeviceCommandConfirm - 设备确认指令执行, deviceId: {}, confirmId: {}, content: {}",
                deviceId, commandConfirm.getParent(), commandConfirm.getContent());
        try {
            // 参数验证
            if (commandConfirm.getParent() == null) {
                log.warn("DeviceCommandConfirm - 指令确认参数无效, commandId = null");
                throw new DeviceResponseException(CommonErrorCode.PARAMETER_MISSING);
            }
            
            // 调用业务逻辑确认指令
            terminalCommandUseCase.confirmCommandExecution(
                    deviceId,
                    commandConfirm.getParent(), 
                    commandConfirm.getContent()
            );
            
            log.info("DeviceCommandConfirm - 指令确认处理完成, deviceId: {}, confirmId: {}", deviceId, commandConfirm.getParent());
            
        } catch (Exception e) {
            log.error("DeviceCommandConfirm - 指令确认异常, deviceId: {}, confirmId: {}", deviceId, commandConfirm.getParent(), e);
            throw new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR);
        }
    }

    @Operation(
            summary = "终端获取节目",
            description = "终端通过HTTP接口获取节目信息",
            tags = {"终端节目"}
    )
    @Override
    public List<DeviceApiProgram> getPrograms(String clt_type) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("DeviceProgram - 终端 {} 获取节目", principal.getDeviceId());
        // todo：实现获取节目接口
        return List.of();
    }

    @Operation(
            summary = "终端获取素材",
            description = "终端通过HTTP接口获取素材信息",
            tags = {"终端节目"}
    )
    @Override
    public List<DeviceApiMedia> getMedia(Integer parent) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("DeviceMedia - 终端 {} 获取素材", principal.getDeviceId());
        // todo：实现获取素材接口
        return List.of();
    }
}