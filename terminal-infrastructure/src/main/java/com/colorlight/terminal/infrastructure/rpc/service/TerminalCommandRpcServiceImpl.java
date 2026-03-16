package com.colorlight.terminal.infrastructure.rpc.service;

import com.colorlight.ccloud.common.utils.JsonUtils;
import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.dto.request.SendCommandRequest;
import com.colorlight.terminal.application.dto.result.CommandSendResult;
import com.colorlight.terminal.application.port.inbound.command.TerminalCommandUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.SingleCommandRequestDTO;
import com.colorlight.terminal.rpc.dto.result.SingleCommandSendResultDTO;
import com.colorlight.terminal.rpc.service.TerminalCommandRpcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 终端指令RPC服务实现
 * 依赖倒置: Infrastructure层实现依赖Application层接口
 *
 * @author Nan
 * @version 1.0.0
 */
@Slf4j
@Component
@DubboService(version = "1.0.0", group = "terminal", timeout = 3000, retries = 0, serialization = "hessian2")
@RequiredArgsConstructor
public class TerminalCommandRpcServiceImpl implements TerminalCommandRpcService {

    private final TerminalCommandUseCase terminalCommandUseCase;

    @Override
    public RpcResult<SingleCommandSendResultDTO> sendCommand(SingleCommandRequestDTO request) {
        try {
            log.info("RPC - 收到指令下发请求, deviceId: {}, authorUrl: {}", 
                    request.getDeviceId(), request.getCommand().getAuthorUrl());
            
            // 转换RPC请求为Application层请求
            SendCommandRequest appRequest = SendCommandRequest.builder()
                    .deviceId(request.getDeviceId())
                    .authorUrl(request.getCommand().getAuthorUrl())
                    .contentRaw(Objects.isNull(request.getCommand().getContent()) ? "" : request.getCommand().getContent().getRaw())
                    .karma(request.getCommand().getKarma())
                    .build();
            
            // 调用业务逻辑
            CommandSendResult result = terminalCommandUseCase.sendCommandToDevice(appRequest);
            
            // 转换为RPC结果
            SingleCommandSendResultDTO rpcResult = new SingleCommandSendResultDTO(
                    result.isSuccess(),
                    result.getCommandId(),
                    result.getSendMethod().name(),
                    result.getMessage()
            );
            
            log.info("RPC - 指令下发完成, success: {}, commandId: {}, method: {}", 
                    result.isSuccess(), result.getCommandId(), result.getSendMethod());
            
            return RpcResult.success(rpcResult);
            
        } catch (IllegalArgumentException e) {
            log.warn("RPC - 指令下发参数错误: {}", e.getMessage());
            return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), CommonErrorCode.INVALID_PARAMETER.getMessage());
        } catch (Exception e) {
            log.error("RPC - 指令下发异常", e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), CommonErrorCode.SYSTEM_ERROR.getMessage());
        }
    }

    @Override
    public RpcResult<String> getCommands(Long deviceId) {

        try {
            final List<TerminalCommand> pendingCommands = terminalCommandUseCase.getPendingCommands(deviceId);
            final String json = JsonUtils.toJson(pendingCommands);
            log.info("RPC - 指令获取成功, deviceId:{}, commands:{}", deviceId, json);
            return RpcResult.success(json);

        } catch (IllegalArgumentException e) {
            log.warn("RPC - 指令下发参数错误: {}", e.getMessage());
            return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), CommonErrorCode.INVALID_PARAMETER.getMessage());
        } catch (Exception e) {
            log.error("RPC - 指令下发异常", e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), CommonErrorCode.SYSTEM_ERROR.getMessage());
        }
    }
}
