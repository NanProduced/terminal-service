package com.colorlight.terminal.boot.config.threadpool;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 统一线程池配置
 * 
 * 统一管理所有异步任务和定时任务的线程池，包括：
 * 1. 定时任务调度器（deviceTaskScheduler）- 支持延迟启动
 * 2. 异步事件处理器（deviceEventExecutor）
 * 3. 异步状态更新器（deviceStatusExecutor）
 * 4. 通用异步处理器（defaultAsyncExecutor）
 * 5. 统计数据处理器（statisticsReportExecutor）
 * 
 * 所有线程池都配置了守护线程和优雅关闭机制
 * 定时任务支持通过配置控制延迟启动时间，避免启动时资源竞争
 * 
 * @author Nan
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class ThreadPoolConfig {
    
    /**
     * 定时任务调度器（全局一个就行，多了没用）
     * 用于离线检测、TTL刷新等定时任务
     *
     */
    @Primary
    @Bean("deviceTaskScheduler")
    public TaskScheduler deviceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        
        // 核心线程数
        scheduler.setPoolSize(5);
        
        // 线程名前缀
        scheduler.setThreadNamePrefix("device-scheduler-");
        
        // 设置线程为守护线程
        scheduler.setDaemon(true);
        
        // 设置等待所有任务完成后关闭线程池
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        
        // 设置等待时间
        scheduler.setAwaitTerminationSeconds(30);
        
        // 拒绝策略：由调用者线程执行
        scheduler.setRejectedExecutionHandler((r, executor) -> {
            log.warn("ThreadPool - 定时任务线程池满，使用调用者线程执行: {}", r.toString());
            r.run();
        });
        
        scheduler.initialize();
        
        log.info("ThreadPool - 定时任务线程池初始化完成: poolSize={}, 支持延迟启动配置", scheduler.getPoolSize());
        
        return scheduler;
    }
    
    /**
     * 异步事件处理器
     * 专用于设备状态事件的异步处理
     */
    @Bean("deviceEventExecutor")
    public Executor deviceEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(3);
        
        // 最大线程数
        executor.setMaxPoolSize(10);
        
        // 队列容量
        executor.setQueueCapacity(1000);
        
        // 线程空闲时间(秒)
        executor.setKeepAliveSeconds(60);
        
        // 线程名前缀
        executor.setThreadNamePrefix("device-event-");
        
        // 线程池关闭时等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // 拒绝策略：由调用者线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        
        log.info("ThreadPool - 异步事件处理器初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * 异步状态更新器
     * 专用于设备状态的异步更新（高频操作）
     */
    @Bean("deviceStatusExecutor")
    public Executor deviceStatusExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(5);
        
        // 最大线程数
        executor.setMaxPoolSize(20);
        
        // 队列容量
        executor.setQueueCapacity(5000);
        
        // 线程空闲时间(秒)
        executor.setKeepAliveSeconds(60);
        
        // 线程名前缀
        executor.setThreadNamePrefix("device-status-");
        
        // 线程池关闭时等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // 拒绝策略：由调用者线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        
        log.info("ThreadPool - 异步状态更新器初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 统计数据上报处理器
     * 专用于高频次统计数据上报：素材播放、节目播放、GPS、传感器数据
     */
    @Bean("statisticsReportExecutor")
    public Executor statisticsReportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - 基于数据库连接池设计
        executor.setCorePoolSize(8);

        // 最大线程数 - 支持突发高峰
        executor.setMaxPoolSize(30);

        // 队列容量 - 缓冲高频上报
        executor.setQueueCapacity(3000);

        // 线程空闲时间
        executor.setKeepAliveSeconds(300);  // 5分钟，适应上报波峰波谷

        // 线程命名
        executor.setThreadNamePrefix("stats-report-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        // 拒绝策略：丢弃最旧任务
        executor.setRejectedExecutionHandler((task, ext) -> {
            log.error("ThreadPool - 统计任务被拒绝: queue={}, active={}",
                    ext.getQueue().size(), ext.getActiveCount());
            // 考虑：收集监控指标
            throw new TechnicalException(TechErrorCode.THREAD_POOL_REJECTED_ERROR);
        });

        executor.initialize();

        log.info("ThreadPool - 统计上报处理器初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }



    /**
     * 默认异步执行器
     * 用于其他通用异步任务（不知道怎么分类的就用这个）
     */
    @Bean("defaultAsyncExecutor")
    @Primary
    public Executor defaultAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(2);
        
        // 最大线程数
        executor.setMaxPoolSize(8);
        
        // 队列容量
        executor.setQueueCapacity(500);
        
        // 线程空闲时间(秒)
        executor.setKeepAliveSeconds(60);
        
        // 线程名前缀
        executor.setThreadNamePrefix("default-async-");
        
        // 线程池关闭时等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // 拒绝策略：由调用者线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        
        log.info("ThreadPool - 默认异步执行器初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
}