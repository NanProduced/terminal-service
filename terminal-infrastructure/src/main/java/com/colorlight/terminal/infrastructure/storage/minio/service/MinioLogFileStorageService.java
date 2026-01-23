package com.colorlight.terminal.infrastructure.storage.minio.service;

import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.storage.LogFileStoragePort;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.storage.minio.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MinioLogFileStorageService 是一个实现 LogFileStoragePort 接口的服务类，
 * 用于将设备历史日志文件上传到 MinIO 存储服务。
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioLogFileStorageService implements LogFileStoragePort {

    private static final long PART_SIZE = 5 * 1024 * 1024;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final MainServerRpcPort mainServerRpcPort;

    @Override
    public void uploadHistoryLogFile(Long deviceId, String originalFilename, InputStream inputStream, long contentLength, String contentType) {
        long startTime = System.currentTimeMillis();
        String safeFilename = normalizeFilename(originalFilename);
        String objectName = buildObjectName(deviceId, safeFilename);
        String bucketName = minioProperties.getLogBucket();
        String resolvedContentType = StringUtils.isNotBlank(contentType) ? contentType : "application/octet-stream";

        try {
            Map<String, String> metadata = buildMetadata(deviceId, safeFilename);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, contentLength, PART_SIZE)
                            .contentType(resolvedContentType)
                            .userMetadata(metadata)
                            .build()
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("HistoryLog - 设备{}日志上传成功: object={}, size={}字节, duration={}ms",
                    deviceId, objectName, contentLength, duration);
            if (duration > 1000) {
                log.warn("HistoryLogPerf - 上传较慢: deviceId={}, duration={}ms, size={}字节",
                        deviceId, duration, contentLength);
            }

        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("HistoryLog - IO异常: deviceId={}, object={}, duration={}ms",
                    deviceId, objectName, duration, e);
            throw new TechnicalException(TechErrorCode.IO_EXCEPTION, "日志文件流处理异常: " + e.getMessage());

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HistoryLog - 签名异常: deviceId={}, object={}", deviceId, objectName, e);
            throw new TechnicalException(TechErrorCode.MINIO_SECURITY_ERROR, "MinIO签名异常: " + e.getMessage());
        } catch (MinioException | RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("HistoryLog - MinIO上传失败: deviceId={}, object={}, duration={}ms, error={}",
                    deviceId, objectName, duration, e.getMessage(), e);
            throw new TechnicalException(TechErrorCode.MINIO_ERROR, "日志文件上传失败: " + e.getMessage());
        }

        // 通知主服务
        mainServerRpcPort.notifyDeviceLogUpload(contentLength, safeFilename, deviceId, objectName);
    }

    private String buildObjectName(Long deviceId, String safeFilename) {
        return deviceId + "/" + "device-" + deviceId + "-" + safeFilename;
    }

    /**
     * 获取安全的文件名
     * @param originalFilename 原始文件名
     * @return 安全的文件名
     */
    private String normalizeFilename(String originalFilename) {
        if (StringUtils.isBlank(originalFilename)) {
            return "unknown.zip";
        }

        String normalized = originalFilename.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        if (index >= 0) {
            normalized = normalized.substring(index + 1);
        }
        if (StringUtils.isBlank(normalized)) {
            return "unknown.zip";
        }
        return normalized;
    }

    private Map<String, String> buildMetadata(Long deviceId, String originalFilename) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("upload_time", Instant.now().toString());
        metadata.put("device_id", deviceId.toString());
        metadata.put("original_filename", originalFilename);
        return metadata;
    }
}
