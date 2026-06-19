package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 素材播放记录上报消息处理器
 * 处理 MEDIA_RECORD 类型消息，用于设备上报素材播放记录
 *
 * @author Codex
 */
@Slf4j
@Component
public class MediaPlayRecordReportHandler implements V11MessageHandler {

    private final TerminalReportUseCase terminalReportUseCase;

    public MediaPlayRecordReportHandler(TerminalReportUseCase terminalReportUseCase) {
        this.terminalReportUseCase = terminalReportUseCase;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.MEDIA_RECORD;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? "{}" : JsonUtils.toJson(message.getData());
        terminalReportUseCase.asyncHandleMediaPlayRecordReport(context.getDeviceId(), dataStr);
        log.info("MediaPlayRecordReportHandler -ws- #MEDIA_PLAY_RECORD_REPORT#【上报素材播放记录】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(
                V11WebsocketMessageTypeEnum.MEDIA_RECORD.getId(), message.getMessageId()));
    }
}
