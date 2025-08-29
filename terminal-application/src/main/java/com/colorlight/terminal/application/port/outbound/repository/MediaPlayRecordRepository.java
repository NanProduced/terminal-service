package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;

import java.util.List;

/**
 * 素材播放记录接口端口
 *
 * @author Nan
 */
public interface MediaPlayRecordRepository {

    /**
     * 保存素材播放记录
     * @param deviceId 设备Id
     * @param reports 上报记录
     */
    void saveMediaPlayRecords(Long deviceId, List<MediaPlayRecordReport> reports);
}
