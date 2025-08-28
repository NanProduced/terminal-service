package com.colorlight.terminal.infrastructure.persistence.mongodb.converter;

import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalLogDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

/**
 * 终端日志转换器
 * Domain对象与Doc对象之间的转换
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TerminalLogDocConverter {

    @Mapping(source = "operation.id", target = "operation")
    TerminalLogDocument convertToTerminalLogDocument(TerminalLog terminalLog);

    List<TerminalLogDocument> convertToTerminalLogDocumentList(List<TerminalLog> terminalLogs);
}
