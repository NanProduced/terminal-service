package com.colorlight.terminal.application.domain.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;


/**
 * 下载进度上报基类
 *
 * @author Nan
 */
@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "what",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProgramDownloadingReport.class, name = "program_status"),
        @JsonSubTypes.Type(value = UpgradePackageDownloadingReport.class, name = "update_status")
})
public class DownloadingReport {

    private String what;
}
