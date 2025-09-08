# Terminal RPC API 版本更新说明

## 项目概述
Terminal RPC API 是 Dubbo RPC 共享接口包，提供服务间通信的标准化 DTO 和接口定义。

**技术特性**:
- JDK 11 编译，无 Lombok 依赖
- Maven Flatten 插件独立版本管理
- 包含 JavaDoc 和源码打包
- 内网 Nexus 仓库发布

---

## 版本历史

### 版本 1.0.2 🆕
**发布日期**: 2025-09-07  
**Git Commit**: `1bd10c0fcd3be51f6a69e50c3a6bfa87eafb80f3`  
**更新类型**: ✨ 新功能

#### 🔧 新增功能
**设备数据清理 RPC 接口**

##### 核心服务接口
- **DeviceDataCleanupRpcService** - 设备数据清理服务
  - `cleanupDeviceDataAsync(DeviceDataCleanupRequestDTO)` - 异步清理单设备数据
  - `batchCleanupDeviceDataAsync(BatchDeviceDataCleanupRequestDTO)` - 批量异步清理设备数据

##### DTO 数据传输对象

**请求 DTO**:
- **DeviceDataCleanupRequestDTO** - 单设备清理请求
  - `deviceId: Long` - 设备ID
  - `config: DataCleanupConfigDTO` - 清理配置(可选)
  - 支持 Builder 模式构建

- **BatchDeviceDataCleanupRequestDTO** - 批量清理请求  
  - `deviceIds: List<Long>` - 设备ID列表
  - `config: DataCleanupConfigDTO` - 统一清理配置

**配置 DTO**:
- **DataCleanupConfigDTO** - 数据清理配置
  - `mode: CleanupMode` - 清理模式(ALL/INCLUDE/EXCLUDE)
  - `dataTypes: Set<DataType>` - 指定数据类型集合
  - 支持 Builder 模式和默认配置

##### 枚举定义

**CleanupMode** - 清理模式枚举:
- `ALL` - 清理全部数据类型
- `INCLUDE` - 仅清理指定数据类型  
- `EXCLUDE` - 清理除指定类型外的所有数据

**DataType** - 数据类型枚举:

*MySQL 数据类型*:
- `SCREENSHOT_RECORD` - 截图记录(含MinIO文件)
- `SWITCH_RECORD` - 开机记录
- `DEVICE_ACCOUNT` - 设备账号信息(必须删除)

*MongoDB 数据类型*:
- `GPS_RECORD` - GPS记录
- `STATUS_REPORT` - 状态上报
- `TERMINAL_LOG` - 终端日志
- `MEDIA_PLAY_RECORD` - 素材播放记录
- `PROGRAM_PLAY_RECORD` - 节目播放记录
- `ONLINE_TIME` - 在线时长
- `ABNORMAL_RECONNECT` - 异常重连记录

*缓存数据*:
- `REDIS_CACHE` - Redis缓存数据

#### 🔄 向后兼容性
- ✅ 完全向后兼容 1.0.1 版本
- ✅ 无破坏性变更
- ✅ 新增功能采用独立包结构

---

### 版本 1.0.1 📦
**发布日期**: 2025-09-06  
**更新类型**: 🎯 初始版本

#### 核心功能
**终端账号管理**:
- **TerminalAccountRpcService** - 终端账号服务
- **CreateTerminalAccountDTO** - 创建账号请求
- **TerminalAccountResultDTO** - 账号结果返回

**终端指令服务**:
- **TerminalCommandRpcService** - 终端指令服务
- **SingleCommandRequestDTO** - 单指令请求
- **SingleCommandSendResultDTO** - 指令发送结果
- **TerminalCommandDTO** - 终端指令数据结构

**设备在线状态**:
- **DeviceOnlineStatusRpcService** - 设备在线状态服务
- **BatchDeviceStatusRequestDTO** - 批量状态查询请求
- **BatchDeviceStatusResultDTO** - 批量状态查询结果
- **DeviceOnlineStatusDTO** - 设备在线状态数据

**通用结构**:
- **RpcResult<T>** - 统一RPC返回结果封装

---

## 🚀 升级指南

### 从 1.0.1 升级到 1.0.2
```xml
<dependency>
    <groupId>com.colorlight.terminal</groupId>
    <artifactId>terminal-rpc-api</artifactId>
    <version>1.0.2</version>
</dependency>
```

**变更影响**:
- ✅ 零代码变更
- ✅ 直接引入新功能包
- ✅ 现有功能完全兼容

---

## 📚 开发参考

### 项目结构
```
terminal-rpc-api/
├── src/main/java/com/colorlight/terminal/rpc/
│   ├── dto/
│   │   ├── command/          # 指令相关DTO
│   │   ├── config/           # 配置相关DTO (1.0.2新增)
│   │   ├── enums/            # 枚举定义 (1.0.2增强)
│   │   ├── request/          # 请求DTO
│   │   ├── result/           # 返回结果DTO  
│   │   ├── status/           # 状态相关DTO
│   │   └── RpcResult.java    # 统一返回封装
│   └── service/              # RPC服务接口
│       ├── DeviceDataCleanupRpcService.java    # 1.0.2新增
│       ├── DeviceOnlineStatusRpcService.java
│       ├── TerminalAccountRpcService.java
│       └── TerminalCommandRpcService.java
└── pom.xml
```

### 技术规范
- **JDK**: 11+ (强制约束)
- **序列化**: 实现 Serializable 接口
- **构建工具**: Maven 3.6+
- **代码风格**: 无 Lombok，纯 JDK 实现
- **文档**: 完整 JavaDoc 注释

### 发布仓库
- **Release**: `http://192.168.1.83:8082/repository/maven-releases/`  
- **Snapshot**: `http://192.168.1.83:8082/repository/maven-snapshots/`

---
