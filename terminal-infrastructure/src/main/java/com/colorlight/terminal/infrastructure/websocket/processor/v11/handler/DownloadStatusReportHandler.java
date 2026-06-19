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
 * 下载进度上报消息处理器
 * 处理 DOWNLOAD_STATUS 类型消息，用于设备上报下载进度
 *
 * @author Codex
 */
@Slf4j
@Component
public class DownloadStatusReportHandler implements V11MessageHandler {

    private final TerminalReportUseCase terminalReportUseCase;

    public DownloadStatusReportHandler(TerminalReportUseCase terminalReportUseCase) {
        this.terminalReportUseCase = terminalReportUseCase;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.DOWNLOAD_STATUS;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? "{}" : JsonUtils.toJson(message.getData());
        terminalReportUseCase.asyncSaveDownloadingReport(context.getDeviceId(), dataStr);
        log.info("DownloadStatusReportHandler -ws- #DOWNLOADING_REPORT#【上报下载状态】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(
                V11WebsocketMessageTypeEnum.DOWNLOAD_STATUS.getId(), message.getMessageId()));
    }
}
