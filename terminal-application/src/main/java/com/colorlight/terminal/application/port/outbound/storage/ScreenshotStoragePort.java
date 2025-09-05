package com.colorlight.terminal.application.port.outbound.storage;

import java.io.InputStream;
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
     * @param in 二进制流
     * @param contentLength 文件大小（-1）
     * @param uploadTime 上传时间
     */
    void uploadScreenshot(Long deviceId, InputStream in, long contentLength, LocalDateTime uploadTime);
}
