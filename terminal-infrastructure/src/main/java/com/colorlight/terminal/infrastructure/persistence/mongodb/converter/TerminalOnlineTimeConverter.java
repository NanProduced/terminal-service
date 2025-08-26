package com.colorlight.terminal.infrastructure.persistence.mongodb.converter;

import com.colorlight.terminal.application.dto.record.TerminalOnlineTimeRecord;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalOnlineTimeDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

import java.time.Duration;
import java.time.LocalDateTime;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TerminalOnlineTimeConverter {

    @Mapping(target = "objectId", ignore = true)
    @Mapping(target = "duration", expression = "java(calculateDuration(record.getStartTime(), record.getEndTime()))")
    @Mapping(target = "createAt", expression = "java(java.time.LocalDateTime.now())")
    TerminalOnlineTimeDocument convertToTerminalOnlineTimeDocument(TerminalOnlineTimeRecord record);

    /**
     * 计算在线时长
     */
    default Long calculateDuration(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0L;
        }
        return Duration.between(startTime, endTime).toSeconds();
    }
}
