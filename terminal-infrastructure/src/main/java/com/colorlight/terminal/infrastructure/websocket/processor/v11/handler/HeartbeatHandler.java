package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 心跳消息处理器
 * 处理 HEARTBEAT 类型消息（已弃用，V1.1协议使用空报文作为心跳）
 *
 * @author Codex
 */
@Slf4j
@Component
@Deprecated
public class HeartbeatHandler implements V11MessageHandler {

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.HEARTBEAT;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        context.sendMessage("");
        log.debug("HeartbeatHandler -ws- #HEARTBEAT#【心跳消息】deviceId:{}", context.getDeviceId());
    }
}
