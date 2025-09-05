package com.colorlight.terminal.infrastructure.storage.minio.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
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

    /**
     * 初始化并配置Minio客户端。此方法首先使用从`minioProperties`中获取的配置信息（包括服务地址、访问密钥和秘密密钥）来构建一个MinioClient实例。
     * 然后，它检查指定的存储桶是否已经存在；如果不存在，则创建该存储桶。
     *
     * @return 配置完成的MinioClient实例，用于与MinIO服务器进行交互。
     * @throws Exception 如果在创建或配置MinioClient过程中发生错误。
     */
    @Bean
    public MinioClient minioClient() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();

        boolean exists = client.bucketExists(BucketExistsArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .build());
        }

        return client;
    }

}
