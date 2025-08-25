package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;

public interface TerminalStatusReportRepository {

    /**
     * 保存或更新终端led_status元数据上报
     * @param deviceId 设备ID
     * @param report 上报数据
     */
    void saveTerminalStatusReport(Long deviceId, TerminalStatusReport report);
}
