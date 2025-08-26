package com.colorlight.terminal.rpc.service;

import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.TerminalStatusReportQueryRequestDTO;
import com.colorlight.terminal.rpc.dto.result.TerminalListItemDTO;
import com.colorlight.terminal.rpc.dto.report.TerminalStatusReportDTO;

import java.util.List;
import java.util.Map;

/**
 * 终端状态报告RPC服务接口
 * 为主服务提供终端状态报告查询功能
 * 
 * @author Demon
 */
public interface TerminalStatusReportRpcService {

    /**
     * 根据设备ID查询终端状态报告
     * 
     * @param deviceId 设备ID
     * @return 终端状态报告
     */
    RpcResult<TerminalStatusReportDTO> getTerminalStatusReport(Long deviceId);

    /**
     * 批量查询终端状态报告
     *
     * @param request 批量查询请求
     * @return 批量查询结果
     */
    RpcResult<Map<Long, TerminalStatusReportDTO>> batchGetTerminalStatusReports(TerminalStatusReportQueryRequestDTO request);

    /**
     * 根据设备ID列表获取终端列表项
     *
     * @param deviceIds 设备ID列表
     * @return 终端列表项映射
     */
    RpcResult<Map<Long, TerminalListItemDTO>> getTerminalListByDeviceIds(List<Long> deviceIds);
}
