package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@Document("terminal_online_time")
@CompoundIndexes({
        @CompoundIndex(name = "idx_query_1", def = "{ 'deviceId' : 1, 'startTime' : -1 }")
})
public class TerminalOnlineTimeDocument {

    @Id
    private String objectId;

    private Long deviceId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long duration;

    private LocalDateTime createAt;
}
