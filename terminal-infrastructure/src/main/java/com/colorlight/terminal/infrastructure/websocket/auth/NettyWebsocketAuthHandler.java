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
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
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
     * 用于 WebSocket 认证的业务线程池，避免阻塞 Netty I/O 线程。
     */
    private final Executor websocketAuthExecutor;

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
    public static final AttributeKey<String> CLIENT_IP = AttributeKey.valueOf("clientIp");

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
        ProtocolVersion matchedVersion = findProtocolVersionByPath(connectPath);

        if (matchedVersion == null) {
            log.warn("NettyWebsocketAuthHandler - 收到未识别的 WebSocket 握手路径: {}", connectPath);
            sendUnsupportedProtocolResponse(ctx);
            ReferenceCountUtil.release(request);
            return;
        }

        boolean versionEnabled = webSocketConfigProperties.getProtocol()
                .isVersionSupported(matchedVersion.getVersion(), matchedVersion.isSupported());
        if (!versionEnabled) {
            String requestedVersion = decoder.parameters()
                    .getOrDefault("protocol_version", List.of(""))
                    .stream()
                    .findFirst()
                    .orElse("");
            log.warn("NettyWebsocketAuthHandler - WebSocket 协议版本未启用: path={}, protocolVersion={}", connectPath, requestedVersion);
            sendUnsupportedProtocolResponse(ctx);
            ReferenceCountUtil.release(request);
            return;
        }

        ReferenceCountUtil.retain(request);
        try {
            websocketAuthExecutor.execute(() -> handleWebSocketAuthentication(ctx, request));
        } catch (TaskRejectedException e) {
            log.error("NettyWebsocketAuthHandler - WebSocket 认证线程池已满，拒绝请求: path={}", connectPath, e);
            ReferenceCountUtil.release(request, 2);
            sendServiceUnavailableResponse(ctx);
            return;
        }
        ReferenceCountUtil.release(request);
    }


    private void handleWebSocketAuthentication(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            if (!ctx.channel().isActive()) {
                log.debug("NettyWebsocketAuthHandler - 连接已关闭，跳过认证处理");
                ReferenceCountUtil.release(request);
                return;
            }

            if (!authenticateRequest(ctx, request)) {
                log.debug("NettyWebsocketAuthHandler - WebSocket 认证失败，返回 401 并关闭连接");
                sendErrorResponse(ctx);
                ReferenceCountUtil.release(request);
                return;
            }

            request.setUri(nettyWebsocketProperties.getServer().getWebsocketPath());
            ctx.channel().eventLoop().execute(() -> forwardAuthenticatedRequest(ctx, request));
        } catch (Exception e) {
            log.error("NettyWebsocketAuthHandler - Websocket 认证异常", e);
            ReferenceCountUtil.release(request);
            ctx.channel().eventLoop().execute(() -> sendErrorResponse(ctx));
        }
    }

    /**
     * 认证成功后，将请求转发到下一个处理器，并移除当前处理器。
     * 如果转发失败，则发送错误响应并关闭连接。
     *
     * @param ctx ChannelHandlerContext
     * @param request FullHttp
     */
    private void forwardAuthenticatedRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(request);
        } catch (Exception e) {
            log.error("NettyWebsocketAuthHandler - 转发认证请求失败", e);
            ReferenceCountUtil.release(request);
            sendErrorResponse(ctx);
        }
    }

    /**
     * 认证请求
     *
     * @param context ChannelHandlerContext
     * @param request FullHttpRequest
     * @return  boolean
     */
    private boolean authenticateRequest(ChannelHandlerContext context, FullHttpRequest request) {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            Map<String, List<String>> params = decoder.parameters();

            String username = params.getOrDefault("username", List.of(""))
                    .get(0);
            String password = params.getOrDefault("password", List.of(""))
                    .get(0);
            String versionStr = params.getOrDefault("protocol_version", List.of(""))
                    .get(0);

            ProtocolVersion protocolVersion = parseProtocolVersion(decoder.path(), versionStr);
            context.channel().attr(PROTOCOL_VERSION).set(protocolVersion);

            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                log.warn("WebSocket 认证失败: 缺少用户名或密码, URI: {}",
                        request.uri().replaceAll("password=[^&]*", "password=***"));
                return false;
            }

            AuthResult authResult = terminalAuthUseCase.authenticate(new AuthRequest(username, password));
            if (authResult.isSuccess()) {
                TerminalPrincipal terminalPrincipal = new TerminalPrincipal(
                        authResult.getDeviceId(), TerminalAccountStatus.ENABLE);
                context.channel().attr(TERMINAL_PRINCIPAL).set(terminalPrincipal);
                context.channel().attr(DEVICE_ID).set(terminalPrincipal.getDeviceId().toString());
                context.channel().attr(CLIENT_IP).set(resolveClientIp(request, context));
                log.debug("NettyWebsocketAuthHandler - Websocket 认证成功: deviceId={}",
                        terminalPrincipal.getDeviceId());
                return true;
            }
            log.warn("NettyWebsocketAuthHandler - Websocket 认证失败: account={}", username);
            return false;
        } catch (Exception e) {
            log.error("NettyWebsocketAuthHandler - Websocket 认证异常", e);
            return false;
        }
    }

    private ProtocolVersion parseProtocolVersion(String path, String versionStr) {
        if (path.equals(ProtocolVersion.V1_0.getPath())) {
            return ProtocolVersion.V1_0;
        }
        return ProtocolVersion.fromVersion(versionStr);
    }

    private boolean isWebSocketHandshakeRequest(FullHttpRequest req) {
        if (!HttpMethod.GET.equals(req.method())) {
            return false;
        }
        CharSequence upgrade = req.headers().get(HttpHeaderNames.UPGRADE);
        return upgrade != null && HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(upgrade);
    }

    private ProtocolVersion findProtocolVersionByPath(String connectPath) {
        return Arrays.stream(ProtocolVersion.values())
                .filter(version -> version.getPath().equals(connectPath))
                .findFirst()
                .orElse(null);
    }

    private String resolveClientIp(FullHttpRequest request, ChannelHandlerContext ctx) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String header : headers) {
            String ip = request.headers().get(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return extractFirstValidIp(ip);
            }
        }

        return getRemoteAddress(ctx);
    }

    private String extractFirstValidIp(String ipList) {
        String[] parts = ipList.split(",");
        for (String part : parts) {
            String ip = part.trim();
            if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return ipList.trim();
    }

    private String getRemoteAddress(ChannelHandlerContext ctx) {
        try {
            String remoteAddress = ctx.channel().remoteAddress().toString();
            if (remoteAddress.startsWith("/")) {
                remoteAddress = remoteAddress.substring(1);
            }
            int colonIndex = remoteAddress.indexOf(':');
            if (colonIndex != -1) {
                remoteAddress = remoteAddress.substring(0, colonIndex);
            }
            return remoteAddress;
        } catch (Exception e) {
            log.warn("NettyWebsocketAuthHandler - 获取客户端IP失败", e);
            return "unknown";
        }
    }

    private void sendUnsupportedProtocolResponse(ChannelHandlerContext ctx) {
        sendResponse(ctx, HttpResponseStatus.UPGRADE_REQUIRED, "不支持的 WebSocket 协议版本");
    }

    private void sendErrorResponse(ChannelHandlerContext ctx) {
        sendResponse(ctx, HttpResponseStatus.UNAUTHORIZED, "认证失败");
    }

    private void sendServiceUnavailableResponse(ChannelHandlerContext ctx) {
        sendResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "服务器过载，请稍后重试");
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        if (ctx.channel().eventLoop().inEventLoop()) {
            doSendResponse(ctx, status, message);
        } else {
            ctx.channel().eventLoop().execute(() -> doSendResponse(ctx, status, message));
        }
    }

    private void doSendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        try {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status,
                    Unpooled.copiedBuffer(message, StandardCharsets.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            log.error("NettyWebsocketAuthHandler - 发送响应异常", e);
            ctx.close();
        }
    }
}
