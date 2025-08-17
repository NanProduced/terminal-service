package com.colorlight.terminal.application.dto.request;

import lombok.Builder;
import lombok.Data;

/**
 * 发送指令请求
 * 
 * @author Nan
 * @version 1.0.0
 */
@Data
@Builder
public class SendCommandRequest {
    
    /**
     * 目标设备ID
     */
    private Long deviceId;
    
    /**
     * 指令操作类型 (用于去重)
     * 例如: "api/brightness", "api/volume", "api/reboot"
     */
    private String authorUrl;
    
    /**
     * 指令内容JSON字符串
     */
    private String contentRaw;
    
    /**
     * 终端执行方式
     * 0-GET, 1-POST, 2-PUT, 3-DELETE
     */
    private Integer karma;
}