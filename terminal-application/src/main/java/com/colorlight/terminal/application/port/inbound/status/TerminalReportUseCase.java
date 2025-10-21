package com.colorlight.terminal.application.port.inbound.status;

import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.domain.sensor.SensorReport;
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
     */
    void asyncSaveStatusReport(Long deviceId, String reportStr);

    /**
     * 异步保存led_status上报数据
     * @param deviceId 设备Id
     * @param report 上报信息
     */
    void saveLedStatus(Long deviceId, TerminalStatusReport report);

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
}
