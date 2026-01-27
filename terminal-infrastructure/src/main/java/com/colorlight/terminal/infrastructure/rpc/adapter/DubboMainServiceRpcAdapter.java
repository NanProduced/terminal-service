package com.colorlight.terminal.infrastructure.rpc.adapter;

import com.colorlight.ccloud.attachment.interfaces.AttachmentRpcService;
import com.colorlight.ccloud.attachment.interfaces.TerminalAttachmentRpcService;
import com.colorlight.ccloud.attachment.vo.RpcTerminalAttachmentVO;
import com.colorlight.ccloud.command.dto.CommandFinishDto;
import com.colorlight.ccloud.command.dto.entity.DeviceGpsRequest;
import com.colorlight.ccloud.command.interfaces.CommandFinishFacade;
import com.colorlight.ccloud.command.interfaces.DeviceReportRpcService;
import com.colorlight.ccloud.common.command.enums.CommandStatusEnum;
import com.colorlight.ccloud.log.dto.DeviceLogFileSaveDTO;
import com.colorlight.ccloud.log.interfaces.DeviceLogFileSaveRpcService;
import com.colorlight.ccloud.program.interfaces.TerminalProgramRpcService;
import com.colorlight.ccloud.program.vo.RpcTerminalProgramVO;
import com.colorlight.ccloud.schedule.interfaces.TerminalScheduleRpcService;
import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.dto.rpc.MediaInfo;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.commons.utils.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 5000, retries = 0,
                   connections = 5, actives = 10, loadbalance = "leastactive")
    private AttachmentRpcService attachmentRpcService;

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 5000, retries = 0,
                   connections = 5, actives = 10, loadbalance = "leastactive")
    private TerminalAttachmentRpcService terminalAttachmentRpcService;

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 5000, retries = 0,
                   connections = 5, actives = 10, loadbalance = "leastactive")
    private TerminalProgramRpcService terminalProgramRpcService;

    @DubboReference(version = "1.0.0", group = "terminal", check = false, timeout = 5000, retries = 0,
                    connections = 5, actives = 10, loadbalance = "leastactive")
    private DeviceLogFileSaveRpcService deviceLogFileSaveRpcService;

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
     * 上报GPS信息
     * @param deviceId 设备Id
     * @param report GPS上报
     */
    @Override
    @Async("rpcNotificationExecutor")
    public void notifyGpsReport(Long deviceId, GpsReport report) {
        long startTime = System.currentTimeMillis();
        try {
            DeviceGpsRequest gps = DeviceGpsRequest.builder()
                    .deviceId(deviceId)
                    .reportTime(TimeUtils.convertLocalDateTimeToTimestamp(report.getServerTime()))
                    .longitude(report.getLongitude())
                    .latitude(report.getLatitude())
                    .altitude(report.getAltitude())
                    .accuracy(report.getAccuracy())
                    .speed(report.getSpeed())
                    .direct(report.getDirect())
                    .satellites(report.getSatellites())
                    .build();
            deviceReportRpcService.reportDeviceGps(gps);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - GPS上报RPC调用成功: duration={}ms", duration);

            // 性能监控：记录调用时长
            if (duration > 500) {
                log.warn("RpcPerf - GPS上报RPC调用较慢: duration={}ms", duration);
            }
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - GPS RPC调用失败，已记录: error={}, duration={}ms",
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
            final String schedule = terminalScheduleRpcService.getScheduleByLedId(deviceId);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - 获取设备排程成功: 耗时={} ms, {}", duration, schedule);
            return schedule;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - 获取设备排程失败，已记录: error={}, duration={}ms",
                    e.getMessage(), duration);
            return null;
        }
    }

    /**
     * 根据设备Id获取节目
     * @param deviceId 设备Id
     * @return 节目JSON
     */
    @Override
    public String getProgramByDeviceId(Long deviceId) {
        long startTime = System.currentTimeMillis();
        try {
            final List<RpcTerminalProgramVO> programs = terminalProgramRpcService.getTerminalPrograms(deviceId);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - 获取设备节目成功: 耗时={} ms, {}", duration, programs);
            if (CollectionUtils.isNotEmpty(programs)) {
                return JsonUtils.toJson(programs);
            }
            else {
                return null;
            }
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - RPC调用获取设备节目失败: deviceId={}, error={}, duration={}ms",
                    deviceId, e.getMessage(), duration);
            return null;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("RpcAdapter - 获取设备节目发生未预期异常: deviceId={}, error={}, duration={}ms",
                    deviceId, e.getMessage(), duration, e);
            return null;
        }
    }

    /**
     * 根据节目Id获取素材
     * @param programId 节目Id
     * @return 素材JSON
     */
    @Override
    public String getMediaByProgramId(Integer programId, Long deviceId) {
        long startTime = System.currentTimeMillis();
        try {
            final List<RpcTerminalAttachmentVO> medias = terminalAttachmentRpcService.getTerminalAttachments(programId, deviceId);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - 获取节目素材成功: 耗时={} ms, {}", duration, medias);
            if (CollectionUtils.isNotEmpty(medias)) {
                return JsonUtils.toJson(medias);
            }
            else {
                return null;
            }
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - RPC调用获取节目素材失败: programId={}, error={}, duration={}ms",
                    programId, e.getMessage(), duration);
            return null;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("RpcAdapter - 获取节目素材发生未预期异常: programId={}, error={}, duration={}ms",
                    programId, e.getMessage(), duration, e);
            return null;
        }
    }

    /**
     * 根据素材MD5获取素材信息（包含素材Id和素材名称）
     * @param mediaMd5 素材MD5
     * @return 素材Id
     */
    @Override
    public MediaInfo getMediaInfoByMd5(String mediaMd5) {
        long startTime = System.currentTimeMillis();
        try {
            Map<Integer, String> mediaInfoMap = attachmentRpcService.getAttachmentId(mediaMd5);
            final MediaInfo mediaInfo = new MediaInfo();
            mediaInfoMap.entrySet().stream().findFirst().ifPresent(entry -> {
                mediaInfo.setMediaId(entry.getKey());
                mediaInfo.setMediaName(entry.getValue());
            });
            long duration = System.currentTimeMillis() - startTime;
            if (Objects.isNull(mediaInfo.getMediaId())) {
                log.warn("RpcAdapter - 获取素材信息失败(素材不存在)，已记录: 素材MD5={}", mediaMd5);
            }
            else {
                log.debug("RpcAdapter - 获取素材信息成功: 耗时={} ms, mediaId={}, mediaName={}", 
                        duration, mediaInfo.getMediaId(), mediaInfo.getMediaName());
            }
            return mediaInfo;
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - RPC调用获取素材信息失败: mediaMd5={}, error={}, duration={}ms",
                    mediaMd5, e.getMessage(), duration);
            return null;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("RpcAdapter - 获取素材信息发生未预期异常: mediaMd5={}, error={}, duration={}ms",
                    mediaMd5, e.getMessage(), duration, e);
            return null;
        }

    }

    @Override
    public void notifyDeviceLogUpload(Long size, String fileName, Long deviceId, String objectName) {
        long startTime = System.currentTimeMillis();
        try {
            DeviceLogFileSaveDTO dto = new DeviceLogFileSaveDTO();
            dto.setTerminalId(deviceId);
            dto.setSize(size);
            dto.setFileName(fileName);
            dto.setObjectName(objectName);
            deviceLogFileSaveRpcService.saveDeviceLog(dto);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("RpcAdapter - 通知日志上传成功: 耗时={} ms, deviceId={}, fileName={}",
                    duration, deviceId, fileName);
        } catch (RpcException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("RpcAdapter - 通知日志上传失败: duration={}, deviceId={}, fileName={}",
                     duration, deviceId, fileName, e);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("RpcAdapter - 通知日志上传发生未预期异常: duration={}, deviceId={}, fileName={}",
                    duration, deviceId, fileName, e);
        }
    }
}
