package com.colorlight.terminal.application.port.outbound.statistics;

import com.colorlight.terminal.application.domain.report.ProgramPlayRecordReport;

import java.util.List;

/**
 * 节目播放记录上报处理接口端口
 *
 * @author Nan
 */
public interface DeviceProgramPlayRecordPort {

    /**
     * 处理节目播放记录上报
     * @param deviceId 设备Id
     * @param reports 上报Json
     */
    void handleProgramPlayRecordReport(Long deviceId, List<ProgramPlayRecordReport> reports);
}
