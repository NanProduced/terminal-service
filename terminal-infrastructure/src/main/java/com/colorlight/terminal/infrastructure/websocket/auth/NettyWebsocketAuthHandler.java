package com.colorlight.terminal.infrastructure.websocket.auth;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.dto.request.AuthRequest;
import com.colorlight.terminal.application.dto.result.AuthResult;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.inbound.auth.TerminalAuthUseCase;
import com.colorlight.terminal.infrastructure.config.properties.WebSocketConfigProperties;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import com.colorlight.terminal.infrastructure.websocket.config.NettyWebsocketProperties;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Component
@ChannelHandler.Sharable
public class NettyWebsocketAuthHandler extends ChannelInboundHandlerAdapter {

    private final TerminalAuthUseCase terminalAuthUseCase;
    private final NettyWebsocketProperties nettyWebsocketProperties;
    private final WebSocketConfigProperties webSocketConfigProperties;
    
    /**
     * WebSocket认证专用线程池
     * 使用设备事件处理器线程池，避免阻塞I/O线程
     */
    private final Executor websocketAuthExecutor;
    
    /**
     * 手动构造函数以支持 @Qualifier 注解
     */
    public NettyWebsocketAuthHandler(
            TerminalAuthUseCase terminalAuthUseCase,
            NettyWebsocketProperties nettyWebsocketProperties,
            WebSocketConfigProperties webSocketConfigProperties,
            @Qualifier("deviceEventExecutor") Executor websocketAuthExecutor) {
        this.terminalAuthUseCase = terminalAuthUseCase;
        this.nettyWebsocketProperties = nettyWebsocketProperties;
        this.webSocketConfigProperties = webSocketConfigProperties;
        this.websocketAuthExecutor = websocketAuthExecutor;
    }

