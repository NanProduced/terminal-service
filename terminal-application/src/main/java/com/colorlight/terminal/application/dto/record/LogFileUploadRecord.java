package com.colorlight.terminal.application.dto.record;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;

/**
 * 终端日志文件上传记录
 *
 * @author Nan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogFileUploadRecord {

    private Long deviceId;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 文件长度
     */
    private Long contentLength;

    /**
     * 日志文件输入流
     */
    private InputStream inputStream;
}
