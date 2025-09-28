package com.colorlight.terminal.application.dto.result;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指令获取结果测试
 *
 * @author Nan
 */
@DisplayName("CommandFetchResult 测试")
class CommandFetchResultTest {

    @Test
    @DisplayName("应该成功创建包含完整信息的结果")
    void should_create_success_result_with_complete_info() {
        // Given
        List<TerminalCommand> validCommands = createTestCommands(3);
        int expiredCleanedCount = 2;
        int totalCandidateCount = 5;
        long executionTimeMs = 150L;
        boolean usedBatchOptimization = true;

        // When
        CommandFetchResult result = CommandFetchResult.success(
                validCommands, expiredCleanedCount, totalCandidateCount,
                executionTimeMs, usedBatchOptimization);

        // Then
        assertThat(result.getValidCommands()).isEqualTo(validCommands);
        assertThat(result.getExpiredCleanedCount()).isEqualTo(expiredCleanedCount);
        assertThat(result.getTotalCandidateCount()).isEqualTo(totalCandidateCount);
        assertThat(result.getExecutionTimeMs()).isEqualTo(executionTimeMs);
        assertThat(result.isUsedBatchOptimization()).isEqualTo(usedBatchOptimization);
        assertThat(result.getValidCommandCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("应该正确计算有效指令数量")
    void should_calculate_valid_command_count_correctly() {
        // Given - 空指令列表
        CommandFetchResult emptyResult = CommandFetchResult.success(
                Collections.emptyList(), 0, 0, 100L, true);

        // Then
        assertThat(emptyResult.getValidCommandCount()).isZero();

        // Given - 有指令的情况
        List<TerminalCommand> commands = createTestCommands(5);
        CommandFetchResult nonEmptyResult = CommandFetchResult.success(
                commands, 2, 7, 200L, true);

        // Then
        assertThat(nonEmptyResult.getValidCommandCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("应该正确计算清理效率百分比")
    void should_calculate_cleanup_efficiency_correctly() {
        // Given - 无候选指令
        CommandFetchResult noCandidate = CommandFetchResult.success(
                Collections.emptyList(), 0, 0, 100L, true);

        // Then
        assertThat(noCandidate.getCleanupEfficiencyPercent()).isEqualTo(0.0);

        // Given - 有清理的情况
        CommandFetchResult withCleanup = CommandFetchResult.success(
                createTestCommands(3), 2, 10, 150L, true);

        // Then - 2/10 = 20%
        assertThat(withCleanup.getCleanupEfficiencyPercent()).isEqualTo(20.0);

        // Given - 全部清理
        CommandFetchResult allCleaned = CommandFetchResult.success(
                Collections.emptyList(), 5, 5, 200L, true);

        // Then - 5/5 = 100%
        assertThat(allCleaned.getCleanupEfficiencyPercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("应该支持Builder模式构建")
    void should_support_builder_pattern() {
        // Given & When
        CommandFetchResult result = CommandFetchResult.builder()
                .validCommands(createTestCommands(2))
                .expiredCleanedCount(1)
                .totalCandidateCount(3)
                .executionTimeMs(120L)
                .usedBatchOptimization(false)
                .build();

        // Then
        assertThat(result.getValidCommandCount()).isEqualTo(2);
        assertThat(result.getExpiredCleanedCount()).isEqualTo(1);
        assertThat(result.getTotalCandidateCount()).isEqualTo(3);
        assertThat(result.getExecutionTimeMs()).isEqualTo(120L);
        assertThat(result.isUsedBatchOptimization()).isFalse();
        assertThat(result.getCleanupEfficiencyPercent()).isCloseTo(33.33, within());
    }

    @Test
    @DisplayName("应该处理null指令列表")
    void should_handle_null_command_list() {
        // Given & When
        CommandFetchResult result = CommandFetchResult.builder()
                .validCommands(null)
                .expiredCleanedCount(0)
                .totalCandidateCount(0)
                .executionTimeMs(100L)
                .usedBatchOptimization(true)
                .build();

        // Then
        assertThat(result.getValidCommandCount()).isZero();
        assertThat(result.getValidCommands()).isNull();
    }

    /**
     * 创建测试指令列表
     */
    private List<TerminalCommand> createTestCommands(int count) {
        return Arrays.stream(new TerminalCommand[count])
                .map(ignored -> TerminalCommand.builder()
                        .commandId(1)
                        .deviceId(1000L)
                        .authorUrl("test-url")
                        .contentRaw("test-content")
                        .karma(100)
                        .createTime(LocalDateTime.now())
                        .expireTime(LocalDateTime.now().plusHours(1))
                        .status(TerminalCommand.CommandStatus.PENDING)
                        .build())
                .toList();
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(0.01);
    }
}