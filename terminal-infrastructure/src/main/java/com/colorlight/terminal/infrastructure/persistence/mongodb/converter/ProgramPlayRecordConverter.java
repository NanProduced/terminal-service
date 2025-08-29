package com.colorlight.terminal.infrastructure.persistence.mongodb.converter;

import com.colorlight.terminal.application.domain.report.ProgramPlayRecordReport;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.ProgramPlayRecordDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

/**
 * 节目统计转换 domain/doc
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface ProgramPlayRecordConverter {

    @Mapping(target = "startLocalTime", expression = "java(report.getStartLocalTime() != null && !report.getStartLocalTime().isEmpty() ? report.getStartLocalTime().getFirst() : null)")
    @Mapping(target = "endLocalTime", expression = "java(report.getEndLocalTime() != null && !report.getEndLocalTime().isEmpty() ? report.getEndLocalTime().getLast() : null)")
    @Mapping(target = "startUtcTime", expression = "java(report.getStartUtcTime() != null && !report.getStartUtcTime().isEmpty() ? report.getStartUtcTime().getFirst() : null)")
    @Mapping(target = "endUtcTime", expression = "java(report.getEndUtcTime() != null && !report.getEndUtcTime().isEmpty() ? report.getEndUtcTime().getLast() : null)")
    ProgramPlayRecordDocument convertToProgramPlayRecordDocument(ProgramPlayRecordReport report);


    List<ProgramPlayRecordDocument> convertToProgramRecordDocumentList(List<ProgramPlayRecordReport> reports);
}
