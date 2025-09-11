package com.colorlight.terminal.rpc.dto.request;

import com.colorlight.terminal.rpc.dto.command.TerminalCommandDTO;

import java.io.Serializable;

/**
 * 单个指令下发请求DTO
 *
 * @author Nan
 */
public class SingleCommandRequestDTO implements Serializable {

    private static final long serialVersionUID = 3662221073422166623L;

    private Long deviceId;

    private TerminalCommandDTO command;

    public SingleCommandRequestDTO() {}

    public SingleCommandRequestDTO(Long deviceId, TerminalCommandDTO command) {
        this.deviceId = deviceId;
        this.command = command;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public TerminalCommandDTO getCommand() {
        return command;
    }

    public void setCommand(TerminalCommandDTO command) {
        this.command = command;
    }
}
