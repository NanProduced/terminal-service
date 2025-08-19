package com.colorlight.terminal.infrastructure.websocket.connection;

import com.colorlight.terminal.application.domain.connection.WebSocketSession;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.Builder;
import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Terminal WebSocket会话封装
 *
 * <p>对原生WebSocketSession的业务封装，提供终端设备相关的会话管理功能：</p>
 *
 * @author Nan
 */

@Data
@Builder
public class TerminalWebsocketSession implements WebSocketSession {

    /**
     * Websocket会话ID
     */
    private String sessionId;

    /**
     * 设备Id（终端设备唯一标识）
     */
    private Long deviceId;

    /**
     * Netty Channel对象（Netty WebSocket）
     */
    private Channel nettyChannel;

    /**
     * 连接建立时间戳
     */
    private Long connectTime;

    /**
     * 客户端IP地址
     */
    private String clientIp;

    /**
     * 最后心跳时间戳
     */
    private volatile Long lastHeartbeatTime;

    /**
     * 接收消息计数
     */
    @Builder.Default
    private final AtomicLong receivedMessageCount = new AtomicLong(0);

    /**
     * 发送消息计数
     */
    @Builder.Default
    private final AtomicLong sentMessageCount = new AtomicLong(0);

    /**
     * 重连次数
     */
    @Builder.Default
    private final AtomicLong reconnectCount = new AtomicLong(0);

    /**
     * 异常计数
     */
    @Builder.Default
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * 检查连接是否有效
     */
    public boolean isConnected() {
        // 基于Netty Channel连接状态检查
        if (nettyChannel != null) {
            return nettyChannel.isActive();
        }
        return false;
    }

    /**
     * 获取连接持续时间（毫秒）
     */
    public long getConnectionDuration() {
        if (connectTime == null) {
            return 0;
        }
        return System.currentTimeMillis() - connectTime;
    }

    /**
     * 获取距离最后心跳的时间（毫秒）
     */
    public long getTimeSinceLastHeartbeat() {
        if (lastHeartbeatTime == null) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() - lastHeartbeatTime;
    }

    /**
     * 通过WebSocket发送消息到设备
     * 
     * @param message 消息内容 (JSON字符串)
     * @return 是否发送成功
     */
    @Override
    public boolean sendMessage(String message) {
        if (!isConnected()) {
            errorCount.incrementAndGet();
            return false;
        }
        
        try {
            // 通过Netty Channel发送文本消息
            TextWebSocketFrame frame = new TextWebSocketFrame(message);
            
            // 异步写入并刷新
            nettyChannel.writeAndFlush(frame);
            
            // 增加发送计数
            sentMessageCount.incrementAndGet();
            
            return true;
            
        } catch (Exception e) {
            // 增加错误计数
            errorCount.incrementAndGet();
            return false;
        }
    }

    /**
     * 关闭WebSocket连接
     */
    @Override
    public void close() {
        if (nettyChannel != null && nettyChannel.isActive()) {
            nettyChannel.close();
        }
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat() {
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    /**
     * 增加接收消息计数
     */
    public void incrementReceivedCount() {
        receivedMessageCount.incrementAndGet();
    }

    /**
     * 增加重连计数
     */
    public void incrementReconnectCount() {
        reconnectCount.incrementAndGet();
    }
    
    // 实现WebSocketSession接口的getter方法
    
    @Override
    public long getSentMessageCount() {
        return sentMessageCount.get();
    }
    
    @Override
    public long getReceivedMessageCount() {
        return receivedMessageCount.get();
    }
    
    @Override
    public long getErrorCount() {
        return errorCount.get();
    }

}
