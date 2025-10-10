package com.colorlight.terminal.dto.program;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 终端获取节目响应
 *
 * @author Nan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "终端获取节目响应")
public class DeviceApiProgram {

    @Schema(description = "节目Id")
    private Integer id;

    @Schema(description = "创建时间")
    private String date;

    @Schema(description = "GMT创建时间")
    private String dateGmt;

    @Schema(description = "修改时间")
    private String modified;

    @Schema(description = "GMT修改时间")
    private String modifiedGmt;

    @Schema(description = "节目类型")
    private String type;

    @Schema(description = "节目名")
    private Title title;

    @Schema(description = "获取素材接口")
    @JsonProperty("_links")
    private Links links;

    @Data
    @Schema(description = "节目名对象，设计如此")
    public static class Title {

        @Schema(description = "节目名")
        private String rendered;
    }

    @Data
    @Schema(description = "获取素材接口")
    public static class Links {

        @Schema(description = "节目地址对象")
        @JsonProperty("wp:attachment")
        private List<AttachmentUrl> attachmentUrls;
    }

    @Data
    @Schema(description = "节目地址对象")
    public static class AttachmentUrl {

        @Schema(description = "节目地址：设备根据这个地址请求", example = "/wp-json/wp/v2/media?parent=1")
        private String href;
    }
}
