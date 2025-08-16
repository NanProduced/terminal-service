package com.colorlight.terminal.application.port.inbound.websocket;

import com.colorlight.terminal.application.domain.connection.TerminalConnection;

/**
 * WebSocket消息处理用例接口
 * 
 * @author Nan
 */
public interface WebsocketMessageUseCase {
    
    /**
     * 处理心跳消息
     * 
     * @param connection 终端连接
     * @return 是否处理成功
     */
    boolean handleHeartbeat(TerminalConnection connection);
    
    /**
     * 处理文本消息
     * 
     * @param connection 终端连接
     * @param message 消息内容
     * @return 是否处理成功
     */
    boolean handleTextMessage(TerminalConnection connection, String message);
    
    /**
     * 处理连接建立
     * 
     * @param deviceId 设备ID
     * @param session 技术会话对象
     * @return 终端连接对象
     */
    TerminalConnection handleConnectionEstablished(Long deviceId, Object session);
    
    /**
     * 处理连接断开
     * 
     * @param deviceId 设备ID
     * @return 是否处理成功
     */
    boolean handleConnectionClosed(Long deviceId);
}