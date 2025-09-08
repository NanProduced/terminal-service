package com.colorlight.terminal.infrastructure.storage.minio.service;

import com.colorlight.terminal.application.port.outbound.storage.ScreenshotStoragePort;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mysql.repository.MysqlScreenshotRecordRepository;
import com.colorlight.terminal.infrastructure.storage.minio.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MinioScreenshotStorageService 是一个实现 ScreenshotStoragePort 接口的服务类，用于将设备的屏幕截图上传到 MinIO 存储服务。
 * 该服务通过注入的 MinioClient 和 MinioProperties 对象来执行上传操作，并在上传过程中记录性能指标和错误日志。
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioScreenshotStorageService implements ScreenshotStoragePort {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final MysqlScreenshotRecordRepository screenshotRecordRepository;

    @Override
    public void uploadScreenshot(Long deviceId, InputStream in, long contentLength, LocalDateTime uploadTime) {
        long startTime = System.currentTimeMillis();
        String objectName = deviceId + "/screenshot.jpeg";
        
        try {
            // 构建元数据 - 包含上传时间信息
            Map<String, String> metadata = buildMetadata(deviceId);
            
            // 上传到MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .stream(in, contentLength, -1) // -1表示未知part size，让MinIO自动处理
                    .contentType("image/jpeg")
                    .userMetadata(metadata)
                    .build()
            );
            
            long duration = System.currentTimeMillis() - startTime;
            String sizeInfo = contentLength > 0 ? contentLength + "字节" : "未知大小(流式传输)";
            log.info("Screenshot - 设备{}截图上传成功: object={}, size={}, duration={}ms", 
                    deviceId, objectName, sizeInfo, duration);
            // 性能监控
            if (duration > 1000) {
                String perfSizeInfo = contentLength > 0 ? contentLength + "字节" : "未知大小";
                log.warn("ScreenshotPerf - 上传较慢: deviceId={}, duration={}ms, size={}",
                        deviceId, duration, perfSizeInfo);
            }

            // 保存上传记录
            screenshotRecordRepository.saveScreenshotRecord(deviceId, uploadTime, objectName, contentLength);
            
        } catch (MinioException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Screenshot - MinIO上传失败: deviceId={}, object={}, duration={}ms, error={}", 
                    deviceId, objectName, duration, e.getMessage(), e);
            throw new TechnicalException(TechErrorCode.MINIO_ERROR, "截图上传失败: " + e.getMessage());
            
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Screenshot - IO异常: deviceId={}, object={}, duration={}ms", 
                    deviceId, objectName, duration, e);
            throw new TechnicalException(TechErrorCode.IO_EXCEPTION, "截图流处理异常: " + e.getMessage());
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Screenshot - 签名异常: deviceId={}, object={}", deviceId, objectName, e);
            throw new TechnicalException(TechErrorCode.MINIO_SECURITY_ERROR, "MinIO签名异常: " + e.getMessage());
        }
    }

    /**
     * 删除MinIO中的对象
     * @param objectKey 对象键名
     */
    public void deleteObject(String objectKey) {
        try {
            log.debug("MinIO - 删除对象: objectKey={}", objectKey);
            
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build()
            );
            
            log.debug("MinIO - 对象删除成功: objectKey={}", objectKey);
            
        } catch (MinioException e) {
            log.error("MinIO - 删除对象失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new TechnicalException(TechErrorCode.MINIO_ERROR, "MinIO删除失败: " + e.getMessage());
        } catch (IOException e) {
            log.error("MinIO - IO异常: objectKey={}", objectKey, e);
            throw new TechnicalException(TechErrorCode.IO_EXCEPTION, "MinIO删除IO异常: " + e.getMessage());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("MinIO - 签名异常: objectKey={}", objectKey, e);
            throw new TechnicalException(TechErrorCode.MINIO_SECURITY_ERROR, "MinIO删除签名异常: " + e.getMessage());
        }
    }

    /**
     * 构建文件元数据
     * @param deviceId 设备ID
     * @return 元数据Map
     */
    private Map<String, String> buildMetadata(Long deviceId) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("upload_time", Instant.now().toString());
        metadata.put("device_id", deviceId.toString());
        return metadata;
    }
}
