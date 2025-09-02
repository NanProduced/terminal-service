package com.colorlight.terminal.infrastructure.websocket.processor.v11.converter;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.enums.TerminalLogType;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.CommandResponse;
import com.colorlight.terminal.infrastructure.websocket.processor.v11.dto.TerminalLogDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface V11WebsocketDtoConverter {


    @Mapping(source = "commandId", target = "id")
    @Mapping(source = "deviceId", target = "post", qualifiedByName = "longToInteger")
    @Mapping(source = "authorUrl", target = "authorUrl")
    @Mapping(source = "contentRaw", target = "content", qualifiedByName = "stringToContent")
    @Mapping(source = "karma", target = "karma")
    CommandResponse convertToCommandResponse(TerminalCommand terminalCommand);

    List<CommandResponse> convertToCommandResponses(List<TerminalCommand> terminalCommands);

    @Mapping(target = "deviceId", ignore = true)
    @Mapping(target = "operation", expression = "java(calculateOperation(dto))")
    @Mapping(target = "serverTime", expression = "java(java.time.LocalDateTime.now())")
    TerminalLog convertToTerminalLog(TerminalLogDTO dto);

    List<TerminalLog> convertToTerminalLogs(List<TerminalLogDTO> dto);

    default TerminalLogType calculateOperation(TerminalLogDTO source) {
        // 创建临时TerminalLog对象用于类型解析
        TerminalLog tempLog = TerminalLog.builder()
                .logType(source.getLogType())
                .logSubtype1(source.getLogSubtype1())
                .logSubtype2(source.getLogSubtype2())
                .logSubtype3(source.getLogSubtype3())
                .build();
        return TerminalLogType.analysisOperation(tempLog);
    }

    /**
     * Long类型deviceId转换为Integer类型post
     */
    @Named("longToInteger")
    default Integer longToInteger(Long value) {
        return value != null ? value.intValue() : null;
    }

    /**
     * 字符串contentRaw转换为Content对象
     */
    @Named("stringToContent")
    default CommandResponse.Content stringToContent(String raw) {
        return raw != null ? new CommandResponse.Content(raw) : null;
    }
}
