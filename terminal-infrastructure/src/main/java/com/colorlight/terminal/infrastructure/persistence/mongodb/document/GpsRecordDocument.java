package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("gps_record")
@CompoundIndexes({
        @CompoundIndex(name = "idx_query_1", def = "{'deviceId': 1, 'serverTime': -1, 'cellId': 1}"),
        @CompoundIndex(name = "idx_geo_device", def = "{'deviceId': 1, 'point': '2dsphere'}")
})
public class GpsRecordDocument {

    @Id
    private String objectId;
    /**
     * 终端 Id
     */
    private Long deviceId;
    /**
     * 终端上报时间
     */
    private LocalDateTime deviceTime;
    /**
     * 服务器时间
     */
    private LocalDateTime serverTime;
    /**
     * 传感器 Id
     */
    private Integer sensorId;
    /**
     * 经度
     */
    private Double longitude;
    /**
     * 纬度
     */
    private Double latitude;
    /**
     * 精度
     */
    private Double accuracy;
    /**
     * 海拔
     */
    private Double altitude;
    /**
     * 速度
     */
    private Double speed;
    /**
     * 方向（不确定）
     */
    private Double direct;
    /**
     * 卫星
     */
    private Integer satellites;

    /*

    以下两项暂时不存

    private CellInfo cellInfo;

    private List<GsvDTO> gsv;

     */

    /*============= Geo索引相关 =============*/

    /**
     * MongoDB GeoJson索引
     * <p>精准地理查询</p>
     */
    private GeoJsonPoint point;

    /**
     * google s2 geometry Id
     * <p>空间聚合</p>
     */
    private String cellId;

    /**
     * google s2 geometry 精度
     */
    private Integer cellLevel;
}
