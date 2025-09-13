package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceScreenshotRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceScreenshotRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * MysqlScreenshotRecordRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：保存设备截图记录到MySQL
 * 2. 参数处理：接收设备ID、上传时间、对象键、文件大小
 * 3. 对象构建：创建DeviceScreenshotRecordDO并设置属性
 * 4. 数据保存：使用insertOrUpdate进行插入或更新操作
 * <p>
 * 测试策略：
 * - 正常保存场景验证
 * - 参数传递正确性验证
 * - 数据库操作异常测试
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MysqlScreenshotRecordRepository单元测试")
class MysqlScreenshotRecordRepositoryTest {

    @Mock
    private DeviceScreenshotRecordMapper screenshotRecordMapper;
    
    @InjectMocks
    private MysqlScreenshotRecordRepository mysqlScreenshotRecordRepository;

    @Test
    @DisplayName("保存截图记录 - 成功场景")
    void saveScreenshotRecord_Success() {
        // Given: 准备保存参数
        Long deviceId = 12345L;
        LocalDateTime uploadTime = LocalDateTime.of(2024, 1, 1, 10, 30, 0);
        String objectKey = "screenshots/device_12345_20240101103000.jpg";
        long contentLength = 1024000L; // 1MB

        // When: 执行保存
        assertDoesNotThrow(() -> mysqlScreenshotRecordRepository.saveScreenshotRecord(
                deviceId, uploadTime, objectKey, contentLength));

        // Then: 验证mapper被调用并检查参数
        ArgumentCaptor<DeviceScreenshotRecordDO> captor = ArgumentCaptor.forClass(DeviceScreenshotRecordDO.class);
        then(screenshotRecordMapper).should().insertOrUpdate(captor.capture());
        
        DeviceScreenshotRecordDO savedRecord = captor.getValue();
        assertEquals(deviceId, savedRecord.getDeviceId());
        assertEquals(uploadTime, savedRecord.getUploadTime());
        assertEquals(objectKey, savedRecord.getObjectKey());
        assertEquals(contentLength, savedRecord.getSize());
    }

    @Test
    @DisplayName("保存截图记录 - 大文件")
    void saveScreenshotRecord_LargeFile() {
        // Given: 大文件参数
        Long deviceId = 99999L;
        LocalDateTime uploadTime = LocalDateTime.now();
        String objectKey = "screenshots/large_file.png";
        long contentLength = 50_000_000L; // 50MB

        // When: 执行保存
        mysqlScreenshotRecordRepository.saveScreenshotRecord(deviceId, uploadTime, objectKey, contentLength);

        // Then: 验证大文件大小被正确设置
        ArgumentCaptor<DeviceScreenshotRecordDO> captor = ArgumentCaptor.forClass(DeviceScreenshotRecordDO.class);
        then(screenshotRecordMapper).should().insertOrUpdate(captor.capture());
        
        DeviceScreenshotRecordDO savedRecord = captor.getValue();
        assertEquals(50_000_000L, savedRecord.getSize());
    }

    @Test
    @DisplayName("保存截图记录 - 零大小文件")
    void saveScreenshotRecord_ZeroSize() {
        // Given: 零大小文件
        Long deviceId = 12345L;
        LocalDateTime uploadTime = LocalDateTime.now();
        String objectKey = "screenshots/empty_file.jpg";
        long contentLength = 0L;

        // When: 执行保存
        mysqlScreenshotRecordRepository.saveScreenshotRecord(deviceId, uploadTime, objectKey, contentLength);

        // Then: 验证零大小被正确设置
        ArgumentCaptor<DeviceScreenshotRecordDO> captor = ArgumentCaptor.forClass(DeviceScreenshotRecordDO.class);
        then(screenshotRecordMapper).should().insertOrUpdate(captor.capture());
        
        DeviceScreenshotRecordDO savedRecord = captor.getValue();
        assertEquals(0L, savedRecord.getSize());
    }

    @Test
    @DisplayName("保存截图记录 - 长对象键")
    void saveScreenshotRecord_LongObjectKey() {
        // Given: 很长的对象键
        Long deviceId = 12345L;
        LocalDateTime uploadTime = LocalDateTime.now();
        String objectKey = "screenshots/very/long/path/with/multiple/subdirectories/device_12345_screenshot_with_very_long_filename.jpg";
        long contentLength = 2048000L;

        // When: 执行保存
        mysqlScreenshotRecordRepository.saveScreenshotRecord(deviceId, uploadTime, objectKey, contentLength);

        // Then: 验证长对象键被正确设置
        ArgumentCaptor<DeviceScreenshotRecordDO> captor = ArgumentCaptor.forClass(DeviceScreenshotRecordDO.class);
        then(screenshotRecordMapper).should().insertOrUpdate(captor.capture());
        
        DeviceScreenshotRecordDO savedRecord = captor.getValue();
        assertEquals(objectKey, savedRecord.getObjectKey());
        assertTrue(savedRecord.getObjectKey().length() > 50);
    }

    @Test
    @DisplayName("保存截图记录 - Mapper异常")
    void saveScreenshotRecord_MapperException() {
        // Given: Mapper操作抛出异常
        RuntimeException mapperException = new RuntimeException("数据库插入失败");
        willThrow(mapperException).given(screenshotRecordMapper).insertOrUpdate(any(DeviceScreenshotRecordDO.class));

        // When & Then: 验证异常被传播
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                mysqlScreenshotRecordRepository.saveScreenshotRecord(12345L, LocalDateTime.now(), "test.jpg", 1024L));
        
        assertEquals(mapperException, exception);
    }

    @Test
    @DisplayName("保存截图记录 - 边界时间值")
    void saveScreenshotRecord_BoundaryTime() {
        // Given: 边界时间值
        Long deviceId = 12345L;
        LocalDateTime uploadTime = LocalDateTime.MIN; // 最小时间值
        String objectKey = "screenshots/boundary_test.jpg";
        long contentLength = 1024L;

        // When: 执行保存
        mysqlScreenshotRecordRepository.saveScreenshotRecord(deviceId, uploadTime, objectKey, contentLength);

        // Then: 验证边界时间值被正确设置
        ArgumentCaptor<DeviceScreenshotRecordDO> captor = ArgumentCaptor.forClass(DeviceScreenshotRecordDO.class);
        then(screenshotRecordMapper).should().insertOrUpdate(captor.capture());
        
        DeviceScreenshotRecordDO savedRecord = captor.getValue();
        assertEquals(LocalDateTime.MIN, savedRecord.getUploadTime());
    }
}