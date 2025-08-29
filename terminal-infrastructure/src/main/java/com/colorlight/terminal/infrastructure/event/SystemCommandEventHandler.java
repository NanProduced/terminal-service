package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.SystemCommandEvent;
import com.colorlight.terminal.application.dto.result.CommandSendResult;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 内部指令事件处理器
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemCommandEventHandler {

    private final TerminalCommandUseCase terminalCommandUseCase;

    /**
     * 处理内部指令下发
     * @param event 内部指令事件
     */
    @Async("deviceEventExecutor")
    @EventListener
    public void handleSystemCommandEvent(SystemCommandEvent event) {
        CommandSendResult commandSendResult = terminalCommandUseCase.sendCommandToDevice(event.getCommand());
        log.info("SystemCommand - 内部指令已下发: {}", commandSendResult);
    }
}
