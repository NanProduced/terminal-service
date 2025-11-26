package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.application.dto.rpc.MediaInfo;

import java.util.List;
import java.util.Map;

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

    /**
     * 保存素材播放记录
     *
     * @param deviceId     设备Id
     * @param reports      上报记录
     * @param mediaInfoMap 素材Id映射
     */
    void saveMediaPlayRecords(Long deviceId, List<MediaPlayRecordReport> reports, Map<String, MediaInfo> mediaInfoMap);
}
