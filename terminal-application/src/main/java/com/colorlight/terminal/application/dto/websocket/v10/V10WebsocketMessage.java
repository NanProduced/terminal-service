package com.colorlight.terminal.application.dto.websocket.v10;

import lombok.Data;

/**
 * v1.0版本-WebSocket消息封装类
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
public class V10WebsocketMessage {

    /**
     * 设备账户名
     */
    private String name;

    private String url;

    /**
     * 文本消息
     */
    private String content;

    /**
     * 使用String适配后续处理
     */
    private String gps;
}
