package com.colorlight.terminal.dto.log;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "终端日志上报")
public class DeviceApiTerminalLog {

    @Schema(description = "终端Id")
    @JsonProperty("led_id")
    private int deviceId;

    @Schema(description = "终端名")
    @JsonProperty("led_name")
    private String deviceName;

    @Schema(description = "日志类型", example = "runtime")
    @JsonProperty("log_type")
    private String logType;

    @Schema(description = "一级分类", example = "storage")
    @JsonProperty("log_subtype1")
    private String logSubtype1;

    @Schema(description = "二级分类")
    @JsonProperty("log_subtype2")
    private String logSubtype2;

    @Schema(description = "三级分类")
    @JsonProperty("log_subtype3")
    private String logSubtype3;

    @Schema(description = "日志描述", example = "Storage")
    private String description;

    @Schema(description = "日志等级（0-7）")
    private int level;

    @Schema(description = "日志分类", example = "LOG_INFO")
    private String categories;

    @Schema(description = "设备时间", example = "1727403455483")
    @JsonProperty("device_time")
    private String deviceTime;

    @Schema(description = "处理状态")
    @JsonProperty("hand_status")
    private int hand_status ;

    @Schema(description = "处理时间戳")
    @JsonProperty("hand_time")
    private String handTime = "0000-10-10 00:00:00";

    @Schema(description = "日志参数1", example = "3958988800")
    @JsonProperty("log_arg1")
    private String logArg1;

    @Schema(description = "日志参数2")
    @JsonProperty("log_arg2")
    private String logArg2;

    @Schema(description = "日志参数3")
    @JsonProperty("log_arg3")
    private String logArg3;

    @Schema(description = "日志参数4")
    @JsonProperty("log_arg4")
    private String logArg4;

    @Schema(description = "日志参数5")
    @JsonProperty("log_arg5")
    private String logArg5;

    @Schema(description = "日志参数6")
    @JsonProperty("log_arg6")
    private String logArg6;

    @Schema(description = "其他信息")
    private String others;
}
