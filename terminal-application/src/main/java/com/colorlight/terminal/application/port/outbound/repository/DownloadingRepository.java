package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.report.DownloadingReport;

/**
 * 设备下载进度状态存储
 *
 * @author Nan
 */
public interface DownloadingRepository {

    /**
     * 保存设备的下载状态信息。
     *
     * @param deviceId 设备ID，用于标识特定设备
     * @param report 下载进度报告，包含有关下载进度的具体信息。根据不同的下载类型（如节目下载或升级包下载），报告的具体内容可能不同。
     */
    void saveDeviceDownloadingStatus(Long deviceId, DownloadingReport report);
}
