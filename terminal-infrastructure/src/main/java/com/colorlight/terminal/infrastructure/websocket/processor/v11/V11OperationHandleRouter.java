package com.colorlight.terminal.infrastructure.websocket.processor.v11;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketErrorEnum;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.application.port.inbound.program.TerminalProgramUseCase;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.converter.V11WebsocketDtoConverter;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.CommandResponse;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.TerminalLogDTO;
import com.colorlight.terminal.infrastructure.websocket.connection.TerminalWebsocketSession;
import io.netty.channel.Channel;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * v11协议 - 操作分发路由
 *
 * @author Nan
 */
@Slf4j
@Component
public class V11OperationHandleRouter {

    private final TerminalCommandUseCase terminalCommandUseCase;
    private final TerminalReportUseCase terminalReportUseCase;
    private final TerminalProgramUseCase terminalProgramUseCase;
    private final V11WebsocketDtoConverter dtoConverter;

    /**
     * WebSocket业务处理专用线程池
     * 用于异步处理耗时的业务操作（Redis查询、数据库操作等），避免阻塞EventLoop
     */
    private final Executor websocketBusinessExecutor;

    /**
     * 构造函数，注入WebSocket业务处理专用线程池
     */
    public V11OperationHandleRouter(
            TerminalCommandUseCase terminalCommandUseCase,
            TerminalReportUseCase terminalReportUseCase,
            TerminalProgramUseCase terminalProgramUseCase,
            V11WebsocketDtoConverter dtoConverter,
            @Qualifier("websocketBusinessExecutor") Executor websocketBusinessExecutor) {
        this.terminalCommandUseCase = terminalCommandUseCase;
        this.terminalReportUseCase = terminalReportUseCase;
        this.terminalProgramUseCase = terminalProgramUseCase;
        this.dtoConverter = dtoConverter;
        this.websocketBusinessExecutor = websocketBusinessExecutor;
    }

    /**
     * 空JSON结构体
     */
    private static final String EMPTY_JSON = "{}";

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

            // 节目播放记录上报
            case PROGRAM_RECORD -> handleProgramPlayRecordReport(context, message);

