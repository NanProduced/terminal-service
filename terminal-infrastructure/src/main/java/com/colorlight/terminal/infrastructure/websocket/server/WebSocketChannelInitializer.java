package com.colorlight.terminal.infrastructure.websocket.server;

import com.colorlight.terminal.infrastructure.websocket.auth.NettyWebsocketAuthHandler;
import com.colorlight.terminal.infrastructure.websocket.config.NettyWebsocketProperties;
import com.colorlight.terminal.infrastructure.websocket.handler.NettyWebsocketFrameHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyWebsocketProperties nettyWebsocketProperties;
    private final NettyWebsocketAuthHandler nettyWebsocketAuthHandler;
    private final NettyWebsocketFrameHandler nettyWebsocketFrameHandler;

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        log.debug("NettyWebsocketChannelInitializer - 初始化Websocket通道:{}", socketChannel.id().asShortText());

        ChannelPipeline pipeline = socketChannel.pipeline();

        // 1. HTTP编码器
        pipeline.addLast("http-codec", new HttpServerCodec());

        // 2. HTTP消息聚合器
        pipeline.addLast("http-aggregator", new HttpObjectAggregator(nettyWebsocketProperties.getMessage().getMaxAggregatedFrameSize()));

        // 3. Websocket认证处理器（协议升级前将认证信息放入上下文）
        pipeline.addLast("websocket-auth", nettyWebsocketAuthHandler);

        // 4. 如果开启压缩则添加压缩处理
        if (nettyWebsocketProperties.getMessage().isCompressionEnabled()) {
            pipeline.addLast("websocket-compress", new WebSocketServerCompressionHandler());
        }

        // 5. WebSocket协议处理器 - 使用统一内部路径处理所有协议版本
        pipeline.addLast("websocket-protocol", new WebSocketServerProtocolHandler(
                nettyWebsocketProperties.getServer().getWebsocketPath(),
                null, // 不限制子协议
                true, // 允许扩展
                nettyWebsocketProperties.getMessage().getMaxFrameSize()
        ));

        // 6. 心跳超时检测 - 根据设备55秒心跳要求配置
        if (nettyWebsocketProperties.getHeartbeat().isEnabled()) {
            pipeline.addLast("idle-state-handler", new IdleStateHandler(
                    nettyWebsocketProperties.getHeartbeat().getReadTimeout(),
                    nettyWebsocketProperties.getHeartbeat().getWriteTimeout(),
                    nettyWebsocketProperties.getHeartbeat().getAllIdleTimeout(),
                    TimeUnit.SECONDS
            ));
        }

        // 7. WebSocket业务处理器
        pipeline.addLast("websocket-frame-handler", nettyWebsocketFrameHandler);

        log.debug("NettyWebsocketChannelInitializer - 通道初始化完成: {}", socketChannel.id().asShortText());
    }
}
