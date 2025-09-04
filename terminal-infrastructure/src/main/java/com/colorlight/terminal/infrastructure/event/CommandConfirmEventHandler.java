package com.colorlight.terminal.infrastructure.event;

import com.colorlight.ccloud.command.dto.CommandFinishDto;
import com.colorlight.ccloud.command.enums.CommandStatusEnum;
import com.colorlight.ccloud.command.interfaces.CommandFinishFacade;
import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
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
    @Async("deviceEventExecutor")
    @EventListener
    public void handleCommandConfirmEvent(CommandConfirmEvent event) {
        log.debug("CommandConfirmEvent - 处理指令确认事件: deviceId={}, commandId={}",
                event.getDeviceId(), event.getCommandId());
        // 通知主服务
        mainServerRpcPort.notifyCommandConfirm(event);
    }

}
