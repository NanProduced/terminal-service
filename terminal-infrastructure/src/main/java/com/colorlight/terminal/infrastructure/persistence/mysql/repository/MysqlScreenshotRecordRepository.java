package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceScreenshotRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceScreenshotRecordMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceSwitchOnRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 截图上报记录（仅infrastructure模块使用）
 *
 * @author Nan
 */
@Repository
@RequiredArgsConstructor
public class MysqlScreenshotRecordRepository {

    private final DeviceScreenshotRecordMapper screenshotRecordMapper;

    public void saveScreenshotRecord(Long deviceId, LocalDateTime uploadTime, String objectKey, long contentLength) {
        DeviceScreenshotRecordDO screenshotRecord = new DeviceScreenshotRecordDO();
        screenshotRecord.setDeviceId(deviceId);
        screenshotRecord.setUploadTime(uploadTime);
        screenshotRecord.setObjectKey(objectKey);
        screenshotRecord.setSize(contentLength);
        screenshotRecordMapper.insertOrUpdate(screenshotRecord);
    }
}
