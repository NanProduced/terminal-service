package com.colorlight.terminal.application.port.outbound.statistics;

import com.colorlight.terminal.application.domain.sensor.GpsReport;

/**
 * gps数据处理接口端口
 *
 * @author Nan
 */
public interface DeviceGpsHandlePort {

    /**
     * 接收一条Gps数据（入池）
     * @param report gps数据
     */
    void receiveGpsRecord(GpsReport report);

    /**
     * 缓冲池数据入库
     */
    void flushBuffer();

    /**
     * 定时刷新缓冲池
     */
    void scheduledFlush();

}
