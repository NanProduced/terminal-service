package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TerminalReportApplicationService implements TerminalReportUseCase {

    private final TerminalStatusReportRepository terminalStatusReportRepository;

    @Override
    public void saveLedStatus(Long deviceId, String reportStr) {
        try {
            // 尝试反序列化为led_status
            final TerminalStatusReport terminalStatusReport = JsonUtils.fromJson(reportStr, TerminalStatusReport.class);
            // 反序列化成功则异步持久化
            asyncSaveTerminalStatusReport(deviceId, terminalStatusReport);
        } catch (Exception e) {
            // todo: 其他的上报处理
        }
    }

    @Override
    @Async("deviceStatusExecutor")
    public void asyncSaveTerminalStatusReport(Long deviceId, TerminalStatusReport report) {
        terminalStatusReportRepository.saveTerminalStatusReport(deviceId, report);
    }
}
