package com.colorlight.terminal.application.port.outbound.status;

import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;

/**
 * 设备指令确认事件发布端口
 *
 * @author Nan
 */
public interface CommandConfirmEventPort {

    /**
     * 发布指令确认事件
     *
     * @param event 指令确认
     */
    void publishCommandConfirmEvent(CommandConfirmEvent event);
}
