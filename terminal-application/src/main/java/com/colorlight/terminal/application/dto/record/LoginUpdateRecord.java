package com.colorlight.terminal.application.dto.record;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录时间更新记录
 * 用于异步批处理缓冲池
 * 
 * @author Nan
 */
@Data
@Builder
public class LoginUpdateRecord {
    
    /**
     * 设备ID
     */
    private Long deviceId;
    
    /**
     * 客户端IP
     */
    private String clientIp;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 创建记录的时间戳（用于去重和统计）
     */
    private Long createTimestamp;
    
    /**
     * 创建登录更新记录
     */
    public static LoginUpdateRecord create(Long deviceId, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        return LoginUpdateRecord.builder()
                .deviceId(deviceId)
                .clientIp(clientIp)
                .updateTime(now)
                .createTimestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建登录更新记录（指定时间）
     */
    public static LoginUpdateRecord create(Long deviceId, String clientIp, LocalDateTime updateTime) {
        return LoginUpdateRecord.builder()
                .deviceId(deviceId)
                .clientIp(clientIp)
                .updateTime(updateTime)
                .createTimestamp(System.currentTimeMillis())
                .build();
    }
}