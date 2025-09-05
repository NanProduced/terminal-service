package com.colorlight.terminal.boot.config.properties;

import com.colorlight.terminal.infrastructure.config.properties.DeviceConfigProperties;
import com.colorlight.terminal.infrastructure.config.properties.TerminalCommandConfigProperties;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import com.colorlight.terminal.infrastructure.config.properties.WebSocketConfigProperties;
import com.colorlight.terminal.infrastructure.storage.minio.config.MinioProperties;
import com.colorlight.terminal.infrastructure.websocket.config.NettyWebsocketProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 终端配置属性装配
 * 统一装配所有配置属性类，支持动态刷新
 * 
 * @author Nan
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
        NettyWebsocketProperties.class,
        TerminalCommandConfigProperties.class,
        TerminalStatsConfigProperties.class,
        DeviceConfigProperties.class,
        WebSocketConfigProperties.class,
        MinioProperties.class
})
public class TerminalPropertiesConfiguration {
    
    /**
     * 为SpEL表达式提供明确的Bean引用
     * 使用@Primary避免类型冲突，直接返回注入的配置实例
     */
    @Bean("webSocketConfigProperties")
    @Primary
    public WebSocketConfigProperties webSocketConfigProperties(WebSocketConfigProperties properties) {
        return properties;
    }

    @Bean("terminalStatsConfigProperties")
    @Primary
    public TerminalStatsConfigProperties terminalStatsConfigProperties(TerminalStatsConfigProperties properties) {
        return properties;
    }
}
