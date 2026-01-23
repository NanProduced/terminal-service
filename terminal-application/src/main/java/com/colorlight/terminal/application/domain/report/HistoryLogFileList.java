package com.colorlight.terminal.application.domain.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryLogFileList {

    private List<HistoryLogFile> files;


    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HistoryLogFile {

        private String name;

        private Long size;

        private Long lastModify;

    }
}
