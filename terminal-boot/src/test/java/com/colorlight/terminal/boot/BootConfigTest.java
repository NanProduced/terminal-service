package com.colorlight.terminal.boot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot模块测试环境配置验证
 * 
 * @author Nan
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BootConfigTest.TestConfig.class)
@DisplayName("Boot模块测试环境配置验证")
class BootConfigTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary 
        public Clock testClock() {
            return Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private Clock testClock;

    @Test 
    @DisplayName("基础测试配置应正确加载")
    void testBasicConfiguration() {
        assertThat(testClock).isNotNull();
        Instant now = testClock.instant();
        assertThat(now).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }
    
    @Test
    @DisplayName("Spring Boot测试依赖应正确配置")  
    void testSpringBootTestDependencies() {
        // 验证Spring Boot Test类在classpath中
        try {
            Class.forName("org.springframework.boot.test.context.SpringBootTest");
            Class.forName("org.springframework.test.web.servlet.MockMvc");
            Class.forName("org.springframework.security.test.context.support.WithMockUser");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Spring Boot测试依赖未正确配置: " + e.getMessage());
        }
    }
}