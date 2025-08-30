package com.colorlight.terminal.application.dto.websocket;

import lombok.Data;

/**
 * WebSocket消息封装类
 * 用于适配WebSocket协议的嵌套JSON格式
 *
 * <p>消息格式示例:</p>
 * <pre>
 * {
 *   "gps": "[{\"sensorType\":\"gps\",\"latitude\":39.9042,\"longitude\":116.4074}]"
 * }
 * </pre>
 *
 * @author Nan
 */
@Data
public class WebsocketMessage {

    /**
     * 使用String适配后续处理
     */
    private String gps;
}
