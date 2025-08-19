package com.colorlight.terminal.infrastructure.cache.redis.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis键过期监听配置
 * 
 * @author Nan
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "terminal.device.expiration-listener.enabled", havingValue = "true", matchIfMissing = true)
public class RedisExpirationConfig {
    
    /**
     * Redis消息监听容器
     * 用于监听键过期事件
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        log.info("RedisConfig - Redis键过期监听配置已启用");
        
        return container;
    }
}