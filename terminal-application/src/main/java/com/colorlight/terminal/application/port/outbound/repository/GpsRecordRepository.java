package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.sensor.GpsReport;

import java.util.List;

/**
 * GPS记录存储接口，用于处理与GPS记录相关的数据操作。
 *
 * @author Nan
 */
public interface GpsRecordRepository {

    /**
     * 批量存储GPS记录
     * @param reports 上报数据
     */
    void batchSaveGpsRecord(List<GpsReport> reports);
}