            default -> throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_TYPE);

        }
    }

    /* =================== 指令类型对应的处理方法 =================== */

    /**
     * 主动推送指令给设备（连接建立时调用）
     * 异步化处理：避免Redis查询阻塞EventLoop线程
     *
     * <p>与handleGetCommand的区别：</p>
     * <ul>
     *   <li>handleGetCommand：响应设备的主动请求，receiptId = messageId</li>
     *   <li>pushCommandsOnConnection：服务器主动推送，receiptId = null</li>
     * </ul>
     *
     * @param context 消息处理上下文，包含设备ID等信息
     */
    public void pushCommandsOnConnection(MessageProcessingContext context) {
        Long deviceId = context.getDeviceId();

        // 异步执行Redis查询操作，避免阻塞EventLoop线程
        // getPendingCommands可能涉及多次Redis操作：List查询 + 批量Get操作
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // 执行可能耗时的Redis查询操作
                        List<TerminalCommand> pendingCommands = terminalCommandUseCase.getPendingCommands(deviceId);
                        return dtoConverter.convertToCommandResponses(pendingCommands);
                    } catch (Exception e) {
                        log.error("V11Router -ws- #PUSH_COMMAND#【连接建立推送指令异常】deviceId:{}", deviceId, e);
                        throw e; // 重新抛出异常，由whenComplete处理
                    }
                }, websocketBusinessExecutor)
                .whenComplete((commandResponses, throwable) -> {
                    // 回调必须在EventLoop线程中执行，确保sendMessage的线程安全
                    TerminalWebsocketSession session = (TerminalWebsocketSession) context.getConnection().getSession();
                    Channel channel = session.getNettyChannel();
                    channel.eventLoop().execute(() -> {
                        try {
                            if (throwable == null) {
                                // 成功获取指令，发送响应
                                // receiptId为null，表示服务器主动推送（区别于设备请求的响应）
                                context.sendMessage(new V11WebsocketMessage(
                                        V11WebsocketMessageTypeEnum.COMMAND.getId(), null, commandResponses));
                                log.info("V11Router -ws- #PUSH_COMMAND#【连接建立推送指令成功】deviceId:{}, commandIds:{}",
                                        deviceId, commandResponses.stream().map(CommandResponse::getId).toList());
                            } else {
                                // 处理异常，记录错误但不发送错误响应（避免干扰正常流程）
                                log.error("V11Router -ws- #PUSH_COMMAND#【连接建立推送指令失败】deviceId:{}", deviceId, throwable);
                            }
                        } catch (Exception e) {
                            log.error("V11Router -ws- #PUSH_COMMAND#【发送推送消息异常】deviceId:{}", deviceId, e);
                        }
                    });
                });
    }

    /**
     * 处理获取指令的命令。
     * 异步化处理：避免Redis查询阻塞EventLoop线程
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId 消息ID，用于响应时关联请求
     */
    private void handleGetCommand(MessageProcessingContext context, Integer messageId) {
        Long deviceId = context.getDeviceId();

        // 异步执行Redis查询操作，避免阻塞EventLoop线程
        // getPendingCommands可能涉及多次Redis操作：List查询 + 批量Get操作
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // 执行可能耗时的Redis查询操作
                        List<TerminalCommand> pendingCommands = terminalCommandUseCase.getPendingCommands(deviceId);
                        return dtoConverter.convertToCommandResponses(pendingCommands);
                    } catch (Exception e) {
                        log.error("V11Router -ws- #GET_COMMENT#【获取指令异常】deviceId:{}", deviceId, e);
                        throw e; // 重新抛出异常，由whenComplete处理
                    }
                }, websocketBusinessExecutor)
                .whenComplete((commandResponses, throwable) -> {
                    // 回调必须在EventLoop线程中执行，确保sendMessage的线程安全
                    TerminalWebsocketSession session = (TerminalWebsocketSession) context.getConnection().getSession();
                    Channel channel = session.getNettyChannel();
                    channel.eventLoop().execute(() -> {
                        try {
                            if (throwable == null) {
                                // 成功获取指令，发送响应
                                context.sendMessage(new V11WebsocketMessage(
                                        V11WebsocketMessageTypeEnum.COMMAND.getId(), messageId, commandResponses));
                                log.info("V11Router -ws- #GET_COMMENT#【获取指令成功】deviceId:{}, commandIds:{}",
                                        deviceId, commandResponses.stream().map(CommandResponse::getId).toList());
                            } else {
                                // 处理异常，发送错误响应
                                log.error("V11Router -ws- #GET_COMMENT#【获取指令失败】deviceId:{}", deviceId, throwable);
                                context.sendMessage(V11WebsocketMessage.generateErrorContent(
                                        V11WebsocketErrorEnum.SERVER_ERROR, messageId, "获取指令失败"));
                            }
                        } catch (Exception e) {
                            log.error("V11Router -ws- #GET_COMMENT#【发送响应异常】deviceId:{}", deviceId, e);
                        }
                    });
                });
    }

    /**
     * 处理获取排程的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId 消息ID，用于响应时关联请求
     */
    private void handleGetSchedule(MessageProcessingContext context, Integer messageId) {
        String schedule = terminalProgramUseCase.getSchedule(context.getDeviceId());
        // 下发排程
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.SCHEDULE.getId(), messageId, schedule));
        log.info("V11Router -ws- #GET_SCHEDULE#【获取排程】deviceId:{}", context.getDeviceId());
    }

    /**
     * 处理获取节目的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param messageId 消息ID，用于响应时关联请求
     */
    private void handleGetProgram(MessageProcessingContext context, Integer messageId) {
        String program = terminalProgramUseCase.getProgram(context.getDeviceId());

        // 下发节目
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.PROGRAMS.getId(), messageId, program));
        log.info("V11Router -ws- #GET_PROGRAMS#【获取节目】deviceId:{}", context.getDeviceId());
    }

    /**
     * 处理指令确认消息
     * 异步化处理：避免Redis删除操作和事件发布阻塞EventLoop线程
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleConfirmCommand(MessageProcessingContext context, V11WebsocketMessage message) {
        // 快速参数验证，在EventLoop线程中完成
        if (Objects.isNull(message.getData())) {
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

        // 异步执行指令确认操作，避免阻塞EventLoop线程
        // confirmCommandExecution涉及：Redis删除操作 + 事件发布
        CompletableFuture
                .runAsync(() -> {
                    try {
                        // 执行可能耗时的Redis删除和事件发布操作
                        terminalCommandUseCase.confirmCommandExecution(deviceId, commandId, content);
                        log.info("V11Router -ws- #CONFIRM_COMMENT#【确认指令业务处理成功】deviceId:{}, commandId:{}",
                                deviceId, commandId);
                    } catch (Exception e) {
                        log.error("V11Router -ws- #CONFIRM_COMMENT#【确认指令业务处理失败】deviceId:{}, commandId:{}",
                                deviceId, commandId, e);
                        throw e; // 重新抛出异常，由whenComplete处理
                    }
                }, websocketBusinessExecutor)
                .whenComplete((result, throwable) -> {
                    // 回调必须在EventLoop线程中执行，确保sendMessage的线程安全
                    TerminalWebsocketSession session = (TerminalWebsocketSession) context.getConnection().getSession();
                    Channel channel = session.getNettyChannel();
                    channel.eventLoop().execute(() -> {
                        try {
                            if (throwable == null) {
                                // 确认成功，发送成功响应
                                context.sendMessage(new V11WebsocketMessage(
                                        V11WebsocketMessageTypeEnum.CONFIRM_COMMAND.getId(), messageId));
                                log.info("V11Router -ws- #CONFIRM_COMMENT#【确认指令成功】deviceId:{}, commandId:{}",
                                        deviceId, commandId);
                            } else {
                                // 确认失败，发送错误响应
                                log.error("V11Router -ws- #CONFIRM_COMMENT#【确认指令失败】deviceId:{}, commandId:{}",
                                        deviceId, commandId, throwable);
                                context.sendMessage(V11WebsocketMessage.generateErrorContent(
                                        V11WebsocketErrorEnum.INVALID_COMMENT_ID, messageId,
                                        "confirm command failed: " + commandId));
                            }
                        } catch (Exception e) {
                            log.error("V11Router -ws- #CONFIRM_COMMENT#【发送确认响应异常】deviceId:{}, commandId:{}",
                                    deviceId, commandId, e);
                        }
                    });
                });
    }

    /**
     * 处理led_status上报的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleLedStatusReport(MessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        terminalReportUseCase.asyncSaveStatusReport(context.getDeviceId(), dataStr);
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
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        terminalReportUseCase.asyncSaveDownloadingReport(context.getDeviceId(), dataStr);

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
        log.info("V11Router -ws- #MEDIA_PLAY_RECORD_REPORT#【上报素材播放记录】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.MEDIA_RECORD.getId(), message.getMessageId()));
    }

    /**
     * 处理节目播放记录报告的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleProgramPlayRecordReport(MessageProcessingContext context, V11WebsocketMessage message) {
        String dataStr = Objects.isNull(message.getData()) ? EMPTY_JSON : JsonUtils.toJson(message.getData());
        terminalReportUseCase.asyncHandleProgramPlayRecordReport(context.getDeviceId(), dataStr);
        log.info("V11Router -ws- #PROGRAM_PLAY_RECORD_REPORT#【上报节目播放记录】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.PROGRAM_RECORD.getId(), message.getMessageId()));
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

    /**
     * 处理数据使用警告消息。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleDataUsageAlert(MessageProcessingContext context, V11WebsocketMessage message) {
        // todo: 待实现业务逻辑
        log.info("V11Router -ws- #DATA_USAGE_ALERT#【流量报警】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.DATA_USAGE_ALERT.getId(), message.getMessageId()));
    }

    /**
     * 处理围栏状态报告的命令。
     *
     * @param context 消息处理上下文，包含设备ID等信息
     * @param message 接收到的WebSocket消息对象
     */
    private void handleFenceStatusReport(MessageProcessingContext context, V11WebsocketMessage message) {
        // todo: 待实现业务逻辑
        log.info("V11Router -ws- #FENCE_STATUS#【围栏状态】 deviceId:{}", context.getDeviceId());
        context.sendMessage(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.FENCE_STATUS_REPORT.getId(), message.getMessageId()));
    }

}
