package com.colorlight.terminal.application.dto.websocket.v10;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import lombok.Data;

import java.util.List;

/**
 * v1.0版本-WebSocket消息封装类
 * 用于适配WebSocket协议的嵌套JSON格式
 *
 * <p>消息格式示例:</p>
 * <pre>
 * {
 *   "gps": [
 *     {"sensorType":"gps","latitude":39.9042,"longitude":116.4074},
 *     {"sensorType":"gps","latitude":39.9043,"longitude":116.4075}
 *   ]
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
     * GPS数据列表
     * V1.0协议支持多条GPS数据上报
     */
    private List<GpsReport> gps;
}
