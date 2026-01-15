package com.colorlight.terminal.infrastructure.websocket.utils;

import com.colorlight.terminal.infrastructure.websocket.auth.NettyWebsocketAuthHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import org.apache.commons.lang3.StringUtils;

public class WsIpUtils {

    private WsIpUtils() {
    }

    public static String getClientIp(ChannelHandlerContext ctx) {
        try {
            Attribute<String> clientIpAttr = ctx.channel().attr(NettyWebsocketAuthHandler.CLIENT_IP);
            String clientIp = clientIpAttr != null ? clientIpAttr.get() : null;
            if (StringUtils.isNotBlank(clientIp) && !"unknown".equalsIgnoreCase(clientIp)) {
                return clientIp;
            }

            String remoteAddress = ctx.channel().remoteAddress().toString();
            // 格式: "/192.168.0.163:35188" -> "192.168.0.163"
            // 移除前缀斜杠和端口号，保持与HTTP格式一致
            if (remoteAddress.startsWith("/")) {
                remoteAddress = remoteAddress.substring(1);
            }
            int colonIndex = remoteAddress.indexOf(':');
            if (colonIndex != -1) {
                remoteAddress = remoteAddress.substring(0, colonIndex);
            }
            return remoteAddress;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
