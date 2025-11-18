package com.colorlight.terminal.application.domain.connection;

import com.colorlight.terminal.application.handler.WebsocketMsgMetricsHelper;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息处理上下文
 *
 * @author Nan
 */
@Slf4j
@Getter
public class MessageProcessingContext {

    /**
     * 终端连接对象
     */
    private final TerminalConnection connection;
    
    /**
     * 原始消息内容
     */
    private final String rawMessage;
    
    /**
     * 消息接收时间戳
     */
    private final long receiveTimestamp;

    /**
     * 私有构造函数，强制使用工厂方法创建
     */
    private MessageProcessingContext(TerminalConnection connection, String rawMessage) {
        this.connection = connection;
        this.rawMessage = rawMessage;
        this.receiveTimestamp = System.currentTimeMillis();
    }

    /**
     * 创建消息处理上下文 - 核心工厂方法
     * 
     * @param connection 终端连接对象
     * @param rawMessage 原始消息内容
     * @return 消息处理上下文，如果连接无效则返回null
     */
    public static MessageProcessingContext create(TerminalConnection connection, String rawMessage) {
        if (connection == null) {
            log.warn("MessageProcessingContext - 连接对象为空，无法创建处理上下文");
            return null;
        }
        
        return new MessageProcessingContext(connection, rawMessage);
    }
    
    /**
     * 检查上下文是否有效
     */
    public boolean isValid() {
        return connection != null && connection.isActive();
    }
    

    /* ============== 便捷访问方法 ============== */

    
    /**
     * 获取设备ID（代理访问）
     */
    public Long getDeviceId() {
        return connection.getDeviceId();
    }
    
    /**
     * 获取协议版本（代理访问）
     */
    public ProtocolVersion getProtocolVersion() {
        return connection.getProtocolVersion();
    }
    
    /**
     * 获取客户端IP（代理访问）
     */
    public String getClientIp() {
        return connection.getClientIp();
    }
    
    /**
     * 获取消息处理耗时（毫秒）
     */
    public long getProcessingTimeMs() {
        return System.currentTimeMillis() - receiveTimestamp;
    }
    
    /**
     * 更新消息统计
     *
     */
    public void updateMessageStatistics() {
        try {
            connection.incrementReceivedMessageCount();
            log.debug("MessageProcessingContext - 接收消息统计更新成功: deviceId={}",
                     getDeviceId() );
        } catch (Exception e) {
            log.warn("MessageProcessingContext - 接收消息统计更新失败: deviceId={}", getDeviceId(), e);
        }
    }
    
    /**
     * 更新发送消息统计
     */
    public void updateSentMessageStatistics() {
        try {
            connection.incrementSentMessageCount();
            log.debug("MessageProcessingContext - 发送消息统计更新: deviceId={}", getDeviceId());
        } catch (Exception e) {
            log.warn("MessageProcessingContext - 发送消息统计更新失败: deviceId={}", getDeviceId(), e);
        }
    }
    
    /**
     * 更新错误统计
     */
    public void updateErrorStatistics() {
        try {
            connection.incrementErrorCount();
            log.debug("MessageProcessingContext - 错误统计更新: deviceId={}", getDeviceId());
        } catch (Exception e) {
            log.warn("MessageProcessingContext - 错误统计更新失败: deviceId={}", getDeviceId(), e);
        }
    }
    
    /**
     * 发送消息（代理访问）
     */
    public boolean sendMessage(String message) {
        try {
            boolean success = connection.sendMessage(message);
            if (success) {
                WebsocketMsgMetricsHelper.incrementSentMessage();
                updateSentMessageStatistics();
            } else {
                WebsocketMsgMetricsHelper.incrementErrorMessage();
                updateErrorStatistics();
            }
            return success;
        } catch (Exception e) {
            log.warn("MessageProcessingContext - 发送消息失败: deviceId={}", getDeviceId(), e);
            WebsocketMsgMetricsHelper.incrementErrorMessage();
            updateErrorStatistics();
            return false;
        }
    }

    /**
     * 将给定的对象转换为JSON格式字符串后发送消息。
     *
     * @param obj 要被转换成JSON并发送的对象
     * @return 如果消息成功发送则返回true，否则返回false
     */
    public boolean sendMessage(Object obj) {
        return sendMessage(JsonUtils.toJson(obj));
    }
    
    @Override
    public String toString() {
        return String.format("MessageProcessingContext{deviceId=%d, protocol=%s, processingTime=%dms}", 
                           getDeviceId(), getProtocolVersion(), getProcessingTimeMs());
    }
}