package com.colorlight.terminal.application.domain.status;

import lombok.Getter;

/**
 * 设备上报来源枚举
 * 
 * @author Nan
 */
@Getter
public enum ReportSource {
    
    /**
     * HTTP轮询请求
     */
    HTTP("HTTP轮询"),
    
    /**
     * WebSocket心跳或消息
     */
    WEBSOCKET("WebSocket连接");
    
    private final String description;
    
    ReportSource(String description) {
        this.description = description;
    }

}