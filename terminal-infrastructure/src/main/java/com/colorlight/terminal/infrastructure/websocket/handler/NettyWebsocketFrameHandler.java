package com.colorlight.terminal.infrastructure.websocket.handler;

import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.dto.websocket.WebsocketMessage;
import com.colorlight.terminal.application.port.inbound.websocket.WebsocketMessageUseCase;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import com.colorlight.terminal.infrastructure.websocket.auth.NettyWebsocketAuthHandler;
import com.colorlight.terminal.infrastructure.websocket.connection.TerminalWebsocketSession;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * NettyWebSocket帧处理器
 * 
 * @author Nan
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class NettyWebsocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final WebsocketMessageUseCase webSocketMessageUseCase;
    private final ConnectionManagerPort connectionManagerPort;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete) {
            log.info("NettyWebsocketFrameHandler - WebSocket握手完成: requestUri={}, channelId={}", 
                    handshakeComplete.requestUri(), ctx.channel().id().asShortText());
            initializeWebsocketSession(ctx);
        } else if (evt instanceof IdleStateEvent idleStateEvent) {
            handleIdleStateEvent(ctx, idleStateEvent);
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * 重写方法，处理从WebSocket连接接收到的帧。
     * 根据接收到的不同类型的WebSocket帧执行相应的逻辑处理。
     *
     * @param ctx ChannelHandlerContext对象，提供有关通道和管道的信息
     * @param frame 接收到的WebSocket帧
     * @throws Exception 如果在处理过程中发生异常，则抛出
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        
        Long deviceId = getDeviceIdFromContext(ctx);
        if (deviceId == null) {
            log.warn("NettyWebsocketFrameHandler - 未认证连接尝试发送消息，关闭连接: {}", ctx.channel().id().asShortText());
            ctx.close();
            return;
        }

        log.debug("NettyWebsocketFrameHandler - 收到WebSocket帧: deviceId={}, frameType={}", deviceId, frame.getClass().getSimpleName());

        try {
            if (frame instanceof TextWebSocketFrame textFrame) {
                handleTextFrame(deviceId, textFrame.text());
            } else if (frame instanceof PingWebSocketFrame) {
                handlePingFrame(ctx, deviceId);
            } else if (frame instanceof PongWebSocketFrame) {
                handlePongFrame(deviceId);
            } else if (frame instanceof CloseWebSocketFrame) {
                handleCloseFrame(ctx, deviceId);
            } else {
                log.warn("NettyWebsocketFrameHandler - 不支持的WebSocket帧类型: {}, deviceId: {}",
                        frame.getClass().getSimpleName(), deviceId);
            }
        } catch (Exception e) {
            log.error("NettyWebsocketFrameHandler - 处理WebSocket帧失败: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long deviceId = getDeviceIdFromContext(ctx);
        if (deviceId != null) {
            webSocketMessageUseCase.handleConnectionClosed(deviceId);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Long deviceId = getDeviceIdFromContext(ctx);
        log.error("NettyWebsocketFrameHandler - WebSocket连接异常: deviceId={}", deviceId, cause);
        ctx.close();
    }

    /**
     * WebSocket会话初始化
     */
    private void initializeWebsocketSession(ChannelHandlerContext ctx) {
        try {
            String deviceIdStr = ctx.channel().attr(NettyWebsocketAuthHandler.DEVICE_ID).get();
            TerminalPrincipal principal = ctx.channel().attr(NettyWebsocketAuthHandler.TERMINAL_PRINCIPAL).get();

            if (StringUtils.isBlank(deviceIdStr) || principal == null) {
                log.error("WebSocket会话初始化失败: 缺少认证信息 - deviceId={}, principal={}", deviceIdStr, principal);
                ctx.close();
                return;
            }

            Long deviceId = Long.parseLong(deviceIdStr);
            
            // 创建技术会话对象
            TerminalWebsocketSession technicalSession = TerminalWebsocketSession.builder()
                    .sessionId(ctx.channel().id().asShortText())
                    .deviceId(deviceId)
                    .nettyChannel(ctx.channel())
                    .clientIp(getClientIp(ctx))
                    .connectTime(System.currentTimeMillis())
                    .lastHeartbeatTime(System.currentTimeMillis())
                    .build();

            // 通过UseCase处理连接建立
            TerminalConnection connection = webSocketMessageUseCase.handleConnectionEstablished(deviceId, technicalSession);
            
            if (connection != null) {
                log.info("WebSocket会话创建成功: deviceId={}, sessionId={}", deviceId, technicalSession.getSessionId());
            } else {
                log.error("WebSocket会话创建失败: deviceId={}", deviceId);
                ctx.close();
            }
            
        } catch (Exception e) {
            log.error("WebSocket会话初始化异常", e);
            ctx.close();
        }
    }

    /**
     * 处理文本消息
     */
    private void handleTextFrame(Long deviceId, String message) {
        log.info("NettyWebsocketFrameHandler - 收到文本消息: deviceId={}, message={}", deviceId, message);
        
        // 更新接收计数
        updateReceivedMessageCount(deviceId);

        try {
            final WebsocketMessage websocketMessage = JsonUtils.fromJson(message, WebsocketMessage.class);
            log.debug("NettyWebsocketFrameHandler - JSON解析成功: websocketMessage={}", websocketMessage);
            
            // 简单的心跳检测
            if ("heartbeat".equalsIgnoreCase(websocketMessage.getContent()) || "ping".equalsIgnoreCase(websocketMessage.getContent())) {
                log.info("NettyWebsocketFrameHandler - 检测到心跳消息: deviceId={}", deviceId);
                handleHeartbeat(deviceId);
            } else {
                log.info("NettyWebsocketFrameHandler - 处理业务消息: deviceId={}, content={}, gps={}",
                        deviceId, websocketMessage.getContent(), websocketMessage.getGps());
                // 处理业务消息
                handleBusinessMessage(deviceId, websocketMessage);
            }
        } catch (Exception e) {
            log.error("NettyWebsocketFrameHandler - JSON解析失败: deviceId={}, message={}", deviceId, message, e);
            // JSON解析失败，尝试作为纯文本心跳处理
            if ("heartbeat".equalsIgnoreCase(message.trim()) || "ping".equalsIgnoreCase(message.trim())) {
                log.debug("NettyWebsocketFrameHandler - 作为纯文本心跳处理: deviceId={}", deviceId);
                handleHeartbeat(deviceId);
            }
        }
    }

    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(Long deviceId) {
        try {
            // 更新心跳时间
            updateHeartbeatTime(deviceId);
            
            // 获取连接对象并处理心跳
            TerminalConnection connection = getConnectionByDeviceId(deviceId);
            if (connection != null) {
                webSocketMessageUseCase.handleHeartbeat(connection);
                log.debug("NettyWebsocketFrameHandler - 心跳处理成功: deviceId={}", deviceId);
            }
        } catch (Exception e) {
            log.error("NettyWebsocketFrameHandler - 心跳处理失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 处理业务消息
     */
    private void handleBusinessMessage(Long deviceId, WebsocketMessage message) {
        try {
            TerminalConnection connection = getConnectionByDeviceId(deviceId);
            if (connection != null) {
                webSocketMessageUseCase.handleTextMessage(connection, message);
            }
        } catch (Exception e) {
            log.error("NettyWebsocketFrameHandler - 业务消息处理失败: deviceId={}, message={}", deviceId, message, e);
        }
    }

    /**
     * 处理Ping帧
     */
    private void handlePingFrame(ChannelHandlerContext ctx, Long deviceId) {
        ctx.writeAndFlush(new PongWebSocketFrame());
        updateReceivedMessageCount(deviceId);
        handleHeartbeat(deviceId);
        log.debug("NettyWebsocketFrameHandler - 响应Ping帧: deviceId={}", deviceId);
    }

    /**
     * 处理Pong帧
     */
    private void handlePongFrame(Long deviceId) {
        updateReceivedMessageCount(deviceId);
        handleHeartbeat(deviceId);
        log.debug("NettyWebsocketFrameHandler - 收到Pong帧: deviceId={}", deviceId);
    }

    /**
     * 处理关闭帧
     */
    private void handleCloseFrame(ChannelHandlerContext ctx, Long deviceId) {
        log.info("NettyWebsocketFrameHandler - 收到关闭帧: deviceId={}", deviceId);
        ctx.close();
    }

    /**
     * 处理空闲状态事件
     */
    private void handleIdleStateEvent(ChannelHandlerContext ctx, IdleStateEvent event) {
        if (event.state() == IdleState.READER_IDLE) {
            Long deviceId = getDeviceIdFromContext(ctx);
            log.warn("NettyWebsocketFrameHandler - 设备心跳超时，关闭连接: deviceId={}", deviceId);
            ctx.close();
        }
    }

    /**
     * 从上下文获取设备ID
     */
    private Long getDeviceIdFromContext(ChannelHandlerContext ctx) {
        try {
            String deviceIdStr = ctx.channel().attr(NettyWebsocketAuthHandler.DEVICE_ID).get();
            return StringUtils.isNotBlank(deviceIdStr) ? Long.parseLong(deviceIdStr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(ChannelHandlerContext ctx) {
        try {
            return ctx.channel().remoteAddress().toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 根据设备ID获取连接对象
     */
    private TerminalConnection getConnectionByDeviceId(Long deviceId) {
        TerminalConnection connection = connectionManagerPort.getConnection(deviceId).orElse(null);
        log.debug("NettyWebsocketFrameHandler - 获取连接对象: deviceId={}, connection={}", deviceId, connection != null ? "found" : "null");
        return connection;
    }

    /**
     * 更新指定设备的接收消息计数。
     *
     * @param deviceId 设备ID
     */
    private void updateReceivedMessageCount(Long deviceId) {
        try {
            TerminalConnection connection = getConnectionByDeviceId(deviceId);
            if (connection != null && connection.getWebSocketSession() instanceof TerminalWebsocketSession session) {
                session.incrementReceivedCount();
            }
        } catch (Exception e) {
            log.warn("NettyWebsocketFrameHandler - 更新接收计数失败: deviceId={}", deviceId, e);
        }
    }
    
    /**
     * 更新心跳时间
     */
    private void updateHeartbeatTime(Long deviceId) {
        try {
            TerminalConnection connection = getConnectionByDeviceId(deviceId);
            if (connection != null && connection.getWebSocketSession() instanceof TerminalWebsocketSession session) {
                session.updateHeartbeat();
            }
        } catch (Exception e) {
            log.warn("NettyWebsocketFrameHandler - 更新心跳时间失败: deviceId={}", deviceId, e);
        }
    }
}
