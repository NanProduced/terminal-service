package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.inbound.status.TerminalStatusReportQueryUseCase;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 终端状态报告查询应用服务
 *
 * @author Demon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalStatusReportQueryApplicationService implements TerminalStatusReportQueryUseCase {

    private final TerminalStatusReportRepository terminalStatusReportRepository;

    @Override
    public Optional<TerminalStatusReport> getTerminalStatusReport(Long deviceId) {
        log.debug("查询设备状态报告: deviceId={}", deviceId);
        
        if (deviceId == null) {
            log.warn("设备ID不能为空");
            return Optional.empty();
        }
        
        return terminalStatusReportRepository.findByDeviceId(deviceId);
    }

    @Override
    public Map<Long, TerminalStatusReport> batchGetTerminalStatusReports(List<Long> deviceIds) {
        log.debug("批量查询设备状态报告: deviceIds={}", deviceIds);
        
        if (deviceIds == null || deviceIds.isEmpty()) {
            log.warn("设备ID列表不能为空");
            return Map.of();
        }
        
        // 过滤掉null值
        List<Long> validDeviceIds = deviceIds.stream()
                .filter(id -> id != null)
                .collect(Collectors.toList());
        
        if (validDeviceIds.isEmpty()) {
            log.warn("没有有效的设备ID");
            return Map.of();
        }
        
        return terminalStatusReportRepository.findByDeviceIds(validDeviceIds);
    }
}
