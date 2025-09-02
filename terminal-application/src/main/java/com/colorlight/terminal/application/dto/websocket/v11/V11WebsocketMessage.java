package com.colorlight.terminal.application.dto.websocket.v11;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * v1.1版本-WebSocket消息封装类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class V11WebsocketMessage {

    private Integer messageId;

    private Integer receiptId;

    private Integer type;

    private Object data;

    public V11WebsocketMessage(Integer type, String data) {
        this.type = type;
        this.data = data;
    }

    public V11WebsocketMessage(Integer type, Object data) {
        this.type = type;
        this.data = data;
    }

    public V11WebsocketMessage(Integer type, Integer receiptId, Object data) {
        this.type = type;
        this.receiptId = receiptId;
        this.data = data;
    }

    public V11WebsocketMessage(Integer type, Integer receiptId) {
        this.type = type;
        this.receiptId = receiptId;
    }

    /**
     * 生成包含错误信息的WebSocket消息。
     *
     * @param errorType 错误类型枚举
     * @param messageId 消息ID
     * @param info 错误信息详情
     * @return 包含错误信息的V11WebsocketMessage对象
     */
    public static V11WebsocketMessage generateErrorContent(V11WebsocketErrorEnum errorType, Integer messageId, String info) {
        V11WebsocketMessage websocketMessage = new V11WebsocketMessage(V11WebsocketMessageTypeEnum.ERROR.getId(), messageId);
        V11WebsocketErrorMessage errorMessage = new V11WebsocketErrorMessage(errorType.getId(), info);
        websocketMessage.setData(errorMessage);
        return websocketMessage;
    }

    /**
     * 生成包含错误信息的WebSocket消息。
     *
     * @param errorType 错误类型枚举
     * @param info 错误信息详情
     * @return 包含错误信息的V11WebsocketMessage对象
     */
    public static V11WebsocketMessage generateErrorContent(V11WebsocketErrorEnum errorType, String info) {
        V11WebsocketErrorMessage errorMessage = new V11WebsocketErrorMessage(errorType.getId(), info);
        return new V11WebsocketMessage(V11WebsocketMessageTypeEnum.ERROR.getId(), errorMessage);
    }

    /**
     * 生成包含错误信息的WebSocket消息。
     *
     * @param errorType 错误类型枚举
     * @return 包含错误信息的V11WebsocketMessage对象
     */
    public static V11WebsocketMessage generateErrorContent(V11WebsocketErrorEnum errorType) {
        V11WebsocketErrorMessage errorMessage = new V11WebsocketErrorMessage(errorType.getId(), errorType.getType());
        return new V11WebsocketMessage(V11WebsocketMessageTypeEnum.ERROR.getId(), errorMessage);
    }
}
