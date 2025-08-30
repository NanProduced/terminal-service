package com.colorlight.terminal.infrastructure.persistence.mongodb.converter;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.infrastructure.generator.GpsIndexesGenerator;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.GpsRecordDocument;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

/**
 * GPS转换器
 *
 * @author Nan
 */
@Mapper(
        componentModel = "spring",
        uses = {GpsIndexesGenerator.class},
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface GpsRecordConverter {

    @Mapping(target = "objectId", ignore = true)
    @Mapping(target = "point", expression = "java(gpsIndexesGenerator.createGeoJsonPoint(gpsReport))")
    @Mapping(target = "cellId", expression = "java(gpsIndexesGenerator.generateS2CellId(gpsReport))")
    @Mapping(target = "cellLevel", expression = "java(gpsIndexesGenerator.getConfiguredCellLevel())")
    GpsRecordDocument convertToGpsDocument(GpsReport gpsReport,
                                           @Context GpsIndexesGenerator gpsIndexesGenerator);


    List<GpsRecordDocument> convertToGpsDocumentList(List<GpsReport> gpsReports, @Context GpsIndexesGenerator gpsIndexesGenerator);

}
