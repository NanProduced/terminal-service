package com.colorlight.terminal.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 终端指令封装
 *
 * @author Nan
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "终端指令封装")
public class DeviceApiCommand {

    @Schema(description = "指令确认ID")
    private Integer id;

    @Schema(description = "终端Id，设备不处理")
    private Integer post;

    @Schema(description = "指令操作类型", example = "api/brightness")
    @JsonProperty("author_url")
    private String authorUrl;

    @Schema(description = "指令对象")
    private Content content;

    /**
     * 屏幕执行方式<p>
     * 0-get, 1-post, 2-put,3-delete
     */
    @Schema(description = "终端屏幕执行方式", example = "0|1|2|3")
    private Integer karma;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "指令对象封装")
    public static class Content {

        @Schema(description = "指令对应的json,{}为空")
        private String raw;
    }

}
