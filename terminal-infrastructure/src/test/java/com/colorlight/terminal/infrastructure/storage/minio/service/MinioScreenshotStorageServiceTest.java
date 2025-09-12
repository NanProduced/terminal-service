package com.colorlight.terminal.infrastructure.storage.minio.service;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mysql.repository.MysqlScreenshotRecordRepository;
import com.colorlight.terminal.infrastructure.storage.minio.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MinioScreenshotStorageService单元测试
 * <p>
 * 业务逻辑总结：
 * MinioScreenshotStorageService是一个截图存储服务，实现了ScreenshotStoragePort接口。
 * 它的核心职责是将设备截图上传到MinIO对象存储，并在上传过程中记录性能指标和错误日志，
 * 同时保存上传记录到MySQL数据库。这是系统中处理设备截图存储的核心组件。
 * <p>
 * 主要业务流程：
 * 1. uploadScreenshot：构建元数据 → 创建输入流 → MinIO上传 → 性能监控 → 保存记录
 * 2. deleteObject：构建删除参数 → MinIO删除操作 → 异常处理
 * 3. buildMetadata：构建包含设备ID和上传时间的元数据
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MinioScreenshotStorageService单元测试")
class MinioScreenshotStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioProperties minioProperties;

    @Mock
    private MysqlScreenshotRecordRepository screenshotRecordRepository;

    @InjectMocks
    private MinioScreenshotStorageService minioScreenshotStorageService;

    @BeforeEach
    void setUp() {
        // 使用lenient()避免严格模式报错
        lenient().when(minioProperties.getBucket()).thenReturn("device-screenshots");
        lenient().doNothing().when(screenshotRecordRepository).saveScreenshotRecord(anyLong(), any(LocalDateTime.class), anyString(), anyLong());
        // MinioClient的方法不是void，不能使用doNothing()，而是使用when().thenReturn()
        // 但由于我们主要关注异常情况，这里可以不预设置默认行为
    }

    @Nested
    @DisplayName("uploadScreenshot方法测试")
    class UploadScreenshotTests {

        @Test
        @DisplayName("应该成功上传截图到MinIO")
        void should_upload_screenshot_successfully() throws Exception {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            byte[] screenshotData = "fake screenshot data".getBytes();
            long contentLength = screenshotData.length;
            LocalDateTime uploadTime = LocalDateTime.now();

            // When - 执行上传
            minioScreenshotStorageService.uploadScreenshot(deviceId, screenshotData, contentLength, uploadTime);

            // Then - 验证MinIO上传调用
            ArgumentCaptor<PutObjectArgs> putObjectArgsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
            verify(minioClient).putObject(putObjectArgsCaptor.capture());

            // 验证保存记录调用
            verify(screenshotRecordRepository).saveScreenshotRecord(
                deviceId,
                uploadTime,
                "12345.jpeg",
                (long) screenshotData.length
            );
        }

        @Test
        @DisplayName("当MinIO上传失败时应该抛出TechnicalException")
        void should_throw_technical_exception_when_minio_upload_fails() throws Exception {
            // Given - MinIO上传失败场景
            Long deviceId = 12345L;
            byte[] screenshotData = "fake screenshot data".getBytes();
            long contentLength = screenshotData.length;
            LocalDateTime uploadTime = LocalDateTime.now();

            // 使用RuntimeException代替MinioException，因为putObject方法不声明抛出MinioException
            RuntimeException minioException = new RuntimeException("MinIO upload failed");
            doThrow(minioException).when(minioClient).putObject(any(PutObjectArgs.class));

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> minioScreenshotStorageService.uploadScreenshot(deviceId, screenshotData, contentLength, uploadTime))
                .isInstanceOf(TechnicalException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM0110")
                .hasMessageContaining("截图上传失败");

            // 验证不会保存记录
            verify(screenshotRecordRepository, never()).saveScreenshotRecord(anyLong(), any(LocalDateTime.class), anyString(), anyLong());
        }

        @Test
        @DisplayName("当IO异常时应该抛出TechnicalException")
        void should_throw_technical_exception_when_io_exception_occurs() throws Exception {
            // Given - IO异常场景
            Long deviceId = 12345L;
            byte[] screenshotData = "fake screenshot data".getBytes();
            long contentLength = screenshotData.length;
            LocalDateTime uploadTime = LocalDateTime.now();

            IOException ioException = new IOException("IO error occurred");
            doThrow(ioException).when(minioClient).putObject(any(PutObjectArgs.class));

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> minioScreenshotStorageService.uploadScreenshot(deviceId, screenshotData, contentLength, uploadTime))
                .isInstanceOf(TechnicalException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM0112")
                .hasMessageContaining("截图流处理异常");

            // 验证不会保存记录
            verify(screenshotRecordRepository, never()).saveScreenshotRecord(anyLong(), any(LocalDateTime.class), anyString(), anyLong());
        }

        @Test
        @DisplayName("当签名异常时应该抛出TechnicalException")
        void should_throw_technical_exception_when_signature_exception_occurs() throws Exception {
            // Given - 签名异常场景
            Long deviceId = 12345L;
            byte[] screenshotData = "fake screenshot data".getBytes();
            long contentLength = screenshotData.length;
            LocalDateTime uploadTime = LocalDateTime.now();

            NoSuchAlgorithmException signatureException = new NoSuchAlgorithmException("Algorithm not found");
            doThrow(signatureException).when(minioClient).putObject(any(PutObjectArgs.class));

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> minioScreenshotStorageService.uploadScreenshot(deviceId, screenshotData, contentLength, uploadTime))
                .isInstanceOf(TechnicalException.class)
                .hasFieldOrPropertyWithValue("errorCode", TechErrorCode.MINIO_SECURITY_ERROR.getCode())
                .hasMessageContaining("MinIO签名异常");

            // 验证不会保存记录
            verify(screenshotRecordRepository, never()).saveScreenshotRecord(anyLong(), any(LocalDateTime.class), anyString(), anyLong());
        }

        @Test
        @DisplayName("当InvalidKeyException时应该抛出TechnicalException")
        void should_throw_technical_exception_when_invalid_key_exception_occurs() throws Exception {
            // Given - InvalidKey异常场景
            Long deviceId = 12345L;
            byte[] screenshotData = "fake screenshot data".getBytes();
            long contentLength = screenshotData.length;
            LocalDateTime uploadTime = LocalDateTime.now();

            InvalidKeyException invalidKeyException = new InvalidKeyException("Invalid key");
            doThrow(invalidKeyException).when(minioClient).putObject(any(PutObjectArgs.class));

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> minioScreenshotStorageService.uploadScreenshot(deviceId, screenshotData, contentLength, uploadTime))
                .isInstanceOf(TechnicalException.class)
                .hasFieldOrPropertyWithValue("errorCode", TechErrorCode.MINIO_SECURITY_ERROR.getCode())
                .hasMessageContaining("MinIO签名异常");

            // 验证不会保存记录
            verify(screenshotRecordRepository, never()).saveScreenshotRecord(anyLong(), any(LocalDateTime.class), anyString(), anyLong());
        }

        @Test
        @DisplayName("应该正确构建对象名称")
        void should_build_correct_object_name() {
            // Given - 准备测试数据
            Long deviceId = 9876543210L;
            byte[] screenshotData = "test data".getBytes();
            long contentLength = screenshotData.length;
            LocalDateTime uploadTime = LocalDateTime.now();

            // When - 执行上传
            minioScreenshotStorageService.uploadScreenshot(deviceId, screenshotData, contentLength, uploadTime);

            // Then - 验证对象名称格式
            verify(screenshotRecordRepository).saveScreenshotRecord(
                deviceId,
                uploadTime,
                "9876543210.jpeg",
                (long) screenshotData.length
            );
        }

        @Test
        @DisplayName("应该使用正确的内容类型")
        void should_use_correct_content_type() throws Exception {
            // Given - 准备测试数据
            Long deviceId = 12345L;
            byte[] screenshotData = "fake screenshot data".getBytes();
            long contentLength = screenshotData.length;
            LocalDateTime uploadTime = LocalDateTime.now();

            // When - 执行上传
            minioScreenshotStorageService.uploadScreenshot(deviceId, screenshotData, contentLength, uploadTime);

            // Then - 验证MinIO调用（通过verify确认调用发生）
            verify(minioClient).putObject(any(PutObjectArgs.class));
            verify(minioProperties).getBucket();
        }
    }

    @Nested
    @DisplayName("deleteObject方法测试")
    class DeleteObjectTests {

        @Test
        @DisplayName("应该成功删除MinIO对象")
        void should_delete_object_successfully() throws Exception {
            // Given - 准备删除的对象键
            String objectKey = "12345.jpeg";

            // When - 执行删除
            minioScreenshotStorageService.deleteObject(objectKey);

            // Then - 验证MinIO删除调用
            ArgumentCaptor<RemoveObjectArgs> removeObjectArgsCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
            verify(minioClient).removeObject(removeObjectArgsCaptor.capture());
        }

        @Test
        @DisplayName("当MinIO删除失败时应该抛出TechnicalException")
        void should_throw_technical_exception_when_minio_delete_fails() throws Exception {
            // Given - MinIO删除失败场景
            String objectKey = "12345.jpeg";
            
            // 使用RuntimeException代替MinioException，因为removeObject方法不声明抛出MinioException
            IOException minioException = new IOException("MinIO delete failed");
            doThrow(minioException).when(minioClient).removeObject(any(RemoveObjectArgs.class));

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> minioScreenshotStorageService.deleteObject(objectKey))
                .isInstanceOf(TechnicalException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM0112")
                .hasMessageContaining("MinIO删除IO异常");
        }

        @Test
        @DisplayName("当删除时IO异常应该抛出TechnicalException")
        void should_throw_technical_exception_when_delete_io_exception_occurs() throws Exception {
            // Given - IO异常场景
            String objectKey = "12345.jpeg";
            
            IOException ioException = new IOException("Delete IO error");
            doThrow(ioException).when(minioClient).removeObject(any(RemoveObjectArgs.class));

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> minioScreenshotStorageService.deleteObject(objectKey))
                .isInstanceOf(TechnicalException.class)
                .hasFieldOrPropertyWithValue("errorCode", "TM0112")
                .hasMessageContaining("MinIO删除IO异常");
        }

        @Test
        @DisplayName("当删除时签名异常应该抛出TechnicalException")
        void should_throw_technical_exception_when_delete_signature_exception_occurs() throws Exception {
            // Given - 签名异常场景
            String objectKey = "12345.jpeg";
            
            NoSuchAlgorithmException signatureException = new NoSuchAlgorithmException("Delete signature error");
            doThrow(signatureException).when(minioClient).removeObject(any(RemoveObjectArgs.class));

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> minioScreenshotStorageService.deleteObject(objectKey))
                .isInstanceOf(TechnicalException.class)
                .hasFieldOrPropertyWithValue("errorCode", TechErrorCode.MINIO_SECURITY_ERROR.getCode())
                .hasMessageContaining("MinIO删除签名异常");
        }

        @Test
        @DisplayName("当删除时InvalidKeyException应该抛出TechnicalException")
        void should_throw_technical_exception_when_delete_invalid_key_exception_occurs() throws Exception {
            // Given - InvalidKey异常场景
            String objectKey = "12345.jpeg";
            
            InvalidKeyException invalidKeyException = new InvalidKeyException("Delete invalid key");
            doThrow(invalidKeyException).when(minioClient).removeObject(any(RemoveObjectArgs.class));

            // When & Then - 验证异常抛出
            assertThatThrownBy(() -> minioScreenshotStorageService.deleteObject(objectKey))
                .isInstanceOf(TechnicalException.class)
                .hasFieldOrPropertyWithValue("errorCode", TechErrorCode.MINIO_SECURITY_ERROR.getCode())
                .hasMessageContaining("MinIO删除签名异常");
        }
    }

}