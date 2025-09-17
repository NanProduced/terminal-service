package com.colorlight.terminal.infrastructure.websocket.monitor;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * EventLoop告警事件
 * 当EventLoop监控发现性能问题时发出的事件
 *
 * @author Nan
 */
@Getter
public class EventLoopAlertEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = -9007945656956002623L;

    /**
     * 告警级别
     */
    @Getter
    public enum AlertLevel {
        WARNING("告警"),
        CRITICAL("严重");

        private final String description;

        AlertLevel(String description) {
            this.description = description;
        }

    }

    /** 告警级别 */
    private final AlertLevel alertLevel;

    /** 待处理任务数 */
    private final long pendingTasks;

    /** EventExecutor信息 */
    private final String eventExecutorInfo;

    /** 告警阈值 */
    private final long threshold;

    /** 告警消息 */
    private final String message;

    /** 事件时间戳 */
    private final long timestamp;

    /**
     * 创建WARNING级别告警事件
     */
    public static EventLoopAlertEvent warning(long pendingTasks, String eventExecutorInfo, long threshold) {
        String message = String.format("EventLoop可能阻塞: eventExecutor=%s, pendingTasks=%d, threshold=%d",
                eventExecutorInfo, pendingTasks, threshold);
        return new EventLoopAlertEvent(AlertLevel.WARNING, pendingTasks, eventExecutorInfo, threshold, message);
    }

    /**
     * 创建CRITICAL级别告警事件
     */
    public static EventLoopAlertEvent critical(long pendingTasks, String eventExecutorInfo, long threshold) {
        String message = String.format("EventLoop严重阻塞: eventExecutor=%s, pendingTasks=%d, threshold=%d",
                eventExecutorInfo, pendingTasks, threshold);
        return new EventLoopAlertEvent(AlertLevel.CRITICAL, pendingTasks, eventExecutorInfo, threshold, message);
    }

    /**
     * 私有构造函数
     */
    private EventLoopAlertEvent(AlertLevel alertLevel, long pendingTasks, String eventExecutorInfo,
                               long threshold, String message) {
        super(new Object()); // ApplicationEvent要求source参数
        this.alertLevel = alertLevel;
        this.pendingTasks = pendingTasks;
        this.eventExecutorInfo = eventExecutorInfo;
        this.threshold = threshold;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("EventLoopAlertEvent{level=%s, pendingTasks=%d, threshold=%d, eventExecutor='%s', timestamp=%d, message='%s'}",
                alertLevel.name(), pendingTasks, threshold, eventExecutorInfo, timestamp, message);
    }
}