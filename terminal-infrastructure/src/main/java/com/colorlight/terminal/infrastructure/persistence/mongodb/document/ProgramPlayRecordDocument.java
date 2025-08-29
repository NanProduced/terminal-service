package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 节目播放上报记录存储
 *
 * @author Nan
 */
@Data
@Document("program_play_record")
@CompoundIndexes({
    @CompoundIndex(name = "idx_query_1", def = "{'deviceId': 1, 'startUtcTime': -1}"),
    @CompoundIndex(name = "idx_query_2", def = "{'programId': 1, 'startUtcTime': -1}")
})
public class ProgramPlayRecordDocument {

    @Id
    private String objectId;

    private Long deviceId;

    private Integer programId;

    private String programName;

    private LocalDateTime startLocalTime;

    private LocalDateTime endLocalTime;

    private LocalDateTime startUtcTime;

    private LocalDateTime endUtcTime;

    /**
     * 播放次数
     */
    private Integer playTimes;

    /**
     * 节目时长
     */
    private Long singleDuration;

    /**
     * 播放时长
     */
    private Long playDuration;
}
