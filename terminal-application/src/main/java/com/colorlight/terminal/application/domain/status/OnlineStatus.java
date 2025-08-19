package com.colorlight.terminal.application.domain.status;

import lombok.Getter;

/**
 * 设备在线状态枚举
 * 
 * @author Nan
 */
@Getter
public enum OnlineStatus {
    
    /**
     * 在线状态
     */
    ONLINE("在线"),
    
    /**
     * 离线状态
     */
    OFFLINE("离线"),
    
    /**
     * 未知状态（Redis故障时的降级状态）
     */
    UNKNOWN("未知");
    
    private final String description;
    
    OnlineStatus(String description) {
        this.description = description;
    }

}