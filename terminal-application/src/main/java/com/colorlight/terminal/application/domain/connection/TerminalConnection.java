package com.colorlight.terminal.application.domain.connection;

import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TerminalConnection {

    /**
     * 设备唯一ID
     */
    private Long deviceId;

    /**
     * 协议版本
     */
    private ProtocolVersion protocolVersion;

    /**
     * Websocket会话
     */
    private WebSocketSession session;

    /**
     * 连接建立时间
     */
    private LocalDateTime connectTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;


    /**
     * 客户端IP地址
     */
    private String clientIp;

    /**
     * 连接状态
     */
    private ConnectionStatus status;

    /**
     * 发送消息计数 - 统一到Connection层管理
     */
    @Builder.Default
    private final AtomicLong sentMessageCount = new AtomicLong(0);

    /**
     * 接收消息计数 - 统一到Connection层管理
     */
    @Builder.Default
    private final AtomicLong receivedMessageCount = new AtomicLong(0);

    /**
     * 错误消息计数 - 统一到Connection层管理
     */
    @Builder.Default
    private final AtomicLong errorMessageCount = new AtomicLong(0);

    /**
     * 创建设备连接
     * @param deviceId 设备Id
     * @param session WebSocket会话
     * @return 连接封装
     */
    public static TerminalConnection create(Long deviceId, WebSocketSession session, ProtocolVersion protocolVersion) {
        LocalDateTime now = LocalDateTime.now();
        TerminalConnection connection = new TerminalConnection();
        connection.setDeviceId(deviceId);
        connection.setSession(session);
        connection.setClientIp(session != null ? session.getClientIp() : "unknown");
        connection.setLastActiveTime(now);
        // 统一使用lastActiveTime表示最后WebSocket通信时间
        connection.setConnectTime(now);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setProtocolVersion(protocolVersion);
        return connection;
    }

    public void updateActiveTime() {
        this.setLastActiveTime(LocalDateTime.now());
    }

    /**
     * 获取发送消息计数
     */
    public long getSentMessageCount() {
        return sentMessageCount.get();
    }

    /**
     * 获取接收消息计数
     */
    public long getReceivedMessageCount() {
        return receivedMessageCount.get();
    }

    /**
     * 获取错误计数
     */
    public long getErrorCount() {
        return errorMessageCount.get();
    }
    
    /**
     * 增加发送消息计数（统一更新：计数+活跃时间）
     */
    public void incrementSentMessageCount() {
        sentMessageCount.incrementAndGet();
        updateActiveTime();
    }

    /**
     * 增加接收消息计数（统一更新：计数+活跃时间）
     */
    public void incrementReceivedMessageCount() {
        receivedMessageCount.incrementAndGet();
        updateActiveTime();
    }

    /**
     * 增加错误计数（统一更新：计数+活跃时间）
     */
    public void incrementErrorCount() {
        errorMessageCount.incrementAndGet();
        updateActiveTime();
    }

    /**
     * 获取WebSocket会话
     */
    public WebSocketSession getWebSocketSession() {
        return session;
    }

    /**
     * 获取连接持续时间（秒）
     *
     * @return 连接持续时间
     */
    public long getConnectionDurationSeconds() {
        if (connectTime == null) {
            return 0;
        }
        return Duration.between(connectTime, LocalDateTime.now()).getSeconds();
    }

    /**
     * 获取空闲时间（秒）
     *
     * @return 空闲时间
     */
    public long getIdleTimeSeconds() {
        if (lastActiveTime == null) {
            return 0;
        }
        return Duration.between(lastActiveTime, LocalDateTime.now()).getSeconds();
    }

    /**
     * 检查连接是否活跃
     */
    public boolean isActive() {
        if (session == null) {
            return false;
        }
        
        return session.isConnected();
    }
    
    /**
     * 发送消息
     */
    public boolean sendMessage(String message) {
        if (session == null) {
            return false;
        }
        return session.sendMessage(message);
    }

    /**
     * 发送消息
     *
     * @param messageObj javaBean消息
     * @return 是否成功
     */
    public boolean sendMessage(Object messageObj) {
        String message = JsonUtils.toJson(messageObj);
        return sendMessage(message);
    }

    /**
     * 连接状态枚举
     */
    public enum ConnectionStatus {
        /**
         * 已连接
         */
        CONNECTED,

        /**
         * 心跳超时
         */
        HEARTBEAT_TIMEOUT,

        /**
         * 已断开
         */
        DISCONNECTED,

        /**
         * 异常状态
         */
        ERROR
    }
}
