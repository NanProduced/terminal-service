package com.colorlight.terminal.application.port.outbound.storage;

import java.io.InputStream;

/**
 * 终端日志文件存储端口
 *
 * @author Nan
 */
public interface LogFileStoragePort {

    /**
     * 上传设备历史日志文件
     * @param deviceId 设备Id
     * @param originalFilename 原始文件名
     * @param inputStream 日志文件流
     * @param contentLength 文件大小
     * @param contentType 文件类型
     */
    void uploadHistoryLogFile(Long deviceId, String originalFilename, InputStream inputStream, long contentLength, String contentType);
}
