# 终端设备管理服务 (Terminal Device Management Service)

[![Java Version](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.11-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](#)
[![Performance](https://img.shields.io/badge/Concurrent%20Connections-20K+-blue.svg)](#性能基准)

## 📋 项目概述

基于Spring Boot 3.x和JDK 17的高性能终端设备管理服务，专门用于管理和监控终端设备的在线状态、指令下发、WebSocket连接等核心功能。支持数万设备并发连接，提供亚秒级响应时间。

### 🏗️ 架构特点

- **清洁架构**: 采用六边形架构，严格分离业务逻辑与基础设施
- **多协议支持**: HTTP REST API + WebSocket长连接 + Dubbo RPC
- **双JDK兼容**: 主服务JDK17，RPC接口JDK11兼容  
- **高可用设计**: Redis缓存 + MongoDB持久化 + 分片连接管理
- **事件驱动**: Spring Events + 异步处理
- **高性能**: 支持20K+并发连接，<100ms响应时间

## 📦 模块结构

```
colorlight-terminal/
├── terminal-commons/         # 🛠️ 通用工具类和异常处理
├── terminal-rpc-api/         # 🔌 Dubbo RPC接口 (JDK11兼容)  
├── terminal-api/             # 📡 设备REST API定义
├── terminal-application/     # 🏢 应用层与领域服务
├── terminal-infrastructure/  # 🏗️ 基础设施实现
└── terminal-boot/           # 🚀 应用启动与配置
```

### 核心模块说明

| 模块 | 职责 | 编译版本 | 关键组件 |
|-----|------|---------|---------|
| **terminal-rpc-api** | Dubbo RPC共享接口/DTO | JDK 11 | RPC接口定义 |
| **terminal-api** | 设备REST API定义 | JDK 17 | API规范 |
| **terminal-application** | 应用编排与领域服务 | JDK 17 | 业务逻辑，Use Cases |
| **terminal-infrastructure** | Redis/Dubbo/WebSocket实现 | JDK 17 | Netty，缓存，持久化 |
| **terminal-boot** | 启动配置与控制器 | JDK 17 | Controllers，配置 |

## ✨ 核心功能

### 🔄 设备状态管理
- **实时监控**: 设备在线/离线状态实时跟踪
- **TTL策略**: 双TTL机制(1小时初始+2分钟重连窗口)  
- **智能清理**: 启动时自动清理过期设备缓存
- **事件驱动**: 状态变更事件异步处理
- **批量更新**: 高性能批处理状态更新

### 📡 通信协议
- **HTTP轮询**: 设备定时获取指令(默认1分钟)
- **WebSocket**: 长连接实时通信(55秒心跳)
- **指令下发**: 支持截图、重启、亮度调节等操作  
- **权限认证**: Basic认证 + 设备序列号校验
- **多协议版本**: 支持v10/v11协议版本

### 🚀 性能优化
- **分片连接管理**: 16分片管理，减少锁竞争
- **缓存策略**: Redis + Caffeine双级缓存
- **批量处理**: 设备状态批量更新优化
- **异步处理**: 状态更新与事件发布异步化
- **线程池优化**: 专业化线程池配置

## 🛠️ 技术栈

### 核心框架
- **Spring Boot**: 3.3.11
- **Spring Cloud**: 2023.0.3  
- **Dubbo**: 3.2.11
- **Java**: 17 (RPC模块使用11)
- **Netty**: 4.1.100.Final

### 数据存储
- **Redis**: 状态缓存与分布式锁
- **MongoDB**: 在线时长与重连记录等统计数据
- **MySQL**: 终端账号管理 (MyBatis Plus 3.5.9)

### 通信组件  
- **Netty**: WebSocket服务器
- **WebSocket**: 设备长连接
- **HTTP**: REST API与设备轮询
- **Caffeine**: 本地一级缓存 (3.2.2)


## 📚 API文档

### 设备交互API

| 接口 | 方法 | 路径 | 描述 |
|-----|------|------|-----|
| 上报终端状态 | POST | `/api/device/report/terminal` | 设备上报LED状态 |
| 获取指令 | GET | `/api/device/commands` | 设备获取待执行指令 |
| 确认指令 | POST | `/api/device/command/confirm` | 设备确认指令执行 |
| 获取节目 | GET | `/api/device/programs` | 获取播放节目信息 |
| 获取素材 | GET | `/api/device/media` | 获取素材文件信息 |
| 上报日志 | POST | `/api/device/logs` | 设备上报运行日志 |
| 上报截图 | POST | `/api/device/screenshot` | 设备上报屏幕截图 |

### 认证方式

```http
# Basic认证  
Authorization: Basic <base64(deviceId:password)>
```

### WebSocket连接

```javascript
// WebSocket连接示例
const ws = new WebSocket('ws://localhost:8443/websocket/v11?deviceId=12345&auth=token');

ws.onmessage = function(event) {
    const message = JSON.parse(event.data);
    console.log('收到消息:', message);
};
```
## 🔧 开发指南

### 代码结构规范

#### 🏗️ 六边形架构映射

本项目严格遵循六边形架构(Hexagonal Architecture)设计原则，实现业务逻辑与技术实现的完全解耦：

```
com.colorlight.terminal/
├── api/                    # 📡 API契约层 (外部接口定义)
├── application/           # 🏢 应用核心层 (业务逻辑)
│   ├── domain/           # 🎯 领域模型 (业务实体与规则)
│   │   ├── command/      # 指令聚合
│   │   ├── connection/   # 连接管理
│   │   └── status/       # 状态管理
│   ├── port/             # 🔌 端口定义 (接口抽象)
│   │   ├── inbound/     # 入站端口 (用例接口)
│   │   │   ├── auth/    # 认证用例
│   │   │   ├── command/ # 指令用例
│   │   │   └── status/  # 状态用例
│   │   └── outbound/    # 出站端口 (基础设施抽象)
│   │       ├── cache/   # 缓存抽象
│   │       ├── repository/ # 数据访问抽象
│   │       └── websocket/  # WebSocket抽象
│   ├── service/         # ⚙️ 应用服务 (用例编排)
│   └── dto/             # 📋 数据传输对象
├── infrastructure/       # 🏗️ 基础设施层 (技术实现)
│   ├── cache/           # Redis + Caffeine缓存适配器
│   ├── persistence/     # MySQL + MongoDB持久化适配器
│   ├── websocket/       # Netty WebSocket适配器
│   ├── rpc/             # Dubbo RPC适配器
│   ├── event/           # Spring事件适配器
│   └── security/        # 认证授权适配器
├── boot/                 # 🚀 启动配置层 (应用入口)
│   ├── controller/      # REST API适配器
│   ├── config/          # 配置类
│   └── interceptor/     # 拦截器
├── rpc-api/             # 🔌 RPC接口定义 (JDK11兼容)
└── commons/             # 🛠️ 通用组件 (工具类与异常)
```

#### 🎯 分层职责与依赖规则

| 层级 | 职责 | 依赖方向 | 命名规范 |
|-----|------|---------|---------|
| **API层** | 接口契约定义，OpenAPI规范 | 被boot层依赖 | `*Api`、`DeviceApi*` |
| **应用层** | 业务用例编排，事务管理 | 依赖domain，通过port抽象基础设施 | `*UseCase`、`*ApplicationService` |
| **领域层** | 业务规则，实体行为，领域逻辑 | 零外部依赖(纯业务模型) | 业务概念名词，如`TerminalCommand` |
| **基础设施层** | 技术实现，外部系统集成 | 实现outbound port接口 | `*Adapter`、`*Repository`、`*Service` |
| **启动层** | HTTP入口，依赖注入配置 | 依赖inbound port | `*Controller`、`*Config` |

#### 🔄 Port-Adapter模式实现

**入站适配器 (Primary Adapters)**：
- `terminal-boot/controller/*Controller.java` → REST API入口
- `terminal-infrastructure/websocket/server/*Server.java` → WebSocket入口

**入站端口 (Primary Ports)**：
- `terminal-application/port/inbound/*UseCase.java` → 业务用例接口定义

**出站端口 (Secondary Ports)**：
- `terminal-application/port/outbound/*Port.java` → 基础设施能力抽象
- `terminal-application/port/outbound/repository/*Repository.java` → 数据访问抽象

**出站适配器 (Secondary Adapters)**：
- `terminal-infrastructure/cache/*Adapter.java` → 缓存技术实现
- `terminal-infrastructure/persistence/*Repository.java` → 数据持久化实现
- `terminal-infrastructure/rpc/*ServiceImpl.java` → RPC服务实现

#### 📋 命名约定规范

**端口层命名**：
```java
// 入站端口：业务用例视角
*UseCase                    // 用例接口定义
*ApplicationService         // 用例具体实现

// 出站端口：技术能力抽象  
*Port                      // 基础设施端口
*Repository                // 数据访问端口
*Gateway                   // 外部服务网关
```

**领域模型命名**：
```java
*Command、*Event、*Query   // CQRS模式支持
*Status、*State           // 状态模式对象
*Entity、*ValueObject     // DDD实体与值对象
```

**适配器命名**：
```java
*Adapter                  // 技术适配器实现
*Controller               // REST控制器适配器
*ServiceImpl              // RPC服务实现
```

#### ⚡ 架构约束原则

1. **依赖倒置**：应用层只依赖抽象接口，不依赖具体实现
2. **单向依赖**：基础设施层实现应用层定义的端口接口
3. **业务纯粹**：领域模型保持技术无关性，专注业务规则
4. **接口分离**：端口接口体现业务语言，而非技术细节
5. **配置外化**：技术配置与业务逻辑完全分离
