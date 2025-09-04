package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.application.dto.result.CommandSendResult;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.application.port.outbound.command.CommandCachePort;
import com.colorlight.terminal.application.port.outbound.command.CommandWebSocketPort;
import com.colorlight.terminal.application.port.outbound.config.CommandConfigPort;
import com.colorlight.terminal.application.port.outbound.generator.CommandIdGeneratorPort;
import com.colorlight.terminal.application.port.outbound.status.CommandConfirmEventPort;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 终端指令应用服务
 * 
 * @author Nan
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalCommandApplicationService implements TerminalCommandUseCase {
    
    private final CommandWebSocketPort commandWebSocketPort;
    private final CommandCachePort commandCachePort;
    private final CommandIdGeneratorPort commandIdGeneratorPort;
    private final CommandConfigPort commandConfigPort;
    private final CommandConfirmEventPort commandConfirmEventPort;
    
    @Override
    public CommandSendResult sendCommandToDevice(SendCommandRequest request) {
        log.info("ApplicationService - 开始下发指令, deviceId: {}, authorUrl: {}", request.getDeviceId(), request.getAuthorUrl());
        
        try {
            // 1. 创建指令对象
            TerminalCommand command = createCommand(request);
            
            // 2. 必须先缓存指令到Redis (设备需要HTTP确认)
            boolean cached = commandCachePort.saveCommand(command);
            if (!cached) {
                log.error("ApplicationService - 指令缓存失败, commandId: {}", command.getCommandId());
                return CommandSendResult.failed("CACHE_ERROR", "指令缓存失败");
            }
            
            log.debug("ApplicationService - 指令已缓存至Redis, commandId: {}", command.getCommandId());
            
            // 3. 检查设备是否在线，在线则WebSocket实时下发
            if (commandWebSocketPort.isDeviceOnline(request.getDeviceId())) {
                boolean sent = commandWebSocketPort.sendCommandViaWebSocket(command);
                if (sent) {
                    log.debug("ApplicationService - 指令通过WebSocket实时下发成功, commandId: {}", command.getCommandId());
                    return CommandSendResult.success(command.getCommandId().toString(), 
                            CommandSendResult.SendMethod.WEBSOCKET, "指令已缓存并实时下发");
                } else {
                    log.warn("ApplicationService - WebSocket下发失败，设备需轮询获取, commandId: {}", command.getCommandId());
                    return CommandSendResult.success(command.getCommandId().toString(), 
                            CommandSendResult.SendMethod.REDIS_CACHE, "指令已缓存，WebSocket下发失败，等待轮询");
                }
            } else {
                // 设备离线，等待HTTP轮询
                log.debug("ApplicationService - 设备离线，指令等待轮询获取, deviceId: {}, commandId: {}",
                        request.getDeviceId(), command.getCommandId());
                return CommandSendResult.success(command.getCommandId().toString(), 
                        CommandSendResult.SendMethod.REDIS_CACHE, "指令已缓存，等待设备轮询");
            }
            
        } catch (Exception e) {
            log.error("ApplicationService - 指令下发异常, deviceId: {}, authorUrl: {}",
                    request.getDeviceId(), request.getAuthorUrl(), e);
            return CommandSendResult.failed(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统内部错误: " + e.getMessage());
        }
    }
    
    @Override
    public List<TerminalCommand> getPendingCommands(Long deviceId) {
        log.debug("ApplicationService - 获取设备待执行指令, deviceId: {}", deviceId);
        
        try {
            // 清理过期指令
            int cleanedCount = commandCachePort.cleanExpiredCommands(deviceId);
            if (cleanedCount > 0) {
                log.debug("ApplicationService - 清理过期指令 {} 条, deviceId: {}", cleanedCount, deviceId);
            }
            
            // 获取有效指令
            List<TerminalCommand> commands = commandCachePort.getPendingCommands(deviceId);
            log.debug("ApplicationService - 返回 {} 条待执行指令, deviceId: {}", commands.size(), deviceId);
            
            return commands;
            
        } catch (Exception e) {
            log.error("ApplicationService - 获取待执行指令异常, deviceId: {}", deviceId, e);
            return List.of(); // 返回空列表避免接口异常
        }
    }
    
    @Override
    public void confirmCommandExecution(Long deviceId, Integer commandId, String result) {
        log.info("ApplicationService - 设备确认指令执行, deviceId: {}, commandId: {}, result: {}",
                deviceId, commandId, result);
        
        try {
            // 从缓存中移除已确认的指令
            boolean removed = commandCachePort.removeCommand(deviceId, commandId);
            if (removed) {
                log.debug("ApplicationService - 指令确认成功并已移除缓存, commandId: {}", commandId);
            } else {
                log.warn("ApplicationService - 指令确认成功但缓存移除失败, commandId: {}", commandId);
            }
            commandConfirmEventPort.publishCommandConfirmEvent(CommandConfirmEvent.success(deviceId, commandId));
            
        } catch (Exception e) {
            log.error("ApplicationService - 指令确认异常, deviceId: {}, commandId: {}", deviceId, commandId, e);
            commandConfirmEventPort.publishCommandConfirmEvent(CommandConfirmEvent.failed(deviceId, commandId));
        }
    }
    
    /**
     * 创建指令对象
     */
    private TerminalCommand createCommand(SendCommandRequest request) {
        Integer commandId = commandIdGeneratorPort.generateCommandId();
        LocalDateTime now = LocalDateTime.now();
        Long expireHours = commandConfigPort.getCommandExpireHours();
        
        log.debug("ApplicationService - 创建指令, commandId: {}, expireHours: {}", commandId, expireHours);
        
        return TerminalCommand.builder()
                .commandId(commandId)
                .deviceId(request.getDeviceId())
                .authorUrl(request.getAuthorUrl())
                .contentRaw(request.getContentRaw())
                .karma(request.getKarma())
                .createTime(now)
                .expireTime(now.plusHours(expireHours))
                .status(TerminalCommand.CommandStatus.PENDING)
                .build();
    }
}