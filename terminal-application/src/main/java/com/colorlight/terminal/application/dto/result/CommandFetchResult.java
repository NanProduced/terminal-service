package com.colorlight.terminal.application.dto.result;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 指令获取结果
 * 整合了清理过期指令和获取有效指令的操作结果
 *
 * @author Nan
 * @version 1.0.0
 */
@Data
@Builder
public class CommandFetchResult {

    /**
     * 有效指令列表
     */
    private final List<TerminalCommand> validCommands;

    /**
     * 清理的过期指令数量
     */
    private final int expiredCleanedCount;

    /**
     * 总的候选指令数量（包含有效和过期）
     */
    private final int totalCandidateCount;

    /**
     * 执行时间（毫秒）
     */
    private final long executionTimeMs;

    /**
     * 是否使用了批量优化
     */
    private final boolean usedBatchOptimization;

    /**
     * 创建成功结果
     */
    public static CommandFetchResult success(List<TerminalCommand> validCommands,
                                           int expiredCleanedCount,
                                           int totalCandidateCount,
                                           long executionTimeMs,
                                           boolean usedBatchOptimization) {
        return CommandFetchResult.builder()
                .validCommands(validCommands)
                .expiredCleanedCount(expiredCleanedCount)
                .totalCandidateCount(totalCandidateCount)
                .executionTimeMs(executionTimeMs)
                .usedBatchOptimization(usedBatchOptimization)
                .build();
    }

    /**
     * 获取有效指令数量
     */
    public int getValidCommandCount() {
        return validCommands != null ? validCommands.size() : 0;
    }

    /**
     * 获取清理效率百分比
     */
    public double getCleanupEfficiencyPercent() {
        if (totalCandidateCount == 0) {
            return 0.0;
        }
        return (double) expiredCleanedCount / totalCandidateCount * 100;
    }
}