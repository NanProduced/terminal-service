package com.colorlight.terminal.boot.config.threadpool;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 统一线程池配置
 * <p>
 * 统一管理所有异步任务和定时任务的线程池，包括：
 * 1. 定时任务调度器（deviceTaskScheduler）- 支持延迟启动
 * 2. 异步事件处理器（deviceEventExecutor）
 * 3. 异步状态更新器（deviceStatusExecutor）
 * 4. 通用异步处理器（defaultAsyncExecutor）
 * 5. 统计数据处理器（statisticsReportExecutor）
 * 6. rpc通知调用处理器（rpcNotificationExecutor）
 * 7. minio上传处理器（minioUploadExecutor）
 * 8. WebSocket连接处理器（websocketConnectionExecutor）- 避免阻塞EventLoop
 * 9. WebSocket业务处理器（websocketBusinessExecutor）- 处理耗时业务操作
 * <p>
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
     * 定时任务调度器 - 用于指标监控
     * 返回ThreadPoolTaskScheduler类型，供DeviceMetricsService监控使用
     */
    @Bean("deviceTaskSchedulerForMetrics")
    public ThreadPoolTaskScheduler deviceTaskSchedulerForMetrics(
            @Qualifier("deviceTaskScheduler") TaskScheduler taskScheduler) {
        return (ThreadPoolTaskScheduler) taskScheduler;
    }

    /**
     * 异步事件处理器
     * 专用于设备状态事件的异步处理
     */
    @Bean("deviceEventExecutor")
    public ThreadPoolTaskExecutor deviceEventExecutor() {
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
    public ThreadPoolTaskExecutor deviceStatusExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(8);
        
        // 最大线程数
        executor.setMaxPoolSize(30);
        
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
     * RPC通知处理器
     * 专用于主服务RPC调用，与核心业务线程池隔离
     * 超时容错，失败仅记录日志不影响业务
     */
    @Bean("rpcNotificationExecutor")
    public ThreadPoolTaskExecutor rpcNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数 - 适中配置
        executor.setCorePoolSize(4);
        
        // 最大线程数 - 避免过多RPC连接
        executor.setMaxPoolSize(8);
        
        // 队列容量 - 缓冲RPC调用
        executor.setQueueCapacity(500);
        
        // 线程空闲时间 - 较长保活
        executor.setKeepAliveSeconds(300);
        
        // 线程命名
        executor.setThreadNamePrefix("rpc-notify-");
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        // 拒绝策略：丢弃任务记录日志（RPC通知非关键）
        executor.setRejectedExecutionHandler((task, ext) -> {
            log.warn("ThreadPool - RPC通知任务被拒绝，已丢弃: queue={}, active={}", 
                    ext.getQueue().size(), ext.getActiveCount());
            // RPC通知失败不影响核心业务
        });
        
        executor.initialize();
        
        log.info("ThreadPool - RPC通知处理器初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 统计数据上报处理器
     * 专用于高频次统计数据上报：素材播放、节目播放、GPS、传感器数据
     */
    @Bean("statisticsReportExecutor")
    public ThreadPoolTaskExecutor statisticsReportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - 基于数据库连接池设计
        executor.setCorePoolSize(5);

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

    @Bean("minioUploadExecutor")
    public ThreadPoolTaskExecutor minioUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：处理稳定状态上传负载
        executor.setCorePoolSize(2);

        // 最大线程数：应对突发场景
        executor.setMaxPoolSize(20);

        // 大队列缓冲上传请求
        executor.setQueueCapacity(1000);

        executor.setThreadNamePrefix("minio-upload-");
        executor.setKeepAliveSeconds(300); // 5分钟空闲超时

        // 拒绝策略：记录并丢弃（非关键上传）
        executor.setRejectedExecutionHandler((task, ext) -> {
            log.warn("MinIO - 上传任务被拒绝: queue={}, active={}",
                    ext.getQueue().size(), ext.getActiveCount());
        });

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        return executor;
    }

    /**
     * WebSocket连接处理器
     * 专用于WebSocket连接建立、认证后续处理等操作
     * 避免阻塞Netty EventLoop线程，提升WebSocket服务性能
     */
    @Bean("websocketConnectionExecutor")
    public ThreadPoolTaskExecutor websocketConnectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - 基于WebSocket连接建立频率设计
        executor.setCorePoolSize(4);

        // 最大线程数 - 支持连接建立高峰
        executor.setMaxPoolSize(16);

        // 队列容量 - 缓冲连接建立请求
        executor.setQueueCapacity(500);

        // 线程空闲时间 - 较长保活时间，适应连接建立的波峰波谷
        executor.setKeepAliveSeconds(300);

        // 线程命名
        executor.setThreadNamePrefix("ws-connection-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // 拒绝策略：记录警告并由调用者线程执行（避免连接建立失败）
        executor.setRejectedExecutionHandler((task, ext) -> {
            log.warn("ThreadPool - WebSocket连接任务被拒绝，使用调用者线程执行: queue={}, active={}",
                    ext.getQueue().size(), ext.getActiveCount());
            // 使用调用者线程执行，确保连接建立不会失败
            task.run();
        });

        executor.initialize();

        log.info("ThreadPool - WebSocket连接处理器初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * WebSocket业务处理器
     * 专用于WebSocket消息处理中的耗时业务操作（如Redis查询、数据库操作）
     * 与连接处理器分离，避免连接建立被业务处理阻塞
     */
    @Bean("websocketBusinessExecutor")
    public ThreadPoolTaskExecutor websocketBusinessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - 基于并发消息处理需求设计
        executor.setCorePoolSize(8);

        // 最大线程数 - 支持高并发消息处理
        executor.setMaxPoolSize(32);

        // 队列容量 - 缓冲消息处理请求
        executor.setQueueCapacity(2000);

        // 线程空闲时间
        executor.setKeepAliveSeconds(120);

        // 线程命名
        executor.setThreadNamePrefix("ws-business-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        // 拒绝策略：丢弃任务并记录（消息处理失败可以容忍）
        executor.setRejectedExecutionHandler((task, ext) -> {
            log.error("ThreadPool - WebSocket业务任务被拒绝，任务已丢弃: queue={}, active={}",
                    ext.getQueue().size(), ext.getActiveCount());
            // 可以考虑发送错误响应给客户端
        });

        executor.initialize();

        log.info("ThreadPool - WebSocket业务处理器初始化完成: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * 默认异步执行器
     * 用于其他通用异步任务（不知道怎么分类的就用这个）
     */
    @Bean("defaultAsyncExecutor")
    @Primary
    public ThreadPoolTaskExecutor defaultAsyncExecutor() {
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

    /**
     * 线程池数组配置 - 用于指标监控
     * 收集所有ThreadPoolTaskExecutor实例，供DeviceMetricsService进行指标监控
     */
    @Bean("threadPoolExecutors")
    public ThreadPoolTaskExecutor[] threadPoolExecutors(
            @Qualifier("deviceEventExecutor") ThreadPoolTaskExecutor deviceEventExecutor,
            @Qualifier("deviceStatusExecutor") ThreadPoolTaskExecutor deviceStatusExecutor,
            @Qualifier("rpcNotificationExecutor") ThreadPoolTaskExecutor rpcNotificationExecutor,
            @Qualifier("statisticsReportExecutor") ThreadPoolTaskExecutor statisticsReportExecutor,
            @Qualifier("minioUploadExecutor") ThreadPoolTaskExecutor minioUploadExecutor,
            @Qualifier("websocketConnectionExecutor") ThreadPoolTaskExecutor websocketConnectionExecutor,
            @Qualifier("websocketBusinessExecutor") ThreadPoolTaskExecutor websocketBusinessExecutor,
            @Qualifier("defaultAsyncExecutor") ThreadPoolTaskExecutor defaultAsyncExecutor
    ) {
        ThreadPoolTaskExecutor[] executors = {
            deviceEventExecutor,
            deviceStatusExecutor,
            rpcNotificationExecutor,
            statisticsReportExecutor,
            minioUploadExecutor,
            websocketConnectionExecutor,
            websocketBusinessExecutor,
            defaultAsyncExecutor
        };

        log.info("ThreadPool - 线程池数组配置完成，共收集到 {} 个线程池用于指标监控", executors.length);

        return executors;
    }
}