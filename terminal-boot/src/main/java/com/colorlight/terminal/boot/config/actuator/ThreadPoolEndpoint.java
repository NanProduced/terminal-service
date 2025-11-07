package com.colorlight.terminal.boot.config.actuator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import static com.colorlight.terminal.boot.config.actuator.ActuatorConstant.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池监控端点
 * 提供所有线程池的实时状态信息
 * <p>
 * 访问路径: /actuator/threadpools
 *
 * @author Nan
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "terminal.metrics.enabled",
        havingValue = "true"
)
@Endpoint(id = "threadpools")
@RequiredArgsConstructor
public class ThreadPoolEndpoint {

    private final ApplicationContext applicationContext;

    /**
     * 获取线程池统计信息
     * GET /actuator/threadpools
     *
     * @return 线程池统计数据
     */
    @ReadOperation
    public Map<String, Object> threadPoolStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 基本信息
            stats.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stats.put(FieldNames.ENDPOINT, EndpointNames.THREAD_POOL_STATISTICS);

            // 获取所有线程池Bean
            Map<String, Executor> executorBeans = applicationContext.getBeansOfType(Executor.class);

            Map<String, Object> poolDetails = new HashMap<>();
            Map<String, Object> summary = new HashMap<>();

            int totalPools = 0;
            int activePools = 0;
            int totalActiveThreads = 0;
            int totalMaxThreads = 0;
            double averageUtilization = 0.0;

            for (Map.Entry<String, Executor> entry : executorBeans.entrySet()) {
                String beanName = entry.getKey();
                Executor executor = entry.getValue();

                Map<String, Object> poolInfo = getThreadPoolInfo(beanName, executor);
                poolDetails.put(beanName, poolInfo);
                totalPools++;

                // 累计统计
                Boolean isActive = (Boolean) poolInfo.get(ThreadPoolFields.IS_ACTIVE);
                if (Boolean.TRUE.equals(isActive)) {
                    activePools++;
                }

                Integer activeThreads = (Integer) poolInfo.getOrDefault(ThreadPoolFields.ACTIVE_THREADS, 0);
                Integer maxThreads = (Integer) poolInfo.getOrDefault(ThreadPoolFields.MAX_POOL_SIZE, 0);
                Double utilization = (Double) poolInfo.getOrDefault(ThreadPoolFields.UTILIZATION, 0.0);

                totalActiveThreads += activeThreads;
                totalMaxThreads += maxThreads;
                averageUtilization += utilization;
            }

            // 计算平均利用率
            if (totalPools > 0) {
                averageUtilization = averageUtilization / totalPools;
            }

            // 汇总信息
            summary.put(ThreadPoolFields.TOTAL_THREAD_POOLS, totalPools);
            summary.put(ThreadPoolFields.ACTIVE_THREAD_POOLS, activePools);
            summary.put(ThreadPoolFields.TOTAL_ACTIVE_THREADS, totalActiveThreads);
            summary.put(ThreadPoolFields.TOTAL_MAX_THREADS, totalMaxThreads);
            summary.put(ThreadPoolFields.AVERAGE_UTILIZATION, String.format("%.2f%%", averageUtilization));
            summary.put(ThreadPoolFields.OVERALL_CAPACITY, totalMaxThreads > 0 ?
                       String.format("%.2f%%", (double) totalActiveThreads / totalMaxThreads * 100) : "N/A");

            stats.put(FieldNames.SUMMARY, summary);
            stats.put(ThreadPoolFields.THREAD_POOLS, poolDetails);

            // 健康状态评估
            Map<String, Object> health = new HashMap<>();
            evaluateThreadPoolHealth(health, poolDetails, averageUtilization, totalActiveThreads, totalMaxThreads);
            stats.put(FieldNames.HEALTH, health);

            log.debug("ThreadPoolEndpoint - 返回线程池统计: pools={}, active={}, utilization={}%",
                     totalPools, activePools, averageUtilization);

