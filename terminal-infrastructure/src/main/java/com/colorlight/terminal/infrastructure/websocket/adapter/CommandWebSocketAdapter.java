package com.colorlight.terminal.infrastructure.websocket.adapter;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.application.port.outbound.command.CommandWebSocketPort;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.infrastructure.websocket.command.WebsocketTerminalCommand;
import com.colorlight.terminal.infrastructure.websocket.connection.TerminalWebsocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 指令WebSocket下发适配器
 * 集成现有的连接管理器，实现指令实时下发
 * 
 * @author Nan
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandWebSocketAdapter implements CommandWebSocketPort {
    
    private final ConnectionManagerPort connectionManager;
    
    @Override
    public boolean sendCommandViaWebSocket(TerminalCommand command) {
        log.debug("CommandWebSocketAdapter - 通过WebSocket发送指令, deviceId: {}, commandId: {}",
                command.getDeviceId(), command.getCommandId());
        
        try {
            // 1. 获取设备连接
            Optional<TerminalConnection> connectionOpt = connectionManager.getConnection(command.getDeviceId());
            if (connectionOpt.isEmpty()) {
                log.warn("CommandWebSocketAdapter - 设备连接不存在, 无法发送WebSocket指令, deviceId: {}", command.getDeviceId());
                return false;
            }
            
            TerminalConnection connection = connectionOpt.get();
            
            // 2. 检查连接状态
            if (!isConnectionValid(connection)) {
                log.warn("CommandWebSocketAdapter - 设备连接无效, deviceId: {}", command.getDeviceId());
                return false;
            }

            // 3. 构造WebSocket消息格式
            String message = buildWebSocketMessage(command, connection.getProtocolVersion());
            
            // 4. 发送消息
            Object session = connection.getSession();
            if (session instanceof TerminalWebsocketSession websocketSession) {
                boolean sent = websocketSession.sendMessage(message);
                
                if (sent) {
                    log.info("CommandWebSocketAdapter - WebSocket指令发送成功, deviceId: {}, command: {}",
                            command.getDeviceId(), message);
                    return true;
                } else {
                    log.warn("CommandWebSocketAdapter - WebSocket指令发送失败, deviceId: {}, commandId: {}",
                            command.getDeviceId(), command.getCommandId());
                    return false;
                }
            } else {
                log.error("CommandWebSocketAdapter - 连接会话类型不匹配, 期望TerminalWebsocketSession, 实际: {}",
                        session != null ? session.getClass().getSimpleName() : "null");
                return false;
            }
            
        } catch (Exception e) {
            log.error("CommandWebSocketAdapter - WebSocket指令发送异常, deviceId: {}, commandId: {}",
                    command.getDeviceId(), command.getCommandId(), e);
            return false;
        }
    }
    
    @Override
    public boolean isDeviceOnline(Long deviceId) {
        try {
            Optional<TerminalConnection> connection = connectionManager.getConnection(deviceId);
            return connection.filter(this::isConnectionValid).isPresent();

        } catch (Exception e) {
            log.error("CommandWebSocketAdapter - 检查设备在线状态异常, deviceId: {}", deviceId, e);
            return false;
        }
    }

    
    /**
     * 检查连接是否有效
     */
    private boolean isConnectionValid(TerminalConnection connection) {
        if (connection == null || connection.getSession() == null) {
            return false;
        }
        
        Object session = connection.getSession();
        if (session instanceof TerminalWebsocketSession websocketSession) {
            return websocketSession.isConnected();
        }
        
        return false;
    }


    /**
     * 构建WebSocket消息格式
     * 根据文档，WebSocket指令格式比HTTP稍有不同，包含额外的led_id字段
     *
     * @param command 指令对象，包含要发送给终端的指令信息
     * @param protocolVersion 协议版本，决定消息的封装格式
     * @return 封装后的WebSocket消息字符串
     */
    private String buildWebSocketMessage(TerminalCommand command, ProtocolVersion protocolVersion) {
        // 创建WebSocket内容对象
        WebsocketTerminalCommand.WebsocketContent content = new WebsocketTerminalCommand.WebsocketContent(command.getContentRaw());
        // 构造WebSocket指令数据
        WebsocketTerminalCommand.WebsocketCommand data = new WebsocketTerminalCommand.WebsocketCommand(command.getCommandId(),
                command.getDeviceId().intValue(),
                command.getAuthorUrl(),
                command.getKarma(),
                content);
        // 根据协议版本选择不同的消息封装格式
        if (protocolVersion == null || protocolVersion == ProtocolVersion.V1_0) {
            return JsonUtils.toJson(new WebsocketTerminalCommand(List.of(data), command.getDeviceId().intValue()));
        }
        else if (protocolVersion == ProtocolVersion.V1_1) {
            return JsonUtils.toJson(new V11WebsocketMessage(V11WebsocketMessageTypeEnum.COMMAND.getId(), data));
        }
        else {
            return JsonUtils.toJson(new WebsocketTerminalCommand(List.of(data), command.getDeviceId().intValue()));
        }
    }
}