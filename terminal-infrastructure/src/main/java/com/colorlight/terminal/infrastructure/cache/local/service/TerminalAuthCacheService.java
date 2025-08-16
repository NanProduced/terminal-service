package com.colorlight.terminal.infrastructure.cache.local.service;

import com.colorlight.terminal.application.dto.cache.TerminalAuthCache;
import com.colorlight.terminal.application.port.outbound.cache.TerminalAuthCachePort;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalAuthCacheService implements TerminalAuthCachePort {

    private final Cache<String, TerminalAuthCache> terminalAuthenticationCache;

    @Override
    public void cache(String accountName, TerminalAuthCache terminalAuthCache) {
        terminalAuthenticationCache.put(accountName, terminalAuthCache);
    }

    @Override
    public Optional<TerminalAuthCache> get(String accountName) {
        return Optional.ofNullable(terminalAuthenticationCache.getIfPresent(accountName));
    }

    @Override
    public void remove(String accountName) {
        terminalAuthenticationCache.invalidate(accountName);
    }

    @Override
    public void clearAll() {
        try {
            long sizeBefore = terminalAuthenticationCache.estimatedSize();
            log.info("清空所有终端认证缓存 - 清理前大小: {}", sizeBefore);
            terminalAuthenticationCache.invalidateAll();

        } catch (Exception e) {
            log.error("清空终端认证缓存失败", e);
        }
    }
}
