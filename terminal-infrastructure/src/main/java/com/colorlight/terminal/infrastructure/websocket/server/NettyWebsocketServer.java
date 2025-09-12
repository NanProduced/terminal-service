package com.colorlight.terminal.infrastructure.websocket.server;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.websocket.config.NettyWebsocketProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Netty WebSocket服务器
 * <p>
 * 这是整个WebSocket服务的核心启动类，负责：
 * 1. 启动Netty服务器监听WebSocket连接
 * 2. 配置线程组和Bootstrap参数
 * 3. 管理服务器生命周期（启动/关闭）
 * 4. 处理异常和优雅关闭
 * <p>
 * Netty基础概念：
 * - EventLoopGroup: 事件循环组，处理I/O操作
 * - Bootstrap: 启动器，配置服务器参数
 * - Channel: 网络连接的抽象，代表一个TCP连接
 * - ChannelPipeline: 处理链，定义数据流处理顺序
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyWebsocketServer implements ApplicationRunner, DisposableBean {

    private final NettyWebsocketProperties nettyWebsocketProperties;
    private final WebSocketChannelInitializer webSocketChannelInitializer;

    // ============== Netty核心组件 ==============
    
    /**
     * Boss线程组
     */
    private EventLoopGroup bossGroup;
    
    /**
     * Worker线程组
     */
    private EventLoopGroup workerGroup;
    
    /**
     * 服务器Channel
     * 用AtomicReference保证多线程访问的线程安全
     */
    private final AtomicReference<Channel> serverChannel = new AtomicReference<>();

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 检查是否启用WebSocket服务
        if (!nettyWebsocketProperties.isEnabled()) {
            log.info("NettyWebsocketServer - WebSocket服务器已禁用，跳过启动");
            return;
        }
        
        log.info("NettyWebsocketServer - 正在同步启动WebSocket服务器...");
        log.info("NettyWebsocketServer - 终端服务特性：确保服务完全就绪后再接受设备连接");
        
        try {
            // 同步启动，确保服务完全就绪
            startWebsocketServerSync();
            log.info("NettyWebsocketServer - WebSocket服务器启动成功，监听端口: {}", 
                    nettyWebsocketProperties.getServer().getPort());
        } catch (Exception e) {
            log.error("NettyWebsocketServer - WebSocket服务器启动失败", e);
            // 启动失败直接抛异常，阻止Spring启动
            throw new TechnicalException(TechErrorCode.NETTY_START_ERROR, "WebSocket服务器启动失败，应用无法正常工作", e);
        }
    }

    /**
     * 应用关闭时自动调用 - 关闭WebSocket服务器
     * 
     * @throws Exception 关闭异常
     */
    @Override
    public void destroy() throws Exception {
        log.info("NettyWebsocketServer - 正在关闭WebSocket服务器...");
        shutdownGracefully();
        log.info("NettyWebsocketServer - WebSocket服务器已关闭");
    }

    // ============== 服务器启动逻辑 ==============

    /**
     * 同步启动WebSocket服务器
     *
     * <p>1. 播放盒断连后会不停重试连接</P>
     * <p>2. 服务启动瞬间会有大量请求涌入</P>
     * <p>3. 必须确保Netty完全就绪后才能接受连接</P>
     */
    private void startWebsocketServerSync() throws Exception {
        // 1. 创建线程组
        initializeEventLoopGroups();
        
        // 2. 配置服务器Bootstrap
        ServerBootstrap bootstrap = configureServerBootstrap();
        
        // 3. 同步绑定端口并启动服务器
        ChannelFuture channelFuture = bootstrap.bind(
                nettyWebsocketProperties.getServer().getHost(),
                nettyWebsocketProperties.getServer().getPort()
        );
        
        // 等待绑定完成，设置30秒超时
        if (!channelFuture.await(30, TimeUnit.SECONDS)) {
            shutdownGracefully();
            throw new TechnicalException(TechErrorCode.NETTY_START_ERROR, "WebSocket服务器绑定超时(30秒)");
        }
        
        if (!channelFuture.isSuccess()) {
            shutdownGracefully();
            throw new TechnicalException(TechErrorCode.NETTY_START_ERROR, "WebSocket服务器绑定失败", channelFuture.cause());
        }
        
        // 保存服务器channel引用
        serverChannel.set(channelFuture.channel());
        
        log.info("NettyWebsocketServer - WebSocket服务器绑定成功: {}:{}", 
                nettyWebsocketProperties.getServer().getHost(),
                nettyWebsocketProperties.getServer().getPort());
    }


    /**
     * 初始化Netty事件循环组
     */
    private void initializeEventLoopGroups() {
        int bossThreads = nettyWebsocketProperties.getServer().getBossThreads();
        bossGroup = new NioEventLoopGroup(bossThreads);
        log.debug("NettyWebsocketServer - Boss线程组初始化完成，线程数: {}", bossThreads);

        int workerThreads = nettyWebsocketProperties.getServer().getWorkerThreads();
        if (workerThreads <= 0) {
            workerGroup = new NioEventLoopGroup(); // 使用默认线程数
            log.debug("NettyWebsocketServer - Worker线程组初始化完成，使用默认线程数: {}", 
                    Runtime.getRuntime().availableProcessors() * 2);
        } else {
            workerGroup = new NioEventLoopGroup(workerThreads);
            log.debug("NettyWebsocketServer - Worker线程组初始化完成，线程数: {}", workerThreads);
        }
    }

    /**
     * 配置服务器Bootstrap
     */
    private ServerBootstrap configureServerBootstrap() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        
        // 配置线程组
        bootstrap.group(bossGroup, workerGroup)
                // 指定Channel类型为NIO ServerSocket
                .channel(NioServerSocketChannel.class)
                // 配置Channel初始化器（负责设置pipeline）
                .childHandler(webSocketChannelInitializer);
        
        // 配置服务器socket选项
        configureServerOptions(bootstrap);
        
        // 配置客户端连接选项
        configureChildOptions(bootstrap);
        
        return bootstrap;
    }

    /**
     * 配置服务器Socket选项
     *
     */
    private void configureServerOptions(ServerBootstrap bootstrap) {
        NettyWebsocketProperties.Server serverConfig = nettyWebsocketProperties.getServer();
        
        // SO_REUSEADDR: 允许重用本地地址
        if (serverConfig.isSoReuseAddr()) {
            bootstrap.option(ChannelOption.SO_REUSEADDR, true);
            log.debug("NettyWebsocketServer - 启用地址重用(SO_REUSEADDR)");
        }
        
        // SO_BACKLOG: 设置TCP连接队列大小
        bootstrap.option(ChannelOption.SO_BACKLOG, serverConfig.getSoBacklog());
        log.debug("NettyWebsocketServer - 设置连接队列大小: {}", serverConfig.getSoBacklog());

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, serverConfig.getConnectTimeout());
        log.debug("NettyWebsocketServer - 连接超时时间: {} ms", serverConfig.getConnectTimeout());

    }

    /**
     * 配置客户端连接选项
     */
    private void configureChildOptions(ServerBootstrap bootstrap) {
        NettyWebsocketProperties.Server serverConfig = nettyWebsocketProperties.getServer();
        
        // SO_KEEPALIVE: 启用TCP keepalive
        // 用于检测死连接，定期发送心跳包
        if (serverConfig.isKeepAlive()) {
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            log.debug("NettyWebsocketServer - 启用TCP KeepAlive");
        }
        
        // TCP_NODELAY: 禁用Nagle算法
        // Nagle算法会延迟发送小包以提高网络效率，但会增加延迟
        // 对于实时通信（如WebSocket），通常禁用它以减少延迟
        if (serverConfig.isTcpNoDelay()) {
            bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
            log.debug("NettyWebsocketServer - 启用TCP_NODELAY（禁用Nagle算法）");
        }

        bootstrap.childOption(ChannelOption.SO_RCVBUF, serverConfig.getSoRcvBuf());
        bootstrap.childOption(ChannelOption.SO_SNDBUF, serverConfig.getSoSndBuf());


        log.debug("NettyWebsocketServer - 设置缓冲区大小: 64KB");
    }

    // ============== 服务器关闭逻辑 ==============

    /**
     * 优雅关闭服务器
     */
    private void shutdownGracefully() {
        // 1. 原子性获取并清除serverChannel引用
        Channel channel = serverChannel.getAndSet(null);
        
        // 2. 关闭服务器Channel（如果存在）
        if (channel != null && channel.isActive()) {
            try {
                log.debug("NettyWebsocketServer - 正在关闭服务器Channel...");
                channel.close().sync();
                log.debug("NettyWebsocketServer - 服务器Channel已关闭");
            } catch (InterruptedException e) {
                log.warn("NettyWebsocketServer - 关闭服务器Channel时被中断");
                Thread.currentThread().interrupt();
            }
        }
        
        // 3. 优雅关闭线程组
        shutdownEventLoopGroups();
    }

    /**
     * 优雅关闭事件循环组
     * <p>
     * 给线程组一些时间来完成正在处理的任务，然后强制关闭
     */
    private void shutdownEventLoopGroups() {
        try {
            if (workerGroup != null) {
                log.debug("NettyWebsocketServer - 正在关闭Worker线程组...");
                // 优雅关闭：等待15秒让线程完成当前任务，然后强制关闭
                if (!workerGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS).await(20, TimeUnit.SECONDS)) {
                    log.warn("NettyWebsocketServer - Worker线程组关闭超时，强制关闭");
                }
                log.debug("NettyWebsocketServer - Worker线程组已关闭");
            }
            
            if (bossGroup != null) {
                log.debug("NettyWebsocketServer - 正在关闭Boss线程组...");
                if (!bossGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS).await(20, TimeUnit.SECONDS)) {
                    log.warn("NettyWebsocketServer - Boss线程组关闭超时，强制关闭");
                }
                log.debug("NettyWebsocketServer - Boss线程组已关闭");
            }
        } catch (InterruptedException e) {
            log.warn("NettyWebsocketServer - 关闭线程组时被中断");
            Thread.currentThread().interrupt();
        }
    }
}
