package com.colorlight.terminal.boot.controller;

import com.colorlight.terminal.api.DeviceInteractionApi;
import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.dto.record.ScreenshotUploadRecord;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.application.port.inbound.program.TerminalProgramUseCase;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.boot.converter.CommandConverter;
import com.colorlight.terminal.boot.converter.TerminalLogConverter;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import com.colorlight.terminal.dto.command.DeviceApiCommand;
import com.colorlight.terminal.dto.command.DeviceApiCommandConfirm;
import com.colorlight.terminal.dto.log.DeviceApiTerminalLog;
import com.colorlight.terminal.dto.media.DeviceApiMedia;
import com.colorlight.terminal.dto.program.DeviceApiProgram;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备交互控制器 - 实现设备二次开发文档中的必须接口
 * <p>由于老版本遗留问题使用Integer做设备Id，这里进行适配使用Auth认证中的Long Id</p>
 * 
 * @author Nan
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "设备交互API", description = "终端设备与服务器直接进行交互的API")
public class DeviceInteractionController implements DeviceInteractionApi {
    
    private final TerminalCommandUseCase terminalCommandUseCase;
    private final TerminalReportUseCase terminalReportUseCase;
    private final TerminalProgramUseCase terminalProgramUseCase;
    private final CommandConverter commandConverter;
    private final TerminalLogConverter terminalLogConverter;

    /**
     * 上报终端信息，设备上报led_status到服务器。
     *
     * @param report 设备上报的led状态信息
     */
    @Operation(
            summary = "上报终端信息",
            description = "设备上报led_status到服务器",
            tags = {"终端上报"}
    )
    @Override
    public ResponseEntity<Void> reportTerminalStatus(String report) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("DeviceReport - 收到上报消息: deviceId={}, report={}", principal.getDeviceId(), report);
        terminalReportUseCase.saveLedStatus(principal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * 获取终端指令的方法。此方法允许终端通过HTTP方式请求其待执行的指令。
     *
     * @param cltType 客户端类型，当前实现中未使用该参数
     * @param deviceNum 设备编号，当前实现中未使用该参数
     * @return 待执行的设备API命令列表；如果发生异常，则返回一个空列表
     */
    @Operation(
            summary = "终端获取指令",
            description = "终端通过HTTP方式获取指令",
            tags = {"终端指令"}
    )
    @Override
    public List<DeviceApiCommand> getCommands(String cltType, String deviceNum) {
        // cltType、deviceNum这两个参数没用，历史问题
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            // 获取待执行指令
            List<TerminalCommand> commands = terminalCommandUseCase.getPendingCommands(principal.getDeviceId());
            
            // 转换为API格式
            List<DeviceApiCommand> apiCommands = commandConverter.convert2DeviceApiCommandList(commands);
            
            log.info("DeviceCommand - 返回 {} 条指令给设备: {}", apiCommands.size(), principal.getDeviceId());
            return apiCommands;
            
        } catch (Exception e) {
            log.error("DeviceCommand - 获取设备指令异常, deviceNum: {}", principal.getDeviceId(), e);
            return List.of(); // 返回空列表避免设备端异常
        }
    }

    /**
     * 终端确认指令的方法。此方法允许终端通过HTTP方式来确认接收到的指令。
     *
     * @param post 该参数在此上下文中未被使用，但作为方法签名的一部分保留。
     * @param commandConfirm 包含待确认命令信息的对象，其中包含parent（父命令ID）和content（确认内容）等关键字段。
     */
    @Operation(
            summary = "终端确认指令",
            description = "终端通过HTTP方式确认指令",
            tags = {"终端指令"}
    )
    @Override
    public ResponseEntity<Void> confirmCommand(Integer post, DeviceApiCommandConfirm commandConfirm) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long deviceId = principal.getDeviceId();
        log.info("DeviceCommandConfirm - 设备确认指令执行, deviceId: {}, confirmId: {}, content: {}",
                deviceId, commandConfirm.getParent(), commandConfirm.getContent());
        try {
            // 参数验证
            if (commandConfirm.getParent() == null) {
                log.warn("DeviceCommandConfirm - 指令确认参数无效, commandId = null");
                throw new DeviceResponseException(CommonErrorCode.PARAMETER_MISSING);
            }
            
            // 调用业务逻辑确认指令
            terminalCommandUseCase.confirmCommandExecution(
                    deviceId,
                    commandConfirm.getParent(), 
                    commandConfirm.getContent()
            );
            
            log.info("DeviceCommandConfirm - 指令确认处理完成, deviceId: {}, confirmId: {}", deviceId, commandConfirm.getParent());
            return ResponseEntity.noContent().build();
            
        } catch (Exception e) {
            log.error("DeviceCommandConfirm - 指令确认异常, deviceId: {}, confirmId: {}", deviceId, commandConfirm.getParent(), e);
            throw new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 终端通过HTTP接口获取节目信息。
     *
     * @param clt_type 客户端类型，用于区分不同的终端设备
     * @return 返回一个包含节目信息的列表，如果未找到相关节目，则返回空列表
     */
    @Operation(
            summary = "终端获取节目",
            description = "终端通过HTTP接口获取节目信息",
            tags = {"终端节目"}
    )
    @Override
    public List<DeviceApiProgram> getPrograms(String clt_type) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("DeviceProgram - 终端 {} 获取节目", principal.getDeviceId());
        // todo：实现获取节目接口
        return List.of();
    }

