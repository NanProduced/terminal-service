package com.colorlight.terminal.infrastructure.rpc.service;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.dto.request.CreateTerminalAccountRequest;
import com.colorlight.terminal.application.port.inbound.account.TerminalAccountUseCase;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.CreateTerminalAccountDTO;
import com.colorlight.terminal.rpc.dto.result.TerminalAccountResultDTO;
import com.colorlight.terminal.rpc.service.TerminalAccountRpcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

/**
 * 终端账号RPC服务实现
 * 依赖倒置: Infrastructure层实现依赖Application层接口
 * 
 * @author Nan
 */
@Slf4j
@Component
@DubboService(version = "1.0.0", group = "terminal", timeout = 3000, retries = 0,  serialization = "hessian2")
@RequiredArgsConstructor
public class TerminalAccountRpcServiceImpl implements TerminalAccountRpcService {
    
    private final TerminalAccountUseCase terminalAccountUseCase;
    
    @Override
    public RpcResult<TerminalAccountResultDTO> createTerminalAccount(CreateTerminalAccountDTO request) {
        try {
            log.debug("RPC - 收到创建终端账号RPC请求, accountName: {}", request.getAccountName());
            
            // 转换RPC请求为Application层请求
            CreateTerminalAccountRequest appRequest = CreateTerminalAccountRequest.builder()
                            .accountName(request.getAccountName())
                            .rawPassword(request.getRawPassword())
                            .source(CreateTerminalAccountRequest.Source.CLOUD)
                            .build();
            
            // 调用业务逻辑
            TerminalAccount terminalAccount = terminalAccountUseCase.createTerminalAccount(appRequest);
            
            // 转换域对象为RPC结果
            TerminalAccountResultDTO result = new TerminalAccountResultDTO(terminalAccount.getDeviceId(), terminalAccount.getAccountName(), terminalAccount.getStatus().name());
            
            log.debug("RPC - 终端账号创建成功, deviceId: {}", terminalAccount.getDeviceId());
            return RpcResult.success(result);
            
        } catch (IllegalArgumentException e) {
            log.warn("RPC - 创建终端账号失败: {}", e.getMessage());
            return RpcResult.error("VALIDATION_ERROR", e.getMessage());
        } catch (Exception e) {
            log.error("RPC - 创建终端账号异常", e);
            return RpcResult.error("SYSTEM_ERROR", "系统内部错误");
        }
    }

}