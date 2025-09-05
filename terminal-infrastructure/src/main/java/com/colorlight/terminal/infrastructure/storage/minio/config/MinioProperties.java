package com.colorlight.terminal.infrastructure.storage.minio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "terminal.storage.minio")
public class MinioProperties {

    /**
     * MinIO服务地址
     */
    private String endpoint;

    /**
     * 账号
     */
    private String accessKey;

    /**
     * 密码
     */
    private String secretKey;

    /**
     * 截图桶名 = device-screenshots
     */
    private String bucket = "device-screenshots";
}