            return stats;

        } catch (Exception e) {
            log.error("ThreadPoolEndpoint - 获取线程池统计失败", e);

            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put(FieldNames.ERROR, ErrorMessages.FAILED_TO_RETRIEVE_THREAD_POOL_STATISTICS);
            errorStats.put(FieldNames.MESSAGE, e.getMessage());
            errorStats.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return errorStats;
        }
    }

    /**
     * 获取单个线程池信息
     */
    private Map<String, Object> getThreadPoolInfo(String beanName, Executor executor) {
        Map<String, Object> info = new HashMap<>();

        try {
            info.put(ThreadPoolFields.BEAN_NAME, beanName);
            info.put(ThreadPoolFields.TYPE, executor.getClass().getSimpleName());

            if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
                // Spring的ThreadPoolTaskExecutor
                ThreadPoolExecutor threadPoolExecutor = taskExecutor.getThreadPoolExecutor();

                info.put(ThreadPoolFields.CORE_POOL_SIZE, taskExecutor.getCorePoolSize());
                info.put(ThreadPoolFields.MAX_POOL_SIZE, taskExecutor.getMaxPoolSize());
                info.put(ThreadPoolFields.ACTIVE_THREADS, taskExecutor.getActiveCount());
                info.put(ThreadPoolFields.POOL_SIZE, taskExecutor.getPoolSize());
                info.put(ThreadPoolFields.QUEUE_SIZE, threadPoolExecutor.getQueue().size());
                info.put(ThreadPoolFields.QUEUE_CAPACITY, taskExecutor.getQueueCapacity());
                info.put(ThreadPoolFields.COMPLETED_TASKS, threadPoolExecutor.getCompletedTaskCount());
                info.put(ThreadPoolFields.TOTAL_TASKS, threadPoolExecutor.getTaskCount());
                info.put(ThreadPoolFields.KEEP_ALIVE_SECONDS, taskExecutor.getKeepAliveSeconds());
                info.put(ThreadPoolFields.THREAD_NAME_PREFIX, taskExecutor.getThreadNamePrefix());
                info.put(ThreadPoolFields.IS_SHUTDOWN, threadPoolExecutor.isShutdown());
                info.put(ThreadPoolFields.IS_TERMINATED, threadPoolExecutor.isTerminated());
                info.put(ThreadPoolFields.IS_ACTIVE, !threadPoolExecutor.isShutdown());

                // 计算利用率
                double utilization = taskExecutor.getMaxPoolSize() > 0 ?
                    (double) taskExecutor.getActiveCount() / taskExecutor.getMaxPoolSize() * 100 : 0.0;
                info.put(ThreadPoolFields.UTILIZATION, utilization);

                // 队列利用率
                double queueUtilization = taskExecutor.getQueueCapacity() > 0 ?
                    (double) threadPoolExecutor.getQueue().size() / taskExecutor.getQueueCapacity() * 100 : 0.0;
                info.put(ThreadPoolFields.QUEUE_UTILIZATION, queueUtilization);

                // 状态评估
                if (utilization > 80) {
                    info.put(FieldNames.STATUS, StatusValues.HIGH_UTILIZATION);
                } else if (utilization > 60) {
                    info.put(FieldNames.STATUS, StatusValues.MEDIUM_UTILIZATION);
                } else {
                    info.put(FieldNames.STATUS, StatusValues.NORMAL);
                }

                return info;

            } else if (executor instanceof ThreadPoolExecutor threadPoolExecutor) {
                // 原生ThreadPoolExecutor
                info.put(ThreadPoolFields.CORE_POOL_SIZE, threadPoolExecutor.getCorePoolSize());
                info.put(ThreadPoolFields.MAX_POOL_SIZE, threadPoolExecutor.getMaximumPoolSize());
                info.put(ThreadPoolFields.ACTIVE_THREADS, threadPoolExecutor.getActiveCount());
                info.put(ThreadPoolFields.POOL_SIZE, threadPoolExecutor.getPoolSize());
                info.put(ThreadPoolFields.QUEUE_SIZE, threadPoolExecutor.getQueue().size());
                info.put(ThreadPoolFields.COMPLETED_TASKS, threadPoolExecutor.getCompletedTaskCount());
                info.put(ThreadPoolFields.TOTAL_TASKS, threadPoolExecutor.getTaskCount());
                info.put(ThreadPoolFields.IS_SHUTDOWN, threadPoolExecutor.isShutdown());
                info.put(ThreadPoolFields.IS_TERMINATED, threadPoolExecutor.isTerminated());
                info.put(ThreadPoolFields.IS_ACTIVE, !threadPoolExecutor.isShutdown());

                double utilization = threadPoolExecutor.getMaximumPoolSize() > 0 ?
                    (double) threadPoolExecutor.getActiveCount() / threadPoolExecutor.getMaximumPoolSize() * 100 : 0.0;
                info.put(ThreadPoolFields.UTILIZATION, utilization);

                return info;
            } else {
                // 其他类型的Executor
                info.put(ThreadPoolFields.TYPE, StatusValues.UNKNOWN);
                info.put(FieldNames.DESCRIPTION, "不支持详细监控的Executor类型");
                return info;
            }

        } catch (Exception e) {
            log.warn("ThreadPoolEndpoint - 获取线程池 {} 信息失败: {}", beanName, e.getMessage());
            info.put(FieldNames.ERROR, ErrorMessages.FAILED_TO_GET_THREAD_POOL_INFO + e.getMessage());
            return info;
        }
    }

    /**
     * 评估线程池健康状态
     */
    private void evaluateThreadPoolHealth(Map<String, Object> health, Map<String, Object> poolDetails,
                                        double averageUtilization, int totalActiveThreads, int totalMaxThreads) {
        health.put(FieldNames.STATUS, StatusValues.HEALTHY);

        // 检查平均利用率
        checkAverageUtilization(health, averageUtilization);

        // 检查单个线程池状态
        PoolCheckResult result = checkIndividualPools(poolDetails);

        if (result.highUtilizationPools > 0) {
            health.put(ThreadPoolFields.HIGH_UTILIZATION_POOLS, result.highUtilizationPools);
            health.put(ThreadPoolFields.UTILIZATION_WARNING, result.highUtilizationPools + " 个线程池利用率超过80%");
        }

        if (result.highQueueUtilizationPools > 0) {
            health.put(ThreadPoolFields.HIGH_QUEUE_UTILIZATION_POOLS, result.highQueueUtilizationPools);
            health.put(ThreadPoolFields.QUEUE_WARNING, result.highQueueUtilizationPools + " 个线程池队列利用率超过80%");
        }

        // 总体容量检查
        checkOverallCapacity(health, totalActiveThreads, totalMaxThreads);

        // 性能建议
        if (averageUtilization > 70) {
            health.put(FieldNames.RECOMMENDATION, "建议监控线程池性能，考虑增加核心线程数或最大线程数");
        }
    }

    /**
     * 检查平均利用率并设置健康状态
     */
    private void checkAverageUtilization(Map<String, Object> health, double averageUtilization) {
        if (averageUtilization > 85) {
            health.put(FieldNames.STATUS, StatusValues.HIGH_UTILIZATION);
            health.put(FieldNames.WARNING, String.format("线程池平均利用率过高: %.2f%%", averageUtilization));
        } else if (averageUtilization > 70) {
            health.put(FieldNames.STATUS, StatusValues.MEDIUM_UTILIZATION);
            health.put(FieldNames.WARNING, String.format("线程池利用率较高: %.2f%%", averageUtilization));
        }
    }

    /**
     * 检查总体容量
     */
    private void checkOverallCapacity(Map<String, Object> health, int totalActiveThreads, int totalMaxThreads) {
        if (totalMaxThreads > 0) {
            double overallCapacity = (double) totalActiveThreads / totalMaxThreads * 100;
            if (overallCapacity > 85) {
                health.put(ThreadPoolFields.CAPACITY_WARNING, String.format("整体线程容量使用率过高: %.2f%%", overallCapacity));
            }
        }
    }

    /**
     * 检查各个线程池的状态
     */
    private PoolCheckResult checkIndividualPools(Map<String, Object> poolDetails) {
        int highUtilizationPools = 0;
        int highQueueUtilizationPools = 0;

        for (Map.Entry<String, Object> entry : poolDetails.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> poolInfo = (Map<String, Object>) entry.getValue();

                Double utilization = (Double) poolInfo.get(ThreadPoolFields.UTILIZATION);
                Double queueUtilization = (Double) poolInfo.get(ThreadPoolFields.QUEUE_UTILIZATION);

                if (utilization != null && utilization > 80) {
                    highUtilizationPools++;
                }

                if (queueUtilization != null && queueUtilization > 80) {
                    highQueueUtilizationPools++;
                }
            }
        }

        return new PoolCheckResult(highUtilizationPools, highQueueUtilizationPools);
    }

    /**
         * 线程池检查结果类
         */
        private record PoolCheckResult(int highUtilizationPools, int highQueueUtilizationPools) {
    }
}