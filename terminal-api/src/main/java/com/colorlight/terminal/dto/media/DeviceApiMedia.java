package com.colorlight.terminal.dto.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "终端获取素材响应")
public class DeviceApiMedia {

    @Schema(description = "素材字节大小")
    @JsonProperty("attachment_filesize")
    private Integer attachmentFileSize;

    @Schema(description = "文件命名格式为XXX_文件MD5_文件字节数.vsn")
    @JsonProperty("source_url")
    private String sourceUrl;
}
