package com.colorlight.terminal.rpc.dto.enums;

/**
 * 清理模式枚举
 * 
 * @author Nan
 */
public enum CleanupMode {
    
    /**
     * 清理全部数据类型
     */
    ALL("清理全部数据"),
    
    /**
     * 仅清理指定数据类型
     */
    INCLUDE("仅清理指定数据类型"),
    
    /**
     * 清理除指定类型外的所有数据
     */
    EXCLUDE("清理除指定类型外的所有数据");
    
    private final String description;
    
    CleanupMode(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}