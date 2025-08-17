package com.colorlight.terminal.boot.config.properties;

import com.colorlight.terminal.infrastructure.config.properties.TerminalCommandConfigProperties;
import com.colorlight.terminal.infrastructure.websocket.config.NettyWebsocketProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
        TerminalCommandConfigProperties.class
})
public class TerminalPropertiesConfiguration {
}