    public static final AttributeKey<TerminalPrincipal> TERMINAL_PRINCIPAL = AttributeKey.valueOf("terminalPrincipal");
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");
    public static final AttributeKey<ProtocolVersion> PROTOCOL_VERSION = AttributeKey.valueOf("protocolVersion");


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!isWebSocketHandshakeRequest(request)) {
            ctx.fireChannelRead(request);
            return;
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        String connectPath = decoder.path();
        // 尝试匹配协议版本
        ProtocolVersion matchedVersion = findProtocolVersionByPath(connectPath);

        if (matchedVersion == null) {
            log.warn("NettyWebsocketAuthHandler - 收到未识别的WebSocket握手路径: {}", connectPath);
            // 发送错误响应
            sendUnsupportedProtocolResponse(ctx);
            ReferenceCountUtil.release(request);
            return;
        }

        // 检查协议版本是否启用
        boolean versionEnabled = webSocketConfigProperties.getProtocol()
                .isVersionSupported(matchedVersion.getVersion(), matchedVersion.isSupported());
        // 如果未启用，则返回错误响应
        if (!versionEnabled) {
            String requestedVersion = decoder.parameters()
                    .getOrDefault("protocol_version", List.of(""))
                    .stream()
                    .findFirst()
                    .orElse("");
            log.warn("NettyWebsocketAuthHandler - WebSocket协议版本未启用: path={}, protocolVersion={}", connectPath, requestedVersion);
            sendUnsupportedProtocolResponse(ctx);
            ReferenceCountUtil.release(request);
            return;
        }

        // 保留引用计数，避免在业务线程池中释放
        ReferenceCountUtil.retain(request);
        // 在业务线程池中处理WebSocket认证
        websocketAuthExecutor.execute(() -> handleWebSocketAuthentication(ctx, request));
    }

    /**
     * 在业务线程池中处理WebSocket认证
     * 避免阻塞Netty的I/O线程
     */
    private void handleWebSocketAuthentication(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            // 检查连接是否还有效
            if (!ctx.channel().isActive()) {
                log.debug("NettyWebsocketAuthHandler - 连接已关闭，跳过认证处理");
                return;
            }

            // 校验连接请求
            if (!authenticateRequest(ctx, request)) {
                log.debug("NettyWebsocketAuthHandler - WebSocket认证失败,发送401并关闭连接");
                sendErrorResponse(ctx);
                return;
            }

            // 认证成功后将请求URI转换为内部统一路径
            // 使所有协议版本都使用统一的内部WebSocket路径进行处理
            request.setUri(nettyWebsocketProperties.getServer().getWebsocketPath());
            
            // 将处理结果回调到I/O线程
            ctx.channel().eventLoop().execute(() -> {
                try {
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(request);
                } catch (Exception e) {
                    log.error("NettyWebsocketAuthHandler - 认证成功后处理异常", e);
                    sendErrorResponse(ctx);
                }
            });

        } catch (Exception e) {
            log.error("NettyWebsocketAuthHandler - Websocket认证异常", e);
            // 异步发送错误响应
            ctx.channel().eventLoop().execute(() -> sendErrorResponse(ctx));
        } finally {
            // 释放引用计数
            ReferenceCountUtil.release(request);
        }
    }

    private boolean authenticateRequest(ChannelHandlerContext context, FullHttpRequest request) {
        try {
            // 解析请求
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            Map<String, List<String>> params = decoder.parameters();

            String username = params.getOrDefault("username", List.of("")).get(0);
            String password = params.getOrDefault("password", List.of("")).get(0);
            String versionStr = params.getOrDefault("protocol_version", List.of("")).get(0);

            ProtocolVersion protocolVersion = parseProtocolVersion(decoder.path(), versionStr);

            // 存储协议版本到Channel
            context.channel().attr(PROTOCOL_VERSION).set(protocolVersion);

            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                log.warn("WebSocket认证失败: 缺少用户名或密码, URI: {}", request.uri().replaceAll("password=[^&]*", "password=***"));
                return false;
            }

            // 认证
            AuthResult authResult = terminalAuthUseCase.authenticate(new AuthRequest(username, password));
            if (authResult.isSuccess()) {
                TerminalPrincipal terminalPrincipal = new TerminalPrincipal(authResult.getDeviceId(), TerminalAccountStatus.ENABLE);
                // 将认证信息存储到Channel属性
                context.channel().attr(TERMINAL_PRINCIPAL).set(terminalPrincipal);
                context.channel().attr(DEVICE_ID).set(terminalPrincipal.getDeviceId().toString());
                log.debug("NettyWebsocketAuthHandler - Websocket认证成功: deviceId={}",  terminalPrincipal.getDeviceId());
                return true;
            }
            log.warn("NettyWebsocketAuthHandler - Websocket认证失败: account={}", username);
            return false;

        } catch (Exception e) {
            log.error("NettyWebsocketAuthHandler - Websocket认证异常", e);
            return false;
        }
    }

    private ProtocolVersion parseProtocolVersion(String path, String versionStr) {
        // v1.0不用protocolVersion
        if (path.equals(ProtocolVersion.V1_0.getPath())) {
            return ProtocolVersion.V1_0;
        }

        return ProtocolVersion.fromVersion(versionStr);
    }

    /**
     * 检查是否为支持的WebSocket握手请求
     * @param req HTTP请求
     * @return 是否为有效的WebSocket握手请求
     */
    private boolean isWebSocketHandshakeRequest(FullHttpRequest req) {
        if (!HttpMethod.GET.equals(req.method())) {
            return false;
        }

        CharSequence upgrade = req.headers().get(HttpHeaderNames.UPGRADE);
        return upgrade != null && HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(upgrade);
    }

    /**
     * 根据连接路径查找对应的协议版本
     * @param connectPath 连接路径
     * @return 匹配的协议版本
     */
    private ProtocolVersion findProtocolVersionByPath(String connectPath) {
        return Arrays.stream(ProtocolVersion.values())
                .filter(version -> version.getPath().equals(connectPath))
                .findFirst()
                .orElse(null);
    }

    /**
     * 发送不支持的协议版本响应
     * @param ctx 通道上下文
     */
    private void sendUnsupportedProtocolResponse(ChannelHandlerContext ctx) {
        sendResponse(ctx, HttpResponseStatus.UPGRADE_REQUIRED, "不支持的WebSocket协议版本");
    }

    /**
     * 发送错误响应
     * @param ctx 通道上下文
     */
    private void sendErrorResponse(ChannelHandlerContext ctx) {
        sendResponse(ctx, HttpResponseStatus.UNAUTHORIZED, "认证失败");
    }

    /**
     * 发送响应
     * @param ctx 通道上下文
     * @param status 响应状态码
     * @param message 响应消息
     */
    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        // 确保在I/O线程中执行发送操作
        if (ctx.channel().eventLoop().inEventLoop()) {
            doSendResponse(ctx, status, message);
        }
        // 否则将发送操作委托给I/O线程执行
        else {
            ctx.channel().eventLoop().execute(() -> doSendResponse(ctx, status, message));
        }
    }

    /**
     * 发送响应
     * @param ctx 通道上下文
     * @param status 响应状态码
     * @param message 响应消息
     */
    private void doSendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        try {
            // 创建响应
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status,
                    Unpooled.copiedBuffer(message, StandardCharsets.UTF_8)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            // 设置响应长度
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            log.error("NettyWebsocketAuthHandler - 发送响应异常", e);
            ctx.close();
        }
    }
}
