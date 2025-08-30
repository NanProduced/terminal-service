package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("terminal_status_report")
public class TerminalStatusReportDocument {

    @Id
    private String objectId;

    @Indexed
    private Long deviceId;

    private TerminalStatusReport terminalStatusReport;

    private LocalDateTime updateTime;
}
