package com.colorlight.terminal.boot.config.properties;

import com.colorlight.terminal.infrastructure.websocket.config.NettyWebsocketProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        NettyWebsocketProperties.class
})
public class TerminalPropertiesConfiguration {
}
