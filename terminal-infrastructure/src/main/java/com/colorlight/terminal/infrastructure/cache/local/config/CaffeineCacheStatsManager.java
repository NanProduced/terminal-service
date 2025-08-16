package com.colorlight.terminal.infrastructure.cache.local.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;

/**
 * 缓存统计管理器
 * 提供缓存性能监控和指标收集功能
 *
 * @author Nan
 */
@Slf4j
public class CaffeineCacheStatsManager {

    /**
     * 获取缓存统计信息
     *
     * @param cache 缓存实例
     * @return 格式化的统计信息
     */
    public String getFormattedStats(Cache<?, ?> cache) {
        CacheStats stats = cache.stats();
        return String.format(
                "命中率: %.2f%%, 请求数: %d, 命中数: %d, 未命中数: %d, " +
                        "加载数: %d, 逐出数: %d, 平均加载时间: %.2fms",
                stats.hitRate() * 100,
                stats.requestCount(),
                stats.hitCount(),
                stats.missCount(),
                stats.loadCount(),
                stats.evictionCount(),
                stats.averageLoadPenalty() / 1_000_000.0 // 转换为毫秒
        );
    }

    /**
     * 检查缓存健康状态
     *
     * @param cache 缓存实例
     * @return 是否健康
     */
    public boolean isHealthy(Cache<?, ?> cache) {
        CacheStats stats = cache.stats();

        // 健康检查规则
        boolean hitRateOk = stats.hitRate() >= 0.85; // 命中率 >= 85%
        boolean loadTimeOk = stats.averageLoadPenalty() <= 10_000_000; // 平均加载时间 <= 10ms

        return hitRateOk && loadTimeOk;
    }

    private static final DecimalFormat DF = new DecimalFormat("0.00");

    /**
     * 记录性能警告
     *
     * @param cacheType 缓存类型
     * @param cache 缓存实例
     */
    public void logPerformanceWarning(String cacheType, Cache<?, ?> cache) {
        CacheStats stats = cache.stats();

        if (stats.hitRate() < 0.85) {
            log.warn("缓存命中率过低 - {}: {}%", cacheType, DF.format(stats.hitRate() * 100));
        }

        if (stats.averageLoadPenalty() > 10_000_000) {
            log.warn("缓存加载时间过长 - {}: {}ms",
                    cacheType, DF.format(stats.averageLoadPenalty() / 1_000_000.0));
        }

        if (stats.evictionCount() > stats.requestCount() * 0.1) {
            log.warn("缓存逐出率过高 - {}: 逐出{}, 请求{}",
                    cacheType, stats.evictionCount(), stats.requestCount());
        }
    }
}
