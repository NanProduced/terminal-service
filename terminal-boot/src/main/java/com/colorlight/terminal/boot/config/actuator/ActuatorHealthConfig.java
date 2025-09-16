package com.colorlight.terminal.boot.config.actuator;

import com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopHealthMonitor;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Actuator健康检查配置
 * 注册自定义健康检查贡献者
 *
 * @author Nan
 */
@Configuration
public class ActuatorHealthConfig {

    /**
     * 注册EventLoop健康检查贡献者
     * 使其能够在健康检查分组中被识别为"eventLoop"
     */
    @Bean("eventLoop")
    public HealthContributor eventLoopHealthContributor(EventLoopHealthMonitor eventLoopHealthMonitor) {
        return eventLoopHealthMonitor;
    }
}