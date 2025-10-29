# Colorlight Terminal K6 性能测试工具


## 🚀 快速开始

### 前置要求

- **K6**：https://k6.io/docs/get-started/installation/
- **Node.js**：https://nodejs.org/ (v14 及以上)
- 网络连接到测试服务器

### 设备账号初始化

```bash
# 第一步：创建测试设备账号（一次性，耗时根据数量而定）
双击 CREATE-DEVICES.bat

# 脚本会自动：
# 1. 验证环境和配置
# 2. 显示创建参数
# 3. 实时显示创建进度和结果
# 4. 生成设备列表文件
```

### 运行测试

```bash
# 第二步：启动性能测试
双击 TEST.bat

# 在菜单中选择：
# [1] HTTP状态上报        (28分钟)
# [2] WebSocket V10连接   (18分钟)
# [3] WebSocket V11连接   (20分钟)
# [4] 混合负载            (20分钟)
# [5] 完整测试套件        (120分钟+)
```

### 查看结果

```bash
# 自动生成报告
# HTML报告会自动生成在 results/ 目录中
# 在浏览器中打开 *_report.html 文件查看详细的性能指标
# 报告包含：
# - 综合评分（总体性能评级）
# - 测试配置参数
# - HTTP/WebSocket详细指标
# - 性能评估结果
# - 优化建议

```

## ⚙️ 参数调整

所有测试参数都在配置文件中，**不要修改脚本**！

### 配置文件位置

| 配置文件 | 说明 |
|---------|------|
| `config/test-params.json` | ⭐ **测试参数**（VU、时长、压力等） |
| `config/server-config.json` | 服务器地址和API端点 |
| `config/device-config.json` | 设备创建认证和参数 |

### 修改测试参数

编辑 `config/test-params.json`，修改 VU 和时长：

```json
{
  "scenarios": {
    "status-report": {
      "basicLoad": {
        "vus": 167,              // ← 修改虚拟用户数
        "duration": "15m"        // ← 修改运行时长
      }
    }
  }
}
```

### 修改服务器地址

编辑 `config/server-config.json`：

```json
{
  "environments": {
    "test": {
      "baseUrl": "http://192.168.51.114:8088",
      "websocketUrl": "ws://192.168.51.114:8443/websocket"
    }
  }
}
```

### 更新设备创建 Token

编辑 `config/device-config.json`，更新 `token` 字段：

```json
{
  "authentication": {
    "token": "your-new-token-here"
  }
}
```

## 📁 文件说明

### 核心脚本

| 文件 | 说明 |
|------|------|
| `CREATE-DEVICES.bat` | 创建测试设备账号（首次必须执行） |
| `TEST.bat` | 性能测试主脚本，菜单驱动 |
| `README.md` | 此文件，使用说明 |

### 配置目录

| 文件 | 说明 |
|------|------|
| `config/test-params.json` | ⭐ 测试参数配置 |
| `config/server-config.json` | 服务器配置 |
| `config/device-config.json` | 设备创建配置 |

### 测试脚本

| 文件 | 说明 |
|------|------|
| `tests/01-status-report.js` | HTTP状态上报测试 |
| `tests/02-websocket-v10.js` | WebSocket V10连接测试 |
| `tests/03-websocket-v11.js` | WebSocket V11协议测试 |
| `tests/04-mixed-load.js` | 混合负载测试 |

### 工具脚本

