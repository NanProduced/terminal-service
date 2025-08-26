package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@Document("terminal_online_time")
public class TerminalOnlineTimeDocument {

    @Id
    private String objectId;

    private Long deviceId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long duration;

    private LocalDateTime createAt;
}
