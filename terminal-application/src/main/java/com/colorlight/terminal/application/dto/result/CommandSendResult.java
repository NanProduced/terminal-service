package com.colorlight.terminal.application.dto.result;

import lombok.Builder;
import lombok.Data;

/**
 * 指令发送结果
 * 
 * @author Nan
 * @version 1.0.0
 */
@Data
@Builder
public class CommandSendResult {
    
    /**
     * 是否发送成功
     */
    private boolean success;
    
    /**
     * 指令ID
     */
    private String commandId;
    
    /**
     * 下发方式
     */
    private SendMethod sendMethod;
    
    /**
     * 结果消息
     */
    private String message;
    
    /**
     * 错误代码 (失败时)
     */
    private String errorCode;
    
    /**
     * 下发方式枚举
     */
    public enum SendMethod {
        WEBSOCKET,  // WebSocket实时下发
        REDIS_CACHE, // Redis缓存等待轮询
        FAILED      // 下发失败
    }
    
    /**
     * 成功结果工厂方法
     */
    public static CommandSendResult success(String commandId, SendMethod sendMethod, String message) {
        return CommandSendResult.builder()
                .success(true)
                .commandId(commandId)
                .sendMethod(sendMethod)
                .message(message)
                .build();
    }
    
    /**
     * 失败结果工厂方法
     */
    public static CommandSendResult failed(String errorCode, String message) {
        return CommandSendResult.builder()
                .success(false)
                .sendMethod(SendMethod.FAILED)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}