package com.colorlight.terminal.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "终端确认指令封装")
public class DeviceApiCommandConfirm {

    private Integer parent;

    private String content;
}
