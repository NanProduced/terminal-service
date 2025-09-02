package com.colorlight.terminal.infrastructure.websocket.processor.v11;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketErrorEnum;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.converter.V11WebsocketDtoConverter;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.CommandResponse;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.TerminalLogDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * v11协议 - 操作分发路由
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class V11OperationHandleRouter {

    private final TerminalCommandUseCase terminalCommandUseCase;
    private final TerminalReportUseCase terminalReportUseCase;
    private final V11WebsocketDtoConverter dtoConverter;

    /**
     * 空JSON结构体
     */
    private final static String EMPTY_JSON = "{}";

    public void handleMessageByType(MessageProcessingContext context, V11WebsocketMessage message) {

        V11WebsocketMessageTypeEnum messageType = V11WebsocketMessageTypeEnum.fromId(message.getType());
        if (messageType == null) {
            throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_TYPE);
        }

        switch (messageType) {

            // 已弃用
            case HEARTBEAT -> context.sendMessage("");

            // 指令获取
            case COMMAND -> handleGetCommand(context, message.getMessageId());

            // 排程获取
            case SCHEDULE -> handleGetSchedule(context, message.getMessageId());

            // 节目获取
            case PROGRAMS -> handleGetProgram(context, message.getMessageId());

            // 指令确认
            case CONFIRM_COMMAND -> handleConfirmCommand(context, message);

            // led_status上报
            case STATUS_REPORT -> handleLedStatusReport(context, message);

            // 下载进度上报
            case DOWNLOAD_STATUS -> handleDownloadingReport(context, message);

            // 素材播放记录上报
            case MEDIA_RECORD -> handleMediaPlayRecordReport(context, message);

            // 传感器数据上报
            case MONITOR_REPORT -> handleSensorDataReport(context, message);

            // 终端日志上报
            case LOG_REPORT -> handleTerminalLogReport(context, message);

            default -> throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_TYPE);

        }
    }

    /* =================== 指令类型对应的处理方法 =================== */

    /**
     * 处理获取指令的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId 消息ID，用于响应时关联请求
     */
    private void handleGetCommand(MessageProcessingContext context, Integer messageId) {

        List<TerminalCommand> pendingCommands = terminalCommandUseCase.getPendingCommands(context.getDeviceId());
        List<CommandResponse> commandResponses = dtoConverter.convertToCommandResponses(pendingCommands);
        // 下发待执行指令列表
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.COMMAND.getId(), messageId, commandResponses));
        log.info("V11Router -ws- #GET_COMMENT#【获取指令】deviceId:{}, commandIds:{}", context.getDeviceId(), commandResponses.stream().map(CommandResponse::getId).toList());
    }

    /**
     * 处理获取排程的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId 消息ID，用于响应时关联请求
     */
    private void handleGetSchedule(MessageProcessingContext context, Integer messageId) {
        // todo: 待主服务RPC接口完成

        // 下发排程
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.SCHEDULE.getId(), messageId, null));
        log.info("V11Router -ws- #GET_SCHEDULE#【获取排程】deviceId:{}", context.getDeviceId());
    }

    /**
     * 处理获取节目的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId 消息ID，用于响应时关联请求
     */
    private void handleGetProgram(MessageProcessingContext context, Integer messageId) {
        // todo: 待主服务RPC接口完成

        // 下发节目
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.PROGRAMS.getId(), messageId, null));
        log.info("V11Router -ws- #GET_PROGRAMS#【获取节目】deviceId:{}", context.getDeviceId());
    }

    /**
     * 处理确认指令的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleConfirmCommand(MessageProcessingContext context, V11WebsocketMessage message) {
        if (Objects.isNull(message.getData())) throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_DATA);
        String dataStr = JsonUtils.toJson(message.getData());
        int commandId = JsonUtils.getIntValue(dataStr, "parent", 0);
        String content = JsonUtils.getStringValue(dataStr, "content", "");
        if (commandId <= 0) throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_DATA);

        try {
            terminalCommandUseCase.confirmCommandExecution(context.getDeviceId(), commandId, content);
            log.info("V11Router -ws- #CONFIRM_COMMENT#【确认指令成功】deviceId:{}, commandId:{}", context.getDeviceId(), commandId);
            // 回复确认指令成功
            context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.CONFIRM_COMMAND.getId(), message.getMessageId()));
        } catch (Exception e) {
            log.error("V11Router -ws- #CONFIRM_COMMENT#【确认指令失败】deviceId:{}, commandId:{}", context.getDeviceId(), commandId);
            context.sendMessage(V11WebsocketMessage.generateErrorContent(V11WebsocketErrorEnum.INVALID_COMMENT_ID, message.getMessageId(), "confirm command failed: " + commandId));
        }
    }

    /**
     * 处理led_status上报的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleLedStatusReport(MessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        terminalReportUseCase.saveLedStatus(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #STATUS_REPORT#【上报终端状态】deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.STATUS_REPORT.getId(), message.getMessageId()));
    }

    /**
     * 处理上报下载状态的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleDownloadingReport(MessageProcessingContext context, V11WebsocketMessage message) {

        //todo: 待主服务实现RPC

        log.info("V11Router -ws- #DOWNLOADING_REPORT#【上报下载状态】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.DOWNLOAD_STATUS.getId(), message.getMessageId()));
    }

    /**
     * 处理素材播放记录报告的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleMediaPlayRecordReport(MessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        terminalReportUseCase.asyncHandleMediaPlayRecordReport(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #MEDIA_PLAY_RECORD_REPORT#【上报素材播放记录】 deviceId:{}", context.getDeviceId() );
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.MEDIA_RECORD.getId(), message.getMessageId()));
    }

    /**
     * 处理传感器数据报告的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleSensorDataReport(MessageProcessingContext context, V11WebsocketMessage message) {
        LocalDateTime now = LocalDateTime.now();
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        terminalReportUseCase.asyncHandleSensorReport(context.getDeviceId(), now, dataStr);
        log.info("V11Router -ws- #MONITOR_REPORT#【上报监控数据】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.MONITOR_REPORT.getId(), message.getMessageId()));
    }

    /**
     * 处理终端日志报告的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleTerminalLogReport(MessageProcessingContext context, V11WebsocketMessage message) {
        if (Objects.isNull(message.getData())) throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_DATA);
        List<TerminalLogDTO> terminalLogDTOS = JsonUtils.fromJson(message.getData().toString(), new TypeReference<List<TerminalLogDTO>>() {});
        List<TerminalLog> terminalLogs = dtoConverter.convertToTerminalLogs(terminalLogDTOS);
        terminalReportUseCase.asyncSaveTerminalLog(context.getDeviceId(), terminalLogs);
        log.info("V11Router -ws- #LOG_REPORT#【上报终端日志】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.LOG_REPORT.getId(), message.getMessageId()));
    }


}
