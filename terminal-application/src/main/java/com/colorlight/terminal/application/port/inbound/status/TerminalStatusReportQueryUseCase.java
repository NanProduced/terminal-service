package com.colorlight.terminal.application.port.inbound.status;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 终端状态报告查询
 *
 * @author Demon
 */
public interface TerminalStatusReportQueryUseCase {

    /**
     * 根据设备ID查询终端状态报告
     * @param deviceId 设备ID
     * @return 终端状态报告
     */
    Optional<TerminalStatusReport> getTerminalStatusReport(Long deviceId);

    /**
     * 批量查询终端状态报告
     * @param deviceIds 设备ID列表
     * @return 设备ID到终端状态报告的映射
     */
    Map<Long, TerminalStatusReport> batchGetTerminalStatusReports(List<Long> deviceIds);
}
