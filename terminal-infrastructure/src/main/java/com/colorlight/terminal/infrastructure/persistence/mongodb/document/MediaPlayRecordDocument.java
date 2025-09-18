package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("media_play_record")
@CompoundIndexes({
        @CompoundIndex(name = "idx_query_1", def = "{ 'deviceId' : 1, 'adjustStartTime' : -1 }")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaPlayRecordDocument {

    @Id
    private String objectId;

    private Long deviceId;

    private Integer mediaId;

    private String mediaName;

    private String mediaMd5;

    private String programName;

    private Integer pageIndex;

    private String pageName;

    private Integer regionIndex;

    private String regionName;

    private String type;

    private LocalDateTime startLocalTime;

    private LocalDateTime endLocalTime;

    private Long duration;

    private LocalDateTime adjustStartTime;
}
