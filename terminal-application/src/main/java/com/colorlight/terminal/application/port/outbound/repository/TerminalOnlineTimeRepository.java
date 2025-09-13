package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.dto.record.TerminalOnlineTimeRecord;

/**
 * 终端在线时长记录存储接口
 *
 * @author Nan
 */
public interface TerminalOnlineTimeRepository {

    /**
     * 保存终端在线时长记录
     * @param record 记录
     */
    void saveTerminalOnlineTime(TerminalOnlineTimeRecord record);

}
