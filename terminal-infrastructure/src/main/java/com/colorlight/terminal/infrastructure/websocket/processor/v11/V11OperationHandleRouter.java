package com.colorlight.terminal.infrastructure.websocket.processor.v11;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.V11MessageHandler;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.V11MessageHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * v11协议 - 操作分发路由
 * <p>
 * 重构说明：
 * - 原实现使用 switch-case 进行消息路由，违反开闭原则
 * - 重构后采用策略模式 + 工厂模式，将每种消息类型的处理逻辑拆分到独立的处理器类中
 * - 本类现在作为门面（Facade），负责路由消息到对应的处理器
 * </p>
 *
 * @author Nan
 * @author Codex (重构)
 */
@Slf4j
@Component
public class V11OperationHandleRouter {

    private final V11MessageHandlerFactory handlerFactory;

    /**
     * 构造函数，注入消息处理器工厂
     *
     * @param handlerFactory 消息处理器工厂
     */
    public V11OperationHandleRouter(V11MessageHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    /**
     * 根据消息类型路由处理
     * <p>
     * 重构说明：
     * - 原实现使用 switch-case 硬编码路由逻辑
     * - 重构后通过工厂获取对应的处理器，实现开闭原则
     * - 新增消息类型只需添加新的处理器实现类，无需修改本类
     * </p>
     *
     * @param context 消息处理上下文
     * @param message WebSocket消息
     */
    public void handleMessageByType(MessageProcessingContext context, V11WebsocketMessage message) {

        V11WebsocketMessageTypeEnum messageType = V11WebsocketMessageTypeEnum.fromId(message.getType());
        if (messageType == null) {
            throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_TYPE);
        }

        V11MessageHandler handler = handlerFactory.getHandler(messageType);
        handler.handle(context, message);
    }

    /**
     * 主动推送指令给设备（连接建立时调用）
     * <p>
     * 重构说明：
     * - 原实现包含完整的异步处理逻辑
     * - 重构后委托给 CommandGetHandler 处理
     * - 保持方法签名不变，确保向后兼容性
     * </p>
     *
     * @param context 消息处理上下文
     */
    public void pushCommandsOnConnection(MessageProcessingContext context) {
        handlerFactory.getCommandGetHandler().pushCommandsOnConnection(context);
    }

    /* =================== 以下方法已迁移到独立处理器类，保留注释供参考 =================== */

    /**
     * 处理获取指令的命令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.CommandGetHandler}
     */
    @Deprecated
    private void handleGetCommand(MessageProcessingContext context, Integer messageId) {
        // 已迁移到 CommandGetHandler
    }

    /**
     * 处理获取排程指令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.ScheduleGetHandler}
     */
    @Deprecated
    private void handleGetSchedule(MessageProcessingContext context, Integer messageId) {
        // 已迁移到 ScheduleGetHandler
    }

    /**
     * 处理获取节目指令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.ProgramGetHandler}
     */
    @Deprecated
    private void handleGetProgram(MessageProcessingContext context, Integer messageId) {
        // 已迁移到 ProgramGetHandler
    }

    /**
     * 处理指令确认消息 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.CommandConfirmHandler}
     */
    @Deprecated
    private void handleConfirmCommand(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 CommandConfirmHandler
    }

    /**
     * 处理led_status上报的命令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.LedStatusReportHandler}
     */
    @Deprecated
    private void handleLedStatusReport(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 LedStatusReportHandler
    }

    /**
     * 处理上报下载状态的命令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.DownloadStatusReportHandler}
     */
    @Deprecated
    private void handleDownloadingReport(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 DownloadStatusReportHandler
    }

    /**
     * 处理素材播放记录报告的命令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.MediaPlayRecordReportHandler}
     */
    @Deprecated
    private void handleMediaPlayRecordReport(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 MediaPlayRecordReportHandler
    }

    /**
     * 处理节目播放记录报告的命令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.ProgramPlayRecordReportHandler}
     */
    @Deprecated
    private void handleProgramPlayRecordReport(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 ProgramPlayRecordReportHandler
    }

    /**
     * 处理传感器数据报告的命令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.SensorDataReportHandler}
     */
    @Deprecated
    private void handleSensorDataReport(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 SensorDataReportHandler
    }

    /**
     * 处理终端日志报告的命令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.TerminalLogReportHandler}
     */
    @Deprecated
    private void handleTerminalLogReport(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 TerminalLogReportHandler
    }

    /**
     * 处理数据使用警告消息 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.DataUsageAlertHandler}
     */
    @Deprecated
    private void handleDataUsageAlert(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 DataUsageAlertHandler
    }

    /**
     * 处理围栏状态报告的命令 - 已迁移到 {@link com.colorlight.terminal.infrastructure.websocket.processor.v11.handler.FenceStatusReportHandler}
     */
    @Deprecated
    private void handleFenceStatusReport(MessageProcessingContext context, V11WebsocketMessage message) {
        // 已迁移到 FenceStatusReportHandler
    }
}
