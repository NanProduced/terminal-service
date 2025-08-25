package com.colorlight.terminal.application.port.inbound.status;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;

/**
 * 终端上报数据处理接口
 *
 * @author Nan
 */
public interface TerminalReportUseCase {

    /**
     * 处理/status接口的上报
     * @param deviceId 设备Id
     * @param reportStr 上报信息
     */
    void saveLedStatus(Long deviceId, String reportStr);

    /**
     * 异步保存led_status上报数据
     * @param deviceId
     * @param report
     */
    void asyncSaveTerminalStatusReport(Long deviceId, TerminalStatusReport report);
}
