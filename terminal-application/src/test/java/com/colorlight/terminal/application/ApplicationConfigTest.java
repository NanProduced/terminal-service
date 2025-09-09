package com.colorlight.terminal.application;

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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApplicationConfigTest.TestConfig.class)
class ApplicationConfigTest {

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
    void testClockConfiguration() {
        assertThat(testClock).isNotNull();
        Instant now = testClock.instant();
        assertThat(now).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }
}