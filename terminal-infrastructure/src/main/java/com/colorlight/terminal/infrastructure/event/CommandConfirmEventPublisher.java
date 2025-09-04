package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.port.outbound.status.CommandConfirmEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 指令确认事件发布器
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandConfirmEventPublisher implements CommandConfirmEventPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Async("deviceEventExecutor")
    public void publishCommandConfirmEvent(CommandConfirmEvent event) {
        try {
            log.debug("CommandConfirmEvent - 发布指令确认事件: deviceId={}, commandId={}", event.getDeviceId(), event.getCommandId());
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("CommandConfirmEvent - 发布指令确认事件失败: deviceId={}, commandId={}", event.getDeviceId(), event.getCommandId());
        }
    }

}