    /**
     * 终端获取素材
     *
     * @param parent 素材的父级标识符，用于指定从哪个层级开始检索素材
     * @return 返回一个包含DeviceApiMedia对象的列表，表示终端可获取的素材信息
     */
    @Operation(
            summary = "终端获取素材",
            description = "终端通过HTTP接口获取素材信息",
            tags = {"终端节目"}
    )
    @Override
    public List<DeviceApiMedia> getMedia(Integer parent) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("DeviceMedia - 终端 {} 获取素材", principal.getDeviceId());
        // todo：实现获取素材接口
        return List.of();
    }

    /**
     * 终端上报素材播放记录。
     * 该方法允许终端通过HTTP接口上传其播放的素材记录。此功能主要用于收集和分析终端播放内容的数据。
     *
     * @param report 素材播放记录的字符串表示，包含了播放的具体信息。如果传入的字符串为空或仅包含空白字符，则不会进行处理。
     */
    @Operation(
            summary = "终端上报素材播放记录",
            description = "终端通过HTTP接口上传素材播放记录",
            tags = {"终端节目"}
    )
    @Override
    public ResponseEntity<Void> reportMediaPlayRecords(String report) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("DeviceMedia - 终端 {} 上报素材播放记录: {}", principal.getDeviceId(), report);
        if (StringUtils.isBlank(report)) return ResponseEntity.status(HttpStatus.CREATED).build();
        terminalReportUseCase.asyncHandleMediaPlayRecordReport(principal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * 终端上报节目播放记录的方法。
     * 该方法通过HTTP接口接收终端发送的节目播放记录，并进行相应的处理，如日志记录等。
     *
     * @param report 节目播放记录的字符串表示形式。这应该包含足够的信息以描述节目的播放情况。
     */
    @Operation(
            summary = "终端上报节目播放记录",
            description = "终端通过HTTP接口上传节目播放记录",
            tags = {"终端节目"}
    )
    @Override
    public ResponseEntity<Void> reportProgramPlayRecords(String report) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("DeviceProgram - 终端 {} 上报节目播放记录: {}", principal.getDeviceId(), report);
        if (StringUtils.isBlank(report)) return ResponseEntity.status(HttpStatus.CREATED).build();
        terminalReportUseCase.asyncHandleProgramPlayRecordReport(principal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * 终端获取排程信息。此方法允许终端通过HTTP接口请求其排程信息。
     *
     * @return 返回一个字符串，表示终端的排程信息。当前实现中总是返回null，可能需要根据实际业务逻辑进行调整以返回具体的排程数据。
     */
    @Operation(
            summary = "终端获取排程信息",
            description = "终端通过HTTP接口获取排程信息",
            tags = {"终端节目"}
    )
    @Override
    public String getSchedule() {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("DeviceSchedule - 终端 {} 请求获取排程", principal.getDeviceId());
        return terminalProgramUseCase.getSchedule(principal.getDeviceId());
    }

    @Operation(
            summary = "终端上报传感器监控数据",
            description = "终端通过HTTP接口上报传感器监控数据",
            tags = {"终端节目"}
    )
    @Override
    public ResponseEntity<Void> reportSensorData(String report) {
        LocalDateTime now = LocalDateTime.now();
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("DeviceSensorData - 终端 {} 上报传感器数据: {}", principal.getDeviceId(), report);
        if (StringUtils.isBlank(report)) return ResponseEntity.status(HttpStatus.CREATED).build();
        terminalReportUseCase.asyncHandleSensorReport(principal.getDeviceId(), now, report);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 该方法用于处理终端通过HTTP接口上报的日志信息。
     *
     * @param logs 终端日志列表，包含待上报的所有日志条目。每个条目应为DeviceApiTerminalLog类型的对象，代表一条具体的终端日志。
     */
    @Operation(
            summary = "终端上报日志",
            description = "终端通过HTTP接口上报终端日志",
            tags = {"终端日志"}

    )
    @Override
    public ResponseEntity<Void> reportTerminalLog(List<DeviceApiTerminalLog> logs) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("DeviceSchedule - 终端 {} 上报终端日志: {}", principal.getDeviceId(), logs);
        List<TerminalLog> terminalLogs = terminalLogConverter.convertToTerminalLog(logs);
        terminalReportUseCase.asyncSaveTerminalLog(principal.getDeviceId(), terminalLogs);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * 终端上报屏幕截图。此方法允许设备通过二进制流的形式上传其屏幕截图到服务器。
     * 支持标准HTTP和分块传输编码(chunked)两种上传方式。
     *
     * @param request 包含了要上传的屏幕截图数据的HTTP请求对象，其中截图数据应作为请求体以二进制流形式提供。
     * @return ResponseEntity<Void> 返回一个表示操作结果的状态码，成功时返回200 OK，若发生错误则根据具体异常情况返回相应的状态码。
     */
    @Operation(
            summary = "终端上报屏幕截图",
            description = "二进制流上报屏幕截图，支持标准HTTP和分块传输编码",
            tags = {"终端截图"}
    )
    @Override
    public ResponseEntity<Void> reportScreenshot(HttpServletRequest request) {
        LocalDateTime now = LocalDateTime.now();
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long deviceId = principal.getDeviceId();

        // 配置上传限制
        final long MAX_SCREENSHOT_SIZE = 10 * 1024 * 1024; // 10MB
        final long MIN_SCREENSHOT_SIZE = 1024; // 1KB 最小有效图片大小
        
        try {
            // 获取声明的内容长度（可能为-1，表示未知或使用chunked编码）
            long declaredContentLength = request.getContentLengthLong();

            // 使用try-with-resources确保InputStream正确关闭
            try (InputStream inputStream = request.getInputStream()) {

                log.info("Screenshot - 设备{}开始处理截图上传，声明大小: {}字节", deviceId, 
                        declaredContentLength > 0 ? declaredContentLength + "" : "未知(chunked)");
                
                // 上传到MinIO，传递实际的contentLength（可能为-1表示未知大小）
                terminalReportUseCase.asyncSaveDeviceScreenshot(
                        new ScreenshotUploadRecord(deviceId, inputStream, declaredContentLength, now)
                );
            }
            
        } catch (IOException e) {
            log.error("Screenshot - 设备{}截图上传IO异常", deviceId, e);
            throw new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR);
        } catch (DeviceResponseException e) {
            // 重新抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("Screenshot - 设备{}截图上传处理异常", deviceId, e);
            throw new DeviceResponseException(CommonErrorCode.SYSTEM_ERROR);
        }
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * 设备下载进度上报
     *
     * 终端通过此方法上报节目或升级包的下载进度。
     *
     * @param report 一个字符串，表示终端上报的下载进度信息。如果为空或者空白，则会抛出异常。
     * @return 返回一个ResponseEntity<Void>对象，HTTP状态码为200 OK，表示请求已成功处理但没有返回内容。
     */
    @Operation(
            summary = "设备下载进度上报",
            description = "终端上报节目或升级包下载进度",
            tags = {"下载进度"}
    )
    @Override
    public ResponseEntity<Void> reportDownloading(String report) {
        TerminalPrincipal principal = (TerminalPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.debug("DeviceDownloading - 终端 {} 上报下载进度: {}", principal.getDeviceId(), report);
        if (StringUtils.isBlank(report)) {
            throw new DeviceResponseException(CommonErrorCode.PARAMETER_MISSING);
        }
        terminalReportUseCase.asyncSaveDownloadingReport(principal.getDeviceId(), report);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}