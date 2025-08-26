package com.colorlight.terminal.infrastructure.rpc.service;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.inbound.status.TerminalStatusReportQueryUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.infrastructure.rpc.converter.TerminalStatusReportConverter;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.TerminalStatusReportQueryRequestDTO;
import com.colorlight.terminal.rpc.dto.result.TerminalListItemDTO;
import com.colorlight.terminal.rpc.dto.report.TerminalStatusReportDTO;
import com.colorlight.terminal.rpc.service.TerminalStatusReportRpcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 终端状态报告RPC服务实现
 * 
 * @author Demon
 */
@Slf4j
@Component
@DubboService(version = "1.0.0", group = "terminal", timeout = 5000, retries = 0, serialization = "hessian2")
@RequiredArgsConstructor
public class TerminalStatusReportRpcServiceImpl implements TerminalStatusReportRpcService {

    private final TerminalStatusReportQueryUseCase terminalStatusReportQueryUseCase;
    private final TerminalStatusReportConverter converter;

    @Override
    public RpcResult<TerminalStatusReportDTO> getTerminalStatusReport(Long deviceId) {
        try {
            log.debug("RPC - 查询终端状态报告: deviceId={}", deviceId);
            
            if (deviceId == null) {
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), "设备ID不能为空");
            }
            
            Optional<TerminalStatusReport> reportOpt = terminalStatusReportQueryUseCase.getTerminalStatusReport(deviceId);
            
            if (reportOpt.isPresent()) {
                TerminalStatusReportDTO dto = converter.convertToDTO(deviceId, reportOpt.get());
                return RpcResult.success(dto);
            } else {
                return RpcResult.success(null);
            }
            
        } catch (Exception e) {
            log.error("RPC - 查询终端状态报告失败: deviceId={}", deviceId, e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }

    @Override
    public RpcResult<Map<Long, TerminalStatusReportDTO>> batchGetTerminalStatusReports(TerminalStatusReportQueryRequestDTO request) {
        try {
            log.debug("RPC - 批量查询终端状态报告: deviceIds={}", request.getDeviceIds());
            
            if (request.getDeviceIds() == null || request.getDeviceIds().isEmpty()) {
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), "设备ID列表不能为空");
            }
            
            Map<Long, TerminalStatusReport> reports = terminalStatusReportQueryUseCase.batchGetTerminalStatusReports(request.getDeviceIds());
            
            Map<Long, TerminalStatusReportDTO> dtoMap = reports.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> converter.convertToDTO(entry.getKey(), entry.getValue())
                    ));
            
            return RpcResult.success(dtoMap);
            
        } catch (Exception e) {
            log.error("RPC - 批量查询终端状态报告失败", e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }

    @Override
    public RpcResult<Map<Long, TerminalListItemDTO>> getTerminalListByDeviceIds(List<Long> deviceIds) {
        try {
            log.debug("RPC - 根据设备ID列表获取终端列表项: deviceIds={}", deviceIds);
            
            if (deviceIds == null || deviceIds.isEmpty()) {
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), "设备ID列表不能为空");
            }
            
            Map<Long, TerminalStatusReport> reports = terminalStatusReportQueryUseCase.batchGetTerminalStatusReports(deviceIds);
            
            Map<Long, TerminalListItemDTO> terminalListMap = reports.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> converter.convertToTerminalListItem(entry.getKey(), entry.getValue())
                    ));
            
            return RpcResult.success(terminalListMap);
            
        } catch (Exception e) {
            log.error("RPC - 根据设备ID列表获取终端列表项失败", e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }
}
