package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 处理指令确认事件处理器
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandConfirmEventHandler {

    private final MainServerRpcPort mainServerRpcPort;

    /**
     * 处理指令确认事件
     * @param event 事件
     */
    @Async("rpcNotificationExecutor")
    @EventListener
    public void handleCommandConfirmEvent(CommandConfirmEvent event) {
        log.debug("CommandConfirmEvent - 处理指令确认事件: deviceId={}, commandId={}",
                event.getDeviceId(), event.getCommandId());
        // 通知主服务（使用独立RPC线程池，避免阻塞设备事件处理）
        mainServerRpcPort.notifyCommandConfirm(event);
    }

}
