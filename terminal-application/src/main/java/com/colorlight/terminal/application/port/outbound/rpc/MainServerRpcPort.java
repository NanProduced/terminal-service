package com.colorlight.terminal.application.port.outbound.rpc;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.application.domain.status.CommandConfirmEvent;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.dto.rpc.MediaInfo;

/**
 * 主服务RPC接口适配接口
 * <p>通过事件驱动进行解耦</p>
 *
 * @author Nan
 */
public interface MainServerRpcPort {

    /**
     * 通知主服务指令确认状态
     * @param event 指令确认事件
     */
    void notifyCommandConfirm(CommandConfirmEvent event);

    /**
     * 通知主服务指令过期
     * @param deviceId 设备Id
     * @param commandId 指令Id
     */
    void notifyCommandExpiration(Long deviceId, Integer commandId);

    /**
     * 通知主服务设备最后上报时间
     * @param event 设备状态事件
     */
    void notifyDeviceLastReportTime(DeviceStatusEvent event);

    /**
     * 发送led_status上报
     * @param deviceId 设备Id
     * @param report 上报
     */
    void notifyLedStatus(Long deviceId, String report);

    /**
     * 发送GPS上报
     * @param deviceId 设备Id
     * @param report GPS上报
     */
    void notifyGpsReport(Long deviceId, GpsReport report);

    /**
     * 获取排程信息
     * @param deviceId 设备Id
     * @return 排程JSON
     */
    String getScheduleByDeviceId(Long deviceId);

    /**
     * 获取节目信息
     * @param deviceId 设备Id
     * @return 节目JSON
     */
    String getProgramByDeviceId(Long deviceId);

    /**
     * 获取素材信息
     * @param programId 节目Id
     * @param deviceId 设备Id
     * @return 素材JSON
     */
    String getMediaByProgramId(Integer programId, Long deviceId);

    /**
     * 获取素材信息（包含素材Id和素材名称）
     * @param mediaMd5 素材MD5
     * @return 素材信息（mediaId和mediaName）
     */
    MediaInfo getMediaInfoByMd5(String mediaMd5);

    /**
     * 通知用户服务设备日志上传完成
     * @param size 文件大小
     * @param fileName 文件名
     * @param deviceId 设备Id
     * @param objectName 存储路径
     */
    void notifyDeviceLogUpload(Long size, String fileName, Long deviceId, String objectName);

}
