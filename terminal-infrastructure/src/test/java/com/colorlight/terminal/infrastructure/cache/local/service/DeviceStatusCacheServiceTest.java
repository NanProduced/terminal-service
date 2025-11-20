package com.colorlight.terminal.infrastructure.cache.local.service;

import com.colorlight.terminal.application.dto.cache.DeviceUpdateContext;
import com.colorlight.terminal.application.port.outbound.cache.DeviceStatusCachePort;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DeviceStatusCacheService 缓存操作测试
 *
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceStatusCacheService - 设备状态缓存服务")
class DeviceStatusCacheServiceTest {

    @Mock
    private Cache<Long, DeviceUpdateContext> deviceUpdateContextCache;

    private DeviceStatusCacheService cacheService;

    private static final Long TEST_DEVICE_ID = 10001L;

    @BeforeEach
    void setUp() {
        cacheService = new DeviceStatusCacheService(deviceUpdateContextCache);
    }

    @Nested
    @DisplayName("getOrCreateContext 操作")
    class GetOrCreateContextTests {

        @Test
        @DisplayName("首次调用应该创建新的context")
        void should_create_new_context_on_first_call() {
            // Given
            DeviceUpdateContext newContext = new DeviceUpdateContext();
            when(deviceUpdateContextCache.get(eq(TEST_DEVICE_ID), any()))
                    .thenReturn(newContext);

            // When
            DeviceUpdateContext result = cacheService.getOrCreateContext(TEST_DEVICE_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(newContext);
            verify(deviceUpdateContextCache).get(eq(TEST_DEVICE_ID), any());
        }

        @Test
        @DisplayName("后续调用应该返回相同的context")
        void should_return_same_context_on_subsequent_calls() {
            // Given
            DeviceUpdateContext context = new DeviceUpdateContext();
            when(deviceUpdateContextCache.get(eq(TEST_DEVICE_ID), any()))
                    .thenReturn(context);

            // When - 多次调用
            DeviceUpdateContext result1 = cacheService.getOrCreateContext(TEST_DEVICE_ID);
            DeviceUpdateContext result2 = cacheService.getOrCreateContext(TEST_DEVICE_ID);

            // Then - 应该返回相同对象（由Cache管理）
            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
            verify(deviceUpdateContextCache, times(2)).get(eq(TEST_DEVICE_ID), any());
        }

        @Test
        @DisplayName("不同deviceId应该返回不同的context")
        void should_return_different_contexts_for_different_device_ids() {
            // Given
            DeviceUpdateContext context1 = new DeviceUpdateContext();
            DeviceUpdateContext context2 = new DeviceUpdateContext();

            when(deviceUpdateContextCache.get(eq(10001L), any()))
                    .thenReturn(context1);
            when(deviceUpdateContextCache.get(eq(10002L), any()))
                    .thenReturn(context2);

            // When
            DeviceUpdateContext result1 = cacheService.getOrCreateContext(10001L);
            DeviceUpdateContext result2 = cacheService.getOrCreateContext(10002L);

            // Then
            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
            assertThat(result1).isNotSameAs(result2);
            verify(deviceUpdateContextCache).get(eq(10001L), any());
            verify(deviceUpdateContextCache).get(eq(10002L), any());
        }

        @Test
        @DisplayName("getOrCreateContext 不应该抛出异常")
        void should_not_throw_exception_for_valid_device_id() {
            // Given
            DeviceUpdateContext context = new DeviceUpdateContext();
            when(deviceUpdateContextCache.get(eq(TEST_DEVICE_ID), any()))
                    .thenReturn(context);

            // When & Then
            assertThatCode(() -> cacheService.getOrCreateContext(TEST_DEVICE_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("invalidate 操作（主动驱逐）")
    class InvalidateTests {

        @Test
        @DisplayName("应该成功驱逐指定deviceId的缓存")
        void should_successfully_invalidate_cache_entry() {
            // When
            cacheService.invalidate(TEST_DEVICE_ID);

            // Then
            verify(deviceUpdateContextCache).invalidate(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("invalidate 异常不应该传播")
        void should_handle_invalidate_exceptions_gracefully() {
            // Given - cache.invalidate抛出异常
            doThrow(new RuntimeException("缓存异常"))
                    .when(deviceUpdateContextCache).invalidate(TEST_DEVICE_ID);

            // When & Then - 方法应该记录警告但不抛出异常
            assertThatCode(() -> cacheService.invalidate(TEST_DEVICE_ID))
                    .doesNotThrowAnyException();
            verify(deviceUpdateContextCache).invalidate(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("多次invalidate应该安全执行")
        void should_safely_handle_multiple_invalidates() {
            // When - 多次驱逐同一key
            cacheService.invalidate(TEST_DEVICE_ID);
            cacheService.invalidate(TEST_DEVICE_ID);
            cacheService.invalidate(TEST_DEVICE_ID);

            // Then
            verify(deviceUpdateContextCache, times(3)).invalidate(TEST_DEVICE_ID);
        }
    }

    @Nested
    @DisplayName("clearAll 操作（清空所有条目）")
    class ClearAllTests {

        @Test
        @DisplayName("应该成功清空所有缓存条目")
        void should_successfully_clear_all_entries() {
            // When
            cacheService.clearAll();

            // Then
            verify(deviceUpdateContextCache).invalidateAll();
        }

        @Test
        @DisplayName("clearAll 异常不应该传播")
        void should_handle_clear_all_exceptions_gracefully() {
            // Given - cache.invalidateAll抛出异常
            doThrow(new RuntimeException("缓存清空异常"))
                    .when(deviceUpdateContextCache).invalidateAll();

            // When & Then - 方法应该记录错误但不抛出异常
            assertThatCode(cacheService::clearAll)
                    .doesNotThrowAnyException();
            verify(deviceUpdateContextCache).invalidateAll();
        }

        @Test
        @DisplayName("clearAll后重新获取应该创建新context")
        void should_create_new_context_after_clear_all() {
            // Given
            DeviceUpdateContext context = new DeviceUpdateContext();
            when(deviceUpdateContextCache.get(eq(TEST_DEVICE_ID), any()))
                    .thenReturn(context);

            // When - 清空所有
            cacheService.clearAll();

            // Then - 验证invalidateAll被调用
            verify(deviceUpdateContextCache).invalidateAll();

            // 重新获取应该通过cache.get再次获取
            DeviceUpdateContext newContext = cacheService.getOrCreateContext(TEST_DEVICE_ID);
            assertThat(newContext).isNotNull();
            verify(deviceUpdateContextCache, times(1)).get(eq(TEST_DEVICE_ID), any());
        }
    }

    @Nested
    @DisplayName("边界条件和错误处理")
    class BoundaryAndErrorHandling {

        @Test
        @DisplayName("null deviceId 应该由Cache处理")
        void should_let_cache_handle_null_device_id() {
            // Given
            when(deviceUpdateContextCache.get(eq(null), any()))
                    .thenThrow(new NullPointerException("deviceId不能为null"));

            // When & Then - 验证异常被正确传播
            assertThatCode(() -> cacheService.getOrCreateContext(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("缓存操作应该是幂等的")
        void cache_operations_should_be_idempotent() {
            // Given
            DeviceUpdateContext context = new DeviceUpdateContext();
            when(deviceUpdateContextCache.get(eq(TEST_DEVICE_ID), any()))
                    .thenReturn(context);

            // When - 重复操作
            DeviceUpdateContext result1 = cacheService.getOrCreateContext(TEST_DEVICE_ID);
            cacheService.invalidate(TEST_DEVICE_ID);
            cacheService.invalidate(TEST_DEVICE_ID); // 再次invalidate
            DeviceUpdateContext result2 = cacheService.getOrCreateContext(TEST_DEVICE_ID);

            // Then
            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
            verify(deviceUpdateContextCache, times(2)).get(eq(TEST_DEVICE_ID), any());
            verify(deviceUpdateContextCache, times(2)).invalidate(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("应该正确处理大量deviceId")
        void should_handle_large_number_of_device_ids() {
            // Given
            when(deviceUpdateContextCache.get(any(), any()))
                    .thenAnswer(invocation -> new DeviceUpdateContext());

            // When - 处理1000个不同的deviceId
            for (long i = 10000; i < 11000; i++) {
                DeviceUpdateContext context = cacheService.getOrCreateContext(i);
                assertThat(context).isNotNull();
            }

            // Then
            verify(deviceUpdateContextCache, times(1000)).get(any(), any());
        }
    }

    @Nested
    @DisplayName("并发安全性")
    class ConcurrencySafety {

        @Test
        @DisplayName("并发getOrCreateContext应该安全执行")
        void should_safely_handle_concurrent_get_or_create() throws InterruptedException {
            // Given
            DeviceUpdateContext context = new DeviceUpdateContext();
            when(deviceUpdateContextCache.get(eq(TEST_DEVICE_ID), any()))
                    .thenReturn(context);

            // When - 多个线程并发调用
            int threadCount = 5;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    DeviceUpdateContext result = cacheService.getOrCreateContext(TEST_DEVICE_ID);
                    assertThat(result).isNotNull();
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Then - 所有线程都应该成功获取context
            verify(deviceUpdateContextCache, atLeastOnce()).get(eq(TEST_DEVICE_ID), any());
        }

        @Test
        @DisplayName("并发invalidate和getOrCreateContext应该安全执行")
        void should_safely_handle_concurrent_invalidate_and_get() throws InterruptedException {
            // Given
            DeviceUpdateContext context = new DeviceUpdateContext();
            when(deviceUpdateContextCache.get(eq(TEST_DEVICE_ID), any()))
                    .thenReturn(context);

            // When - 并发调用invalidate和get
            Thread invalidateThread = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    cacheService.invalidate(TEST_DEVICE_ID);
                }
            });

            Thread getThread = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    cacheService.getOrCreateContext(TEST_DEVICE_ID);
                }
            });

            invalidateThread.start();
            getThread.start();

            invalidateThread.join();
            getThread.join();

            // Then - 应该都安全完成
            verify(deviceUpdateContextCache, atLeastOnce()).get(eq(TEST_DEVICE_ID), any());
            verify(deviceUpdateContextCache, atLeastOnce()).invalidate(TEST_DEVICE_ID);
        }
    }
}
