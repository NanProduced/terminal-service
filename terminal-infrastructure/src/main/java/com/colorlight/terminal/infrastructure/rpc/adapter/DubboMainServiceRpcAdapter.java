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

    /** RPC check=false 避免RPC服务影响本服务 */

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 5000, retries = 0)
    private CommandFinishFacade commandFinishFacade;

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 3000, retries = 0)
    private DeviceReportRpcService deviceReportRpcService;

    /**
     * 通知主服务指令确认状态
     * @param event 指令确认事件
     */
    @Override
    public void notifyCommandConfirm(CommandConfirmEvent event) {
        CommandFinishDto dto = new CommandFinishDto();
        dto.setDeviceId(event.getDeviceId());
        dto.setCommandId(event.getCommandId());
        dto.setStatus(event.isSuccess() ? CommandStatusEnum.SUCCESS : CommandStatusEnum.FAILED);

        try {
            commandFinishFacade.commandFinish(dto);
        } catch (RpcException e) {
            log.error("RpcAdapter - 指令确认RPC调用失败: dto={}", dto, e);
            throw new TechnicalException(TechErrorCode.RPC_EXCEPTION);
        }
    }

    /**
     * 通知主服务设备最后上报时间
     * @param event 设备状态事件
     */
    @Override
    public void notifyDeviceLastReportTime(DeviceStatusEvent event) {
        DeviceOnlineReportRequest request = DeviceOnlineReportRequest.builder()
                .deviceId(event.getDeviceId())
                .clientIp(event.getClientIp())
                .reportSource(event.getReportSource().name())
                .reportTime(event.getEventTime())
                .build();
        try {
            deviceReportRpcService.reportDeviceOnlineStatus(request);
        }catch (RpcException e) {
            log.error("RpcAdapter - 最后上报时间RPC调用失败: request={}", request, e);
            throw new TechnicalException(TechErrorCode.RPC_EXCEPTION);
        }
    }
}
