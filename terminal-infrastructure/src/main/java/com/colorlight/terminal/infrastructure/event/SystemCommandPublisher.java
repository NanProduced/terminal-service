package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.SystemCommandEvent;
import com.colorlight.terminal.application.handler.SystemCommandHelper;
import com.colorlight.terminal.application.port.outbound.command.SystemCommandPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 内部指令事件发布器
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemCommandPublisher implements SystemCommandPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void requestTimeZoneReport(Long deviceId, String trigger) {
        SystemCommandEvent event = SystemCommandEvent.builder()
                .businessScene(SystemCommandEvent.BusinessScene.DEVICE_META_DATA)
                .triggerBeanName(trigger)
                .command(SystemCommandHelper.generateTimeReportCommand(deviceId))
                .build();
        applicationEventPublisher.publishEvent(event);
        log.info("SystemCommand - 内部指令事件已下发: 请求上报设备时间及时区配置; deviceId={}", deviceId);
    }
}
