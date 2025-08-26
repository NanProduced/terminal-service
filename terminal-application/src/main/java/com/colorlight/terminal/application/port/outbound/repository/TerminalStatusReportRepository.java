package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TerminalStatusReportRepository {

    /**
     * 保存或更新终端led_status元数据上报
     * @param deviceId 设备ID
     * @param report 上报数据
     */
    void saveTerminalStatusReport(Long deviceId, TerminalStatusReport report);

    /**
     * 根据设备ID查询终端状态报告
     * @param deviceId 设备ID
     * @return 终端状态报告
     */
    Optional<TerminalStatusReport> findByDeviceId(Long deviceId);

    /**
     * 批量查询终端状态报告
     * @param deviceIds 设备ID列表
     * @return 设备ID到终端状态报告的映射
     */
    Map<Long, TerminalStatusReport> findByDeviceIds(List<Long> deviceIds);
}
