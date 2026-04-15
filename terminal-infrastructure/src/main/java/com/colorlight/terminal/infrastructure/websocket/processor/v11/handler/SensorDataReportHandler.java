package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.sensor.SensorReport;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 传感器数据上报消息处理器
 * 处理 MONITOR_REPORT 类型消息，用于设备上报传感器数据
 *
 * <p>注意：由于V11WebsocketMessage.data是Object类型，JSON反序列化后会产生ArrayList<LinkedHashMap>
 * 而不是ArrayList<SensorReport>，因此需要使用JsonUtils.convertValue()进行类型转换</p>
 *
 * @author Codex
 */
@Slf4j
@Component
public class SensorDataReportHandler implements V11MessageHandler {

    private final TerminalReportUseCase terminalReportUseCase;

    public SensorDataReportHandler(TerminalReportUseCase terminalReportUseCase) {
        this.terminalReportUseCase = terminalReportUseCase;
    }

    @Override
    public V11WebsocketMessageTypeEnum getSupportedType() {
        return V11WebsocketMessageTypeEnum.MONITOR_REPORT;
    }

    @Override
    public void handle(MessageProcessingContext context, V11WebsocketMessage message) {
        LocalDateTime now = LocalDateTime.now();
        Long deviceId = context.getDeviceId();

        List<SensorReport> reports;
        if (Objects.isNull(message.getData())) {
            reports = List.of();
        } else {
            try {
                reports = JsonUtils.convertValue(message.getData(), new TypeReference<List<SensorReport>>() {});
                if (reports == null) {
                    reports = List.of();
                }
            } catch (Exception e) {
                log.error("SensorDataReportHandler -ws- #MONITOR_REPORT#【传感器数据转换失败】deviceId:{}, data:{}",
                        deviceId, message.getData(), e);
                reports = List.of();
            }
        }

        terminalReportUseCase.asyncHandleSensorReport(deviceId, now, reports);
        log.info("SensorDataReportHandler -ws- #MONITOR_REPORT#【上报监控数据】 deviceId:{}", deviceId);
        context.sendMessage(new V11WebsocketMessage(
                V11WebsocketMessageTypeEnum.MONITOR_REPORT.getId(), message.getMessageId()));
    }
}
