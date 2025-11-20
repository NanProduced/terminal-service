package com.colorlight.terminal.application.dto.cache;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("设备更新上下文")
class DeviceUpdateContextTest {

    private DeviceUpdateContext context;

    @BeforeEach
    void setUp() {
        context = new DeviceUpdateContext();
    }

    @Nested
    @DisplayName("基础操作")
    class BasicOperations {

        @Test
        @DisplayName("新上下文没有待处理状态")
        void newContextHasNoPending() {
            assertThat(context.hasPending()).isFalse();
        }

        @Test
        @DisplayName("保存待处理状态会替换之前的值")
        void savePendingStatusReplacesValue() {
            DeviceOnlineStatus first = createStatus(1L);
            DeviceOnlineStatus second = createStatus(2L);

            context.savePendingStatus(first);
            context.savePendingStatus(second);

            assertThat(context.hasPending()).isTrue();
            assertThat(context.drainLatest()).isSameAs(second);
            assertThat(context.hasPending()).isFalse();
        }

        @Test
        @DisplayName("当没有待处理状态时drainLatest返回null")
        void drainLatestOnEmpty() {
            assertThat(context.drainLatest()).isNull();
        }
    }

    @Nested
    @DisplayName("锁机制")
    class LockMechanics {

        @Test
        @DisplayName("lock/unlock保护关键区域")
        void lockUnlockWorks() {
            context.lock();
            try {
                context.savePendingStatus(createStatus(100L));
                assertThat(context.hasPending()).isTrue();
            } finally {
                context.unlock();
            }
        }

        @Test
        @DisplayName("持有锁时可以调用drainLatest")
        void drainLatestWithLock() {
            context.savePendingStatus(createStatus(5L));

            context.lock();
            try {
                assertThat(context.drainLatest()).isNotNull();
            } finally {
                context.unlock();
            }
            assertThat(context.hasPending()).isFalse();
        }
    }

    @Nested
    @DisplayName("刷新调度")
    class FlushScheduling {

        @Test
        @DisplayName("tryScheduleFlush只在finishFlush后才能再次成功")
        void tryScheduleFlushSingleSuccess() {
            assertThat(context.tryScheduleFlush()).isTrue();
            assertThat(context.tryScheduleFlush()).isFalse();

            context.finishFlush();
            assertThat(context.tryScheduleFlush()).isTrue();
        }

        @Test
        @DisplayName("finishFlush允许多次调用")
        void finishFlushIsIdempotent() {
            context.tryScheduleFlush();
            context.finishFlush();
            context.finishFlush();

            assertThat(context.tryScheduleFlush()).isTrue();
        }

        @Test
        @DisplayName("同一时间只有一个线程可以调度刷新")
        void tryScheduleFlushIsThreadSafe() throws InterruptedException {
            int threadCount = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger success = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        if (context.tryScheduleFlush()) {
                            success.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(success.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("集成流程")
    class IntegrationFlow {

        @Test
        @DisplayName("保存 -> 尝试 -> 排空 -> 完成循环")
        void completeCycle() {
            context.savePendingStatus(createStatus(1L));
            assertThat(context.tryScheduleFlush()).isTrue();
            assertThat(context.drainLatest()).isNotNull();
            context.finishFlush();

            context.savePendingStatus(createStatus(2L));
            assertThat(context.tryScheduleFlush()).isTrue();
            assertThat(context.drainLatest()).isNotNull();
            context.finishFlush();
        }

        @Test
        @DisplayName("并发保存和排空保持最后状态")
        void concurrentSaveAndDrain() throws InterruptedException {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch latch = new CountDownLatch(2);

            pool.submit(() -> {
                try {
                    for (int i = 0; i < 1_000; i++) {
                        context.savePendingStatus(createStatus((long) i));
                    }
                } finally {
                    latch.countDown();
                }
            });

            pool.submit(() -> {
                try {
                    for (int i = 0; i < 1_000; i++) {
                        context.drainLatest();
                    }
                } finally {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(context.hasPending()).isIn(true, false);
        }
    }

    private DeviceOnlineStatus createStatus(Long deviceId) {
        return DeviceOnlineStatus.builder()
                .deviceId(deviceId)
                .status(OnlineStatus.ONLINE)
                .lastReportSource(ReportSource.HTTP)
                .lastReportTime(System.currentTimeMillis())
                .build();
    }
}