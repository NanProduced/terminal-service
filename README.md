# 终端设备管理服务 (Terminal Device Management Service)

[![Java Version](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.11-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](#)

## 📋 项目概述

基于Spring Boot 3.x和JDK 17，专门用于管理和监控终端设备的在线状态、指令下发、WebSocket连接等核心功能。

### 🏗️ 架构特点

- **清洁架构**: 采用六边形架构，严格分离业务逻辑与基础设施
- **多协议支持**: HTTP REST API + WebSocket长连接
- **双JDK兼容**: 主服务JDK17，RPC接口JDK11兼容
- **高可用设计**: Redis缓存 + MongoDB持久化 + 分片连接管理
- **事件驱动**: Spring Events + 异步处理

## 📦 模块结构

```
colorlight-terminal/
├── terminal-commons          # 通用工具类和异常处理
├── terminal-rpc-api         # Dubbo RPC接口 (JDK11兼容)
├── terminal-api             # 设备REST API定义
├── terminal-application     # 应用层与领域服务
├── terminal-infrastructure  # 基础设施实现
└── terminal-boot           # 应用启动与配置
```

### 核心模块说明

| 模块 | 职责 | 编译版本 |
|-----|------|---------|
| **terminal-rpc-api** | Dubbo RPC共享接口/DTO | JDK 11 |
| **terminal-api** | 设备REST API定义 | JDK 17 |
| **terminal-application** | 应用编排与领域服务 | JDK 17 |
| **terminal-infrastructure** | Redis/Dubbo/WebSocket实现 | JDK 17 |
| **terminal-boot** | 启动配置与控制器 | JDK 17 |

## ✨ 核心功能

### 🔄 设备状态管理
- **实时监控**: 设备在线/离线状态实时跟踪
- **TTL策略**: 双TTL机制(1小时初始+2分钟重连窗口)
- **智能清理**: 启动时自动清理过期设备缓存
- **事件驱动**: 状态变更事件异步处理

### 📡 通信协议
- **HTTP轮询**: 设备定时获取指令(默认1分钟)
- **WebSocket**: 长连接实时通信(55秒心跳)
- **指令下发**: 支持截图、重启、亮度调节等操作
- **权限认证**: Basic认证 + 设备序列号校验

### 🚀 性能优化
- **连接池管理**: 分片式WebSocket连接管理
- **缓存策略**: Redis + Caffeine双级缓存
- **批量处理**: 设备状态批量更新优化
- **异步处理**: 状态更新与事件发布异步化

## 🛠️ 技术栈

### 核心框架
- **Spring Boot**: 3.3.11
- **Spring Cloud**: 2023.0.3
- **Dubbo**: 3.2.11
- **Java**: 17 (RPC模块使用11)

### 数据存储
- **Redis**: 状态缓存与分布式锁
- **MongoDB**: 在线时长与重连记录等统计数据
- **MySQL**: 终端账号管理

### 通信组件
- **Netty**: WebSocket服务器
- **WebSocket**: 设备长连接
- **HTTP**: REST API与设备轮询


