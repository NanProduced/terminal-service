package com.colorlight.terminal.application.properties;

import lombok.Data;

/**
 * 终端指令配置属性
 * Domain配置数据传输对象，用于在Application层传递配置信息
 * 
 * @author Nan
 * @version 1.0.0
 */
@Data
public class TerminalCommandProperties {
    
    /**
     * 指令过期时间配置
     */
    private ExpireConfig expire = new ExpireConfig();
    
    /**
     * 指令缓存配置
     */
    private CacheConfig cache = new CacheConfig();
    
    /**
     * 过期时间配置
     */
    @Data
    public static class ExpireConfig {
        /**
         * 指令过期时间(小时)
         */
        private Long hours = 24L;
        
        /**
         * 是否启用自动清理过期指令
         */
        private Boolean autoCleanEnabled = true;
        
        /**
         * 自动清理间隔(分钟)
         */
        private Long cleanIntervalMinutes = 60L;
    }
    
    /**
     * 缓存配置
     */
    @Data
    public static class CacheConfig {
        /**
         * Redis TTL时间(小时)，应与expire.hours保持一致
         */
        private Long ttlHours = 24L;
        
        /**
         * 最大缓存指令数(每设备)
         */
        private Integer maxCommandsPerDevice = 1000;
        
        /**
         * 是否启用指令去重
         */
        private Boolean deduplicationEnabled = true;
    }
}
