package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;

/**
 * V11协议消息处理器接口
 * 定义消息处理的统一契约，采用策略模式实现不同消息类型的处理
 *
 * @author Codex
 */
public interface V11MessageHandler {

    /**
     * 获取该处理器支持的消息类型
     *
     * @return 消息类型枚举
     */
    V11WebsocketMessageTypeEnum getSupportedType();

    /**
     * 处理消息
     *
     * @param context 消息处理上下文
     * @param message WebSocket消息
     */
    void handle(MessageProcessingContext context, V11WebsocketMessage message);
}
