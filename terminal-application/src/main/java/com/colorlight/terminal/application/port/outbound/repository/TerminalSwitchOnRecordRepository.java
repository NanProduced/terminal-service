package com.colorlight.terminal.application.port.outbound.repository;

/**
 * 设备开机记录存储接口
 *
 * @author Nan
 */
public interface TerminalSwitchOnRecordRepository {

    /**
     * 存储开机记录
     * @param deviceId 设备Id
     * @param timestamp 开机时间戳
     */
    void saveSwitchOnRecord(Long deviceId, Long timestamp);
}
