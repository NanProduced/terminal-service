package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;

import java.util.Optional;

public interface TerminalStatusReportRepository {

    /**
     * 保存或更新终端led_status元数据上报
     * @param deviceId 设备ID
     * @param report 上报数据
     * @param rawReportJson 原始上报JSON字符串
     */
    void saveTerminalStatusReport(Long deviceId, TerminalStatusReport report, String rawReportJson);

    /**
     * 获取设备的led_status
     * @param deviceId 设备Id
     * @return led_Status封装
     */
    Optional<TerminalStatusReport> getReportData(Long deviceId);
}