| 文件 | 说明 | 版本 |
|------|------|------|
| `tools/create-devices.js` | 设备创建核心逻辑（支持实时交互和彩色输出） | ✅ v2.0 |
| `

**create-devices.js 特性（v2.0）**：
- 实时彩色输出（成功/失败/错误）
- 动态进度条和百分比
- 实时统计面板
- 详细的性能指标（吞吐量、成功率、平均响应时间）
- 支持扁平 JSON 格式的 API 调用

### 结果目录

| 目录 | 说明 |
|------|------|
| `results/` | 自动保存的测试结果 JSON 文件 |
| `reports/` | 自动生成的分析报告 |
| `logs/` | 执行日志（execution.log） |
| `devices/` | 创建的设备账号列表 |

## 📊 性能指标说明

| 指标 | 说明 | 参考值 |
|------|------|--------|
| **平均响应时间** | HTTP 请求平均耗时 | < 200ms |
| **P50 响应时间** | 50% 的请求在此时间内完成 | < 100ms |
| **P95 响应时间** | 95% 的请求在此时间内完成 | < 100-200ms |
| **P99 响应时间** | 99% 的请求在此时间内完成 | < 500ms |
| **错误率** | 失败请求占比 | < 1-2% |
| **成功率** | 成功请求占比 | > 98% |
| **吞吐量(RPS)** | 每秒请求数 | ≥目标值 |

### 性能等级评定

| 指标 | 优秀 ✅ | 良好 ✓ | 一般 ⚠️ | 差 ❌ |
|------|---------|---------|---------|-------|
| **P95响应时间** | < 100ms | < 200ms | < 500ms | > 500ms |
| **错误率** | < 0.5% | < 1% | < 5% | > 5% |
| **成功率** | > 99% | > 98% | > 95% | < 95% |

## ❓ 常见问题

### Q: 能修改脚本吗？
A: **不能！** 所有参数都在 `config/test-params.json` 中，只修改配置文件，不要改脚本。

### Q: 没有 Prometheus 怎么办？
A: 正常！本工具完全本地化分析，无需 Prometheus。测试结果保存为 JSON，自动分析生成报告。

### Q: 设备账号创建失败怎么办？
A: 脚本现在提供详细的错误信息，检查以下几点：
- `config/device-config.json` 中的 Token 是否有效
- `config/server-config.json` 中的服务器地址是否正确
- 网络连接是否正常
- **查看实时统计面板中的错误信息** - 脚本会显示具体的 API 返回错误
- 常见错误：
  - `用户名不能为空`：检查 accountPrefix 配置
  - `终端组ID不能为空`：检查 terminalGroup 配置
  - `连接超时`：检查网络和服务器地址
  - 参考 `docs/IMPROVEMENTS_v2.md` 了解更多详情

### Q: 如何中止测试？
A: 按 `Ctrl+C` 中止，已收集的数据会保存到 JSON 文件。

### Q: 结果文件在哪里？
A: 在 `results/` 目录，自动以时间戳命名。

### Q: 如何查看执行日志？
A: 查看 `logs/execution.log` 文件，记录了所有操作的时间和结果。

### Q: 能同时运行多个测试吗？
A: 可以，每个测试窗口独立运行。但建议在一个窗口中顺序执行，以避免资源竞争。

### Q: 测试参数太复杂？
A: 关键参数就这几个：
- `vus`：虚拟用户数
- `duration`：运行时长
- `rate`：吞吐量目标
- 其他参数使用默认值即可

### Q: 如何自定义测试？
A: 编辑 `config/test-params.json`，调整相应的参数，然后运行 TEST.bat 即可。

## 🔧 配置示例

### 示例 1：增加并发负载

修改 `config/test-params.json`，增加 VU 数量：

```json
{
  "status-report": {
    "basicLoad": {
      "vus": 333,              // 从 167 增加到 333
      "duration": "20m"        // 从 15m 增加到 20m
    }
  }
}
```

### 示例 2：快速压测（5 分钟）

```json
{
  "status-report": {
    "basicLoad": {
      "vus": 50,
      "duration": "5m"
    }
  }
}
```

### 示例 3：极限压力测试

```json
{
  "status-report": {
    "peakLoad": {
      "peakVUs": 500,          // 峰值 VU 增加到 500
      "stages": [
        {"duration": "1m", "target": 500},
        {"duration": "5m", "target": 500},
        {"duration": "1m", "target": 0}
      ]
    }
  }
}
```

## 📈 工作流程

```
┌─────────────────────────────────────────────────┐
│ 第一次使用                                      │
└─────────────────────────────────────────────────┘
  1. 创建设备账号
     └─> 双击 CREATE-DEVICES.bat (30-60分钟)
     └─> 等待完成，看到 ✅ 设备创建完成

  2. 准备配置
     └─> 修改 config/server-config.json (如需)
     └─> 修改 config/device-config.json (如需)

  3. 启动测试
     └─> 双击 TEST.bat
     └─> 选择测试场景 [1-5]
     └─> 等待测试完成

  4. 分析结果
     └─> 查看性能报告

┌─────────────────────────────────────────────────┐
│ 后续使用                                        │
└─────────────────────────────────────────────────┘
  1. 修改测试参数 (可选)
     └─> 编辑 config/test-params.json

  2. 启动测试
     └─> 双击 TEST.bat
     └─> 选择测试场景

  3. 分析结果
```

