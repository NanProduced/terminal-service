package com.colorlight.terminal.application.domain.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 节目下载进度上报
 *
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("program_status")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProgramDownloadingReport extends DownloadingReport {

    private Downloading downloading;

    @Data
    public static class Downloading {
        private List<Program> programs;
        private Long downloadStatusTime;
    }

    @Data
    public static class Program {
        private Integer id;
        private String name;
        private List<File> files;
    }

    @Data
    public static class File {
        private Integer programId;
        private String name;
        private Long downloaded;
        private Long total;
        private String downloadUrl;
    }
}
