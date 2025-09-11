package com.colorlight.terminal.infrastructure.rpc.service;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.BatchDeviceStatusRequestDTO;
import com.colorlight.terminal.rpc.dto.result.BatchDeviceStatusResultDTO;
import com.colorlight.terminal.rpc.dto.status.DeviceOnlineStatusDTO;
import com.colorlight.terminal.rpc.service.DeviceOnlineStatusRpcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备在线状态RPC服务实现
 * 
 * @author Nan
 */
@Slf4j
@Component
@DubboService(version = "1.0.0", group = "terminal", timeout = 5000, retries = 0, serialization = "hessian2")
@RequiredArgsConstructor
public class DeviceOnlineStatusRpcServiceImpl implements DeviceOnlineStatusRpcService {
    
    private final DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;
    
    @Override
    public RpcResult<Boolean> isDeviceOnline(Long deviceId) {
        try {
            log.debug("RPC - 检查设备在线状态: deviceId={}", deviceId);
            
            if (deviceId == null) {
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), "设备ID不能为空");
            }
            
            boolean online = deviceOnlineStatusUseCase.isDeviceOnline(deviceId);
            
            return RpcResult.success(online);
            
        } catch (Exception e) {
            log.error("RPC - 检查设备在线状态失败: deviceId={}", deviceId, e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }
    
    @Override
    public RpcResult<DeviceOnlineStatusDTO> getDeviceStatus(Long deviceId) {
        try {
            log.debug("RPC - 获取设备状态详情: deviceId={}", deviceId);
            
            if (deviceId == null) {
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), "设备ID不能为空");
            }
            
            Optional<DeviceOnlineStatus> statusOpt = deviceOnlineStatusUseCase.getDeviceStatus(deviceId);
            
            if (statusOpt.isPresent()) {
                DeviceOnlineStatusDTO dto = convertToDTO(statusOpt.get());
                return RpcResult.success(dto);
            } else {
                return RpcResult.success(null);
            }
            
        } catch (Exception e) {
            log.error("RPC - 获取设备状态详情失败: deviceId={}", deviceId, e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }
    
    @Override
    public RpcResult<BatchDeviceStatusResultDTO> batchQueryDeviceStatus(BatchDeviceStatusRequestDTO request) {
        try {
            long startTime = System.currentTimeMillis();
            
            log.info("RPC - 批量查询设备状态: 设备数量={}, 包含详情={}", 
                    request.getDeviceIds() != null ? request.getDeviceIds().size() : 0, 
                    request.getIncludeDetails());
            
            // 参数验证
            if (request.getDeviceIds() == null || request.getDeviceIds().isEmpty()) {
                return RpcResult.error(CommonErrorCode.INVALID_PARAMETER.getCode(), "设备ID列表不能为空");
            }
            
            List<Long> deviceIds = request.getDeviceIds();
            
            // 限制批量查询大小
            if (deviceIds.size() > 1000) {
                log.warn("RPC - 批量查询设备数量过多: count={}, 限制为1000", deviceIds.size());
                deviceIds = deviceIds.subList(0, 1000);
            }
            
            // 批量查询在线状态
            Map<Long, Boolean> onlineStatusMap = deviceOnlineStatusUseCase.batchCheckOnline(deviceIds);
            
            // 如果需要详细信息，批量查询详情
            Map<Long, DeviceOnlineStatusDTO> detailStatusMap = null;
            if (Boolean.TRUE.equals(request.getIncludeDetails())) {
                Map<Long, DeviceOnlineStatus> detailMap = deviceOnlineStatusUseCase.batchGetDeviceStatus(deviceIds);
                detailStatusMap = detailMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> convertToDTO(entry.getValue())
                        ));
            }
            
            // 统计信息
            long onlineCount = onlineStatusMap.values().stream().mapToLong(online -> Boolean.TRUE.equals(online) ? 1 : 0).sum();
            long queryTime = System.currentTimeMillis() - startTime;
            
            BatchDeviceStatusResultDTO.QueryStatistics statistics = BatchDeviceStatusResultDTO.QueryStatistics.builder()
                    .requestedCount(deviceIds.size())
                    .onlineCount((int) onlineCount)
                    .offlineCount(deviceIds.size() - (int) onlineCount)
                    .queryTimeMs(queryTime)
                    .build();
            
            BatchDeviceStatusResultDTO result = BatchDeviceStatusResultDTO.builder()
                    .onlineStatusMap(onlineStatusMap)
                    .detailStatusMap(detailStatusMap)
                    .statistics(statistics)
                    .build();
            
            log.info("RPC - 批量查询设备状态完成: 请求数量={}, 在线数量={}, 耗时={}ms", 
                    deviceIds.size(), onlineCount, queryTime);
            
            return RpcResult.success(result);
            
        } catch (Exception e) {
            log.error("RPC - 批量查询设备状态失败", e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }
    
    @Override
    public RpcResult<List<Long>> getOnlineDeviceIds() {
        try {
            log.debug("RPC - 获取在线设备ID列表");
            
            Set<Long> onlineDeviceIds = deviceOnlineStatusUseCase.getOnlineDeviceIds();
            List<Long> result = new ArrayList<>(onlineDeviceIds);
            
            log.info("RPC - 获取在线设备ID列表完成: 在线设备数量={}", result.size());
            
            return RpcResult.success(result);
            
        } catch (Exception e) {
            log.error("RPC - 获取在线设备ID列表失败", e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }
    
    @Override
    public RpcResult<Integer> getOnlineDeviceCount() {
        try {
            log.debug("RPC - 获取在线设备数量");
            
            int count = deviceOnlineStatusUseCase.getOnlineDeviceCount();
            
            return RpcResult.success(count);
            
        } catch (Exception e) {
            log.error("RPC - 获取在线设备数量失败", e);
            return RpcResult.error(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统错误");
        }
    }
    /**
     * 转换领域对象为DTO
     */
    private DeviceOnlineStatusDTO convertToDTO(DeviceOnlineStatus status) {
        return DeviceOnlineStatusDTO.builder()
                .deviceId(status.getDeviceId())
                .lastReportTime(status.getLastReportTime())
                .lastReportSource(status.getLastReportSource() != null ? status.getLastReportSource().name() : null)
                .status(status.getStatus().name())
                .statusChangeTime(status.getStatusChangeTime())
                .onlineStartTime(status.getOnlineStartTime())
                .clientIp(status.getClientIp())
                .currentOnlineDuration(status.getCurrentOnlineDuration())
                .online(status.isOnline())
                .build();
    }
}