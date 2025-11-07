package com.colorlight.terminal.boot.config.actuator;

import com.colorlight.terminal.infrastructure.websocket.monitor.EventLoopHealthMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static com.colorlight.terminal.boot.config.actuator.ActuatorConstant.*;

/**
 * EventLoop监控端点
 * 提供详细的EventLoop性能统计信息
 * <p>
 * 访问路径: /actuator/eventloop
 *
 * @author Nan
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "terminal.metrics.enabled",
        havingValue = "true"
)
@Endpoint(id = "eventloop")
@RequiredArgsConstructor
public class EventLoopEndpoint {

    private final EventLoopHealthMonitor eventLoopHealthMonitor;

    /**
     * 获取EventLoop统计信息
     * GET /actuator/eventloop
     *
     * @return EventLoop统计数据
     */
    @ReadOperation
    public Map<String, Object> eventLoopStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 基本信息
            stats.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stats.put(FieldNames.ENDPOINT, EndpointNames.EVENTLOOP_STATISTICS);

            // 获取EventLoop统计信息
            EventLoopHealthMonitor.EventLoopStatistics statistics = eventLoopHealthMonitor.getStatistics();

            // 当前统计数据
            Map<String, Object> currentStats = new HashMap<>();
            currentStats.put(EventLoopFields.TOTAL_PENDING_TASKS, statistics.totalPendingTasks());
            currentStats.put(EventLoopFields.MAX_PENDING_TASKS, statistics.maxPendingTasks());
            currentStats.put(EventLoopFields.WARNING_COUNT, statistics.warningCount());
            currentStats.put(EventLoopFields.CRITICAL_COUNT, statistics.criticalCount());
            stats.put(EventLoopFields.STATISTICS, currentStats);

            // 阈值配置
            Map<String, Object> thresholds = new HashMap<>();
            thresholds.put(EventLoopFields.WARNING_THRESHOLD, EventLoopValues.WARNING_THRESHOLD_VALUE);
            thresholds.put(EventLoopFields.CRITICAL_THRESHOLD, EventLoopValues.CRITICAL_THRESHOLD_VALUE);
            stats.put(EventLoopFields.THRESHOLDS, thresholds);

            // 健康状态评估
            Map<String, Object> health = new HashMap<>();
            String status = StatusValues.HEALTHY;
            String message = EventLoopValues.DEFAULT_HEALTHY_MESSAGE;

            if (statistics.maxPendingTasks() > EventLoopValues.CRITICAL_THRESHOLD_VALUE) {
                status = StatusValues.CRITICAL;
                message = "EventLoop严重阻塞，待处理任务数: " + statistics.maxPendingTasks();
            } else if (statistics.maxPendingTasks() > EventLoopValues.WARNING_THRESHOLD_VALUE) {
                status = StatusValues.WARNING;
                message = "EventLoop可能存在阻塞，待处理任务数: " + statistics.maxPendingTasks();
            } else if (statistics.criticalCount() > 0) {
                status = StatusValues.WARNING;
                message = "历史上出现过严重阻塞，累计次数: " + statistics.criticalCount();
            } else if (statistics.warningCount() > 0) {
                status = StatusValues.CAUTION;
                message = "历史上出现过告警，累计次数: " + statistics.warningCount();
            }

            health.put(FieldNames.STATUS, status);
            health.put(FieldNames.MESSAGE, message);
            health.put(FieldNames.RECOMMENDATION, getRecommendation(statistics));
            stats.put(FieldNames.HEALTH, health);

            // 性能指标分析
            Map<String, Object> performance = new HashMap<>();
            performance.put(EventLoopFields.TASK_BACKLOG_RATIO, calculateTaskBacklogRatio(statistics));
            performance.put(EventLoopFields.ALERT_FREQUENCY, calculateAlertFrequency(statistics));
            performance.put(EventLoopFields.SYSTEM_STABILITY, assessSystemStability(statistics));
            stats.put(EventLoopFields.PERFORMANCE, performance);

            log.debug("EventLoopEndpoint - 返回EventLoop统计: maxPendingTasks={}, warningCount={}, criticalCount={}",
                     statistics.maxPendingTasks(), statistics.warningCount(), statistics.criticalCount());

            return stats;

        } catch (Exception e) {
            log.error("EventLoopEndpoint - 获取EventLoop统计失败", e);

            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put(FieldNames.ERROR, ErrorMessages.FAILED_TO_RETRIEVE_EVENTLOOP_STATISTICS);
            errorStats.put(FieldNames.MESSAGE, e.getMessage());
            errorStats.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return errorStats;
        }
    }

    /**
     * 重置EventLoop统计信息
     * POST /actuator/eventloop
     *
     * @return 重置结果
     */
    @WriteOperation
    public Map<String, Object> resetEventLoopStats() {
        try {
            eventLoopHealthMonitor.resetStatistics();

            Map<String, Object> result = new HashMap<>();
            result.put(EventLoopFields.SUCCESS, true);
            result.put(FieldNames.MESSAGE, EventLoopValues.RESET_SUCCESS_MESSAGE);
            result.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            log.info("EventLoopEndpoint - EventLoop统计信息已通过actuator端点重置");

            return result;

        } catch (Exception e) {
            log.error("EventLoopEndpoint - 重置EventLoop统计失败", e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put(EventLoopFields.SUCCESS, false);
            errorResult.put(FieldNames.ERROR, ErrorMessages.FAILED_TO_RESET_EVENTLOOP_STATISTICS);
            errorResult.put(FieldNames.MESSAGE, e.getMessage());
            errorResult.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return errorResult;
        }
    }

    /**
     * 计算任务积压比率
     */
    private double calculateTaskBacklogRatio(EventLoopHealthMonitor.EventLoopStatistics statistics) {
        if (statistics.maxPendingTasks() == 0) {
            return 0.0;
        }
        return Math.min((double) statistics.maxPendingTasks() / EventLoopValues.CRITICAL_THRESHOLD_VALUE, 1.0);
    }

    /**
     * 计算告警频率
     */
    private String calculateAlertFrequency(EventLoopHealthMonitor.EventLoopStatistics statistics) {
        long totalAlerts = statistics.warningCount() + statistics.criticalCount();
        if (totalAlerts == 0) {
            return StatusValues.NO_ALERTS;
        } else if (totalAlerts < 10) {
            return StatusValues.OCCASIONAL_ALERTS;
        } else if (totalAlerts < 50) {
            return StatusValues.FREQUENT_ALERTS;
        } else {
            return StatusValues.CONTINUOUS_ALERTS;
        }
    }

    /**
     * 评估系统稳定性
     */
    private String assessSystemStability(EventLoopHealthMonitor.EventLoopStatistics statistics) {
        if (statistics.criticalCount() > 0) {
            return StatusValues.UNSTABLE;
        } else if (statistics.warningCount() > 10) {
            return StatusValues.SLIGHTLY_UNSTABLE;
        } else if (statistics.warningCount() > 0) {
            return StatusValues.BASICALLY_STABLE;
        } else {
            return StatusValues.STABLE;
        }
    }

    /**
     * 获取性能优化建议
     */
    private String getRecommendation(EventLoopHealthMonitor.EventLoopStatistics statistics) {
        if (statistics.criticalCount() > 0) {
            return EventLoopValues.CRITICAL_BLOCKING_RECOMMENDATION;
        } else if (statistics.warningCount() > 10) {
            return EventLoopValues.FREQUENT_WARNING_RECOMMENDATION;
        } else if (statistics.maxPendingTasks() > 100) {
            return EventLoopValues.BACKLOG_RECOMMENDATION;
        } else {
            return EventLoopValues.GOOD_PERFORMANCE_RECOMMENDATION;
        }
    }
}