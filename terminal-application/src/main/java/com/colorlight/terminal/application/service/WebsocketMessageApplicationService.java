package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.connection.WebSocketSession;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.inbound.websocket.WebsocketMessageUseCase;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolMessageProcessor;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolProcessorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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
            updateDeviceOnlineStatus(connection);

            // 获取对应协议的处理器
            ProtocolMessageProcessor processor = getProtocolProcessor(connection.getProtocolVersion(), connection.getDeviceId());

            // 处理文本消息并更新统计
            handleTextMessageWithStatistics(context, processor);

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

            // 创建并注册连接
            TerminalConnection connection = createAndRegisterConnection(deviceId, session, protocolVersion);
            if (connection == null) {
                return null;
            }

            // 更新设备在线状态
            updateDeviceOnlineStatus(connection);
            log.info("ApplicationService - ws - 设备连接建立成功: deviceId={}, 总连接数={}",
                    deviceId, connectionManagerPort.getConnectionCount());

            // 触发协议特定的连接建立回调
            triggerProtocolConnectionEstablished(connection, protocolVersion);

            return connection;

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

            // 获取活跃的连接
            TerminalConnection connection = getActiveConnection(deviceId);
            if (connection == null) {
                return false;
            }

            // 执行消息发送并更新统计
            return performSendMessageAndUpdateStats(connection, message);

        } catch (Exception e) {
            log.error("ApplicationService - ws - 发送消息异常: deviceId={}", deviceId, e);
            return false;
        }
    }
    
    @Override
    public List<Long> broadcastMessage(List<Long> deviceIds, String message) {
        // 验证广播目标
        if (CollectionUtils.isEmpty(deviceIds)) {
            return Collections.emptyList();
        }

        try {
            log.info("ApplicationService - ws - 开始批量发送消息: targetDevices={}, messageLength={}",
                    deviceIds.size(), message.length());

            // 执行批量发送
            List<Long> successList = performBroadcast(deviceIds, message);

            logBroadcastResult(successList, deviceIds);
            return successList;

        } catch (Exception e) {
            log.error("ApplicationService - ws - 批量发送消息异常: targetDevices={}", deviceIds.size(), e);
            return Collections.emptyList();
        }
    }

    // ====================== 私有辅助方法 ======================

    /**
     * 更新设备在线状态
     *
     * @param connection 终端连接
     */
    private void updateDeviceOnlineStatus(TerminalConnection connection) {
        deviceOnlineStatusUseCase.updateLastReportTime(
                connection.getDeviceId(),
                ReportSource.WEBSOCKET,
                connection.getClientIp()
        );
    }

    /**
     * 获取协议处理器
     *
     * @param protocolVersion 协议版本
     * @param deviceId 设备ID（用于日志）
     * @return 协议消息处理器
     */
    private ProtocolMessageProcessor getProtocolProcessor(ProtocolVersion protocolVersion, Long deviceId) {
        ProtocolMessageProcessor processor = protocolProcessorPort.getProcessor(protocolVersion);
        log.debug("ApplicationService - ws - 选择协议处理器: deviceId={}, protocol={}, processor={}",
                 deviceId, protocolVersion, processor.getClass().getSimpleName());
        return processor;
    }

    /**
     * 处理文本消息并更新统计
     *
     * @param context 消息处理上下文
     * @param processor 协议消息处理器
     */
    private void handleTextMessageWithStatistics(MessageProcessingContext context, ProtocolMessageProcessor processor) {
        ProtocolMessageProcessor.TextMessageProcessResult result = processor.processTextMessage(context);

        if (result.success()) {
            context.updateMessageStatistics();
            log.debug("ApplicationService - ws - 设备文本消息处理成功: deviceId={}, protocol={}",
                     context.getConnection().getDeviceId(), context.getConnection().getProtocolVersion());
        } else {
            log.error("ApplicationService - ws - 协议处理器消息处理失败: deviceId={}, error={}",
                     context.getConnection().getDeviceId(), result.errorMessage());
            context.updateErrorStatistics();
        }
    }

    /**
     * 创建并注册连接
     *
     * @param deviceId 设备ID
     * @param session WebSocket会话
     * @param protocolVersion 协议版本
     * @return 创建的终端连接，如果添加失败则返回null
     */
    private TerminalConnection createAndRegisterConnection(Long deviceId, WebSocketSession session, ProtocolVersion protocolVersion) {
        // 创建业务连接对象
        TerminalConnection connection = TerminalConnection.create(deviceId, session, protocolVersion);

        // 添加到连接管理器
        boolean added = connectionManagerPort.addConnection(deviceId, connection);
        if (!added) {
            log.warn("ApplicationService - ws - 设备连接添加失败: deviceId={}", deviceId);
            return null;
        }

        return connection;
    }

    /**
     * 触发协议特定的连接建立回调
     *
     * @param connection 终端连接
     * @param protocolVersion 协议版本
     */
    private void triggerProtocolConnectionEstablished(TerminalConnection connection, ProtocolVersion protocolVersion) {
        try {
            ProtocolMessageProcessor processor = protocolProcessorPort.getProcessor(protocolVersion);
            MessageProcessingContext context = MessageProcessingContext.create(connection, "");
            processor.onConnectionEstablished(context);
            log.debug("ApplicationService - ws - 协议连接建立回调完成: deviceId={}, protocol={}",
                     connection.getDeviceId(), protocolVersion);
        } catch (Exception e) {
            // 连接建立回调失败不应影响连接本身，仅记录错误
            log.error("ApplicationService - ws - 协议连接建立回调异常: deviceId={}, protocol={}",
                     connection.getDeviceId(), protocolVersion, e);
        }
    }

    /**
     * 获取活跃的连接
     *
     * @param deviceId 设备ID
     * @return 活跃的连接，如果不存在或无效则返回null
     */
    private TerminalConnection getActiveConnection(Long deviceId) {
        // 获取设备连接
        Optional<TerminalConnection> connectionOpt = connectionManagerPort.getConnection(deviceId);
        if (connectionOpt.isEmpty()) {
            log.warn("ApplicationService - ws - 设备未连接，消息发送失败: deviceId={}", deviceId);
            return null;
        }

        TerminalConnection connection = connectionOpt.get();

        // 检查连接有效性
        if (!connection.isActive()) {
            log.warn("ApplicationService - ws - 设备连接无效，消息发送失败: deviceId={}", deviceId);
            return null;
        }

        return connection;
    }

    /**
     * 执行消息发送并更新统计
     *
     * @param connection 终端连接
     * @param message 消息内容
     * @return 发送是否成功
     */
    private boolean performSendMessageAndUpdateStats(TerminalConnection connection, String message) {
        boolean success = connection.sendMessage(message);

        if (success) {
            log.debug("ApplicationService - ws - 消息发送成功: deviceId={}", connection.getDeviceId());
            connection.incrementSentMessageCount();
        } else {
            log.warn("ApplicationService - ws - 消息发送失败: deviceId={}", connection.getDeviceId());
            connection.incrementErrorCount();
        }

        return success;
    }

    /**
     * 执行批量发送
     *
     * @param deviceIds 设备ID列表
     * @param message 消息内容
     * @return 发送成功的设备ID列表
     */
    private List<Long> performBroadcast(List<Long> deviceIds, String message) {
        List<Long> successList = new ArrayList<>();

        for (Long deviceId : deviceIds) {
            try {
                if (sendMessage(deviceId, message)) {
                    successList.add(deviceId);
                }
            } catch (Exception e) {
                log.warn("ApplicationService - ws - 批量发送中单个设备失败: deviceId={}", deviceId, e);
            }
        }

        return successList;
    }

    /**
     * 记录批量发送结果
     *
     * @param successList 成功的设备ID列表
     * @param allDeviceIds 所有目标设备ID列表
     */
    private void logBroadcastResult(List<Long> successList, List<Long> allDeviceIds) {
        int failureCount = allDeviceIds.size() - successList.size();
        log.info("ApplicationService - ws - 批量发送完成: success={}, failure={}, total={}",
                successList.size(), failureCount, allDeviceIds.size());
    }
}