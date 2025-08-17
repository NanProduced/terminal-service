package com.colorlight.terminal.application.port.outbound.generator;

/**
 * ID生成器端口接口
 * 
 * @author Nan
 * @version 1.0.0
 */
public interface CommandIdGeneratorPort {
    
    /**
     * 生成递增的指令ID
     * ID从1开始递增，到达Integer.MAX_VALUE后重新从1开始
     * 保证单应用实例内的唯一性和顺序性
     * 
     * @return 递增的Integer ID
     */
    Integer generateCommandId();
    
    /**
     * 获取当前ID值
     * 用于监控和调试
     * 
     * @return 当前ID值
     */
    Integer getCurrentId();
    
    /**
     * 重置ID生成器
     * 仅用于测试环境
     */
    void reset();
}