package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.report.*;
import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.application.domain.sensor.SensorReport;
import com.colorlight.terminal.application.dto.record.ScreenshotUploadRecord;
import com.colorlight.terminal.application.handler.ReportTimePopulator;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.application.port.outbound.repository.DownloadingRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalLogRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceGpsHandlePort;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceMediaPlayRecordPort;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceProgramPlayRecordPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceDownloadingPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceSwitchRecordPort;
import com.colorlight.terminal.application.port.outbound.storage.ScreenshotStoragePort;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalReportApplicationService implements TerminalReportUseCase {

    private final TerminalStatusReportRepository terminalStatusReportRepository;
    private final TerminalLogRepository terminalLogRepository;
    private final DeviceSwitchRecordPort  deviceSwitchRecordPort;
    private final DeviceMediaPlayRecordPort  deviceMediaPlayRecordPort;
    private final DeviceProgramPlayRecordPort deviceProgramPlayRecordPort;
    private final DeviceGpsHandlePort deviceGpsHandlePort;
    private final ScreenshotStoragePort screenshotStoragePort;
    private final DeviceDownloadingPort deviceDownloadingPort;
    private final DownloadingRepository downloadingRepository;
    private final MainServerRpcPort mainServerRpcPort;

    /**
     * 保存LED状态报告。
     * 该方法接收设备ID和一个JSON格式的状态报告字符串，尝试将其反序列化为终端状态报告对象，并自动填充报告时间。如果反序列化成功，则异步保存状态报告到数据库中，并处理开机时间戳记录（如果存在）。任何异常将被捕获但当前未作进一步处理。
     *
     * @param deviceId 设备的唯一标识符
     * @param reportStr JSON格式的终端状态报告字符串
     */
    @Override
    @Async("deviceStatusExecutor")
    public void asyncSaveStatusReport(Long deviceId, String reportStr, String clientIp) {
        try {
            // 通知主服务led_status
            mainServerRpcPort.notifyLedStatus(deviceId, reportStr);
            // 尝试反序列化为led_status
            TerminalStatusReport terminalStatusReport = JsonUtils.fromJson(reportStr, TerminalStatusReport.class);
            // 自动填充reportTime
            ReportTimePopulator.populateReportTime(terminalStatusReport, System.currentTimeMillis() / 1000);
            terminalStatusReport.setClientIp(clientIp);
            // 反序列化成功则异步持久化
            saveLedStatus(deviceId, terminalStatusReport);
            // 处理开机时间戳记录
            if (Objects.nonNull(terminalStatusReport.getInfo())) {
                deviceSwitchRecordPort.asyncHandlerSwitchOnRecord(deviceId, terminalStatusReport.getInfo());
            }

        } catch (Exception e) {
            log.debug("ApplicationService - 保存led_status失败，可能是其他的上报: deviceId={}, report={}", deviceId, reportStr);
            // TODO: 实现其他的上报数据处理
        }
    }

    /**
     * 异步保存终端状态报告。
     * 该方法接收一个设备ID和一个终端状态报告对象，将状态报告异步保存到数据库中。如果保存成功，则记录一条信息日志；若过程中发生异常，则抛出业务异常。
     *
     * @param deviceId 设备ID
     * @param report 终端状态报告对象
     */
    @Override
    public void saveLedStatus(Long deviceId, TerminalStatusReport report) {
        try {
            terminalStatusReportRepository.saveTerminalStatusReport(deviceId, report);

            log.info("ApplicationService - 异步保存终端led_status成功: deviceId={}", deviceId);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.OPERATION_FAILED, e);
        }
    }

    /**
     * 异步保存终端日志。
     * 该方法接收一个设备ID和一系列终端日志对象，为每个日志对象设置设备ID后，批量保存这些日志到数据库中。操作成功时会记录一条信息日志；如果过程中发生异常，则抛出业务异常。
     *
     * @param deviceId 设备ID
     * @param logs 待保存的终端日志列表
     */
    @Override
    @Async("defaultAsyncExecutor")
    public void asyncSaveTerminalLog(Long deviceId, List<TerminalLog> logs) {
        try {
            logs.forEach(e -> e.setDeviceId(deviceId));
            terminalLogRepository.batchSaveTerminalLog(logs);
            log.info("ApplicationService - 异步保存终端日志成功: deviceId={}, 日志数量:{}", deviceId, logs.size());
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.OPERATION_FAILED, e);
        }
    }

    /**
     * 异步处理媒体播放记录报告。
     * 该方法首先将传入的JSON字符串反序列化为一系列的媒体播放记录报告对象，然后调用相应的端口处理这些记录。
     *
     * @param deviceId 设备ID
     * @param reportStr 包含媒体播放记录的JSON字符串
     */
    @Override
    @Async("statisticsReportExecutor")
    public void asyncHandleMediaPlayRecordReport(Long deviceId, String reportStr) {
        // 反序列化
        List<MediaPlayRecordReport> recordReports = JsonUtils.fromJson(reportStr, new TypeReference<List<MediaPlayRecordReport>>() {});
        // 数据处理
        deviceMediaPlayRecordPort.handleMediaPlayRecordReport(deviceId, recordReports);
    }

    /**
     * 异步处理节目播放记录报告。
     * 该方法首先将传入的JSON字符串反序列化为一系列的节目播放记录报告对象，然后调用相应的端口处理这些记录。
     *
     * @param deviceId 设备ID
     * @param reportStr 包含节目播放记录的JSON字符串
     */
    @Override
    @Async("statisticsReportExecutor")
    public void asyncHandleProgramPlayRecordReport(Long deviceId, String reportStr) {
        // 反序列化
        List<ProgramPlayRecordReport> recordReports = JsonUtils.fromJson(reportStr, new TypeReference<List<ProgramPlayRecordReport>>() {});
        // 数据处理
        deviceProgramPlayRecordPort.handleProgramPlayRecordReport(deviceId, recordReports);
    }

    /**
     * 异步处理传感器上报数据
     * @param deviceId 设备Id
     * @param reportTime 上报时间
     * @param reports 传感器上报数据列表
     */
    @Override
    @Async("statisticsReportExecutor")
    public void asyncHandleSensorReport(Long deviceId, LocalDateTime reportTime, List<SensorReport> reports) {
        try {
            if (CollectionUtils.isEmpty(reports)) {
                log.debug("ApplicationService -SensorReport- 空的传感器数据: deviceId={}", deviceId);
                return;
            }

            // 按类型处理
            for (SensorReport report : reports) {
                if (report == null || report.getSensorType() == null) {
                    log.warn("ApplicationService -SensorReport- 传感器数据格式无效: deviceId={}, report={}", deviceId, report);
                    continue;
                }

                if ("gps".equals(report.getSensorType()) && report instanceof GpsReport gpsReport) {
                    processGpsReport(deviceId, reportTime, gpsReport);
                } else {
                    processOtherReport(deviceId, report);
                }
            }

        } catch (Exception e) {
            log.error("ApplicationService -SensorReport- 处理传感器数据异常: deviceId={}", deviceId, e);
            throw new BusinessException(CommonErrorCode.OPERATION_FAILED, "传感器数据处理失败", e);
        }
    }

    @Override
    @Async("minioUploadExecutor")
    public void asyncSaveDeviceScreenshot(ScreenshotUploadRecord uploadRecord) {
        Long deviceId = uploadRecord.getDeviceId();
        long actualSize = uploadRecord.getActualDataSize();
        
        try {
            log.debug("ApplicationService -screenshot- 设备{}开始上传屏幕截图，大小: {}字节", deviceId, actualSize);
            
            // 参数验证
            if (uploadRecord.getScreenshotData() == null) {
                throw new BusinessException(CommonErrorCode.PARAMETER_MISSING, "截图数据不能为空");
            }
            
            // 执行上传
            screenshotStoragePort.uploadScreenshot(
                    deviceId, 
                    uploadRecord.getScreenshotData(), 
                    actualSize, 
                    uploadRecord.getUploadTime()
            );
            
            log.info("ApplicationService -screenshot- 设备{}截图上传请求处理完成，实际大小: {}字节", deviceId, actualSize);
            
        } catch (BusinessException e) {
            // 重新抛出业务异常
            log.error("ApplicationService -screenshot- 设备{}截图上传业务异常: {}", deviceId, e.getMessage());
            throw e;
        } catch (Exception e) {
            // 包装为业务异常
            log.error("ApplicationService -screenshot- 设备{}截图上传异常", deviceId, e);
            throw new BusinessException(CommonErrorCode.OPERATION_FAILED, "截图上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Async("statisticsReportExecutor") 
    public void asyncSaveDownloadingReport(Long deviceId, String reportStr) {
        try {
            // 反序列化下载状态报告
            DownloadingReport report = JsonUtils.fromJson(reportStr, DownloadingReport.class);

            // 缓存
            deviceDownloadingPort.saveDownloadingStatus(deviceId, report);
            // 持久化
            downloadingRepository.saveDeviceDownloadingStatus(deviceId, report);
            
            log.info("ApplicationService - 异步保存下载进度成功: deviceId={}, type={}", 
                    deviceId, report.getWhat());
            
        } catch (Exception e) {
            log.error("ApplicationService - 异步保存下载进度失败: deviceId={}, reportStr={}", 
                    deviceId, reportStr != null ? reportStr.substring(0, Math.min(100, reportStr.length())) : "null", e);
            throw new BusinessException(CommonErrorCode.OPERATION_FAILED, "下载进度保存失败", e);
        }
    }

    @Override
    @Async("defaultAsyncExecutor")
    public void asyncSaveHistoryLogFileList(Long deviceId, String files) {
        try {

            HistoryLogFileList fileList = JsonUtils.fromJson(files, new TypeReference<HistoryLogFileList>() {});

            terminalLogRepository.saveHistoryLogFileList(deviceId, fileList.getFiles());

            log.info("ApplicationService - 异步保存设备本地日志列表成功: deviceId={}", deviceId);

        } catch (Exception e) {
            log.error("ApplicationService - 异步保存设备本地日志列表失败: deviceId={}, reportStr={}", deviceId, files);
            throw new BusinessException(CommonErrorCode.OPERATION_FAILED, "设备本地日志列表保存失败", e);
        }

    }

    /*==================== 传感器上报数据私有辅助方法 ====================*/

    /**
     * 处理GPS报告。
     * 该方法接收设备ID、报告时间和一个GPS报告对象，首先设置报告的设备ID和服务器时间。然后验证报告的有效性，如果无效则记录一条调试日志并返回；如果有效，则通过端口接收GPS记录。
     *
     * @param deviceId 设备的唯一标识符
     * @param reportTime 报告的时间
     * @param report GPS报告对象
     */
    private void processGpsReport(Long deviceId, LocalDateTime reportTime, GpsReport report) {
        report.setDeviceId(deviceId);
        report.setServerTime(reportTime);
        if (!report.validate()) {
            log.debug("ApplicationService -GPS- 上报数据无效:{}", report);
            return;
        }
        // 通知主服务
        mainServerRpcPort.notifyGpsReport(deviceId, report);
        deviceGpsHandlePort.receiveGpsRecord(report);
    }

    /**
     * 暂时不处理其他的传感器数据（仅debug日志）
     * @param deviceId 设备Id
     * @param report 基类
     */
    private void processOtherReport(Long deviceId, SensorReport report) {
        log.debug("ApplicationService -SensorData- 设备 {} : 其他类型传感器上报（不处理）:{}", deviceId, report);
    }
}
