package com.colorlight.terminal.infrastructure.rpc.service;

import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.infrastructure.cleanup.DeviceDataCleanupService;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.BatchDeviceDataCleanupRequestDTO;
import com.colorlight.terminal.rpc.dto.request.DeviceDataCleanupRequestDTO;
import com.colorlight.terminal.rpc.service.DeviceDataCleanupRpcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

/**
 * 设备数据清理RPC服务实现
 * Infrastructure层实现，直接使用DeviceDataCleanupService
 * 
 * @author Nan
 */
@Slf4j
@Component
@DubboService(version = "1.0.0", group = "terminal", timeout = 10000, retries = 0, serialization = "hessian2")
@RequiredArgsConstructor
public class DeviceDataCleanupRpcServiceImpl implements DeviceDataCleanupRpcService {
    
    private final DeviceDataCleanupService deviceDataCleanupService;
    
    @Override
    public RpcResult<Void> cleanupDeviceDataAsync(DeviceDataCleanupRequestDTO request) {
        try {
            log.debug("RPC - 收到设备数据清理请求: deviceId={}, config={}", 
                    request.getDeviceId(), request.getConfig());
            
            // 参数验证
            if (request.getDeviceId() == null) {
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), "设备ID不能为空");
            }
            
            // 异步执行清理任务
            deviceDataCleanupService.cleanupDeviceDataAsync(
                    request.getDeviceId(), 
                    request.getConfig()
            );
            
            // RPC调用不等待异步结果，直接返回成功
            // 具体的执行结果通过删除记录表记录
            log.debug("RPC - 设备数据清理任务已提交: deviceId={}", request.getDeviceId());
            
            return RpcResult.success();
            
        } catch (IllegalArgumentException e) {
            log.warn("RPC - 设备数据清理参数错误: deviceId={}, error={}", request.getDeviceId(), e.getMessage());
            return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("RPC - 设备数据清理任务提交失败: deviceId={}", request.getDeviceId(), e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }
    
    @Override
    public RpcResult<Void> batchCleanupDeviceDataAsync(BatchDeviceDataCleanupRequestDTO request) {
        try {
            log.debug("RPC - 收到批量设备数据清理请求: 设备数量={}, config={}", 
                    request.getDeviceIds() != null ? request.getDeviceIds().size() : 0, 
                    request.getConfig());
            
            // 参数验证
            if (request.getDeviceIds() == null || request.getDeviceIds().isEmpty()) {
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), "设备ID列表不能为空");
            }
            
            // 限制批量清理大小，避免系统负载过重
            if (request.getDeviceIds().size() > 100) {
                log.warn("RPC - 批量清理设备数量过多: count={}, 限制为100", request.getDeviceIds().size());
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), 
                        "批量清理设备数量不能超过100个，当前: " + request.getDeviceIds().size());
            }
            
            // 异步执行批量清理任务
            deviceDataCleanupService.batchCleanupDeviceDataAsync(
                    request.getDeviceIds(), 
                    request.getConfig()
            );
            
            // RPC调用不等待异步结果，直接返回成功
            // 具体的执行结果通过删除记录表记录
            log.debug("RPC - 批量设备数据清理任务已提交: 设备数量={}", request.getDeviceIds().size());
            
            return RpcResult.success();
            
        } catch (IllegalArgumentException e) {
            log.warn("RPC - 批量设备数据清理参数错误: error={}", e.getMessage());
            return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("RPC - 批量设备数据清理任务提交失败", e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }
}