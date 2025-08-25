package com.colorlight.terminal.infrastructure.websocket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "terminal.netty")
public class NettyWebsocketProperties {

    private boolean enabled = true;

    private Server server = new Server();

    private Message message = new Message();
    
    private Heartbeat heartbeat = new Heartbeat();

    /**
     * netty websocket Server配置
     * <p>一些配置需要与播放盒协议保持一致</p>
     * @see .doc/云服务器对接设备二次开发文档总结.md
     */
    @Data
    public static class Server {

        private String host = "0.0.0.0";

        // 播放盒规定的ws连接端口
        private int port = 8443;

        // 播放盒规定的ws连接端点
        private String path = "/ColorWebSocket/websocket/chat";

        /**
         * Boss线程数 - 接受连接的线程数
         */
        private Integer bossThreads = 1;

        /**
         * Worker线程数 - 处理I/O的线程数
         * 0表示使用CPU核心数*2
         */
        private Integer workerThreads = 0;

        /**
         * 是否启用TCP Keep-Alive
         */
        private boolean keepAlive = true;

        /**
         * 是否禁用Nagle算法(启用TCP_NODELAY)
         */
        private boolean tcpNoDelay = true;

        /**
         * 是否启用地址重用
         */
        private boolean soReuseAddr = true;
        /**
         * TCP连接队列大小
         */
        private int soBacklog = 1024;

        /**
         * 接收缓冲区大小(字节)
         */
        private int soRcvBuf = 64 * 1024;

        /**
         * 发送缓冲区大小(字节)
         */
        private int soSndBuf = 64 * 1024;

        /**
         * 连接超时时间(毫秒)
         */
        private int connectTimeout = 10000;

    }

    /**
     * netty消息配置
     */
    @Data
    public static class Message {
        /**
         * 最大帧大小(字节)
         */
        private int maxFrameSize = 65536;

        /**
         * 最大聚合帧大小(字节)
         */
        private int maxAggregatedFrameSize = 1048576;

        /**
         * 文本消息编码
         */
        private String textEncoding = "UTF-8";

        /**
         * 是否启用消息压缩
         * 注：后续播放盒Websocket2.0需要使用消息压缩，当前不支持
         */
        private boolean compressionEnabled = false;

        /**
         * 压缩级别 (1-9)
         */
        private int compressionLevel = 6;

        /**
         * 消息处理超时时间(毫秒)
         */
        private long processingTimeout = 30000;

        /**
         * 是否启用消息队列
         */
        private boolean queueEnabled = true;

        /**
         * 消息队列最大长度
         */
        private int maxQueueSize = 10000;

    }

    /**
     * 心跳配置
     * 根据设备二次开发文档要求：设备每55秒发送心跳，服务器需要相应配置超时检测
     */
    @Data
    public static class Heartbeat {
        
        /**
         * 心跳间隔时间(秒) - 设备端固定55秒
         * 此配置用于服务器端参考，实际心跳由设备控制
         * @see .doc/云服务器对接设备二次开发文档总结.md
         */
        private int interval = 55;
        
        /**
         * 读取超时时间(秒) - 超过此时间未收到消息则关闭连接
         * 设置为心跳间隔的1.2倍，给网络延迟留出缓冲
         */
        private int readTimeout = (int) (interval * 1.2);
        
        /**
         * 写入超时时间(秒) - 发送消息超时时间
         */
        private int writeTimeout = 30;
        
        /**
         * 全体空闲超时时间(秒) - 读写都空闲的超时时间
         */
        private int allIdleTimeout = 0; // 0表示不启用
        
        /**
         * 是否启用心跳检测
         */
        private boolean enabled = true;
        
        /**
         * 心跳失败最大重试次数
         */
        private int maxRetries = 3;
        
        /**
         * 心跳检测间隔(秒) - 服务器主动检测心跳的间隔
         */
        private int checkInterval = 30;
    }

}
