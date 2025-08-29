package com.colorlight.terminal.application.port.outbound.statistics;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;

import java.util.List;

/**
 * 素材播放记录上报处理接口端口
 *
 * @author Nan
 */
public interface DeviceMediaPlayRecordPort {

    /**
     * 处理素材播放记录上报
     * <p>
     *     <li>1.根据终端时区矫正播放时间</li>
     *     <li>2.存储素材播放记录</li>
     * </p>
     * @param deviceId 设备Id
     * @param reportList 上报数据列表
     */
    void handleMediaPlayRecordReport(Long deviceId, List<MediaPlayRecordReport> reportList);

}
