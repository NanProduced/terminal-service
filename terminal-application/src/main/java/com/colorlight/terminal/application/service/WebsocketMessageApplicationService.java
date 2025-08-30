package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.connection.WebSocketSession;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.dto.websocket.WebsocketMessage;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.inbound.status.TerminalReportUseCase;
import com.colorlight.terminal.application.port.inbound.websocket.WebsocketMessageUseCase;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * WebSocket消息应用服务
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebsocketMessageApplicationService implements WebsocketMessageUseCase {
    
    private final ConnectionManagerPort connectionManagerPort;
    private final DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;
    private final TerminalReportUseCase terminalReportUseCase;

    /**
     * 处理心跳消息
     * @param connection 终端连接
     * @return
     */
    @Override
    public boolean handleHeartbeat(TerminalConnection connection) {
        try {
            log.debug("ApplicationService - ws - 处理设备心跳: deviceId={}", connection.getDeviceId());
            
            // 更新心跳时间
            connection.updateLastHeartbeatTime();
            
            // 心跳统计
            connection.incrementReceivedMessageCount();
            
            // 更新设备在线状态
            deviceOnlineStatusUseCase.updateLastReportTime(
                    connection.getDeviceId(), 
                    ReportSource.WEBSOCKET, 
                    connection.getClientIp()
            );
            
            log.debug("ApplicationService - ws - 设备心跳处理成功: deviceId={}, 最后心跳时间={}",
                    connection.getDeviceId(), connection.getLastHeartbeatTime());

            connection.sendMessage("PONG");

            return true;
            
        } catch (Exception e) {
            log.error("ApplicationService - ws - 处理设备心跳失败: deviceId={}", connection.getDeviceId(), e);
            connection.incrementErrorCount();
            return false;
        }
    }

    /**
     * 处理文本消息
     * @param connection 终端连接
     * @param message 消息内容
     * @return
     */
    @Override
    public boolean handleTextMessage(TerminalConnection connection, WebsocketMessage message) {
        try {
            log.debug("ApplicationService - ws - 处理设备文本消息: deviceId={}, message={}", connection.getDeviceId(), message);
            
            // 更新活跃时间
            connection.updateActiveTime();
            connection.incrementReceivedMessageCount();
            
            // 更新设备在线状态
            deviceOnlineStatusUseCase.updateLastReportTime(
                    connection.getDeviceId(), 
                    ReportSource.WEBSOCKET, 
                    connection.getClientIp()
            );

            if (StringUtils.isNotBlank(message.getGps())) {
                // 当前websocket只上报GPS信息
                terminalReportUseCase.asyncHandleSensorReport(connection.getDeviceId(), LocalDateTime.now(), message.getGps());
            }

            return true;
            
        } catch (Exception e) {
            log.error("ApplicationService - ws - 处理设备消息失败: deviceId={}, message={}", connection.getDeviceId(), message, e);
            connection.incrementErrorCount();
            return false;
        }
    }

    /**
     * 处理连接建立
     * @param deviceId 设备ID
     * @param session 技术会话对象
     * @return
     */
    @Override
    public TerminalConnection handleConnectionEstablished(Long deviceId, WebSocketSession session) {
        try {
            log.debug("ApplicationService - ws - 处理设备连接建立: deviceId={}", deviceId);
            
            // 创建业务连接对象
            TerminalConnection connection = TerminalConnection.create(deviceId, session);
            
            // 添加到连接管理器
            boolean added = connectionManagerPort.addConnection(deviceId, connection);
            
            if (added) {
                // 更新设备在线状态（WebSocket连接建立）
                deviceOnlineStatusUseCase.updateLastReportTime(
                        deviceId, 
                        ReportSource.WEBSOCKET, 
                        connection.getClientIp()
                );
                
                log.info("ApplicationService - ws - 设备连接建立成功: deviceId={}, 总连接数={}",
                        deviceId, connectionManagerPort.getConnectionCount());
                return connection;
            } else {
                log.warn("ApplicationService - ws - 设备连接添加失败: deviceId={}", deviceId);
                return null;
            }
            
        } catch (Exception e) {
            log.error("ApplicationService - ws - 处理设备连接建立失败: deviceId={}", deviceId, e);
            return null;
        }
    }

    /**
     * 处理连接关闭
     * @param deviceId 设备ID
     * @return
     */
    @Override
    public boolean handleConnectionClosed(Long deviceId) {
        try {
            log.debug("ApplicationService - ws - 处理设备连接断开: deviceId={}", deviceId);
            
            // 从连接管理器移除
            Object removed = connectionManagerPort.removeConnection(deviceId);
            
            if (removed != null) {
                log.info("ApplicationService - ws - 设备连接断开成功: deviceId={}, 剩余连接数={}",
                        deviceId, connectionManagerPort.getConnectionCount());
                return true;
            } else {
                log.warn("ApplicationService - ws - 设备连接移除失败: deviceId={}", deviceId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("ApplicationService - ws - 处理设备连接断开失败: deviceId={}", deviceId, e);
            return false;
        }
    }
    
    @Override
    public boolean sendMessage(Long deviceId, String message) {
        try {
            log.debug("ApplicationService - ws - 发送消息给设备: deviceId={}, messageLength={}", deviceId, message.length());
            
            // 获取设备连接
            Optional<TerminalConnection> connectionOpt = connectionManagerPort.getConnection(deviceId);
            if (connectionOpt.isEmpty()) {
                log.warn("ApplicationService - ws - 设备未连接，消息发送失败: deviceId={}", deviceId);
                return false;
            }
            
            TerminalConnection connection = connectionOpt.get();
            
            // 检查连接有效性
            if (!connection.isActive()) {
                log.warn("ApplicationService - ws - 设备连接无效，消息发送失败: deviceId={}", deviceId);
                return false;
            }
            
            // 通过会话发送消息
            boolean success = connection.sendMessage(message);
            
            if (success) {
                log.debug("ApplicationService - ws - 消息发送成功: deviceId={}", deviceId);
                connection.incrementSentMessageCount();
            } else {
                log.warn("ApplicationService - ws - 消息发送失败: deviceId={}", deviceId);
                connection.incrementErrorCount();
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("ApplicationService - ws - 发送消息异常: deviceId={}", deviceId, e);
            return false;
        }
    }
    
    @Override
    public List<Long> broadcastMessage(List<Long> deviceIds, String message) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            log.warn("ApplicationService - ws - 设备列表为空，跳过批量发送");
            return Collections.emptyList();
        }
        
        try {
            log.info("ApplicationService - ws - 开始批量发送消息: targetDevices={}, messageLength={}", 
                    deviceIds.size(), message.length());
            
            List<Long> successList = new ArrayList<>();
            int failureCount = 0;
            
            for (Long deviceId : deviceIds) {
                try {
                    if (sendMessage(deviceId, message)) {
                        successList.add(deviceId);
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.warn("ApplicationService - ws - 批量发送中单个设备失败: deviceId={}", deviceId, e);
                    failureCount++;
                }
            }
            
            log.info("ApplicationService - ws - 批量发送完成: success={}, failure={}, total={}", 
                    successList.size(), failureCount, deviceIds.size());
            
            return successList;
            
        } catch (Exception e) {
            log.error("ApplicationService - ws - 批量发送消息异常: targetDevices={}", deviceIds.size(), e);
            return Collections.emptyList();
        }
    }
}