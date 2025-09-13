package com.colorlight.terminal.infrastructure.websocket.connection;

import com.colorlight.terminal.application.domain.connection.WebSocketSession;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.Builder;
import lombok.Data;


/**
 * Terminal WebSocket会话封装 - 简化版本（纯技术会话）
 *
 * <p>统计功能已迁移到TerminalConnection层，此类仅保留WebSocket技术功能</p>
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
     * 通过WebSocket发送消息到设备
     * 
     * @param message 消息内容 (JSON字符串)
     * @return 是否发送成功
     */
    @Override
    public boolean sendMessage(String message) {
        if (!isConnected()) {
            return false;
        }
        
        try {
            // 通过Netty Channel发送文本消息
            TextWebSocketFrame frame = new TextWebSocketFrame(message);
            
            // 异步写入并刷新
            nettyChannel.writeAndFlush(frame);
            
            return true;
            
        } catch (Exception e) {
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


}
