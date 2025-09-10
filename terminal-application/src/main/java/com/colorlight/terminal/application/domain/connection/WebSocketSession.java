package com.colorlight.terminal.application.domain.connection;

/**
 * WebSocket会话接口
 * 定义WebSocket会话的基本操作，避免application层直接依赖infrastructure层
 * 
 * @author Nan
 */
public interface WebSocketSession {
    
    /**
     * 检查连接是否有效
     * 
     * @return 是否连接有效
     */
    boolean isConnected();
    
    /**
     * 发送消息
     * 
     * @param message 消息内容
     * @return 是否发送成功
     */
    boolean sendMessage(String message);
    
    /**
     * 关闭连接
     */
    void close();
    
    /**
     * 获取会话ID
     * 
     * @return 会话ID
     */
    String getSessionId();
    
    /**
     * 获取设备ID
     * 
     * @return 设备ID
     */
    Long getDeviceId();
    
    /**
     * 获取客户端IP
     * 
     * @return 客户端IP
     */
    String getClientIp();

}