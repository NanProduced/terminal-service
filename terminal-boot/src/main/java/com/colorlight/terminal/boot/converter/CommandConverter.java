package com.colorlight.terminal.boot.converter;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.dto.command.DeviceApiCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

/**
 * 指令转换器
 * Domain对象与DeviceApi对象之间的转换
 * 
 * @author Nan
 * @version 1.0.0
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface CommandConverter {

    /**
     * 领域对象转换为设备API对象
     * 
     * @param terminalCommand 领域指令对象
     * @return 设备API指令对象
     */
    @Mapping(source = "commandId", target = "id")
    @Mapping(source = "deviceId", target = "post", qualifiedByName = "longToInteger")
    @Mapping(source = "authorUrl", target = "authorUrl")
    @Mapping(source = "contentRaw", target = "content", qualifiedByName = "stringToContent")
    @Mapping(source = "karma", target = "karma")
    DeviceApiCommand convert2DeviceApiCommand(TerminalCommand terminalCommand);

    List<DeviceApiCommand> convert2DeviceApiCommandList(List<TerminalCommand> terminalCommandList);

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
    default DeviceApiCommand.Content stringToContent(String raw) {
        return raw != null ? new DeviceApiCommand.Content(raw) : null;
    }
}
