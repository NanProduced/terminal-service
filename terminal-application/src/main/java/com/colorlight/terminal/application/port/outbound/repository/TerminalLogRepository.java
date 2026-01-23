package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.report.HistoryLogFileList;
import com.colorlight.terminal.application.domain.report.TerminalLog;

import java.util.List;

/**
 * 终端日志存储接口
 *
 * @author Nan
 */
public interface TerminalLogRepository {

    /**
     * 保存终端日志
     * @param terminalLog 终端日志domain
     */
    void saveTerminalLog(TerminalLog terminalLog);

    void batchSaveTerminalLog(List<TerminalLog> terminalLogs);

    void saveHistoryLogFileList(Long deviceId, List<HistoryLogFileList.HistoryLogFile> files);



}
