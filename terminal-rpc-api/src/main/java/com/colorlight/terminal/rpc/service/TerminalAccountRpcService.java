package com.colorlight.terminal.rpc.service;

import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.CreateTerminalAccountDTO;
import com.colorlight.terminal.rpc.dto.result.TerminalAccountResultDTO;

/**
 * 终端账号RPC服务接口
 * 
 * @author Nan
 */
public interface TerminalAccountRpcService {
    
    /**
     * 创建终端账号
     * 
     * @param request 创建请求
     * @return RPC结果
     */
    RpcResult<TerminalAccountResultDTO> createTerminalAccount(CreateTerminalAccountDTO request);
}