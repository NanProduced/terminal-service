package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.ccloud.program.vo.RpcTerminalProgramVO;
import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketErrorEnum;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.program.TerminalProgramUseCase;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 节目获取消息处理器
 * 处理 PROGRAMS 类型消息，用于设备获取节目列表
 *
 * @author Codex
 */
@Slf4j
@Component
public class ProgramGetHandler extends AbstractV11MessageHandler {

    private final TerminalProgramUseCase terminalProgramUseCase;

    public ProgramGetHandler(
            TerminalProgramUseCase terminalProgramUseCase,
            @Qualifier("websocketBusinessExecutor") Executor websocketBusinessExecutor) {
        super(websocketBusinessExecutor);
        this.terminalProgramUseCase = terminalProgramUseCase;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.PROGRAMS;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        Long deviceId = context.getDeviceId();
        Integer messageId = message.getMessageId();

        executeAsync(
                context,
                () -> {
                    try {
                        String programStr = terminalProgramUseCase.getProgram(deviceId);
                        if (StringUtils.isBlank(programStr)) {
                            return List.<RpcTerminalProgramVO>of();
                        }
                        List<RpcTerminalProgramVO> programs = JsonUtils.fromJson(
                                programStr, new TypeReference<List<RpcTerminalProgramVO>>() {});
                        return programs == null ? List.of() : programs;
                    } catch (Exception e) {
                        log.error("ProgramGetHandler -ws- #GET_PROGRAMS#【获取节目异常】deviceId:{}", deviceId, e);
                        throw e;
                    }
                },
                programs -> {
                    sendSuccessResponse(context, messageId, programs);
                    log.info("ProgramGetHandler -ws- #GET_PROGRAMS#【获取节目成功】deviceId:{}", deviceId);
                },
                throwable -> {
                    log.error("ProgramGetHandler -ws- #GET_PROGRAMS#【获取节目失败】deviceId:{}", deviceId, throwable);
                    sendErrorResponse(context, messageId, V11WebsocketErrorEnum.SERVER_ERROR, "获取节目失败");
                }
        );
    }
}
