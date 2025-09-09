package com.colorlight.terminal.commons.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 测试配置类 - 提供测试专用的Bean配置
 * 
 * @author Nan
 */
@TestConfiguration
public class TestConfig {
    
    /**
     * 测试用固定时钟 - 确保时间相关测试的一致性
     */
    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(
            Instant.parse("2024-01-01T00:00:00Z"), 
            ZoneOffset.UTC
        );
    }
    
    /**
     * 测试用线程池配置 - 使用同步执行避免异步复杂性
     */
    @Bean
    @Primary
    public java.util.concurrent.Executor testTaskExecutor() {
        return java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("test-executor");
            thread.setDaemon(true);
            return thread;
        });
    }
}