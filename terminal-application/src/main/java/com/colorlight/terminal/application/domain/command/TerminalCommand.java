package com.colorlight.terminal.application.domain.command;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 终端指令领域对象
 * 
 * @author Nan
 * @version 1.0.0
 */
@Data
@Builder
public class TerminalCommand {
    
    /**
     * 指令唯一标识 (Integer类型，与设备确认的parent字段对应)
     */
    private Integer commandId;
    
    /**
     * 设备ID
     */
    private Long deviceId;
    
    /**
     * 指令操作类型 (用于去重判断)
     */
    private String authorUrl;
    
    /**
     * 指令内容JSON
     */
    private String contentRaw;
    
    /**
     * 终端执行方式
     * 0-GET, 1-POST, 2-PUT, 3-DELETE
     */
    private Integer karma;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 指令状态
     */
    private CommandStatus status;

    /**
     * 指令状态枚举
     */
    public enum CommandStatus {
        PENDING,    // 待执行
        SENT,       // 已下发
        CONFIRMED,  // 已确认
        EXECUTED,   // 已执行
        EXPIRED     // 已过期
    }
    
    /**
     * 检查指令是否过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

}