package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "mileage_aggregation")
@CompoundIndex(name = "query_1", def = "{'deviceId': 1, 'windowStartTime': -1}")
public class MileageAggDocument {

    @Id
    private String objectId;

    private Long deviceId;

    private LocalDateTime windowStartTime;

    private Double totalMileage;

    private Integer gpsPointCount;

    private GpsPointData firstGpsPoint;

    private GpsPointData lastGpsPoint;

    private LocalDateTime updateAt;
    private LocalDateTime createAt;


    @Data
    public static class GpsPointData {

        private Double latitude;

        private Double longitude;

        private LocalDateTime reportTime;
    }
}
