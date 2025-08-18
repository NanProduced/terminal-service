package com.colorlight.terminal.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.colorlight.terminal")
@EnableDiscoveryClient
@MapperScan("com.colorlight.terminal.infrastructure.persistence.mysql.mapper")
public class TerminalApplication {
    public static void main(String[] args) {
        SpringApplication.run(TerminalApplication.class, args);
    }
}
