package com.colorlight.terminal.application.port.outbound.storage;

import java.time.LocalDateTime;

/**
 * 设备截图存储端口
 *
 * @author Nan
 */
public interface ScreenshotStoragePort {

    /**
     * 上传设备屏幕截图
     * @param deviceId 设备Id
     * @param screenshotData 截图二进制数据
     * @param contentLength 文件大小
     * @param uploadTime 上传时间
     */
    void uploadScreenshot(Long deviceId, byte[] screenshotData, long contentLength, LocalDateTime uploadTime);
}
