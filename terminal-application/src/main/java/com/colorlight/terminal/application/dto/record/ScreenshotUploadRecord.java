package com.colorlight.terminal.application.dto.record;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScreenshotUploadRecord {

    private Long deviceId;

    /**
     * 截图二进制数据 - 避免跨线程传递Servlet InputStream
     */
    private byte[] screenshotData;

    private Long contentLength;

    private LocalDateTime uploadTime;
    
    /**
     * 获取实际数据大小
     * @return 字节数组长度，如果为null则返回contentLength
     */
    public long getActualDataSize() {
        return screenshotData != null ? screenshotData.length : (contentLength != null ? contentLength : 0);
    }
}
