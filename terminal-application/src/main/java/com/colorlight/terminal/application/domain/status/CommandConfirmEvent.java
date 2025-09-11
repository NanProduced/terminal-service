package com.colorlight.terminal.application.domain.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommandConfirmEvent {

    private Long deviceId;

    private String commandId;

    private boolean success;

    public static CommandConfirmEvent success(Long deviceId, Integer commandId) {
        return new CommandConfirmEvent(deviceId, String.valueOf(commandId), true);
    }

    public static CommandConfirmEvent failed(Long deviceId, Integer commandId) {
        return new CommandConfirmEvent(deviceId, String.valueOf(commandId), false);
    }
}
