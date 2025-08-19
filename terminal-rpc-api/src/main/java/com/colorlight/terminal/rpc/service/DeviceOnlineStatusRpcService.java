package com.colorlight.terminal.rpc.service;

import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.BatchDeviceStatusRequestDTO;
import com.colorlight.terminal.rpc.dto.result.BatchDeviceStatusResultDTO;
import com.colorlight.terminal.rpc.dto.status.DeviceOnlineStatusDTO;

import java.util.List;

/**
 * 设备在线状态RPC服务接口
 * 为主服务提供设备状态查询功能
 * 
 * @author Nan
 */
public interface DeviceOnlineStatusRpcService {
    
    /**
     * 检查单个设备是否在线
     * 
     * @param deviceId 设备ID
     * @return 是否在线
     */
    RpcResult<Boolean> isDeviceOnline(Long deviceId);
    
    /**
     * 获取设备状态详情
     * 
     * @param deviceId 设备ID
     * @return 设备状态详情
     */
    RpcResult<DeviceOnlineStatusDTO> getDeviceStatus(Long deviceId);
    
    /**
     * 批量查询设备在线状态
     * 
     * @param request 批量查询请求
     * @return 批量查询结果
     */
    RpcResult<BatchDeviceStatusResultDTO> batchQueryDeviceStatus(BatchDeviceStatusRequestDTO request);
    
    /**
     * 获取所有在线设备ID列表
     * 
     * @return 在线设备ID列表
     */
    RpcResult<List<Long>> getOnlineDeviceIds();
    
    /**
     * 获取在线设备数量统计
     * 
     * @return 在线设备数量
     */
    RpcResult<Integer> getOnlineDeviceCount();
}