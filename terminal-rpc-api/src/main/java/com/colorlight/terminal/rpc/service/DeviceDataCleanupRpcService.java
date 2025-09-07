package com.colorlight.terminal.rpc.service;

import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.BatchDeviceDataCleanupRequestDTO;
import com.colorlight.terminal.rpc.dto.request.DeviceDataCleanupRequestDTO;

/**
 * 设备数据清理RPC服务接口
 * 为主服务提供异步数据清理功能
 * 
 * @author Nan
 */
public interface DeviceDataCleanupRpcService {
    
    /**
     * 异步清理单个设备数据
     * @param request 清理请求
     * @return 清理任务是否成功提交 (不等待完成)
     */
    RpcResult<Void> cleanupDeviceDataAsync(DeviceDataCleanupRequestDTO request);
    
    /**
     * 批量异步清理设备数据  
     * @param request 批量清理请求
     * @return 批量清理任务是否成功提交
     */
    RpcResult<Void> batchCleanupDeviceDataAsync(BatchDeviceDataCleanupRequestDTO request);
}