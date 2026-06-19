package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketErrorEnum;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 指令确认消息处理器
 * 处理 CONFIRM_COMMAND 类型消息，用于设备确认指令执行结果
 *
 * @author Codex
 */
@Slf4j
@Component
public class CommandConfirmHandler extends AbstractV11MessageHandler {

    private final TerminalCommandUseCase terminalCommandUseCase;

    public CommandConfirmHandler(
            TerminalCommandUseCase terminalCommandUseCase,
            @Qualifier("websocketBusinessExecutor") Executor websocketBusinessExecutor) {
        super(websocketBusinessExecutor);
        this.terminalCommandUseCase = terminalCommandUseCase;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.CONFIRM_COMMAND;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        if (isEmptyData(message.getData())) {
            throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_DATA);
        }

        String dataStr = JsonUtils.toJson(message.getData());
        int commandId = JsonUtils.getIntValue(dataStr, "parent", 0);
        String content = JsonUtils.getStringValue(dataStr, "content", "");

        if (commandId <= 0) {
            throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_DATA);
        }

        Long deviceId = context.getDeviceId();
        Integer messageId = message.getMessageId();

        executeAsyncVoid(
                context,
                () -> {
                    try {
                        terminalCommandUseCase.confirmCommandExecution(deviceId, commandId, content);
                        log.info("CommandConfirmHandler -ws- #CONFIRM_COMMENT#【确认指令业务处理成功】deviceId:{}, commandId:{}",
                                deviceId, commandId);
                    } catch (Exception e) {
                        log.error("CommandConfirmHandler -ws- #CONFIRM_COMMENT#【确认指令业务处理失败】deviceId:{}, commandId:{}",
                                deviceId, commandId, e);
                        throw e;
                    }
                },
                () -> {
                    sendSuccessResponse(context, messageId);
                    log.info("CommandConfirmHandler -ws- #CONFIRM_COMMENT#【确认指令成功】deviceId:{}, commandId:{}",
                            deviceId, commandId);
                },
                throwable -> {
                    log.error("CommandConfirmHandler -ws- #CONFIRM_COMMENT#【确认指令失败】deviceId:{}, commandId:{}",
                            deviceId, commandId, throwable);
                    sendErrorResponse(context, messageId, V11WebsocketErrorEnum.INVALID_COMMENT_ID,
                            "confirm command failed: " + commandId);
                }
        );
    }
}
