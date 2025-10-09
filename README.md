# 终端设备管控服务 (Terminal Device Management Service)

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.11-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build](https://img.shields.io/badge/Build-mvn%20clean%20verify-blue.svg)](pom.xml)
[![Architecture](https://img.shields.io/badge/Hexagonal-DDD-blueviolet.svg)](#架构与模块)

## 项目简介
Colorlight 终端设备管控服务是面向 LED 显示终端的接入网关，负责处理终端的 HTTP/WebSocket 通讯、指令发放、状态治理与运行监控。项目基于 Spring Boot 3、Dubbo、Redis、MongoDB、MySQL、MinIO 构建，已经完成主要用例与测试用例，适用于生产化环境的快速部署。

系统采用 Port-Adapter（六边形）架构，将业务规则沉淀在 `terminal-application` 模块，`terminal-infrastructure` 提供缓存、持久化、RPC、WebSocket 等适配器，`terminal-boot` 则暴露 REST 接口与运行时配置。

## 核心能力
- 统一接入：HTTP Basic 认证与 Netty WebSocket 双通道接入，兼容终端协议 v1.0/v1.1。
- 指令生命周期：命令生成、排队、下发、回执、事件通知全链路可追踪，支持 Redis/Caffeine 双层缓存与 WebSocket 推送。
- 状态治理：终端心跳、上下线、重连事件通过分布式锁与 TTL 机制治理，支持异步/同步模式切换。
- 数据留存：状态报告、截图、日志、播放记录分别入 MongoDB、MinIO、MySQL，支持批量落库与补偿策略。
- 异常告警：Redis 过期监听、离线调度、Dubbo 清理任务协同处理异常设备。
- 运维观测：内置多项 Actuator 自定义端点、Prometheus 指标、线程池与 EventLoop 监控。

## 架构与模块
服务遵循 DDD 分层约定，Inbound Port (`*UseCase`) 驱动业务流程，Outbound Port (`*Port`, `*Repository`, `*Adapter`) 接入外部系统。

| 模块 | 描述 | 关键职责 |
| --- | --- | --- |
| terminal-commons | 公共异常、工具、常量 | 错误码、JSON 工具、基础响应模型 |
| terminal-rpc-api | Dubbo RPC 契约（JDK 11 兼容） | DTO、服务接口、外部系统契约 |
| terminal-api | REST 契约与 DTO | `DeviceInteractionApi` 等控制器接口定义 |
| terminal-application | 业务用例与领域模型 | 指令、状态、连接、日志等 UseCase 与领域对象 |
| terminal-infrastructure | 适配器实现 | Redis/Mongo/MySQL/MinIO、Netty、Dubbo、事件、调度 |
| terminal-boot | Spring Boot 启动与控制层 | REST Controller、Spring Security、Actuator、配置装配 |

### 目录速览
```text
colorlight-terminal/
├── pom.xml
├── terminal-api/
├── terminal-application/
├── terminal-infrastructure/
├── terminal-boot/
└── terminal-commons/ … (公共异常、工具)
```

## 快速开始
- **环境准备**
  - JDK 17、Maven 3.9+、Docker（可选）。
  - MySQL 8.0、Redis 7.x、MongoDB 6.x、MinIO、Nacos（Dubbo 注册中心）等依赖服务。
  - 如需调试 RPC，请确保主控系统提供 Dubbo 服务或启动测试桩。

- **构建**
  ```bash
  mvn -T 1C clean package -DskipTests
  ```

- **运行测试**
  ```bash
  mvn clean test
  mvn -pl terminal-application -am test                # 仅应用层
  mvn -pl terminal-infrastructure -am -Dtest=DeviceOnlineStatusRedisServiceTest test
  mvn clean verify                                     # 含 Jacoco 覆盖率
  ```

- **本地运行**
  ```bash
  mvn -pl terminal-boot -am spring-boot:run -Plocal -Dspring-boot.run.profiles=local
  ```
  根据实际环境覆盖 `terminal-boot/src/main/resources/application-local.yml` 中的数据库、缓存、存储、Dubbo、Zipkin 配置，或通过环境变量 `SPRING_*`、`TERMINAL_*` 覆盖。

- **打包与启动**
  ```bash
  mvn -pl terminal-boot -am package
  java -jar terminal-boot/target/terminal-boot-1.0.0.jar --spring.profiles.active=prod
  ```

- **容器镜像**
  ```bash
  docker build -t colorlight/terminal .
  docker run -d --name terminal -p 8088:8088 -e SPRING_PROFILES_ACTIVE=prod colorlight/terminal
  ```

## 配置说明
| Profile | 场景 | 配置文件 |
| --- | --- | --- |
| local | 本地开发（禁用 Nacos 注册） | `terminal-boot/src/main/resources/application-local.yml` |
| dev | 开发环境 | `.../application-dev.yml` |
| intranet | 内网部署（保留部分调试配置） | `.../application-intranet.yml` |
| prod | 生产环境，需外部化敏感信息 | `.../application.yml` + 外部配置中心 |

- 可通过 Maven Profile (`-Plocal/-Pdev/...`) 与 `--spring.profiles.active=` 组合指定运行环境。
- 推荐将数据库、Redis、MongoDB、MinIO、Dubbo、Zipkin 等凭据以环境变量或配置中心注入，避免硬编码。
- 运行容器时可使用环境变量自定义 JVM 选项：`JAVA_TOOL_OPTIONS`、`JAVA_OPTS`、`DATA_DIR`。
- `terminal.netty`、`terminal.websocket` 配置段控制 Netty 端口、分片、心跳间隔及统计任务，可根据负载调整。

## REST API 速查
全部接口均要求 Basic Auth（终端账号），路径与官方终端协议保持兼容。

| 方法 | 路径 | 功能 | 备注 |
| --- | --- | --- | --- |
| PUT | `/wp-json/screen/v1/status` | 上报终端状态 | 异步写入 Redis + 事件队列 |
| GET | `/wp-json/wp/v2/comments` | 拉取待执行指令 | 返回空数组即无指令 |
| POST | `/wp-json/wp/v2/comments` | 指令确认 | 204 表示确认成功 |
| GET | `/wp-json/wp/v2/programs` | 获取节目清单 | 适配节目/排期数据 |
| GET | `/wp-json/wp/v2/media` | 获取媒资文件信息 | 支持分页与分类过滤 |
| POST | `/wp-json/led/flowfee` | 媒资播放上报 | 批量 JSON 报文 |
| POST | `/wp-json/led/flowfee/v2/program` | 节目播放上报 | 记录节目播放轨迹 |
| POST | `/wp-json/led/monitor/log` | 终端日志上报 | 支持批量日志写入 |
| POST | `/wp-json/wp/v2/media` | 上传截图 | 需携带 `Content-Disposition` 头部 |
| POST | `/wp-json/led/v2/monitor` | 传感器/告警数据 | 201 表示持久化成功 |
| POST | `/wp-json/screen/v1/info` | 下载进度上报 | 用于断点续传监控 |

详见 `terminal-api/src/main/java/com/colorlight/terminal/api/DeviceInteractionApi.java`。

## WebSocket 接入
- 支持协议路径：
  - v1.0：`ws://{host}:8443/ColorWebSocket/websocket/chat?username={id}&password={token}`
  - v1.1：`ws://{host}:8443/ColorWebSocket/terminal?username={id}&password={token}&protocol_version=1.1`
- 握手认证由 `NettyWebsocketAuthHandler` 执行，认证成功后会将报文转发至内部 `/websocket` 管道。
- `ShardedConnectionManager` 基于设备 ID 分片管理连接，支持 16 分片水平扩展与离线清理调度。
- WebSocket 指令/日志协议由 `ProtocolProcessorRegistry` 按版本路由，v1.0/v1.1 各自拥有独立处理器。
- 若 `terminal.websocket.stats.enabled=true`，系统按计划任务输出连接统计、负载均衡与异常告警。

## RPC 与异步事件
- `terminal-rpc-api` 定义 Dubbo 接口，`terminal-infrastructure` 提供适配器 (`DubboMainServiceRpcAdapter`, `TerminalCommandRpcServiceImpl` 等)，用于与主控或外部业务系统协同。
- 状态/指令/系统事件通过 Spring ApplicationEvent、异步缓冲区与 Redis 过期监听触发，对外可订阅离线、重连、指令确认等事件。
- `AsyncDeviceStatusUpdatePort` 支持高峰期将状态写入后台线程池，必要时可降级为同步写入保障一致性。

## 可观测性与运维
- Actuator 默认开放：`/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, `/actuator/devices`, `/actuator/websocket`, `/actuator/threadpools`, `/actuator/eventloop` 等。
- `ThreadPoolEndpoint`、`EventLoopEndpoint`、`WebSocketConnectionEndpoint` 提供线程池利用率、Netty EventLoop 健康、在线连接详情。
- Micrometer 提供 HTTP 指标、业务定制指标，可直接抓取 Prometheus；Zipkin 链路追踪可通过 `management.zipkin.tracing.endpoint` 配置。
- 日志使用 `logback-spring.xml`，默认 DEBUG 输出业务日志，可通过 `logging.level` 动态调整。

## 测试与质量保障
- 单元/集成测试覆盖应用层、基础设施层、WebSocket、缓存、事件等关键模块 (`terminal-application/src/test`, `terminal-infrastructure/src/test`)。
- 使用 Mockito、Awaitility、Testcontainers 提供模拟或临时依赖，`IntegrationTestBase` 封装公共测试装配。
- 运行 `mvn clean verify` 可生成 `target/site/jacoco/index.html` 覆盖率报告；提交前请确保构建通过并附上测试结果。
- 建议针对新增端点补充契约测试或 MockMvc 测试。

## 贡献与约定
- 代码风格：Java 17、四空格缩进，优先构造器注入；控制器仅做请求编排，业务逻辑下沉至 `*UseCase` / `*ApplicationService`。
- 模块命名遵循 `*Port`、`*Adapter`、`*Repository`、`*Gateway`，DTO 由 Lombok/MapStruct 辅助。
- 提交信息遵循 Conventional Commits，如 `feat(terminal-boot): add health endpoint`。
- 禁止提交真实密钥，跨环境配置请放置于 `application-*.yml` 或外部化配置；必要时在 PR 中同步风险与回滚方案。
