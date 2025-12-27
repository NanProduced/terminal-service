package com.colorlight.terminal.infrastructure.websocket.processor.v10;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.sensor.SensorReport;
import com.colorlight.terminal.application.dto.websocket.v10.V10WebsocketMessage;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolMessageProcessor;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * V1.0协议消息处理器 - Infrastructure层实现
 * 
 * <p>V1.0协议特点：</p>
 * <ul>
 *   <li>消息格式：标准JSON格式，包含content和gps字段</li>
 *   <li>业务处理：主要处理GPS传感器数据上报</li>
 * </ul>
 * 
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class V10ProtocolMessageProcessor implements ProtocolMessageProcessor {
    
    private final TerminalReportUseCase terminalReportUseCase;

    /**
     * V1.0协议的心跳响应消息
     */
    private static final String HEARTBEAT_FIELD = "heartbeat";
    
    @Override
    public ProtocolVersion getSupportedVersion() {
        return ProtocolVersion.V1_0;
    }
    
    @Override
    public TextMessageProcessResult processTextMessage(MessageProcessingContext context) {
        try {
            log.debug("V10ProtocolMessageProcessor - 处理V1.0文本消息: deviceId={}, message={}",
                     context.getDeviceId(), context.getRawMessage());

            // 心跳
            if (StringUtils.isBlank(context.getRawMessage()) || "{}".equals(context.getRawMessage())) {
                return handleHeartbeat(context);
            }

            final V10WebsocketMessage message = JsonUtils.fromJson(context.getRawMessage(), V10WebsocketMessage.class);

            // 心跳
            if (HEARTBEAT_FIELD.equalsIgnoreCase(message.getContent())) {
                return handleHeartbeat(context);
            }

            // 处理GPS传感器数据（V1.0协议的主要业务逻辑）
            if (!CollectionUtils.isEmpty(message.getGps())) {
                return handleGpsMessage(context, message);
            }

            else {
                log.warn("V10ProtocolMessageProcessor - 未知消息类型: deviceId={}, message={}",
                         context.getDeviceId(), context.getRawMessage());
                return TextMessageProcessResult.ofSuccess(false);
            }
            
        } catch (Exception e) {
            log.error("V10ProtocolMessageProcessor - V1.0文本消息处理失败", e);
            return TextMessageProcessResult.ofFailure("V1.0消息处理异常: " + e.getMessage());
        }
    }

    /**
     * 处理心跳
     * @return 返回成功
     */
    private TextMessageProcessResult handleHeartbeat(MessageProcessingContext context) {
        if (context.sendMessage(HEARTBEAT_FIELD)) {
            // 心跳没有业务逻辑
            return TextMessageProcessResult.ofSuccess(true);
        }
        return TextMessageProcessResult.ofFailure("PONG消息发送失败");
    }

    private TextMessageProcessResult handleGpsMessage(MessageProcessingContext context, V10WebsocketMessage message) {

        log.debug("V10ProtocolMessageProcessor - 处理GPS数据: deviceId={}, count={}",
                context.getDeviceId(), message.getGps().size());

        // 直接传递List对象给应用服务
        List<SensorReport> sensorReports = new ArrayList<>(message.getGps());
        terminalReportUseCase.asyncHandleSensorReport(
                context.getDeviceId(),
                LocalDateTime.now(),
                sensorReports
        );

        log.debug("V10ProtocolMessageProcessor - GPS数据处理提交成功: deviceId={}",
                context.getDeviceId());

        return TextMessageProcessResult.ofSuccess(false);
    }

}