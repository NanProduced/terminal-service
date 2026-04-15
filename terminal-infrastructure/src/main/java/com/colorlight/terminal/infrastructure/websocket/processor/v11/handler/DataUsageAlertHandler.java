package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 流量报警消息处理器
 * 处理 DATA_USAGE_ALERT 类型消息（待实现）
 *
 * @author Codex
 */
@Slf4j
@Component
public class DataUsageAlertHandler implements V11MessageHandler {

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.DATA_USAGE_ALERT;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        log.info("DataUsageAlertHandler -ws- #DATA_USAGE_ALERT#【流量报警】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(
                V11WebsocketMessageTypeEnum.DATA_USAGE_ALERT.getId(), message.getMessageId()));
    }
}
