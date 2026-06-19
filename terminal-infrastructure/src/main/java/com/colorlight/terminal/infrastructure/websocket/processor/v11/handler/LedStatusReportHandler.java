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
 * LED状态上报消息处理器
 * 处理 STATUS_REPORT 类型消息，用于设备上报LED状态
 *
 * @author Codex
 */
@Slf4j
@Component
public class LedStatusReportHandler implements V11MessageHandler {

    private final TerminalReportUseCase terminalReportUseCase;

    public LedStatusReportHandler(TerminalReportUseCase terminalReportUseCase) {
        this.terminalReportUseCase = terminalReportUseCase;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.STATUS_REPORT;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? "{}" : JsonUtils.toJson(message.getData());
        String clientIp = context.getConnection().getClientIp();
        terminalReportUseCase.asyncSaveStatusReport(context.getDeviceId(), dataStr, clientIp);
        log.info("LedStatusReportHandler -ws- #STATUS_REPORT#【上报终端状态】deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(
                V11WebsocketMessageTypeEnum.STATUS_REPORT.getId(), message.getMessageId()));
    }
}
