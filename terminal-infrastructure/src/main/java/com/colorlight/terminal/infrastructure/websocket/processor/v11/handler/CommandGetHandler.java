package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketErrorEnum;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.converter.V11WebsocketDtoConverter;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.CommandResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 指令获取消息处理器
 * 处理 COMMAND 类型消息，用于设备获取待执行指令
 *
 * @author Codex
 */
@Slf4j
@Component
public class CommandGetHandler extends AbstractV11MessageHandler {

    private final TerminalCommandUseCase terminalCommandUseCase;
    private final V11WebsocketDtoConverter dtoConverter;

    public CommandGetHandler(
            TerminalCommandUseCase terminalCommandUseCase,
            V11WebsocketDtoConverter dtoConverter,
            @Qualifier("websocketBusinessExecutor") Executor websocketBusinessExecutor) {
        super(websocketBusinessExecutor);
        this.terminalCommandUseCase = terminalCommandUseCase;
        this.dtoConverter = dtoConverter;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.COMMAND;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        Long deviceId = context.getDeviceId();
        Integer messageId = message.getMessageId();

        executeAsync(
                context,
                () -> {
                    try {
                        List<TerminalCommand> pendingCommands = terminalCommandUseCase.getPendingCommands(deviceId);
                        return dtoConverter.convertToCommandResponses(pendingCommands);
                    } catch (Exception e) {
                        log.error("CommandGetHandler -ws- #GET_COMMENT#【获取指令异常】deviceId:{}", deviceId, e);
                        throw e;
                    }
                },
                commandResponses -> {
                    sendSuccessResponse(context, messageId, commandResponses);
                    log.info("CommandGetHandler -ws- #GET_COMMENT#【获取指令成功】deviceId:{}, commandIds:{}",
                            deviceId, commandResponses.stream().map(CommandResponse::getId).toList());
                },
                throwable -> {
                    log.error("CommandGetHandler -ws- #GET_COMMENT#【获取指令失败】deviceId:{}", deviceId, throwable);
                    sendErrorResponse(context, messageId, V11WebsocketErrorEnum.SERVER_ERROR, "获取指令失败");
                }
        );
    }

    /**
     * 连接建立时主动推送指令
     * 与 handle 方法的区别：
     * - handle：响应设备的主动请求，receiptId = messageId
     * - pushCommandsOnConnection：服务器主动推送，receiptId = null
     *
     * @param context 消息处理上下文
     */
    public void pushCommandsOnConnection(MessageProcessingContext context) {
        Long deviceId = context.getDeviceId();

        executeAsync(
                context,
                () -> {
                    try {
                        List<TerminalCommand> pendingCommands = terminalCommandUseCase.getPendingCommands(deviceId);
                        return dtoConverter.convertToCommandResponses(pendingCommands);
                    } catch (Exception e) {
                        log.error("CommandGetHandler -ws- #PUSH_COMMAND#【连接建立推送指令异常】deviceId:{}", deviceId, e);
                        throw e;
                    }
                },
                commandResponses -> {
                    context.sendMessage(new V11WebsocketMessage(
                            V11WebsocketMessageTypeEnum.COMMAND.getId(), null, commandResponses));
                    log.info("CommandGetHandler -ws- #PUSH_COMMAND#【连接建立推送指令成功】deviceId:{}, commandIds:{}",
                            deviceId, commandResponses.stream().map(CommandResponse::getId).toList());
                },
                throwable -> {
                    log.error("CommandGetHandler -ws- #PUSH_COMMAND#【连接建立推送指令失败】deviceId:{}", deviceId, throwable);
                }
        );
    }
}
