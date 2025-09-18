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
 * 统一线程池配置 - 基于CPU核心数动态优化
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
 * 配置原理：
 * • I/O密集型：core = CPU×2, max = CPU×3-4 (充分利用I/O等待时间)
 * • 混合型：core = CPU×1.5, max = CPU×2.5 (平衡CPU计算和I/O等待)
 * • 调度型：保持适度固定值 (避免过度调度开销)
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
     * 获取CPU核心数，用于动态配置线程池大小
     */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    static {
        log.info("ThreadPool - 检测到CPU核心数: {}, 将根据CPU核心数动态配置线程池", CPU_COUNT);
    }
    
    /**
     * 定时任务调度器（全局一个就行，多了没用）
     * 用于离线检测、TTL刷新等定时任务
     * <p>
     * 【任务分析】实际执行：
     * • DeviceOfflineCheckScheduler.checkOfflineDevices() - Redis SCAN操作 + 数据库查询
     * • AsyncDeviceStatusUpdateService定时刷新 - 缓冲池刷新调度
     * • AsyncTerminalLoginUpdateService定时刷新 - 登录状态更新调度
     * <p>
     * 【分类】调度型 - 不宜过多线程，避免调度开销
     * 【策略】保持适度固定值，最多不超过CPU核心数
     */
    @Primary
    @Bean("deviceTaskScheduler")
    public TaskScheduler deviceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        
        // 核心线程数 - 调度型任务，避免过度调度，最多不超过CPU核心数
        int poolSize = Math.min(10, Math.max(2, CPU_COUNT));
        scheduler.setPoolSize(poolSize);
        
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
        
        log.info("ThreadPool - 定时任务线程池初始化完成: poolSize={} (CPU核心数: {}), 支持延迟启动配置",
                scheduler.getPoolSize(), CPU_COUNT);
        
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
     * <p>
     * 【任务分析】实际执行：
     * • DeviceStatusEventHandler.handleDeviceOnline() - 数据库更新 + RPC调用
     * • DeviceStatusEventHandler.handlerDeviceReconnect() - 在线时长记录 + 重连记录
     * • CommandConfirmEventPublisher.publishCommandConfirmEvent() - 事件发布
     * • SystemCommandEventHandler.handleSystemCommandEvent() - 系统指令处理
     * <p>
     * 【分类】I/O密集型 - 主要是数据库操作和网络RPC调用
     * 【策略】core = CPU×2, max = CPU×4 (充分利用I/O等待时间)
     */
    @Bean("deviceEventExecutor")
    public ThreadPoolTaskExecutor deviceEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数 - I/O密集型任务，基于CPU核心数动态配置
        int corePoolSize = Math.max(4, CPU_COUNT * 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - I/O密集型，允许更多线程处理并发
        int maxPoolSize = Math.max(20, CPU_COUNT * 4);
        executor.setMaxPoolSize(maxPoolSize);
        
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
        
        log.info("ThreadPool - 异步事件处理器初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);
        
        return executor;
    }
    
    /**
     * 异步状态更新器
     * 专用于设备状态的异步更新（高频操作）
     * <p>
     * 【任务分析】实际执行：
     * • TerminalReportApplicationService.asyncSaveStatusReport() - JSON序列化 + 数据库存储 + RPC
     * • DeviceOnlineStatusApplicationService.updateLoginTime() - 登录时间更新
     * • AsyncBufferFlushEventListener.flushOnlineTimeBuffer() - 在线时长刷新
     * • DeviceSwitchOnRecordService.asyncHandlerSwitchOnRecord() - 开机记录处理
     * <p>
     * 【分类】混合型 - JSON序列化（CPU密集）+ 数据库存储（I/O密集）
     * 【策略】core = CPU×1.5, max = CPU×2.5 (平衡CPU计算和I/O等待)
     */
    @Bean("deviceStatusExecutor")
    public ThreadPoolTaskExecutor deviceStatusExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数 - 高频状态更新，混合型任务
        int corePoolSize = Math.max(8, (int) (CPU_COUNT * 1.5));
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 支持突发高峰
        int maxPoolSize = Math.max(30, CPU_COUNT * 3);
        executor.setMaxPoolSize(maxPoolSize);
        
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
        
        log.info("ThreadPool - 异步状态更新器初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);
        
        return executor;
    }

    /**
     * RPC通知处理器
     * 专用于主服务RPC调用，与核心业务线程池隔离
     * 超时容错，失败仅记录日志不影响业务
     * <p>
     * 【任务分析】实际执行：
     * • CommandConfirmEventHandler.handleCommandConfirmEvent() - 指令确认RPC调用
     * • DubboMainServiceRpcAdapter.notifyLedStatus() - LED状态上报RPC
     * • 其他主服务RPC通知操作 - 纯网络 I/O操作
     * <p>
     * 【分类】I/O密集型 - 主要是网络 RPC 调用
     * 【策略】core = CPU×2, max = CPU×3 (避免过多RPC连接，但支持并发)
     */
    @Bean("rpcNotificationExecutor")
    public ThreadPoolTaskExecutor rpcNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数 - I/O密集型：RPC网络调用
        int corePoolSize = Math.max(6, CPU_COUNT * 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 避免过多RPC连接，但支持并发
        int maxPoolSize = Math.max(12, CPU_COUNT * 3);
        executor.setMaxPoolSize(maxPoolSize);
        
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
        
        log.info("ThreadPool - RPC通知处理器初始化完成: core={}, max={}, queue={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);
        
        return executor;
    }

    /**
     * 统计数据上报处理器
     * 专用于高频次统计数据上报：素材播放、节目播放、GPS、传感器数据
     * <p>
     * 【任务分析】实际执行：
     * • TerminalReportApplicationService.asyncHandleMediaPlayRecordReport() - JSON解析 + MongoDB存储
     * • TerminalReportApplicationService.asyncHandleProgramPlayRecordReport() - JSON解析 + 数据处理
     * • TerminalReportApplicationService.asyncHandleSensorReport() - 传感器数据处理
     * • DeviceDownloadingRedisService.asyncHandlerDeviceDownloading() - Redis缓存操作
     * • AsyncBufferFlushEventListener.flushStatisticsBuffer() - 统计数据刷新
     * <p>
     * 【分类】混合型 - JSON处理（CPU密集）+ MongoDB存储（I/O密集）
     * 【策略】core = CPU×1.5, max = CPU×2.5 (支持突发数据处理高峰)
     */
    @Bean("statisticsReportExecutor")
    public ThreadPoolTaskExecutor statisticsReportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - 混合型：JSON处理+MongoDB存储
        int corePoolSize = Math.max(6, (int) (CPU_COUNT * 1.5));
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 混合型，支持突发高峰
        int maxPoolSize = Math.max(30, (int) (CPU_COUNT * 2.5));
        executor.setMaxPoolSize(maxPoolSize);

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

        log.info("ThreadPool - 统计上报处理器初始化完成: core={}, max={}, queue={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);

        return executor;
    }

    /**
     * MinIO文件上传处理器
     * 专用于设备截图等文件上传到MinIO存储
     * <p>
     * 【任务分析】实际执行：
     * • TerminalReportApplicationService.asyncSaveDeviceScreenshot() - 设备截图上传
     * • MinIO文件存储操作 - 文件I/O + 网络传输
     * <p>
     * 【分类】I/O密集型 - 主要是文件上传和网络传输
     * 【策略】core = CPU×1, max = CPU×3 (支持文件上传并发)
     */
    @Bean("minioUploadExecutor")
    public ThreadPoolTaskExecutor minioUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：I/O密集型文件上传，保持适中基数
        int corePoolSize = Math.max(2, CPU_COUNT);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数：支持文件上传并发
        int maxPoolSize = Math.max(20, CPU_COUNT * 3);
        executor.setMaxPoolSize(maxPoolSize);

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
     * <p>
     * 【任务分析】实际执行：
     * • NettyWebsocketFrameHandler.initializeWebsocketSessionAsync() - 连接初始化
     * • WebSocket连接状态存储到Redis - Redis写操作
     * • 连接管理和状态维护 - 网络 I/O 操作
     * <p>
     * 【分类】I/O密集型 - 连接初始化 + Redis 操作
     * 【策略】core = CPU×2, max = CPU×3 (支持连接建立高峰)
     */
    @Bean("websocketConnectionExecutor")
    public ThreadPoolTaskExecutor websocketConnectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - I/O密集型：连接初始化+Redis操作
        int corePoolSize = Math.max(4, CPU_COUNT * 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 支持连接建立高峰
        int maxPoolSize = Math.max(16, CPU_COUNT * 3);
        executor.setMaxPoolSize(maxPoolSize);

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

        log.info("ThreadPool - WebSocket连接处理器初始化完成: core={}, max={}, queue={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);

        return executor;
    }

    /**
     * WebSocket业务处理器
     * 专用于WebSocket消息处理中的耗时业务操作（如Redis查询、数据库操作）
     * 与连接处理器分离，避免连接建立被业务处理阻塞
     * <p>
     * 【任务分析】实际执行：
     * • V11OperationHandleRouter.handleGetCommand() - Redis查询指令
     * • V11OperationHandleRouter.handleReportMessage() - 上报数据处理
     * • WebSocket消息业务逻辑处理 - 数据库查询和更新
     * <p>
     * 【分类】I/O密集型 - Redis查询 + 数据库操作
     * 【策略】core = CPU×2, max = CPU×4 (支持高并发消息处理)
     */
    @Bean("websocketBusinessExecutor")
    public ThreadPoolTaskExecutor websocketBusinessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - I/O密集型：Redis查询+数据库操作
        int corePoolSize = Math.max(8, CPU_COUNT * 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 支持高并发消息处理
        int maxPoolSize = Math.max(32, CPU_COUNT * 4);
        executor.setMaxPoolSize(maxPoolSize);

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

        log.info("ThreadPool - WebSocket业务处理器初始化完成: core={}, max={}, queue={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);

        return executor;
    }

    /**
     * 默认异步执行器
     * 用于其他通用异步任务（不知道怎么分类的就用这个）
     * <p>
     * 【任务分析】实际执行：
     * • 未指定特定线程池的@Async方法 - 任务类型不确定
     * • Spring默认异步任务 - 可能包含CPU计算和I/O操作
     * <p>
     * 【分类】混合型 - 任务类型不确定，采用保守策略
     * 【策略】core = CPU×1, max = CPU×1.5 (保守配置，适度扩展)
     */
    @Bean("defaultAsyncExecutor")
    @Primary
    public ThreadPoolTaskExecutor defaultAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数 - 混合型：通用异步任务，保守配置
        int corePoolSize = Math.max(2, CPU_COUNT);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 混合型，适度扩展
        int maxPoolSize = Math.max(8, (int) (CPU_COUNT * 1.5));
        executor.setMaxPoolSize(maxPoolSize);
        
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
        
        log.info("ThreadPool - 默认异步执行器初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);
        
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