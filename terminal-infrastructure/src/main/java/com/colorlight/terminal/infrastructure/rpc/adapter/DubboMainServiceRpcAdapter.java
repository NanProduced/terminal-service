package com.colorlight.terminal.infrastructure.rpc.adapter;

import com.colorlight.ccloud.command.dto.CommandFinishDto;
import com.colorlight.ccloud.command.enums.CommandStatusEnum;
import com.colorlight.ccloud.command.interfaces.CommandFinishFacade;
import com.colorlight.ccloud.command.interfaces.DeviceReportRpcService;
import com.colorlight.ccloud.schedule.dto.Schedule;
import com.colorlight.ccloud.schedule.interfaces.TerminalScheduleRpcService;
import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Objects;

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

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 3000, retries = 0,
                   connections = 5, actives = 10, loadbalance = "leastactive")
    private CommandFinishFacade commandFinishFacade;

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 3000, retries = 0,
                   connections = 5, actives = 10, loadbalance = "leastactive")
    private DeviceReportRpcService deviceReportRpcService;

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 5000, retries = 0,
                    connections = 5, actives = 10, loadbalance = "leastactive")
    private TerminalScheduleRpcService terminalScheduleRpcService;

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
     * 通知主服务指令过期
     * @param deviceId 设备Id
     * @param commandId 指令Id
     */
    @Override
    public void notifyCommandExpiration(Long deviceId, Integer commandId) {
        CommandFinishDto dto = new CommandFinishDto();
        dto.setDeviceId(deviceId);
        dto.setCommandId(commandId.toString());
        dto.setStatus(CommandStatusEnum.TIMEOUT);
        try {
            commandFinishFacade.commandFinish(dto);
            log.debug("RpcAdapter - 指令过期通知RPC调用成功: dto={}", dto);

        } catch (RpcException e) {
            log.warn("RpcAdapter - 指令过期通知RPC调用失败，已记录: dto={}, error={}",
                    dto, e.getMessage());
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
        try {
            deviceReportRpcService.reportDeviceHeartbeat(event.getDeviceId(), event.getEventTime(), event.getClientIp(), event.getReportSource().name());
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - 设备状态上报RPC调用成功: duration={}ms", duration);
            
            // 性能监控：记录调用时长
            if (duration > 500) {
                log.warn("RpcPerf - 设备状态上报RPC调用较慢: duration={}ms", duration);
            }
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - 设备状态上报RPC调用失败，已记录: error={}, duration={}ms",
                    e.getMessage(), duration);
            // RPC失败仅记录日志，不中断业务流程
        }
    }

    /**
     * led_status上报
     * @param deviceId 设备Id
     * @param report 上报
     */
    @Override
    @Async("rpcNotificationExecutor")
    public void notifyLedStatus(Long deviceId, String report) {
        long startTime = System.currentTimeMillis();
        try {
            deviceReportRpcService.reportDeviceLedStatus(deviceId, System.currentTimeMillis(), report);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - Led_status上报RPC调用成功: duration={}ms", duration);

            // 性能监控：记录调用时长
            if (duration > 500) {
                log.warn("RpcPerf - Led_status上报RPC调用较慢: duration={}ms", duration);
            }
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - Led_statusRPC调用失败，已记录: error={}, duration={}ms",
                    e.getMessage(), duration);
            // RPC失败仅记录日志，不中断业务流程
        }
    }

    /**
     * 根据设备Id获取排程
     * @param deviceId 设备Id
     * @return 排程JSON
     */
    @Override
    public String getScheduleByDeviceId(Long deviceId) {
        long startTime = System.currentTimeMillis();
        try {
            final Schedule schedule = terminalScheduleRpcService.getScheduleByLedId(deviceId);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - 获取设备排程成功: 耗时={} ms, {}", duration, schedule);
            if (Objects.isNull(schedule)) {
                return null;
            } else {
                return JsonUtils.toJson(schedule);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - 获取设备排程失败，已记录: error={}, duration={}ms",
                    e.getMessage(), duration);
            return null;
        }
    }
}
