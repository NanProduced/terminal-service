package com.colorlight.terminal.infrastructure.cache.redis.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
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

    // 注入Redis数据库索引
    @Value("${spring.data.redis.database}")
    private int redisDatabase;
    
    /**
     * Redis消息监听容器
     * 用于监听当前数据库的键过期事件
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        log.info("RedisConfig - Redis键过期监听配置已启用，仅监听数据库索引: {}", redisDatabase);
        
        return container;
    }
    
    /**
     * 获取当前数据库的键过期事件主题
     * 格式: __keyevent@{database}__:expired
     */
    @Bean
    public PatternTopic keyExpirationTopic() {
        String pattern = "__keyevent@" + redisDatabase + "__:expired";
        log.info("RedisConfig - 设置键过期监听主题: {}", pattern);
        return new PatternTopic(pattern);
    }
}