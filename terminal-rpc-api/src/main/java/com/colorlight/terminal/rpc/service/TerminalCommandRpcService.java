package com.colorlight.terminal.rpc.service;

import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.SingleCommandRequestDTO;
import com.colorlight.terminal.rpc.dto.result.SingleCommandSendResultDTO;

/**
 * 终端指令RPC服务接口
 *
 * @author Nan
 */
public interface TerminalCommandRpcService {


    /**
     * 下发单个指令
     *
     * @param commandRequest
     * @return
     */
    RpcResult<SingleCommandSendResultDTO> sendCommand(SingleCommandRequestDTO commandRequest);

    /**
     * 获取待执行指令
     * @param deviceId
     * @return
     */
    RpcResult<String> getCommands(Long deviceId);
}
