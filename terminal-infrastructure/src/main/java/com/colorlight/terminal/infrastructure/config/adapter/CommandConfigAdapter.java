package com.colorlight.terminal.infrastructure.config.adapter;

import com.colorlight.terminal.application.port.outbound.config.CommandConfigPort;
import com.colorlight.terminal.application.properties.TerminalCommandProperties;
import com.colorlight.terminal.infrastructure.config.properties.TerminalCommandConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 指令配置适配器
 * Infrastructure层实现，将Spring Boot配置转换为Application层需要的格式
 * 支持Nacos动态刷新
 * 
 * @author Nan
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandConfigAdapter implements CommandConfigPort {
    
    private final TerminalCommandConfigProperties configProperties;
    
    @Override
    public TerminalCommandProperties getCommandConfig() {
        log.debug("CommandConfigAdapter - 获取指令配置, expire.hours: {}, cache.ttlHours: {}", 
                configProperties.getExpire().getHours(), 
                configProperties.getCache().getTtlHours());
        
        TerminalCommandProperties domainConfig = new TerminalCommandProperties();
        
        // 使用BeanUtils进行深度复制
        copyExpireConfig(configProperties.getExpire(), domainConfig.getExpire());
        copyCacheConfig(configProperties.getCache(), domainConfig.getCache());
        
        return domainConfig;
    }
    
    /**
     * 复制过期配置
     */
    private void copyExpireConfig(TerminalCommandConfigProperties.ExpireConfig source, 
                                  TerminalCommandProperties.ExpireConfig target) {
        target.setHours(source.getHours());
        target.setAutoCleanEnabled(source.getAutoCleanEnabled());
        target.setCleanIntervalMinutes(source.getCleanIntervalMinutes());
    }
    
    /**
     * 复制缓存配置
     */
    private void copyCacheConfig(TerminalCommandConfigProperties.CacheConfig source,
                                 TerminalCommandProperties.CacheConfig target) {
        target.setTtlHours(source.getTtlHours());
        target.setMaxCommandsPerDevice(source.getMaxCommandsPerDevice());
        target.setDeduplicationEnabled(source.getDeduplicationEnabled());
    }
}