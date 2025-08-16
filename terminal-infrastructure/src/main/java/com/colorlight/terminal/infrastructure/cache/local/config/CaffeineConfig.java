package com.colorlight.terminal.infrastructure.cache.local.config;

import com.colorlight.terminal.application.dto.cache.TerminalAuthCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Caffeine本地一级缓存配置类
 * <li>为高性能Netty WebSocket终端服务提供低延迟缓存支持</li>
 *
 * @author Nan
 * @version 1.0.0
 * @since 2024-12-15
 */
@Slf4j
@Configuration
public class CaffeineConfig {

    // =================== 核心缓存Bean定义 ===================

    /**
     * 认证缓存 - 缓存终端认证信息和权限
     * <p>适用场景: WebSocket连接认证、API认证检查</p>
     *
     * @return 认证信息缓存实例
     */
    @Bean("terminalAuthenticationCache")
    @Primary
    public Cache<String, TerminalAuthCache> terminalAuthenticationCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000L)
                .expireAfterWrite(Duration.ofMinutes(5))
                .expireAfterAccess(Duration.ofMinutes(3))
                .recordStats()
                .removalListener(createRemovalListener("TERMINAL_AUTH_CACHE"))
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
                log.debug("缓存移除事件 - 类型: {}, Key: {}, 原因: {}", cacheType, key, cause);
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