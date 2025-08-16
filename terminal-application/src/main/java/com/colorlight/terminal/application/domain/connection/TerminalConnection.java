package com.colorlight.terminal.application.domain.connection;

import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
public class TerminalConnection {

    /**
     * 设备唯一ID
     */
    private Long deviceId;

    /**
     * Websocket会话
     */
    private Object session;

    /**
     * 连接建立时间
     */
    private LocalDateTime connectTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;

    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeatTime;

    /**
     * 客户端IP地址
     */
    private String clientIp;

    /**
     * 连接状态
     */
    private ConnectionStatus status;

    /**
     * 发送消息计数
     */
    private long sentMessageCount;

    /**
     * 接收消息计数
     */
    private long receivedMessageCount;

    /**
     * 错误计数
     */
    private long errorCount;

    /**
     * 创建设备连接
     * @param deviceId 设备Id
     * @param session 会话Id
     * @return
     */
    public static TerminalConnection create(Long deviceId, Object session) {
        LocalDateTime now = LocalDateTime.now();
        TerminalConnection connection = new TerminalConnection();
        connection.setDeviceId(deviceId);
        connection.setSession(session);
        connection.setClientIp(extractClientIp(session));
        connection.setLastActiveTime(now);
        connection.setLastHeartbeatTime(now);
        connection.setConnectTime(now);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setSentMessageCount(0);
        connection.setReceivedMessageCount(0);
        return connection;
    }

    public static String extractClientIp(Object session) {
        if (session instanceof TerminalConnection connection) {
            return connection.getClientIp();
        }
        return "unknown";
    }

    public void updateActiveTime() {
        this.setLastActiveTime(LocalDateTime.now());
    }

    public void updateLastHeartbeatTime() {
        LocalDateTime now = LocalDateTime.now();
        this.setLastHeartbeatTime(now);
        this.setLastActiveTime(now);
    }

    /**
     * 增加发送消息计数
     */
    public void incrementSentMessageCount() {
        this.sentMessageCount++;
        updateActiveTime();
    }

    /**
     * 增加接收消息计数
     */
    public void incrementReceivedMessageCount() {
        this.receivedMessageCount++;
        updateActiveTime();
    }

    /**
     * 增加错误计数
     */
    public void incrementErrorCount() {
        this.errorCount++;
        updateActiveTime();
    }

    /**
     * 获取WebSocket会话
     */
    public Object getWebSocketSession() {
        return session;
    }

    /**
     * 检查连接是否过期
     *
     * @param expireThreshold 过期时间阈值
     * @return 是否过期
     */
    public boolean isExpired(LocalDateTime expireThreshold) {
        return lastHeartbeatTime != null && lastHeartbeatTime.isBefore(expireThreshold);
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
