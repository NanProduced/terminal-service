package com.colorlight.terminal.application.domain.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 升级包下载进度上报
 *
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("update_status")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpgradePackageDownloadingReport extends DownloadingReport {

    private Downloading downloading;

    @Data
    public static class Downloading {
        private UpdateZip updateZip;
        private Long updateStatusTimes;
    }

    @Data
    public static class UpdateZip {
        private String status;
        private String desVersion;
        private String name;
        private Long downloaded;
        private Integer programId;
        private Long total;
    }
}
