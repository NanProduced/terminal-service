package com.colorlight.terminal.application.port.outbound.command;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.dto.result.CommandFetchResult;

import java.util.List;
import java.util.Optional;

/**
 * 指令缓存端口接口
 * 
 * @author Nan
 * @version 1.0.0
 */
public interface CommandCachePort {
    
    /**
     * 保存指令到缓存
     * 实现去重逻辑：相同authorUrl的指令会被覆盖
     * 
     * @param command 指令对象
     * @return 是否保存成功
     */
    boolean saveCommand(TerminalCommand command);
    
    /**
     * 获取设备的所有待执行指令
     * 
     * @param deviceId 设备ID
     * @return 指令列表
     */
    List<TerminalCommand> getPendingCommands(Long deviceId);
    
    /**
     * 根据指令ID获取指令详情
     * 
     * @param deviceId 设备ID
     * @param commandId 指令ID
     * @return 指令对象
     */
    Optional<TerminalCommand> getCommand(Long deviceId, Integer commandId);
    
    /**
     * 删除指令 (确认执行后)
     * 
     * @param deviceId 设备ID
     * @param commandId 指令ID
     * @return 是否删除成功
     */
    boolean removeCommand(Long deviceId, Integer commandId);
    
    /**
     * 清理设备的过期指令
     * 
     * @param deviceId 设备ID
     * @return 清理的指令数量
     */
    int cleanExpiredCommands(Long deviceId);
    
    /**
     * 检查设备是否有待执行指令
     *
     * @param deviceId 设备ID
     * @return 是否有待执行指令
     */
    boolean hasPendingCommands(Long deviceId);

    /**
     * 获取待执行指令并自动清理过期指令（性能优化版本）
     * 一次查询同时完成清理过期和获取有效指令，减少Redis往返次数
     *
     * @param deviceId 设备ID
     * @return 指令获取结果，包含有效指令列表和清理统计
     */
    CommandFetchResult getPendingCommandsWithCleanup(Long deviceId);
}