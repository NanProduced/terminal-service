package com.colorlight.terminal.infrastructure.websocket.monitor;

import com.colorlight.terminal.infrastructure.websocket.server.NettyWebsocketServer;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EventLoop健康监控器
 * 监控Netty EventLoop的健康状态，及时发现性能问题
 * <p>
 * 监控指标：
 * 1. EventLoop待处理任务数量
 * 2. EventLoop是否处于关闭状态
 * 3. EventLoop性能统计
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventLoopHealthMonitor implements HealthIndicator {

    /**
     * EventLoop待处理任务告警阈值
     * 当待处理任务超过此阈值时，认为EventLoop可能存在阻塞
     */
    private static final long PENDING_TASKS_WARNING_THRESHOLD = 1000L;

    /**
     * EventLoop待处理任务严重告警阈值
     * 当待处理任务超过此阈值时，认为EventLoop严重阻塞
     */
    private static final long PENDING_TASKS_CRITICAL_THRESHOLD = 5000L;

    /**
     * 统计计数器
     */
    private final AtomicLong totalPendingTasks = new AtomicLong(0);
    private final AtomicLong maxPendingTasks = new AtomicLong(0);
    private final AtomicLong warningCount = new AtomicLong(0);
    private final AtomicLong criticalCount = new AtomicLong(0);

    /**
     * 工作线程组字段名
     */
    private static final String WORK_GROUP_FIELD_NAME = "workerGroup";

    /**
     * Netty WebSocket服务器引用
     * 用于获取EventLoopGroup进行监控
     */
    @Autowired(required = false)
    private NettyWebsocketServer nettyWebsocketServer;

    /**
     * 定期监控EventLoop健康状态
     * 每120秒检查一次，避免过于频繁的监控影响性能
     */
    @Scheduled(fixedRate = 120000)
    public void monitorEventLoopHealth() {
        try {
            if (nettyWebsocketServer == null) {
                log.debug("EventLoopMonitor - NettyWebsocketServer未启用，跳过监控");
                return;
            }

            // 获取WorkerGroup进行监控
            EventLoopGroup workerGroup = getWorkerGroupFromServer();
            if (workerGroup == null) {
                log.debug("EventLoopMonitor - 无法获取WorkerGroup，跳过监控");
                return;
            }

            monitorEventLoopGroup(workerGroup);

        } catch (Exception e) {
            log.warn("EventLoopMonitor - EventLoop健康监控异常", e);
        }
    }

    /**
     * 监控EventLoopGroup中所有EventLoop的状态
     */
    private void monitorEventLoopGroup(EventLoopGroup eventLoopGroup) {
        long groupTotalPendingTasks = 0;
        long groupMaxPendingTasks = 0;
        int activeEventLoops = 0;
        int inactiveEventLoops = 0;

        // 遍历EventLoopGroup中的所有EventExecutor
        for (EventExecutor eventExecutor : eventLoopGroup) {
            try {
                // 检查EventExecutor是否活跃
                if (eventExecutor.isShuttingDown() || eventExecutor.isShutdown()) {
                    inactiveEventLoops++;
                    continue;
                }

                activeEventLoops++;

                // 获取待处理任务数量（只有SingleThreadEventExecutor才有pendingTasks方法）
                long pendingTasks = 0;
                if (eventExecutor instanceof SingleThreadEventExecutor singleThreadEventExecutor) {
                    pendingTasks = singleThreadEventExecutor.pendingTasks();
                }
                groupTotalPendingTasks += pendingTasks;
                groupMaxPendingTasks = Math.max(groupMaxPendingTasks, pendingTasks);

                // 检查是否超过告警阈值
                if (pendingTasks > PENDING_TASKS_CRITICAL_THRESHOLD) {
                    criticalCount.incrementAndGet();
                    log.error("EventLoopMonitor - EventLoop严重阻塞: group={}, eventExecutor={}, pendingTasks={}",
                            WORK_GROUP_FIELD_NAME, eventExecutor.toString(), pendingTasks);
                } else if (pendingTasks > PENDING_TASKS_WARNING_THRESHOLD) {
                    warningCount.incrementAndGet();
                    log.warn("EventLoopMonitor - EventLoop可能阻塞: group={}, eventExecutor={}, pendingTasks={}",
                            WORK_GROUP_FIELD_NAME, eventExecutor.toString(), pendingTasks);
                }

            } catch (Exception e) {
                log.warn("EventLoopMonitor - 监控单个EventExecutor异常: group={}, eventExecutor={}",
                        WORK_GROUP_FIELD_NAME, eventExecutor.toString(), e);
            }
        }

        // 更新统计信息
        totalPendingTasks.set(groupTotalPendingTasks);
        final long finalGroupMaxPendingTasks = groupMaxPendingTasks;
        maxPendingTasks.updateAndGet(current -> Math.max(current, finalGroupMaxPendingTasks));

        // 输出监控摘要
        log.debug("EventLoopMonitor - {}监控摘要: activeEventLoops={}, inactiveEventLoops={}, " +
                        "totalPendingTasks={}, maxPendingTasks={}",
                WORK_GROUP_FIELD_NAME, activeEventLoops, inactiveEventLoops, groupTotalPendingTasks, groupMaxPendingTasks);
    }

    /**
     * 通过反射获取NettyWebsocketServer的WorkerGroup
     * 由于WorkerGroup字段是私有的，需要通过反射访问
     */
    private EventLoopGroup getWorkerGroupFromServer() {
        try {
            Field workerGroupField = NettyWebsocketServer.class.getDeclaredField(WORK_GROUP_FIELD_NAME);
            workerGroupField.trySetAccessible();
            return (EventLoopGroup) workerGroupField.get(nettyWebsocketServer);
        } catch (Exception e) {
            log.debug("EventLoopMonitor - 无法通过反射获取WorkerGroup", e);
            return null;
        }
    }

    /**
     * 实现Spring Boot Actuator健康检查接口
     * 将EventLoop健康状态集成到应用健康检查中
     */
    @Override
    public Health health() {
        try {
            if (nettyWebsocketServer == null) {
                return Health.down()
                        .withDetail("reason", "NettyWebsocketServer未启用")
                        .build();
            }

            EventLoopGroup workerGroup = getWorkerGroupFromServer();
            if (workerGroup == null) {
                return Health.down()
                        .withDetail("reason", "无法获取EventLoopGroup")
                        .build();
            }

            // 检查EventLoopGroup是否关闭
            if (workerGroup.isShuttingDown() || workerGroup.isShutdown()) {
                return Health.down()
                        .withDetail("reason", "EventLoopGroup已关闭")
                        .withDetail("isShuttingDown", workerGroup.isShuttingDown())
                        .withDetail("isShutdown", workerGroup.isShutdown())
                        .build();
            }

            // 检查当前待处理任务数量
            long currentTotalPendingTasks = totalPendingTasks.get();
            long currentMaxPendingTasks = maxPendingTasks.get();

            Health.Builder healthBuilder = Health.up();

            // 根据待处理任务数量判断健康状态
            if (currentMaxPendingTasks > PENDING_TASKS_CRITICAL_THRESHOLD) {
                healthBuilder = Health.down()
                        .withDetail("reason", "EventLoop严重阻塞");
            } else if (currentMaxPendingTasks > PENDING_TASKS_WARNING_THRESHOLD) {
                healthBuilder = Health.up()
                        .withDetail("warning", "EventLoop可能存在阻塞");
            }

            return healthBuilder
                    .withDetail("totalPendingTasks", currentTotalPendingTasks)
                    .withDetail("maxPendingTasks", currentMaxPendingTasks)
                    .withDetail("warningCount", warningCount.get())
                    .withDetail("criticalCount", criticalCount.get())
                    .withDetail("warningThreshold", PENDING_TASKS_WARNING_THRESHOLD)
                    .withDetail("criticalThreshold", PENDING_TASKS_CRITICAL_THRESHOLD)
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("reason", "健康检查异常")
                    .withDetail("exception", e.getMessage())
                    .build();
        }
    }

    /**
     * 重置统计计数器
     * 可用于定期重置统计信息或测试
     */
    public void resetStatistics() {
        totalPendingTasks.set(0);
        maxPendingTasks.set(0);
        warningCount.set(0);
        criticalCount.set(0);
        log.info("EventLoopMonitor - 统计计数器已重置");
    }

    /**
     * 获取当前监控统计信息
     */
    public EventLoopStatistics getStatistics() {
        return new EventLoopStatistics(
                totalPendingTasks.get(),
                maxPendingTasks.get(),
                warningCount.get(),
                criticalCount.get()
        );
    }

    /**
     * EventLoop统计信息数据类
     */
    public record EventLoopStatistics(long totalPendingTasks, long maxPendingTasks, long warningCount,
                                      long criticalCount) {

    @Override
        public @NotNull String toString() {
            return String.format("EventLoopStatistics{totalPendingTasks=%d, maxPendingTasks=%d, " +
                            "warningCount=%d, criticalCount=%d}",
                    totalPendingTasks, maxPendingTasks, warningCount, criticalCount);
        }
    }
}