package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.connection.WebSocketSession;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.inbound.websocket.WebsocketMessageUseCase;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolProcessorPort;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolMessageProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * WebSocket消息应用服务 - 协议感知的WebSocket消息处理
 * 
 * <p>职责范围：</p>
 * <ul>
 *   <li>协议路由：根据连接协议版本选择合适的处理器</li>
 *   <li>统计协调：通过MessageProcessingContext协调统计更新</li>
 *   <li>状态管理：维护设备在线状态和连接管理</li>
 *   <li>错误处理：处理协议路由和业务异常</li>
 * </ul>
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebsocketMessageApplicationService implements WebsocketMessageUseCase {
    
    private final ConnectionManagerPort connectionManagerPort;
    private final DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;
    private final ProtocolProcessorPort protocolProcessorPort;

    /**
     * 处理文本消息 - 协议感知版本，支持多协议文本消息处理
     *
     * @param context 消息处理上下文
     */
    @Override
    public void handleTextMessageByProcessor(MessageProcessingContext context) {
        TerminalConnection connection = context.getConnection();
        try {
            // 更新设备在线状态
            deviceOnlineStatusUseCase.updateLastReportTime(
                    connection.getDeviceId(),
                    ReportSource.WEBSOCKET,
                    connection.getClientIp()
            );

            // 协议路由：根据连接协议版本获取对应的处理器
            ProtocolMessageProcessor processor = protocolProcessorPort.getProcessor(connection.getProtocolVersion());
            log.debug("ApplicationService - ws - 选择协议处理器: deviceId={}, protocol={}, processor={}", 
                     connection.getDeviceId(), connection.getProtocolVersion(), 
                     processor.getClass().getSimpleName());

            // 执行协议特定的文本消息处理
            ProtocolMessageProcessor.TextMessageProcessResult result = processor.processTextMessage(context);
            
            if (result.success()) {
                context.updateMessageStatistics();
                
                log.debug("ApplicationService - ws - 设备文本消息处理成功: deviceId={}, protocol={}", 
                         connection.getDeviceId(), connection.getProtocolVersion());

            } else {
                log.error("ApplicationService - ws - 协议处理器消息处理失败: deviceId={}, error={}", 
                         connection.getDeviceId(), result.errorMessage());
                context.updateErrorStatistics();
            }
            
        } catch (Exception e) {
            log.error("ApplicationService - ws - 处理设备消息异常: deviceId={}, message={}", 
                     connection.getDeviceId(), context.getRawMessage(), e);
            context.updateErrorStatistics();
        }
    }

    /**
     * 处理连接建立
     * @param deviceId 设备ID
     * @param session 技术会话对象
     * @param protocolVersion 协议版本
     * @return 设备ws连接封装
     */
    @Override
    public TerminalConnection handleConnectionEstablished(Long deviceId, WebSocketSession session, ProtocolVersion protocolVersion) {
        try {
            log.debug("ApplicationService - ws - 处理设备连接建立: deviceId={}", deviceId);
            
            // 创建业务连接对象
            TerminalConnection connection = TerminalConnection.create(deviceId, session, protocolVersion);
            
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
     *
     * @param deviceId 设备ID
     */
    @Override
    public void handleConnectionClosed(Long deviceId) {
        try {
            log.debug("ApplicationService - ws - 处理设备连接断开: deviceId={}", deviceId);
            
            // 从连接管理器移除
            Object removed = connectionManagerPort.removeConnection(deviceId);
            
            if (removed != null) {
                log.info("ApplicationService - ws - 设备连接断开成功: deviceId={}, 剩余连接数={}",
                        deviceId, connectionManagerPort.getConnectionCount());
            } else {
                log.warn("ApplicationService - ws - 设备连接移除失败: deviceId={}", deviceId);
            }
            
        } catch (Exception e) {
            log.error("ApplicationService - ws - 处理设备连接断开失败: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void handlePingFrame(TerminalConnection terminalConnection) {
        terminalConnection.incrementReceivedMessageCount();
        // 更新在线
        deviceOnlineStatusUseCase.updateLastReportTime(
                terminalConnection.getDeviceId(),
                ReportSource.WEBSOCKET,
                terminalConnection.getClientIp());
        terminalConnection.sendMessage("PONG");
        terminalConnection.incrementSentMessageCount();
    }

    @Override
    public void handlePongFrame(TerminalConnection terminalConnection) {
        terminalConnection.incrementReceivedMessageCount();
        // 更新在线
        deviceOnlineStatusUseCase.updateLastReportTime(
                terminalConnection.getDeviceId(),
                ReportSource.WEBSOCKET,
                terminalConnection.getClientIp());
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
                boolean sendResult = sendSingleMessage(deviceId, message);
                if (sendResult) {
                    successList.add(deviceId);
                } else {
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
    
    /**
     * 发送单条消息给指定设备
     *
     * @param deviceId 设备ID
     * @param message 消息内容
     * @return 发送是否成功
     */
    private boolean sendSingleMessage(Long deviceId, String message) {
        try {
            return sendMessage(deviceId, message);
        } catch (Exception e) {
            log.warn("ApplicationService - ws - 批量发送中单个设备失败: deviceId={}", deviceId, e);
            return false;
        }
    }
}