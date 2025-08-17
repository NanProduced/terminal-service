package com.colorlight.terminal.application.port.outbound.command;

import com.colorlight.terminal.application.domain.command.TerminalCommand;

/**
 * 指令WebSocket下发端口接口
 * 
 * @author Nan
 * @version 1.0.0
 */
public interface CommandWebSocketPort {
    
    /**
     * 通过WebSocket发送指令到设备
     * 
     * @param command 指令对象
     * @return 是否发送成功
     */
    boolean sendCommandViaWebSocket(TerminalCommand command);
    
    /**
     * 检查设备是否在线 (WebSocket连接存在)
     * 
     * @param deviceId 设备ID
     * @return 是否在线
     */
    boolean isDeviceOnline(Long deviceId);

}