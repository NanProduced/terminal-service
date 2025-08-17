package com.colorlight.terminal.application.port.inbound.command;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.application.dto.result.CommandSendResult;

import java.util.List;

/**
 * 终端指令用例接口
 * 
 * @author Nan
 * @version 1.0.0
 */
public interface TerminalCommandUseCase {
    
    /**
     * 下发指令到设备
     * 智能路由：在线设备使用WebSocket，离线设备使用Redis缓存
     * 
     * @param request 指令下发请求
     * @return 下发结果
     */
    CommandSendResult sendCommandToDevice(SendCommandRequest request);
    
    /**
     * 获取设备待执行指令列表
     * 用于HTTP轮询接口
     * 
     * @param deviceId 设备ID
     * @return 指令列表
     */
    List<TerminalCommand> getPendingCommands(Long deviceId);
    
    /**
     * 确认指令执行
     * 设备确认后从缓存中移除指令
     * 
     * @param deviceId 设备ID
     * @param commandId 指令ID
     * @param result 执行结果
     */
    void confirmCommandExecution(Long deviceId, Integer commandId, String result);
}