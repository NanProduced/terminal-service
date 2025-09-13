package com.colorlight.terminal.application.port.outbound.status;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;

import java.util.List;

/**
 * 异步设备状态更新端口
 * 定义异步状态更新能力，支持缓冲池和批量处理
 * 
 * @author Nan
 */
public interface AsyncDeviceStatusUpdatePort {
    
    /**
     * 异步提交设备状态更新
     * 将状态更新请求加入缓冲池，由后台线程批量处理
     * 
     * @param status 设备状态
     */
    void submitStatusUpdate(DeviceOnlineStatus status);
    
    /**
     * 异步批量提交设备状态更新
     * 
     * @param statusList 设备状态列表
     */
    void submitBatchStatusUpdate(List<DeviceOnlineStatus> statusList);
    
    /**
     * 立即刷新缓冲池
     * 强制将缓冲池中的所有待处理状态立即提交
     */
    void flushBuffer();
    
    /**
     * 获取缓冲池状态信息
     * 
     * @return 缓冲池状态
     */
    BufferPoolStatus getBufferPoolStatus();

    /**
     * 定时刷新设备状态缓冲池。
     * <p>
     * 该方法由 Spring {@code @Scheduled} 注解自动触发执行。
     * <b>请勿在业务代码中手动调用此方法，因为它专为后台定时任务设计。</b>
     *
     */
    void scheduledFlush();

    /**
     * 定时输出统计信息
     * <p>
     * 该方法由 Spring {@code @Scheduled} 注解自动触发执行。
     * <b>请勿在业务代码中手动调用此方法，因为它专为后台定时任务设计。</b>
     *
     */
    void logStatistics();

    /**
     * 缓冲池状态信息
     */
    record BufferPoolStatus(int currentSize, int maxSize, double utilizationRate, long lastFlushTime,
                            long totalProcessed, long totalFlushed) {

    @Override
        public String toString() {
            return String.format("BufferPoolStatus{currentSize=%d, maxSize=%d, utilizationRate=%.2f%%, " +
                            "lastFlushTime=%d, totalProcessed=%d, totalFlushed=%d}",
                    currentSize, maxSize, utilizationRate * 100, lastFlushTime, totalProcessed, totalFlushed);
        }
    }
}