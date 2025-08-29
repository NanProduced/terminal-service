package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.report.ProgramPlayRecordReport;

import java.util.List;

/**
 * 素材播放记录接口端口
 *
 * @author Nan
 */
public interface ProgramPlayRecordRepository {

    /**
     * 批量存储节目播放上报
     * @param reports 处理过的上报数据（填充业务字段）
     */
    void saveProgramPlayRecords(List<ProgramPlayRecordReport> reports);
}
