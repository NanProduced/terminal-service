package com.colorlight.terminal.application.port.outbound.config;

import com.colorlight.terminal.application.properties.TerminalCommandProperties;

/**
 * 指令配置端口
 * 定义Application层获取配置的接口，遵循依赖倒置原则
 * 
 * @author Nan
 * @version 1.0.0
 */
public interface CommandConfigPort {
    
    /**
     * 获取终端指令配置
     * @return 指令配置属性
     */
    TerminalCommandProperties getCommandConfig();
    
    /**
     * 获取指令过期时间(小时)
     * @return 过期时间
     */
    default Long getCommandExpireHours() {
        return getCommandConfig().getExpire().getHours();
    }
    
    /**
     * 获取缓存TTL时间(小时)  
     * @return TTL时间
     */
    default Long getCacheTtlHours() {
        return getCommandConfig().getCache().getTtlHours();
    }

    
    /**
     * 是否启用指令去重
     * @return 是否启用
     */
    default Boolean isDeduplicationEnabled() {
        return getCommandConfig().getCache().getDeduplicationEnabled();
    }
    
    /**
     * 是否启用自动清理
     * @return 是否启用
     */
    default Boolean isAutoCleanEnabled() {
        return getCommandConfig().getExpire().getAutoCleanEnabled();
    }
}