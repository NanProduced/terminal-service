package com.colorlight.terminal.boot.config.actuator;

import com.colorlight.terminal.infrastructure.websocket.connection.ShardedConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import static com.colorlight.terminal.boot.config.actuator.ActuatorConstant.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket连接监控端点
 * 提供实时WebSocket连接统计信息
 * <p>
 * 访问路径: /actuator/websocket
 *
 * @author Nan
 */
@Slf4j
@Component
@Endpoint(id = "websocket")
@RequiredArgsConstructor
public class WebSocketConnectionEndpoint {

    private final ShardedConnectionManager connectionManager;

    /**
     * 获取WebSocket连接统计信息
     * GET /actuator/websocket
     *
     * @return WebSocket连接统计数据
     */
    @ReadOperation
    public Map<String, Object> websocketStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 基本信息
            stats.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stats.put(FieldNames.ENDPOINT, EndpointNames.WEBSOCKET_CONNECTION_STATISTICS);

            // 获取连接管理器统计信息
            Map<String, Object> connectionStats = connectionManager.getShardStatistics();
            stats.put(WebSocketFields.CONNECTIONS, connectionStats);

            // 连接摘要信息
            Map<String, Object> summary = new HashMap<>();
            summary.put(WebSocketFields.TOTAL_CONNECTIONS, connectionStats.getOrDefault(WebSocketFields.TOTAL_CONNECTIONS, 0));
            summary.put(WebSocketFields.ACTIVE_SHARDS, connectionStats.getOrDefault(WebSocketFields.ACTIVE_SHARDS, 0));
            summary.put(WebSocketFields.AVERAGE_SHARD_SIZE, connectionStats.getOrDefault(WebSocketFields.AVERAGE_SHARD_SIZE, 0.0));
            summary.put(WebSocketFields.LOAD_BALANCE, connectionStats.getOrDefault(WebSocketFields.LOAD_BALANCE, 1.0));
            stats.put(FieldNames.SUMMARY, summary);

            // 健康状态评估
            Map<String, Object> health = new HashMap<>();
            int totalConnections = (Integer) connectionStats.getOrDefault(WebSocketFields.TOTAL_CONNECTIONS, 0);
            double loadBalance = (Double) connectionStats.getOrDefault(WebSocketFields.LOAD_BALANCE, 1.0);

            // 连接数健康检查
            if (totalConnections > 15000) {
                health.put(WebSocketFields.CONNECTION_STATUS, StatusValues.HIGH_LOAD);
                health.put(WebSocketFields.CONNECTION_WARNING, "连接数超过15K，建议监控性能");
            } else if (totalConnections > 10000) {
                health.put(WebSocketFields.CONNECTION_STATUS, StatusValues.MEDIUM_LOAD);
                health.put(WebSocketFields.CONNECTION_WARNING, "连接数超过10K，注意监控");
            } else {
                health.put(WebSocketFields.CONNECTION_STATUS, StatusValues.NORMAL);
            }

            // 负载均衡健康检查
            if (loadBalance > 3.0) {
                health.put(WebSocketFields.BALANCE_STATUS, StatusValues.UNBALANCED);
                health.put(WebSocketFields.BALANCE_WARNING, "分片负载不均衡，最大分片是最小分片的" + loadBalance + "倍");
            } else if (loadBalance > 2.0) {
                health.put(WebSocketFields.BALANCE_STATUS, StatusValues.SLIGHTLY_UNBALANCED);
                health.put(WebSocketFields.BALANCE_WARNING, "分片负载轻微不均衡");
            } else {
                health.put(WebSocketFields.BALANCE_STATUS, StatusValues.BALANCED);
            }

            stats.put(FieldNames.HEALTH, health);

            log.debug("WebSocketEndpoint - 返回连接统计: totalConnections={}, loadBalance={}",
                     totalConnections, loadBalance);

            return stats;

        } catch (Exception e) {
            log.error("WebSocketEndpoint - 获取连接统计失败", e);

            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put(FieldNames.ERROR, ErrorMessages.FAILED_TO_RETRIEVE_WEBSOCKET_STATISTICS);
            errorStats.put(FieldNames.MESSAGE, e.getMessage());
            errorStats.put(FieldNames.TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return errorStats;
        }
    }
}