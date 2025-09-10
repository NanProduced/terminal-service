package com.colorlight.terminal.infrastructure.testutil;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 异步测试工具类
 * 提供异步操作的测试支持和验证工具
 * 
 * @author Nan
 */
public class AsyncTestUtils {
    
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_POLL_DELAY = Duration.ofMillis(100);
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(200);
    
    static {
        // 配置Awaitility默认设置
        Awaitility.setDefaultTimeout(DEFAULT_TIMEOUT);
        Awaitility.setDefaultPollDelay(DEFAULT_POLL_DELAY);
        Awaitility.setDefaultPollInterval(DEFAULT_POLL_INTERVAL);
    }
    
    /**
     * 等待异步条件满足（使用默认超时时间）
     * 
     * @param condition 待验证的条件
     * @throws ConditionTimeoutException 如果超时条件仍未满足
     */
    public static void awaitCondition(Callable<Boolean> condition) {
        awaitCondition(condition, DEFAULT_TIMEOUT);
    }
    
    /**
     * 等待异步条件满足
     * 
     * @param condition 待验证的条件
     * @param timeout 超时时间
     * @throws ConditionTimeoutException 如果超时条件仍未满足
     */
    public static void awaitCondition(Callable<Boolean> condition, Duration timeout) {
        try {
            await()
                .atMost(timeout)
                .pollDelay(DEFAULT_POLL_DELAY)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(condition);
        } catch (Exception e) {
            throw new ConditionTimeoutException(
                String.format("Condition was not satisfied within %s", timeout), e);
        }
    }
    
    /**
     * 等待异步条件满足，并返回结果
     * 
     * @param supplier 结果供应商
     * @param timeout 超时时间
     * @param <T> 结果类型
     * @return 满足条件时的结果
     */
    public static <T> T awaitResult(Callable<T> supplier, Duration timeout) {
        try {
            return await()
                .atMost(timeout)
                .pollDelay(DEFAULT_POLL_DELAY)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(supplier, Objects::nonNull);
        } catch (Exception e) {
            throw new AssertionError("Failed to wait for result", e);
        }
    }
    
    /**
     * 验证CompletableFuture成功完成
     * 
     * @param future 异步操作的Future
     * @param timeout 超时时间
     * @param <T> 结果类型
     * @return Future的结果
     */
    public static <T> T verifyFutureSuccess(CompletableFuture<T> future, Duration timeout) {
        try {
            awaitCondition(future::isDone, timeout);
            assertThat(future).succeedsWithin(timeout);
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("Future execution failed", e);
        }
    }
    
    /**
     * 验证CompletableFuture成功完成（使用默认超时）
     */
    public static <T> T verifyFutureSuccess(CompletableFuture<T> future) {
        return verifyFutureSuccess(future, DEFAULT_TIMEOUT);
    }
    
    /**
     * 验证CompletableFuture失败
     * 
     * @param future 异步操作的Future
     * @param timeout 超时时间
     * @param expectedExceptionClass 期望的异常类型
     */
    public static void verifyFutureFailure(CompletableFuture<?> future, Duration timeout, 
                                         Class<? extends Throwable> expectedExceptionClass) {
        try {
            awaitCondition(future::isDone, timeout);
            assertThat(future).failsWithin(timeout);
            
            if (expectedExceptionClass != null) {
                assertThat(future)
                    .isCompletedExceptionally()
                    .failsWithin(timeout)
                    .withThrowableOfType(expectedExceptionClass);
            }
        } catch (Exception e) {
            throw new AssertionError("Future failure verification failed", e);
        }
    }
    
    /**
     * 等待异步方法执行完成
     * 
     * @param asyncMethod 异步方法执行
     * @param timeout 超时时间
     */
    public static void awaitAsyncExecution(Runnable asyncMethod, Duration timeout) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(asyncMethod);
        verifyFutureSuccess(future, timeout);
    }
    
    /**
     * 等待异步方法执行完成（使用默认超时）
     */
    public static void awaitAsyncExecution(Runnable asyncMethod) {
        awaitAsyncExecution(asyncMethod, DEFAULT_TIMEOUT);
    }
    
    /**
     * 验证在指定时间内没有发生某个事件
     * 
     * @param condition 不应该满足的条件
     * @param duration 观察时间
     */
    public static void verifyNoEventOccurs(Callable<Boolean> condition, Duration duration) {
        try {
            await()
                .pollDelay(Duration.ofMillis(50))
                .pollInterval(Duration.ofMillis(100))
                .during(duration)
                .until(() -> !condition.call());
        } catch (Exception e) {
            throw new AssertionError("Unexpected event occurred during observation period", e);
        }
    }

    /**
     * 创建一个简单的异步任务用于测试
     * 
     * @param delay 延迟时间
     * @param result 返回结果
     * @param <T> 结果类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> createDelayedTask(Duration delay, T result) {
        return CompletableFuture.supplyAsync(() -> result, 
            CompletableFuture.delayedExecutor(delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
    }
    
    /**
     * 创建一个会失败的异步任务用于测试
     * 
     * @param delay 延迟时间
     * @param exception 抛出的异常
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> createFailingTask(Duration delay, Throwable exception) {
        return CompletableFuture.runAsync(() -> {
            throw new RuntimeException(exception);
        }, CompletableFuture.delayedExecutor(delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
    }
    
    /**
     * 验证方法在指定时间内被调用了指定次数
     * 
     * @param verificationMethod 验证方法（通常是Mockito.verify调用）
     * @param timeout 超时时间
     */
    public static void awaitVerification(Runnable verificationMethod, Duration timeout) {
        awaitCondition(() -> {
            try {
                verificationMethod.run();
                return true;
            } catch (AssertionError e) {
                return false;
            }
        }, timeout);
    }
    
    /**
     * 线程安全的计数器，用于异步测试中的计数
     */
    public static class AsyncCounter {
        private volatile int count = 0;
        
        public synchronized void increment() {
            count++;
        }
        
        public synchronized void decrement() {
            count--;
        }
        
        public synchronized int get() {
            return count;
        }
        
        public synchronized void reset() {
            count = 0;
        }
        
        public boolean equals(int expected) {
            return get() == expected;
        }
    }
}