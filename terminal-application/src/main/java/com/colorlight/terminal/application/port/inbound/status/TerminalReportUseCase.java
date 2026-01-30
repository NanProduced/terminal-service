package com.colorlight.terminal.application.port.inbound.status;

import com.colorlight.terminal.application.domain.report.HistoryLogFileList;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.domain.sensor.SensorReport;
import com.colorlight.terminal.application.dto.record.LogFileUploadRecord;
import com.colorlight.terminal.application.dto.record.ScreenshotUploadRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 终端上报数据处理接口
 *
 * @author Nan
 */
public interface TerminalReportUseCase {

    /**
     * 处理/status接口的上报
     * @param deviceId 设备Id
     * @param reportStr 上报信息
     * @param clientIp 客户端Ip
     */
    void asyncSaveStatusReport(Long deviceId, String reportStr, String clientIp);

    /**
     * 异步保存终端日志
     * @param deviceId 设备Id
     * @param logs 日志
     */
    void asyncSaveTerminalLog(Long deviceId, List<TerminalLog> logs);

    /**
     * 异步处理素材播放记录上报
     * @param deviceId 设备Id
     * @param reportStr 上报Json
     */
    void asyncHandleMediaPlayRecordReport(Long deviceId, String reportStr);

    /**
     * 异步处理节目播放记录上报
     * @param deviceId 设备Id
     * @param reportStr 上报Json
     */
    void asyncHandleProgramPlayRecordReport(Long deviceId, String reportStr);

    /**
     * 处理传感器上报
     * @param deviceId 设备Id
     * @param reportTime 上报时间
     * @param reports 传感器上报数据列表
     */
    void asyncHandleSensorReport(Long deviceId, LocalDateTime reportTime, List<SensorReport> reports);

    /**
     * 异步保存设备截图。
     *
     * @param uploadRecord 截图信息
     */
    void asyncSaveDeviceScreenshot(ScreenshotUploadRecord uploadRecord);

    /**
     * 异步保存下载进度
     * @param deviceId 设备Id
     * @param reportStr 上报数据
     */
    void asyncSaveDownloadingReport(Long deviceId, String reportStr);

    /**
     * 异步保存设备上报文件列表
     * @param files 文件列表
     */
    void asyncSaveHistoryLogFileList(Long deviceId, String files);

    /**
     * 上传设备历史日志文件（同步）
     * @param uploadRecord 日志文件上传记录
     */
    void uploadHistoryLogFile(LogFileUploadRecord uploadRecord);

}
