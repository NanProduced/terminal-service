package com.colorlight.terminal.infrastructure.generator;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Component;

/**
 * 转换GPS文档数据时填充索引信息的辅助类
 *
 * @author Nan
 */
@Component
@RequiredArgsConstructor
public class GpsIndexesGenerator {

    private final TerminalStatsConfigProperties statsConfig;

    /**
     * 创建Mongo GeoJson索引
     * @param report 上报数据
     * @return GeoJsonPoint
     */
    public GeoJsonPoint createGeoJsonPoint(GpsReport report) {
        return new GeoJsonPoint(report.getLongitude(), report.getLatitude());
    }

    /**
     * 创建Google S2 geometry cellId
     * @param report 上报数据
     * @return cellId
     */
    public String generateS2CellId(GpsReport report) {
        S2LatLng latLng = S2LatLng.fromDegrees(report.getLatitude(), report.getLongitude());
        int level = statsConfig.getGps().getDefaultS2CellLevel();
        return S2CellId.fromLatLng(latLng).parent(level).toToken();
    }

    /**
     * 获取默认cell精度
     * @return 默认精度
     */
    public Integer getConfiguredCellLevel() {
        return statsConfig.getGps().getDefaultS2CellLevel();
    }

}
