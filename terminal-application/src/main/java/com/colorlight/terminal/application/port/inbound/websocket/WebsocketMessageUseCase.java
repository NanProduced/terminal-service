package com.colorlight.terminal.application.port.inbound.websocket;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.connection.WebSocketSession;

import java.util.List;

/**
 * WebSocket消息处理用例接口
 * 
 * @author Nan
 */
public interface WebsocketMessageUseCase {
    
    /**
     * 处理文本消息
     *
     * @param context 消息上下文
     */
    void handleTextMessageByProcessor(MessageProcessingContext context);
    
    /**
     * 处理连接建立
     * 
     * @param deviceId 设备ID
     * @param session 技术会话对象
     * @param protocolVersion 协议版本
     * @return 终端连接对象
     */
    TerminalConnection handleConnectionEstablished(Long deviceId, WebSocketSession session, ProtocolVersion protocolVersion);
    
    /**
     * 处理连接断开
     *
     * @param deviceId 设备ID
     */
    void handleConnectionClosed(Long deviceId);

    /**
     * 处理PING帧
     * @param terminalConnection 设备连接
     */
    void handlePingFrame(TerminalConnection terminalConnection);

    /**
     * 处理PONG帧
     * @param terminalConnection 设备连接
     */
    void handlePongFrame(TerminalConnection terminalConnection);
    
    /**
     * 发送消息给指定设备
     * 
     * @param deviceId 设备ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    boolean sendMessage(Long deviceId, String message);
    
    /**
     * 批量发送消息给多个设备
     * 
     * @param deviceIds 设备ID列表
     * @param message 消息内容
     * @return 发送成功的设备ID列表
     */
    List<Long> broadcastMessage(List<Long> deviceIds, String message);

}