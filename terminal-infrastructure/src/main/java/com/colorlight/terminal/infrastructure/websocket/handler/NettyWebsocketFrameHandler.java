package com.colorlight.terminal.infrastructure.websocket.handler;

import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.port.inbound.websocket.WebsocketMessageUseCase;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
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
            log.debug("NettyWebsocketFrameHandler - WebSocket握手完成: requestUri={}", handshakeComplete.requestUri());
            initializeWebsocketSession(ctx);
        } else if (evt instanceof IdleStateEvent idleStateEvent) {
            handleIdleStateEvent(ctx, idleStateEvent);
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * 消息处理 - 播放盒目前只发text
     * @param ctx
     * @param frame
     * @throws Exception
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
        log.debug("收到文本消息: deviceId={}, message={}", deviceId, message);
        
        // 更新接收计数
        updateReceivedMessageCount(deviceId);
        
        // 简单的心跳检测
        if ("ping".equalsIgnoreCase(message.trim())) {
            handleHeartbeat(deviceId);
        } else {
            // 处理业务消息
            handleBusinessMessage(deviceId, message);
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
                log.debug("心跳处理成功: deviceId={}", deviceId);
            }
        } catch (Exception e) {
            log.error("心跳处理失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 处理业务消息
     */
    private void handleBusinessMessage(Long deviceId, String message) {
        try {
            TerminalConnection connection = getConnectionByDeviceId(deviceId);
            if (connection != null) {
                webSocketMessageUseCase.handleTextMessage(connection, message);
            }
        } catch (Exception e) {
            log.error("业务消息处理失败: deviceId={}, message={}", deviceId, message, e);
        }
    }

    /**
     * 处理Ping帧
     */
    private void handlePingFrame(ChannelHandlerContext ctx, Long deviceId) {
        ctx.writeAndFlush(new PongWebSocketFrame());
        updateReceivedMessageCount(deviceId);
        handleHeartbeat(deviceId);
        log.debug("响应Ping帧: deviceId={}", deviceId);
    }

    /**
     * 处理Pong帧
     */
    private void handlePongFrame(Long deviceId) {
        updateReceivedMessageCount(deviceId);
        handleHeartbeat(deviceId);
        log.debug("收到Pong帧: deviceId={}", deviceId);
    }

    /**
     * 处理关闭帧
     */
    private void handleCloseFrame(ChannelHandlerContext ctx, Long deviceId) {
        log.info("收到关闭帧: deviceId={}", deviceId);
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
     * 更新接收消息计数
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
