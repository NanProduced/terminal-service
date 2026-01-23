package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;


@Document("device_local_logs")
@Data
@Builder
@CompoundIndex(name = "device_id", def = "{ 'deviceId' : 1 }")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceLocalLogDocument {

    @Id
    private String objectId;

    private Long deviceId;

    private List<Log> logs;

    private LocalDateTime updateTime;


    @Data
    @AllArgsConstructor
    public static class Log {

        private String name;

        private Long size;

        private Long lastModify;

    }

}
