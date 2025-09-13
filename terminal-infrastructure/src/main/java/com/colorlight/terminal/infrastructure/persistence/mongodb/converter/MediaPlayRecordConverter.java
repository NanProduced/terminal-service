package com.colorlight.terminal.infrastructure.persistence.mongodb.converter;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.MediaPlayRecordDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

/**
 * 素材统计domain/doc转换
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface MediaPlayRecordConverter {

    @Mapping(target = "deviceId", source = "deviceId")
    @Mapping(target = "mediaName", source = "report.resOriginName")
    @Mapping(target = "mediaMd5", source = "report.resMd5Name")
    @Mapping(target = "type", source = "report.itemType")
    MediaPlayRecordDocument convertToMediaPlayRecordDocument(Long deviceId, MediaPlayRecordReport report);

    @Named("convertWithDeviceId")
    default List<MediaPlayRecordDocument> convertToMediaPlayRecordDocumentList(Long deviceId, List<MediaPlayRecordReport> reports) {
        return reports.stream()
                .map(report -> convertToMediaPlayRecordDocument(deviceId, report))
                .toList();
    }
}
