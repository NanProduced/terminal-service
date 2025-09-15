package com.colorlight.terminal.infrastructure.generator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import com.colorlight.terminal.infrastructure.testutil.RedisTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RedisCommandIdGenerator 单元测试
 * <p>
 * 测试覆盖：
 * - 基本ID生成功能
 * - Redis key不存在时的处理
 * - 重置机制（手动和自动）
 * - 异常处理和降级策略
 * - 统计信息获取
 * - 并发安全性
 *
 * @author Nan
 */
@DisplayName("Redis指令ID生成器测试")
class RedisCommandIdGeneratorTest {

    private RedisCommandIdGenerator generator;
    private ValueOperations<String, Object> mockValueOps;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        // 创建Mock的RedisTemplate
        RedisTemplate<String, Object> mockRedisTemplate = RedisTestUtils.createMockRedisTemplate();
        mockValueOps = mockRedisTemplate.opsForValue();

        // 创建生成器实例
        generator = new RedisCommandIdGenerator(mockRedisTemplate);

        // 设置日志监听器用于验证日志输出
        Logger logger = (Logger) LoggerFactory.getLogger(RedisCommandIdGenerator.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Nested
    @DisplayName("基本ID生成测试")
    class BasicIdGenerationTests {

        @Test
        @DisplayName("首次生成应该返回1")
        void should_return_1_for_first_generation() {
            // When
            Integer firstId = generator.generateCommandId();

            // Then
            assertThat(firstId).isEqualTo(1);
            verify(mockValueOps).increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
        }

        @Test
        @DisplayName("应该生成递增的ID序列")
        void should_generate_incremental_id_sequence() {
            // When
            List<Integer> generatedIds = IntStream.range(0, 5)
                    .mapToObj(i -> generator.generateCommandId())
                    .toList();

            // Then
            assertThat(generatedIds).containsExactly(1, 2, 3, 4, 5);
            verify(mockValueOps, times(5)).increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
        }

        @Test
        @DisplayName("应该正确调用Redis INCR命令")
        void should_call_redis_incr_command_correctly() {
            // When
            generator.generateCommandId();
            generator.generateCommandId();

            // Then
            verify(mockValueOps, times(2)).increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
        }
    }

    @Nested
    @DisplayName("获取当前ID测试")
    class GetCurrentIdTests {

        @Test
        @DisplayName("初始状态应该返回0")
        void should_return_0_for_initial_state() {
            // Given - Redis中没有对应的key
            when(mockValueOps.get(RedisKeyConstant.COMMAND_ID_SEQ_KEY)).thenReturn(null);

            // When
            Integer currentId = generator.getCurrentId();

            // Then
            assertThat(currentId).isZero();
            verify(mockValueOps).get(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
        }

        @Test
        @DisplayName("应该返回正确的当前ID值")
        void should_return_correct_current_id() {
            // Given - 模拟Redis中已有值
            when(mockValueOps.get(RedisKeyConstant.COMMAND_ID_SEQ_KEY)).thenReturn(42);

            // When
            Integer currentId = generator.getCurrentId();

            // Then
            assertThat(currentId).isEqualTo(42);
            verify(mockValueOps).get(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
        }

        @Test
        @DisplayName("Redis异常时应该返回0")
        void should_return_0_when_redis_exception() {
            // Given
            when(mockValueOps.get(anyString())).thenThrow(new RuntimeException("Redis error"));

            // When
            Integer currentId = generator.getCurrentId();

            // Then
            assertThat(currentId).isZero();
        }
    }

    @Nested
    @DisplayName("重置功能测试")
    class ResetFunctionalityTests {

        @Test
        @DisplayName("手动重置应该设置为0并记录日志")
        void should_reset_to_0_and_log_warning() {
            // Given - 当前ID为10
            when(mockValueOps.get(RedisKeyConstant.COMMAND_ID_SEQ_KEY)).thenReturn(10);

            // When
            generator.reset();

            // Then
            verify(mockValueOps).set(RedisKeyConstant.COMMAND_ID_SEQ_KEY, 0);

            List<ILoggingEvent> logsList = listAppender.list;
            assertThat(logsList).hasSize(1);
            ILoggingEvent logEvent = logsList.get(0);
            assertAll(
                () -> assertThat(logEvent.getLevel()).isEqualTo(Level.WARN),
                () -> assertThat(logEvent.getFormattedMessage()).contains("Redis ID生成器手动重置"),
                () -> assertThat(logEvent.getFormattedMessage()).contains("原值: 10")
            );
        }

        @Test
        @DisplayName("重置异常时应该记录错误日志")
        void should_log_error_when_reset_fails() {
            // Given - get操作和set操作都抛异常
            when(mockValueOps.get(anyString())).thenThrow(new RuntimeException("Redis error"));
            doThrow(new RuntimeException("Redis error")).when(mockValueOps).set(anyString(), any());

            // When
            generator.reset();

            // Then - 应该记录两个日志：获取当前ID异常 + 重置异常
            List<ILoggingEvent> logsList = listAppender.list;
            assertThat(logsList).hasSizeGreaterThanOrEqualTo(1);

            // 查找重置异常的日志
            boolean hasResetErrorLog = logsList.stream()
                .anyMatch(event -> event.getLevel() == Level.ERROR &&
                          event.getFormattedMessage().contains("重置Redis指令ID异常"));
            assertThat(hasResetErrorLog).isTrue();
        }
    }

    @Nested
    @DisplayName("自动重置测试")
    class AutoResetTests {

        @Test
        @DisplayName("达到重置阈值时应该自动重置")
        void should_auto_reset_when_reaching_threshold() {
            // Given - 模拟increment返回接近阈值的值
            long nearThreshold = Integer.MAX_VALUE - 50L;
            when(mockValueOps.increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY)).thenReturn(nearThreshold);

            // When
            Integer generatedId = generator.generateCommandId();

            // Then
            assertThat(generatedId).isEqualTo(1);
            verify(mockValueOps).set(RedisKeyConstant.COMMAND_ID_SEQ_KEY, 1);

            List<ILoggingEvent> logsList = listAppender.list;
            assertThat(logsList).hasSize(1);
            ILoggingEvent logEvent = logsList.get(0);
            assertAll(
                () -> assertThat(logEvent.getLevel()).isEqualTo(Level.INFO),
                () -> assertThat(logEvent.getFormattedMessage()).contains("Redis指令ID生成器已重置"),
                () -> assertThat(logEvent.getFormattedMessage()).contains("从1重新开始")
            );
        }

        @Test
        @DisplayName("正常范围内不应该触发重置")
        void should_not_reset_within_normal_range() {
            // Given - 正常范围内的值
            when(mockValueOps.increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY)).thenReturn(1000L);

            // When
            Integer generatedId = generator.generateCommandId();

            // Then
            assertThat(generatedId).isEqualTo(1000);
            verify(mockValueOps, never()).set(anyString(), any());
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Redis连接异常时应该使用降级策略")
        void should_use_fallback_when_redis_connection_fails() {
            // Given
            when(mockValueOps.increment(anyString())).thenThrow(new RuntimeException("Redis connection failed"));

            // When
            Integer generatedId = generator.generateCommandId();

            // Then
            assertThat(generatedId).isEqualTo(1); // 降级策略返回1

            List<ILoggingEvent> logsList = listAppender.list;
            assertThat(logsList).hasSize(1);
            ILoggingEvent logEvent = logsList.get(0);
            assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);
            assertThat(logEvent.getFormattedMessage()).contains("Redis生成指令ID异常，降级使用时间戳");
        }

        @Test
        @DisplayName("Redis increment返回null时应该返回1")
        void should_return_1_when_increment_returns_null() {
            // Given
            when(mockValueOps.increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY)).thenReturn(null);

            // When
            Integer generatedId = generator.generateCommandId();

            // Then
            assertThat(generatedId).isEqualTo(1);

            List<ILoggingEvent> logsList = listAppender.list;
            assertThat(logsList).hasSize(1);
            ILoggingEvent logEvent = logsList.get(0);
            assertThat(logEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(logEvent.getFormattedMessage()).contains("Redis INCR返回null，使用默认值1");
        }
    }

    @Nested
    @DisplayName("重置阈值测试")
    class ResetThresholdTests {

        @Test
        @DisplayName("重置阈值应该有合理的安全边界")
        void should_have_reasonable_reset_threshold() throws Exception {
            // Given - 通过反射获取重置阈值常量
            Field thresholdField = RedisCommandIdGenerator.class.getDeclaredField("RESET_THRESHOLD");
            thresholdField.setAccessible(true);
            int resetThreshold = (int) thresholdField.get(null);

            // Then - 验证阈值设置合理
            assertAll(
                () -> assertThat(resetThreshold).isLessThan(Integer.MAX_VALUE),
                () -> assertThat(resetThreshold).isGreaterThan(Integer.MAX_VALUE - 10000), // 安全边界不能太大
                () -> assertThat(resetThreshold).isEqualTo(Integer.MAX_VALUE - 100) // 具体值验证
            );
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("多线程并发生成ID应该调用正确次数的Redis操作")
        void should_call_redis_correct_times_in_concurrent_environment() throws InterruptedException {
            // Given
            int threadCount = 10;
            int idsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < idsPerThread; j++) {
                            generator.generateCommandId();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - 验证Redis increment被调用了正确的次数
            verify(mockValueOps, times(threadCount * idsPerThread)).increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
        }

        @Test
        @DisplayName("并发重置应该线程安全")
        void should_handle_concurrent_resets_safely() throws InterruptedException {
            // Given
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // 模拟getCurrentId返回不同值
            when(mockValueOps.get(RedisKeyConstant.COMMAND_ID_SEQ_KEY))
                .thenReturn(10, 20, 30, 40, 50);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        generator.reset();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - 验证set操作被调用了正确次数
            verify(mockValueOps, times(threadCount)).set(RedisKeyConstant.COMMAND_ID_SEQ_KEY, 0);
        }
    }

    @Nested
    @DisplayName("Redis Key常量测试")
    class RedisKeyTests {

        @Test
        @DisplayName("应该使用正确的Redis Key")
        void should_use_correct_redis_key() {
            // When
            generator.generateCommandId();

            // Then
            verify(mockValueOps).increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
            assertThat(RedisKeyConstant.COMMAND_ID_SEQ_KEY).isEqualTo("terminal:command:id:seq");
        }

        @Test
        @DisplayName("获取当前ID应该使用相同的Redis Key")
        void should_use_same_redis_key_for_get_current_id() {
            // When
            generator.getCurrentId();

            // Then
            verify(mockValueOps).get(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
        }

        @Test
        @DisplayName("重置应该使用相同的Redis Key")
        void should_use_same_redis_key_for_reset() {
            // Given
            when(mockValueOps.get(RedisKeyConstant.COMMAND_ID_SEQ_KEY)).thenReturn(100);

            // When
            generator.reset();

            // Then
            verify(mockValueOps).get(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
            verify(mockValueOps).set(RedisKeyConstant.COMMAND_ID_SEQ_KEY, 0);
        }
    }
}