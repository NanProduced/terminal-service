package com.colorlight.terminal.application.port.outbound.status;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;

/**
 * 设备开机记录统计端口接口
 *
 * @author Nan
 */
public interface DeviceSwitchRecordPort {

    /**
     * 处理开机时间戳
     * @param deviceId 设备Id
     * @param info 上报的Info
     */
    void asyncHandlerSwitchOnRecord(Long deviceId, TerminalStatusReport.InfoWrapper info);

    /**
     * 获取最近一次开机时间缓存
     * @param deviceId 设备Id
     * @return 开机时间戳
     */
    Long getLatestSwitchOnTime(Long deviceId);

    /**
     * 缓存最近一次开机时间戳
     * @param deviceId 设备Id
     * @param timestamp 开机时间戳
     */
    void saveLatestSwitchOnTime(Long deviceId, Long timestamp);
}
