package com.colorlight.terminal.infrastructure.rpc.adapter;

import com.colorlight.ccloud.command.dto.CommandFinishDto;
import com.colorlight.ccloud.command.dto.entity.DeviceOnlineReportRequest;
import com.colorlight.ccloud.command.enums.CommandStatusEnum;
import com.colorlight.ccloud.command.interfaces.CommandFinishFacade;
import com.colorlight.ccloud.command.interfaces.DeviceReportRpcService;
import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.stereotype.Service;

/**
 * 主服务rpc接口dubbo实现类
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DubboMainServiceRpcAdapter implements MainServerRpcPort {

    /** RPC check=false 避免RPC服务影响本服务，优化超时时间提升性能 */

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 1000, retries = 0, 
                   connections = 5, actives = 10, loadbalance = "leastactive")
    private CommandFinishFacade commandFinishFacade;

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 1000, retries = 0,
                   connections = 5, actives = 10, loadbalance = "leastactive")
    private DeviceReportRpcService deviceReportRpcService;

    /**
     * 通知主服务指令确认状态
     * @param event 指令确认事件
     */
    @Override
    public void notifyCommandConfirm(CommandConfirmEvent event) {
        long startTime = System.currentTimeMillis();
        CommandFinishDto dto = new CommandFinishDto();
        dto.setDeviceId(event.getDeviceId());
        dto.setCommandId(event.getCommandId());
        dto.setStatus(event.isSuccess() ? CommandStatusEnum.SUCCESS : CommandStatusEnum.FAILED);

        try {
            commandFinishFacade.commandFinish(dto);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - 指令确认RPC调用成功: dto={}, duration={}ms", dto, duration);
            
            // 性能监控：记录调用时长
            if (duration > 500) {
                log.warn("RpcPerf - 指令确认RPC调用较慢: duration={}ms, dto={}", duration, dto);
            }
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - 指令确认RPC调用失败，已记录: dto={}, error={}, duration={}ms", 
                    dto, e.getMessage(), duration);
            // RPC失败仅记录日志，不中断业务流程
        }
    }

    /**
     * 通知主服务设备最后上报时间
     * @param event 设备状态事件
     */
    @Override
    public void notifyDeviceLastReportTime(DeviceStatusEvent event) {
        long startTime = System.currentTimeMillis();
        DeviceOnlineReportRequest request = DeviceOnlineReportRequest.builder()
                .deviceId(event.getDeviceId())
                .clientIp(event.getClientIp())
                .reportSource(event.getReportSource().name())
                .reportTime(event.getEventTime())
                .build();
        try {
            deviceReportRpcService.reportDeviceOnlineStatus(request);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - 设备状态上报RPC调用成功: request={}, duration={}ms", request, duration);
            
            // 性能监控：记录调用时长
            if (duration > 500) {
                log.warn("RpcPerf - 设备状态上报RPC调用较慢: duration={}ms, request={}", duration, request);
            }
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - 设备状态上报RPC调用失败，已记录: request={}, error={}, duration={}ms", 
                    request, e.getMessage(), duration);
            // RPC失败仅记录日志，不中断业务流程
        }
    }
}
