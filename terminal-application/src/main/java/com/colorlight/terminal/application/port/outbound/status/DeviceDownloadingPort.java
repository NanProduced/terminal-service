package com.colorlight.terminal.application.port.outbound.status;

import com.colorlight.terminal.application.domain.report.DownloadingReport;

/**
 * 设备下载进度上报处理端口
 *
 * @author Nan
 */
public interface DeviceDownloadingPort {

    /**
     * 保存设备下载状态。
     *
     * @param deviceId 设备ID
     * @param report 下载进度报告，包含具体的下载状态信息
     */
    void saveDownloadingStatus(Long deviceId, DownloadingReport report);
}
