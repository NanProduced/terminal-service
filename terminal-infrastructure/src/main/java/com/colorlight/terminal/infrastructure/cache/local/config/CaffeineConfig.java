package com.colorlight.terminal.infrastructure.cache.local.config;

import com.colorlight.terminal.application.dto.cache.DeviceUpdateContext;
import com.colorlight.terminal.application.dto.cache.TerminalAuthCache;
import com.colorlight.terminal.application.port.outbound.cache.DeviceStatusFlushCallback;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Caffeine本地一级缓存配置类
 *
 * @author Nan
 * @version 1.0.0
 * @since 2024-12-15
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CaffeineConfig {

    private final DeviceConfigPort deviceConfigPort;

    // 设备状态 flush 回调
    private final ObjectProvider<DeviceStatusFlushCallback> flushCallbackProvider;

    // =================== 核心缓存Bean定义 ===================

    /**
     * 认证缓存 - 缓存终端认证信息和权限
     * <p>适用场景: WebSocket连接认证、API认证检查</p>
     *
     * @return 认证信息缓存实例
     */
    @Bean("terminalAuthenticationCache")
    @Primary
    public Cache<@NotNull String, TerminalAuthCache> terminalAuthenticationCache() {
        return Caffeine.newBuilder()
                .maximumSize(20_000L)
                .expireAfterWrite(Duration.ofMinutes(30))
                .expireAfterAccess(Duration.ofMinutes(15))
                .recordStats()
                .removalListener(createRemovalListener("TERMINAL_AUTH_CACHE"))
                .build();
    }

    /**
     * 设备状态更新上下文缓存 - 本地状态机替代 Redis 分布式锁
     *
     * @return 设备状态更新上下文缓存实例
     */
    @Bean("deviceUpdateContextCache")
    public Cache<@NotNull Long, DeviceUpdateContext> deviceUpdateContextCache() {
        long expireAfterMs = Math.max(deviceConfigPort.getOfflineTimeoutThreshold(), 60_000L);
        long maxEntries = Math.max(deviceConfigPort.getBufferPoolMaxSize() * 4L, 20_000L);

        return Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterAccess(Duration.ofMillis(expireAfterMs))
                .recordStats()
                .removalListener((Long deviceId, DeviceUpdateContext context, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    // 当缓存条目被驱逐时，尝试 flush 待处理状态
                    if (context.tryScheduleFlush()) {
                        // 通过 ObjectProvider 获取回调，避免循环依赖
                        flushCallbackProvider.ifAvailable(callback -> {
                            try {
                                callback.onContextEvicted(deviceId, context);
                                log.debug("设备状态缓存驱逐成功 flush: deviceId={}", deviceId);
                            } catch (Exception e) {
                                log.error("设备状态缓存驱逐时 flush 失败: deviceId={}", deviceId, e);
                            }
                        });
                    }
                })
                .build();
    }

    // =================== 缓存管理和监控 ===================

    /**
     * 缓存统计管理器
     * <ul>
     *   <li>统计信息收集</li>
     *   <li>性能指标监控</li>
     *   <li>告警阈值检查</li>
     * </ul>
     */
    @Bean("cacheStatsManager")
    public CaffeineCacheStatsManager cacheStatsManager() {
        return new CaffeineCacheStatsManager();
    }

    // =================== 工具方法 ===================

    /**
     * 创建移除监听器
     * 监控缓存条目的移除事件，用于调试和性能分析
     *
     * @param cacheType 缓存类型标识
     * @return RemovalListener实例
     */
    private <K, V> RemovalListener<K, V> createRemovalListener(String cacheType) {
        return (key, value, cause) -> {
            if (log.isDebugEnabled()) {
                log.debug("Caffeine - 缓存移除事件 - 类型: {}, Key: {}, 原因: {}", cacheType, key, cause);
            }
            
            // 根据移除原因进行不同处理
            switch (cause) {
                case EXPIRED:
                    log.debug("缓存过期: {} - {}", cacheType, key);
                    break;
                case SIZE:
                    log.debug("缓存容量限制: {} - {}", cacheType, key);
                    break;
                case COLLECTED:
                    log.debug("缓存被垃圾回收: {} - {}", cacheType, key);
                    break;
                case EXPLICIT:
                    log.debug("缓存主动清除: {} - {}", cacheType, key);
                    break;
                case REPLACED:
                    // 正常替换，无需记录
                    break;
            }
        };
    }
}