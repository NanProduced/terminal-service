package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 终端日志文档
 *
 * @author Nan
 */
@Data
@Document("terminal_log")
@CompoundIndexes({
        @CompoundIndex(name = "idx_query_1", def = "{ 'serverTime' : -1 , 'terminalId' : 1 , 'operation' : 1 }")
})
public class TerminalLogDocument {

    @Id
    private String objectId;

    private Long deviceId;

    private String description;

    private Integer operation;

    private String logArg1;

    private String logArg2;

    private String logArg3;

    private String logArg4;

    private String logArg5;

    private String logArg6;

    private String deviceTime;

    /**
     * 查询时间范围字段/TTL索引
     */
    @Indexed(expireAfterSeconds = 15552000)
    private LocalDateTime serverTime;


}
