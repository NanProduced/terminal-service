package com.colorlight.terminal.application.dto.record;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScreenshotUploadRecord {

    private Long deviceId;

    private InputStream inputStream;

    private Long contentLength;

    private LocalDateTime uploadTime;
}
