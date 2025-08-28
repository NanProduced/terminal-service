package com.colorlight.terminal.boot.converter;

import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.enums.TerminalLogType;
import com.colorlight.terminal.dto.log.DeviceApiTerminalLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

/**
 * 终端日志转换器
 * Domain对象与DeviceApi对象之间的转换
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TerminalLogConverter {

    @Mapping(target = "deviceId", ignore = true)
    @Mapping(target = "operation", expression = "java(calculateOperation(deviceApiTerminalLog))")
    @Mapping(target = "serverTime", expression = "java(java.time.LocalDateTime.now())")
    TerminalLog convertToTerminalLog(DeviceApiTerminalLog deviceApiTerminalLog);

    List<TerminalLog> convertToTerminalLog(List<DeviceApiTerminalLog> deviceApiTerminalLogs);
    
    /**
     * 计算业务日志类型
     * 基于logType和subtype组合解析对应的TerminalLogType枚举值
     * 
     * @param source DeviceApi日志对象
     * @return 解析后的业务日志类型
     */
    default TerminalLogType calculateOperation(DeviceApiTerminalLog source) {
        // 创建临时TerminalLog对象用于类型解析
        TerminalLog tempLog = TerminalLog.builder()
            .logType(source.getLogType())
            .logSubtype1(source.getLogSubtype1())
            .logSubtype2(source.getLogSubtype2())
            .logSubtype3(source.getLogSubtype3())
            .build();
        return TerminalLogType.analysisOperation(tempLog);
    }
}
