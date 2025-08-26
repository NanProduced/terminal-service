package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.dto.record.TerminalReconnectRecord;

/**
 * 设备重连记录接口
 *
 * @author Nan
 */
public interface TerminalReconnectRepository {

    /**
     * 保存设备重连记录
     * @param record 记录
     */
    void saveReconnectRecord(TerminalReconnectRecord record);
}
