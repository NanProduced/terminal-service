package com.colorlight.terminal.infrastructure.cleanup.cleaner;

import com.colorlight.terminal.rpc.dto.enums.DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisDataStoreCleaner 清理策略测试")
class RedisDataStoreCleanerTest {

    private final RedisDataStoreCleaner cleaner = new RedisDataStoreCleaner();

    @Test
    @DisplayName("仅包含 Redis 数据类型时返回 0 并记录提示")
    void should_ignore_cleanup_when_only_redis_cache() {
        int affected = cleaner.cleanup(1001L, EnumSet.of(DataType.REDIS_CACHE));
        assertThat(affected).isZero();
        assertThat(cleaner.getStorageType()).isEqualTo("Redis");
    }

    @Test
    @DisplayName("supports 应正确识别 Redis 类型")
    void should_support_only_redis_data_type() {
        assertThat(cleaner.supports(DataType.REDIS_CACHE)).isTrue();
        assertThat(cleaner.supports(DataType.SCREENSHOT_RECORD)).isFalse();
    }

    @Test
    @DisplayName("忽略非 Redis 数据类型的清理请求")
    void should_skip_non_redis_cleanup() {
        int affected = cleaner.cleanup(1001L, EnumSet.of(DataType.SCREENSHOT_RECORD));
        assertThat(affected).isZero();
    }
}
