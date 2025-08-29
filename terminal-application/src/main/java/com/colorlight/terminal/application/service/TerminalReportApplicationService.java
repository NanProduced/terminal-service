package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.application.domain.report.ProgramPlayRecordReport;
import com.colorlight.terminal.application.handler.ReportTimePopulator;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.application.port.outbound.repository.TerminalLogRepository;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceMediaPlayRecordPort;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceProgramPlayRecordPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceSwitchRecordPort;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    @Override
    public void saveLedStatus(Long deviceId, String reportStr) {
        try {
            // 尝试反序列化为led_status
            TerminalStatusReport terminalStatusReport = JsonUtils.fromJson(reportStr, TerminalStatusReport.class);
            // 自动填充reportTime
            ReportTimePopulator.populateReportTime(terminalStatusReport, System.currentTimeMillis() / 1000);
            // 反序列化成功则异步持久化
            asyncSaveTerminalStatusReport(deviceId, terminalStatusReport);
            // 处理开机时间戳记录
            if (Objects.nonNull(terminalStatusReport.getInfo())) {
                deviceSwitchRecordPort.asyncHandlerSwitchOnRecord(deviceId, terminalStatusReport.getInfo());
            }

        } catch (Exception e) {
            // todo: 其他的上报处理
        }
    }

    @Override
    @Async("deviceStatusExecutor")
    public void asyncSaveTerminalStatusReport(Long deviceId, TerminalStatusReport report) {
        try {
            terminalStatusReportRepository.saveTerminalStatusReport(deviceId, report);

            log.info("ApplicationService - 异步保存终端led_status成功: deviceId={}", deviceId);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.OPERATION_FAILED, e);
        }
    }



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
     * 异步处理素材播放记录
     * @param deviceId 设备Id
     * @param reportStr 上报Json
     */
    @Override
    @Async("statisticsReportExecutor")
    public void asyncHandleMediaPlayRecordReport(Long deviceId, String reportStr) {
        // 反序列化
        List<MediaPlayRecordReport> recordReports = JsonUtils.fromJson(reportStr, new TypeReference<List<MediaPlayRecordReport>>() {});
        // 数据处理
        deviceMediaPlayRecordPort.handleMediaPlayRecordReport(deviceId, recordReports);
    }


    @Override
    @Async("statisticsReportExecutor")
    public void asyncHandleProgramPlayRecordReport(Long deviceId, String reportStr) {
        // 反序列化
        List<ProgramPlayRecordReport> recordReports = JsonUtils.fromJson(reportStr, new TypeReference<List<ProgramPlayRecordReport>>() {});
        // 数据处理
        deviceProgramPlayRecordPort.handleProgramPlayRecordReport(deviceId, recordReports);
    }
}
