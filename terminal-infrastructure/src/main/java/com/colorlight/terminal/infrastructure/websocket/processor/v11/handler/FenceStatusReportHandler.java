package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 围栏状态上报消息处理器
 * 处理 FENCE_STATUS_REPORT 类型消息（待实现）
 *
 * @author Codex
 */
@Slf4j
@Component
public class FenceStatusReportHandler implements V11MessageHandler {

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.FENCE_STATUS_REPORT;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        log.info("FenceStatusReportHandler -ws- #FENCE_STATUS#【围栏状态】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(
                V11WebsocketMessageTypeEnum.FENCE_STATUS_REPORT.getId(), message.getMessageId()));
    }
}
