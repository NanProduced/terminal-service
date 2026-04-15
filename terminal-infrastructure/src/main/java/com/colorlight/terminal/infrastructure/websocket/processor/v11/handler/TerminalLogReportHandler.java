package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.converter.V11WebsocketDtoConverter;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.TerminalLogDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 终端日志上报消息处理器
 * 处理 LOG_REPORT 类型消息，用于设备上报终端日志
 *
 * @author Codex
 */
@Slf4j
@Component
public class TerminalLogReportHandler implements V11MessageHandler {

    private final TerminalReportUseCase terminalReportUseCase;
    private final V11WebsocketDtoConverter dtoConverter;

    public TerminalLogReportHandler(
            TerminalReportUseCase terminalReportUseCase,
            V11WebsocketDtoConverter dtoConverter) {
        this.terminalReportUseCase = terminalReportUseCase;
        this.dtoConverter = dtoConverter;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.LOG_REPORT;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        if (Objects.isNull(message.getData())) {
            throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_DATA);
        }

        List<TerminalLogDTO> terminalLogDTOS = JsonUtils.fromJson(
                message.getData().toString(), new TypeReference<List<TerminalLogDTO>>() {});
        List<TerminalLog> terminalLogs = dtoConverter.convertToTerminalLogs(terminalLogDTOS);
        terminalReportUseCase.asyncSaveTerminalLog(context.getDeviceId(), terminalLogs);
        log.info("TerminalLogReportHandler -ws- #LOG_REPORT#【上报终端日志】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(
                V11WebsocketMessageTypeEnum.LOG_REPORT.getId(), message.getMessageId()));
    }
}
