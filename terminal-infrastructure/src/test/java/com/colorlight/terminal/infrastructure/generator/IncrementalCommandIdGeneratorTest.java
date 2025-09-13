package com.colorlight.terminal.infrastructure.generator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * IncrementalCommandIdGenerator 单元测试
 * 
 * 测试覆盖：
 * - 基本ID生成功能
 * - 线程安全性
 * - 重置机制（手动和自动）
 * - 边界条件处理
 * - 统计信息获取
 * 
 * @author Nan
 */
@DisplayName("递增ID生成器测试")
class IncrementalCommandIdGeneratorTest {

    private IncrementalCommandIdGenerator generator;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        generator = new IncrementalCommandIdGenerator();
        
        // 设置日志监听器用于验证日志输出
        Logger logger = (Logger) LoggerFactory.getLogger(IncrementalCommandIdGenerator.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Nested
    @DisplayName("基本ID生成测试")
    class BasicIdGenerationTests {

        @Test
        @DisplayName("应该从1开始生成ID")
        void should_start_generating_from_1() {
            // When
            Integer firstId = generator.generateCommandId();
            
            // Then
            assertThat(firstId).isEqualTo(1);
        }

        @Test
        @DisplayName("应该生成递增的ID序列")
        void should_generate_incremental_id_sequence() {
            // When
            List<Integer> generatedIds = IntStream.range(0, 10)
                    .mapToObj(i -> generator.generateCommandId())
                    .toList();
            
            // Then
            assertThat(generatedIds).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        }

        @Test
        @DisplayName("应该返回当前ID值")
        void should_return_current_id_value() {
            // Given
            generator.generateCommandId(); // 生成第一个ID
            generator.generateCommandId(); // 生成第二个ID
            
            // When
            Integer currentId = generator.getCurrentId();
            
            // Then
            assertThat(currentId).isEqualTo(2);
        }

        @Test
        @DisplayName("初始状态getCurrentId应该返回0")
        void should_return_0_for_initial_current_id() {
            // When
            Integer initialId = generator.getCurrentId();
            
            // Then
            assertThat(initialId).isZero();
        }
    }

    @Nested
    @DisplayName("重置功能测试")
    class ResetFunctionalityTests {

        @Test
        @DisplayName("手动重置后应该从1重新开始生成")
        void should_restart_from_1_after_manual_reset() {
            // Given - 生成一些ID
            generator.generateCommandId(); // 1
            generator.generateCommandId(); // 2
            generator.generateCommandId(); // 3
            
            // When - 手动重置
            generator.reset();
            Integer firstIdAfterReset = generator.generateCommandId();
            Integer secondIdAfterReset = generator.generateCommandId();
            
            // Then
            assertAll(
                () -> assertThat(generator.getCurrentId()).isEqualTo(2),
                () -> assertThat(firstIdAfterReset).isEqualTo(1),
                () -> assertThat(secondIdAfterReset).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("手动重置应该记录警告日志")
        void should_log_warning_when_manually_reset() {
            // Given
            generator.generateCommandId(); // 生成ID使currentId变为1
            
            // When
            generator.reset();
            
            // Then
            List<ILoggingEvent> logsList = listAppender.list;
            assertThat(logsList).hasSize(1);
            
            ILoggingEvent logEvent = logsList.get(0);
            assertAll(
                () -> assertThat(logEvent.getLevel()).isEqualTo(Level.WARN),
                () -> assertThat(logEvent.getFormattedMessage()).contains("ID生成器手动重置"),
                () -> assertThat(logEvent.getFormattedMessage()).contains("原值: 1")
            );
        }

        @Test
        @DisplayName("重置多次应该每次都从0开始")
        void should_reset_to_0_multiple_times() {
            // Given
            generator.generateCommandId(); // 1
            generator.generateCommandId(); // 2
            
            // When & Then - 第一次重置
            generator.reset();
            assertThat(generator.getCurrentId()).isZero();
            
            // 生成一些ID
            generator.generateCommandId(); // 1
            generator.generateCommandId(); // 2
            generator.generateCommandId(); // 3
            
            // When & Then - 第二次重置
            generator.reset();
            assertThat(generator.getCurrentId()).isZero();
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("接近重置阈值时应该自动重置")
        void should_auto_reset_when_approaching_threshold() throws Exception {
            // Given - 使用反射设置currentId接近阈值
            Field currentIdField = IncrementalCommandIdGenerator.class.getDeclaredField("currentId");
            currentIdField.setAccessible(true);
            AtomicInteger currentId = (AtomicInteger) currentIdField.get(generator);
            
            // 设置为重置阈值-1，这样下次incrementAndGet()就会触发重置
            int resetThreshold = Integer.MAX_VALUE - 1000;
            currentId.set(resetThreshold - 1);
            
            // When
            Integer generatedId = generator.generateCommandId();
            
            // Then
            assertThat(generatedId).isEqualTo(1); // 应该重置为1
            assertThat(generator.getCurrentId()).isEqualTo(1);
        }

        @Test
        @DisplayName("自动重置时应该记录信息日志")
        void should_log_info_when_auto_reset() throws Exception {
            // Given - 设置currentId接近阈值
            Field currentIdField = IncrementalCommandIdGenerator.class.getDeclaredField("currentId");
            currentIdField.setAccessible(true);
            AtomicInteger currentId = (AtomicInteger) currentIdField.get(generator);
            
            int resetThreshold = Integer.MAX_VALUE - 1000;
            currentId.set(resetThreshold - 1);
            
            // When
            generator.generateCommandId();
            
            // Then
            List<ILoggingEvent> logsList = listAppender.list;
            assertThat(logsList).hasSize(1);
            
            ILoggingEvent logEvent = logsList.get(0);
            assertAll(
                () -> assertThat(logEvent.getLevel()).isEqualTo(Level.INFO),
                () -> assertThat(logEvent.getFormattedMessage()).contains("指令ID生成器已重置"),
                () -> assertThat(logEvent.getFormattedMessage()).contains("从1重新开始")
            );
        }

        @Test
        @DisplayName("重置阈值应该有合理的安全边界")
        void should_have_reasonable_reset_threshold() throws Exception {
            // Given - 获取重置阈值常量
            Field thresholdField = IncrementalCommandIdGenerator.class.getDeclaredField("RESET_THRESHOLD");
            thresholdField.setAccessible(true);
            int resetThreshold = (int) thresholdField.get(null);
            
            // Then - 验证阈值设置合理
            assertAll(
                () -> assertThat(resetThreshold).isLessThan(Integer.MAX_VALUE),
                () -> assertThat(resetThreshold).isGreaterThan(Integer.MAX_VALUE - 10000), // 安全边界不能太大
                () -> assertThat(resetThreshold).isEqualTo(Integer.MAX_VALUE - 1000) // 具体值验证
            );
        }
    }

    @Nested
    @DisplayName("线程安全测试")
    class ThreadSafetyTests {

        @Test
        @DisplayName("多线程并发生成ID应该保证唯一性")
        void should_generate_unique_ids_in_concurrent_environment() throws InterruptedException {
            // Given
            int threadCount = 10;
            int idsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            Set<Integer> generatedIds = ConcurrentHashMap.newKeySet();
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < idsPerThread; j++) {
                            Integer id = generator.generateCommandId();
                            generatedIds.add(id);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            
            // Then
            int expectedUniqueCount = threadCount * idsPerThread;
            assertThat(generatedIds).hasSize(expectedUniqueCount);
            assertThat(Collections.min(generatedIds)).isEqualTo(1);
            assertThat(Collections.max(generatedIds)).isEqualTo(expectedUniqueCount);
        }

        @Test
        @DisplayName("并发重置应该线程安全")
        void should_handle_concurrent_resets_safely() throws InterruptedException {
            // Given
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Integer> results = Collections.synchronizedList(new ArrayList<>());
            
            // 先生成一些ID
            IntStream.range(0, 10).forEach(i -> generator.generateCommandId());
            
            // When - 并发执行重置和生成ID
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        generator.reset();
                        Integer id = generator.generateCommandId();
                        results.add(id);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            
            // Then - 所有生成的ID都应该是有效的正数
            assertThat(results)
                .hasSize(threadCount)
                .allMatch(id -> id > 0);
        }

        @Test
        @DisplayName("并发自动重置应该线程安全")
        void should_handle_concurrent_auto_reset_safely() throws Exception {
            // Given - 设置接近重置阈值
            Field currentIdField = IncrementalCommandIdGenerator.class.getDeclaredField("currentId");
            currentIdField.setAccessible(true);
            AtomicInteger currentId = (AtomicInteger) currentIdField.get(generator);
            
            int resetThreshold = Integer.MAX_VALUE - 1000;
            currentId.set(resetThreshold - 10); // 设置接近阈值
            
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            Set<Integer> generatedIds = ConcurrentHashMap.newKeySet();
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Integer id = generator.generateCommandId();
                        generatedIds.add(id);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            
            // Then
            assertThat(generatedIds)
                    .hasSize(threadCount)
                    .allMatch(id -> id > 0)
                    .anyMatch(id -> id >= resetThreshold - 9)
                    .anyMatch(id -> id <= 10);
        }
    }

    @Nested
    @DisplayName("统计信息测试")
    class StatisticsTests {

        @Test
        @DisplayName("应该返回正确的统计信息")
        void should_return_correct_statistics() {
            // Given
            generator.generateCommandId(); // 1
            generator.generateCommandId(); // 2
            generator.generateCommandId(); // 3
            
            // When
            IncrementalCommandIdGenerator.GeneratorStats stats = generator.getStats();
            
            // Then
            assertAll(
                () -> assertThat(stats.currentId()).isEqualTo(3),
                () -> assertThat(stats.maxId()).isEqualTo(Integer.MAX_VALUE - 1000),
                () -> assertThat(stats.usagePercentage()).isGreaterThan(0).isLessThan(1)
            );
        }

        @Test
        @DisplayName("初始状态的统计信息应该正确")
        void should_return_correct_initial_statistics() {
            // When
            IncrementalCommandIdGenerator.GeneratorStats stats = generator.getStats();
            
            // Then
            assertAll(
                () -> assertThat(stats.currentId()).isZero(),
                () -> assertThat(stats.maxId()).isEqualTo(Integer.MAX_VALUE - 1000),
                () -> assertThat(stats.usagePercentage()).isEqualTo(0.0)
            );
        }

        @Test
        @DisplayName("统计信息的toString应该格式化正确")
        void should_format_statistics_toString_correctly() {
            // Given
            generator.generateCommandId(); // 1
            
            // When
            IncrementalCommandIdGenerator.GeneratorStats stats = generator.getStats();
            String statsString = stats.toString();
            
            // Then
            assertThat(statsString)
                    .contains("CommandIdGenerator - GeneratorStats")
                    .contains("currentId=1")
                    .contains("maxId=" + (Integer.MAX_VALUE - 1000))
                    .contains("usage=")
                    .contains("%");
        }

        @Test
        @DisplayName("重置后统计信息应该更新")
        void should_update_statistics_after_reset() {
            // Given
            generator.generateCommandId(); // 1
            generator.generateCommandId(); // 2
            
            // When
            generator.reset();
            IncrementalCommandIdGenerator.GeneratorStats stats = generator.getStats();
            
            // Then
            assertAll(
                () -> assertThat(stats.currentId()).isZero(),
                () -> assertThat(stats.usagePercentage()).isEqualTo(0.0)
            );
        }
    }

    @Nested
    @DisplayName("异常场景测试")
    class ExceptionScenarioTests {

        @Test
        @DisplayName("生成器应该在极限情况下正常工作")
        void should_work_normally_in_extreme_conditions() throws Exception {
            // Given - 模拟极限情况：多次接近重置阈值
            Field currentIdField = IncrementalCommandIdGenerator.class.getDeclaredField("currentId");
            currentIdField.setAccessible(true);
            AtomicInteger currentId = (AtomicInteger) currentIdField.get(generator);
            
            int resetThreshold = Integer.MAX_VALUE - 1000;
            
            // When & Then - 多次触发自动重置
            for (int cycle = 0; cycle < 3; cycle++) {
                currentId.set(resetThreshold - 1);
                Integer id = generator.generateCommandId();
                assertThat(id).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("高频调用时性能应该稳定")
        void should_maintain_stable_performance_under_high_frequency_calls() {
            // Given
            int callCount = 10000;
            
            // When
            long startTime = System.nanoTime();
            for (int i = 0; i < callCount; i++) {
                generator.generateCommandId();
            }
            long endTime = System.nanoTime();
            
            // Then
            long durationMs = (endTime - startTime) / 1_000_000;
            assertThat(durationMs).isLessThan(1000); // 10000次调用应该在1秒内完成
            assertThat(generator.getCurrentId()).isEqualTo(callCount);
        }
    }
}