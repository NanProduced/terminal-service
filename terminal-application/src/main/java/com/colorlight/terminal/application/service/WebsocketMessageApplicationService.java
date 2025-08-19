package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.inbound.websocket.WebsocketMessageUseCase;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            
            // 心跳统计(可选)
            connection.incrementReceivedMessageCount();
            
            // 更新设备在线状态
            deviceOnlineStatusUseCase.updateLastReportTime(
                    connection.getDeviceId(), 
                    ReportSource.WEBSOCKET, 
                    connection.getClientIp()
            );
            
            log.debug("ApplicationService - ws - 设备心跳处理成功: deviceId={}, 最后心跳时间={}",
                    connection.getDeviceId(), connection.getLastHeartbeatTime());
            
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
    public boolean handleTextMessage(TerminalConnection connection, String message) {
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
            
            // TODO: 根据业务需求处理具体消息类型
            // 例如: 指令响应、状态上报、错误报告等
            
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
    public TerminalConnection handleConnectionEstablished(Long deviceId, Object session) {
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
}