package com.colorlight.terminal.infrastructure.storage.minio.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * minio配置文件
 *
 * @author Nan
 */
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();

        // 1. 处理截图桶
        String mainBucket = minioProperties.getBucket();
        ensureBucketExistsAndSetPublic(client, mainBucket);

        // 2. 处理日志桶
        String logBucket = minioProperties.getLogBucket();
        if (logBucket != null && !logBucket.isBlank()) {
            ensureBucketExistsAndSetPublic(client, logBucket);
        }

        return client;
    }

    /**
     * 确保桶存在并设置公共读取策略
     */
    private void ensureBucketExistsAndSetPublic(MinioClient client, String bucketName) throws Exception {
        // 检查是否存在，不存在则创建
        boolean exists = client.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName)
                .build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
        }

        // 设置公共读取权限
        setBucketPublicReadPolicy(client, bucketName);
    }

    /**
     * 通用的策略设置方法
     */
    private void setBucketPublicReadPolicy(MinioClient client, String bucketName) throws Exception {
        // 定义公共读取策略JSON
        String policy = String.format(
                "{"
                        + "\"Version\": \"2012-10-17\","
                        + "\"Statement\": ["
                        + "{"
                        + "\"Effect\": \"Allow\","
                        + "\"Principal\": \"*\","
                        + "\"Action\": [\"s3:GetObject\"],"
                        + "\"Resource\": \"arn:aws:s3:::%s/*\""
                        + "}"
                        + "]"
                        + "}", bucketName);

        client.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                        .bucket(bucketName)
                        .config(policy)
                        .build()
        );
    }

}

