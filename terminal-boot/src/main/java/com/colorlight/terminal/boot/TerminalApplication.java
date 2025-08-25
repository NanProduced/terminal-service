package com.colorlight.terminal.boot;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@Slf4j
@SpringBootApplication(scanBasePackages = "com.colorlight.terminal")
@EnableDiscoveryClient
@MapperScan("com.colorlight.terminal.infrastructure.persistence.mysql.mapper")
public class TerminalApplication {
    public static void main(String[] args) {
        // 添加启动前的系统信息
        log.info("========== 设备终端通信服务启动开始 ==========");
        log.info("Java版本: {}", System.getProperty("java.version"));
        log.info("操作系统: {}", System.getProperty("os.name"));
        log.info("工作目录: {}", System.getProperty("user.dir"));
        log.info("可用处理器核心数: {}", Runtime.getRuntime().availableProcessors());
        log.info("最大堆内存: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);

        try {
            SpringApplication.run(TerminalApplication.class, args);

            log.info("📡 设备终端通信服务 (Terminal Service) 启动成功!");
            log.info("🌐 HTTP服务端口: 8088");
            log.info("🔌 WebSocket服务端口: 8443");
            log.info("📱 设备HTTP接口: http://localhost:8088/terminal/**");
            log.info("🔗 WebSocket连接: ws://localhost:8443/ColorWebSocket/websocket/chat");
            log.info("🔐 认证方式: URL参数认证 (?username=xxx&password=xxx)");
            log.info("📋 管理端点: http://localhost:8088/actuator");
            log.info("📊 健康检查: http://localhost:8088/actuator/health");
            log.info("========== 设备终端通信服务启动完成 ==========");

        } catch (Exception e) {
            log.error("❌ 设备终端通信服务启动失败！", e);
            System.exit(1);
        }
    }
}
