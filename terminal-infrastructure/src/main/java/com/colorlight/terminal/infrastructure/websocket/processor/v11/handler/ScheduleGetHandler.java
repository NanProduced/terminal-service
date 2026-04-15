package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketErrorEnum;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.program.TerminalProgramUseCase;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 排程获取消息处理器
 * 处理 SCHEDULE 类型消息，用于设备获取排程信息
 *
 * @author Codex
 */
@Slf4j
@Component
public class ScheduleGetHandler extends AbstractV11MessageHandler {

    private final TerminalProgramUseCase terminalProgramUseCase;

    public ScheduleGetHandler(
            TerminalProgramUseCase terminalProgramUseCase,
            @Qualifier("websocketBusinessExecutor") Executor websocketBusinessExecutor) {
        super(websocketBusinessExecutor);
        this.terminalProgramUseCase = terminalProgramUseCase;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.SCHEDULE;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        Long deviceId = context.getDeviceId();
        Integer messageId = message.getMessageId();

        executeAsync(
                context,
                () -> {
                    try {
                        String schedule = terminalProgramUseCase.getSchedule(deviceId);
                        return StringUtils.isBlank(schedule)
                                ? JsonUtils.fromJson(EMPTY_JSON)
                                : JsonUtils.fromJson(schedule);
                    } catch (Exception e) {
                        log.error("ScheduleGetHandler -ws- #GET_SCHEDULE#【获取排程异常】deviceId:{}", deviceId, e);
                        throw e;
                    }
                },
                schedulePayload -> {
                    sendSuccessResponse(context, messageId, schedulePayload);
                    log.info("ScheduleGetHandler -ws- #GET_SCHEDULE#【获取排程成功】deviceId:{}", deviceId);
                },
                throwable -> {
                    log.error("ScheduleGetHandler -ws- #GET_SCHEDULE#【获取排程失败】deviceId:{}", deviceId, throwable);
                    sendErrorResponse(context, messageId, V11WebsocketErrorEnum.SERVER_ERROR, "获取排程失败");
                }
        );
    }
}
