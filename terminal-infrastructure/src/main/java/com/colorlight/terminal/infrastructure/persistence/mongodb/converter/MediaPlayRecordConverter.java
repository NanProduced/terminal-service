package com.colorlight.terminal.infrastructure.persistence.mongodb.converter;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.MediaPlayRecordDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import com.colorlight.terminal.application.dto.rpc.MediaInfo;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;
import java.util.Map;

/**
 * 素材统计domain/doc转换
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface MediaPlayRecordConverter {

    @Mapping(target = "deviceId", source = "deviceId")
    @Mapping(target = "mediaName", ignore = true)
    @Mapping(target = "mediaMd5", source = "report.resMd5Name")
    @Mapping(target = "type", source = "report.itemType")
    @Mapping(target = "mediaId", ignore = true)
    MediaPlayRecordDocument convertToMediaPlayRecordDocument(Long deviceId, MediaPlayRecordReport report);

    @Named("convertWithDeviceId")
    default List<MediaPlayRecordDocument> convertToMediaPlayRecordDocumentList(Long deviceId, List<MediaPlayRecordReport> reports) {
        return reports.stream()
                .map(report -> convertToMediaPlayRecordDocument(deviceId, report))
                .toList();
    }

    @Named("convertWithDeviceIdAndMediaIdMap")
    default List<MediaPlayRecordDocument> convertToMediaPlayRecordDocumentList(Long deviceId, List<MediaPlayRecordReport> reports, Map<String, MediaInfo> mediaIdMap) {
        return reports.stream()
                .map(report -> {
                    MediaPlayRecordDocument document = convertToMediaPlayRecordDocument(deviceId, report);
                    // 根据素材名称设置素材ID
                    document.setMediaId(mediaIdMap.get(document.getMediaMd5()).getMediaId());
                    document.setMediaName(mediaIdMap.get(document.getMediaMd5()).getMediaName());
                    return document;
                })
                .toList();
    }
}
