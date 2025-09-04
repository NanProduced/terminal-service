package com.colorlight.terminal.infrastructure.websocket.processor.v11.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TerminalLogDTO {

    @JsonProperty("led_id")
    private int deviceId;

    @JsonProperty("led_name")
    private String deviceName;

    @JsonProperty("log_type")
    private String logType;

    @JsonProperty("log_subtype1")
    private String logSubtype1;

    @JsonProperty("log_subtype2")
    private String logSubtype2;

    @JsonProperty("log_subtype3")
    private String logSubtype3;

    private String description;

    private int level;

    private String categories;

    @JsonProperty("device_time")
    private String deviceTime;

    @JsonProperty("hand_status")
    private int hand_status ;

    @JsonProperty("hand_time")
    private String handTime = "0000-10-10 00:00:00";

    @JsonProperty("log_arg1")
    private String logArg1;

    @JsonProperty("log_arg2")
    private String logArg2;

    @JsonProperty("log_arg3")
    private String logArg3;

    @JsonProperty("log_arg4")
    private String logArg4;

    @JsonProperty("log_arg5")
    private String logArg5;

    @JsonProperty("log_arg6")
    private String logArg6;

    private String others;
}
