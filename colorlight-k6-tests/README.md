# Colorlight Terminal K6 性能测试工具

K6 分布式压力测试框架，用于 Colorlight Terminal 设备管理服务的性能验证。支持 HTTP REST API、WebSocket 长连接、混合协议等多种测试场景。

## 📋 快速开始

### 前置要求

- **K6**（v0.40+）：[安装指南](https://k6.io/docs/get-started/installation/)
- **Node.js**（v14+）：[下载地址](https://nodejs.org/)
- 网络连接到测试服务器

### 首次使用（3 个步骤）

#### 第一步：初始化设备账号（一次性）

```bash
双击运行 CREATE-DEVICES.bat
```

脚本会自动：
- ✅ 验证 K6 和 Node.js 环境
- ✅ 从 `config/device-config.json` 读取设备创建参数
- ✅ 创建指定范围的测试设备账号
- ✅ 输出实时进度和统计信息
- ✅ 生成设备列表文件

> 💡 **耗时**: 500 个设备约需 30-60 分钟，取决于网络和服务器性能

#### 第二步：启动性能测试

```bash
双击运行 TEST.bat
```

交互菜单会显示：

```
请选择要执行的压测场景:

[1] HTTP状态上报测试 - 约 3 分钟
[2] WebSocket V10频繁连接测试 - 约 2 分 30 秒
[3] WebSocket V11协议测试 - 约 2 分 30 秒
[4] 混合负载测试 - 约 20 分钟
[5] 压测基准 - 约 37 分钟

[Q] 退出脚本
```

> ✨ **智能菜单**: 预估时间**自动计算**，修改 `config/test-params.json` 后实时更新

#### 第三步：查看测试报告

测试完成后，在 `results/` 目录中找到对应的 HTML 报告：

```
results/
├── 20250101-120000_scenario1_metrics.html  ← 打开此文件查看报告
├── 20250101-120000_scenario1_metrics.ndjson
└── ...
```

报告包含：
- 📊 HTTP/WebSocket 详细指标统计
- 📈 响应时间分布（min/avg/P50/P95/P99/max）
- ✅ 性能测试阈值评估
- ⚙️ 完整的测试配置参数
- ⚠️ 配置验证警告（VU 过高、RPS 异常等）

---

## ⚙️ 参数调整

### 配置文件结构

| 文件 | 说明 | 编辑频率 |
|------|------|--------|
| `config/test-params.json` | ⭐ **主要配置**：VU、时长、RPS、设备范围 | 常修改 |
| `config/server-config.json` | 服务器地址、API 端点、WebSocket URL | 偶尔 |
| `config/device-config.json` | 设备创建认证 Token、账号范围、请求间隔 | 偶尔 |

### 快速修改：调整测试负载

编辑 `config/test-params.json`：

```json
{
  "scenarios": {
    "status-report": {
      "basicLoad": {
        "vus": 15,              // ← 虚拟用户数（并发数）
        "duration": "1m",       // ← 运行时长
        "arrivalRate": 15       // ← 吞吐量(RPS)
      },
      "peakLoad": {
        "startTime": "1m",      // ← 峰值开始时间
        "stages": [
          {"duration": "1m", "target": 50},   // ← 阶段持续时间
          {"duration": "1m", "target": 100}
        ]
      }
    }
  }
}
```

修改后 **TEST.bat 菜单会自动更新** 预估时间！

### 常见调整示例

#### 示例 1：增加并发负载
```json
"basicLoad": {
  "vus": 50,          // 增加虚拟用户
  "duration": "3m"    // 延长运行时间
}
```

#### 示例 2：降低测试强度
```json
"basicLoad": {
  "vus": 5,           // 减少虚拟用户
  "duration": "1m"    // 缩短时间
}
```

#### 示例 3：修改设备范围
```json
{
  "deviceRange": {
    "startNumber": 1,
    "endNumber": 1000   // 使用前 1000 个设备
  }
}
```

---

## 📁 文件说明

### 启动脚本

| 文件 | 说明 |
|------|------|
| `CREATE-DEVICES.bat` | 创建测试设备账号（首次必须运行） |
| `TEST.bat` | 性能测试主脚本，交互菜单式 |
| `ANALYZE.bat` | 结果分析脚本（可选） |

### 测试脚本

| 文件 | 场景 | K6 运行时间 |
|------|------|-----------|
| `tests/01-status-report.js` | HTTP 状态上报 | **3 分钟** |
| `tests/02-websocket-v10.js` | WebSocket V10 高频连接 | **2 分 30 秒** |
| `tests/03-websocket-v11.js` | WebSocket V11 长连接 | **2 分 30 秒** |
| `tests/04-mixed-load.js` | HTTP + WebSocket 混合 | **20 分钟** |

### 工具脚本（tools/）

| 文件 | 用途 | 说明 |
|------|------|------|
| `create-devices.js` | 设备创建 | 实时交互式设备创建引擎 |
| `calculate-duration.js` | 时间计算 | 根据配置动态计算 K6 运行时长 |
| `generate-menu.js` | 菜单生成 | TEST.bat 动态菜单项生成 |
| `generate-html-report.js` | 报告生成 | JSON 指标转换为 HTML 报告 |
| `analyze-results.js` | 结果分析 | 批量分析测试结果（可选） |

### 配置文件（config/）

| 文件 | 内容 |
|------|------|
| `test-params.json` | 4 个测试场景的完整参数配置 |
| `server-config.json` | 服务器地址、API 端点 |
| `device-config.json` | 设备创建认证、账号范围 |

### 输出目录

| 目录 | 说明 |
|------|------|
| `results/` | 原始 K6 指标数据（NDJSON 格式） |
| `logs/` | 执行日志 |
| `reports/` | 分析报告（若使用 ANALYZE.bat） |
| `devices/` | 创建的设备列表（若使用 CREATE-DEVICES.bat） |

---

## 📊 性能指标说明

### HTTP 测试指标

| 指标 | 说明 | 良好范围 |
|------|------|--------|
| **http_req_duration** | HTTP 请求响应时间 | < 200ms (P95) |
| **http_reqs_count** | 总请求数 | ≥ 阈值 |
| **http_req_failed_rate** | 请求失败率 | < 2% |
| **http_req_duration_p95** | P95 响应时间 | < 200ms |
| **http_req_duration_p99** | P99 响应时间 | < 500ms |

### WebSocket 测试指标

| 指标 | 说明 | 良好范围 |
|------|------|--------|
| **ws_connecting** | 连接建立时间 | < 1000-1200ms (P95) |
| **ws_connection_failed** | 连接失败率 | < 10% |
| **gps_success_rate** | GPS 发送成功率 | > 95% |
| **command_ack_latency** | 命令确认延迟 | < 800ms (P95) |

### 性能评级

| 等级 | P95 响应时间 | 错误率 | 成功率 |
|------|------------|--------|--------|
| 🟢 **优秀** | < 100ms | < 0.5% | > 99% |
| 🟡 **良好** | < 200ms | < 1% | > 98% |
| 🟠 **一般** | < 500ms | < 5% | > 95% |
| 🔴 **差** | > 500ms | > 5% | < 95% |

---

## ❓ 常见问题

### Q: 预估时间为什么会变化？
**A:** TEST.bat 菜单中的预估时间是 **动态计算** 的。修改 `config/test-params.json` 后，菜单会自动根据新配置重新计算并显示预估时间。

### Q: K6 脚本运行时间和菜单显示时间为什么不同？
**A:** 菜单显示的是 **K6 脚本的纯运行时间**（从启动到结束）。包括：
- 基础负载阶段（basicLoad.duration）
- 峰值负载阶段（sum of peakLoad.stages[].duration）

**不包括**：
- 脚本启动/初始化时间
- 设备连接/认证时间

### Q: 修改了参数为什么菜单没更新？
**A:** 确保：
1. ✅ 保存了 `config/test-params.json`
2. ✅ JSON 格式正确（使用 JSON 验证工具）
3. ✅ 时间值格式正确：`"1m"`、`"30s"`、`"2h"`

### Q: 报告中看不到某些配置参数？
**A:** HTML 报告会显示 **所有配置参数**。如果看不到：
1. 检查 JSON 中是否使用了注释键（`_comment`, `_note`）- 这些会被忽略
2. 刷新浏览器
3. 检查浏览器控制台是否有错误

### Q: 如何理解 WebSocket 的 stages 配置？
**A:** `stages` 表示分阶段改变虚拟用户数：

```json
"stages": [
  {"duration": "1m", "target": 40},    // 第1分钟：升高到40个VU
  {"duration": "30s", "target": 60}    // 第2分钟30秒：升高到60个VU
]
```

总运行时间 = 1m + 30s = **1.5 分钟**

### Q: mixed-load 场景的时间怎么计算？
**A:** mixed-load 中 HTTP 和 WebSocket **并发执行**，取最长时间：

```json
"httpScenario": {
  "duration": "20m"  // HTTP 运行 20 分钟
},
"websocketScenarios": {
  "v10": {
    "stages": [{"duration": "3m"}, {"duration": "14m"}, {"duration": "3m"}]  // 总 20 分钟
  }
}
// 并发运行，取最长 = 20 分钟
```

### Q: 能同时运行多个 TEST.bat 吗？
**A:** 可以，但建议**顺序运行**。每个测试窗口独立，但会共享：
- 数据库资源
- 设备连接池
- 网络带宽

可能导致结果不准确。

### Q: 中途中止测试怎么办？
**A:** 按 `Ctrl+C` 中止，已收集的数据会保存到 `results/` 目录。

### Q: 结果文件在哪里？
**A:**
- 指标数据：`results/TIMESTAMP_scenarioN_metrics.ndjson`
- HTML 报告：`results/TIMESTAMP_scenarioN_metrics.html`（自动生成）

### Q: 如何调试脚本问题？
**A:** 查看日志：
- Windows 控制台输出（彩色，实时）
- `logs/execution.log`（完整记录）

### Q: 没有 Prometheus 怎么样？
**A:** 本工具**完全独立**，无需外部监控系统。所有数据本地存储和分析，生成自包含的 HTML 报告。

### Q: 能修改脚本吗？
**A:** **不建议修改脚本**。所有参数都在 `config/test-params.json` 中，通过修改配置文件即可调整所有行为。

---

## 🔄 工作流程

### 第一次使用

```
1️⃣  创建设备账号
    └─ 双击 CREATE-DEVICES.bat
    └─ 等待完成（可能需要 30-60 分钟）

2️⃣  准备配置（可选）
    └─ 如需修改，编辑 config/*.json

3️⃣  启动测试
    └─ 双击 TEST.bat
    └─ 选择测试场景 [1-5]
    └─ 等待完成（预估时间见菜单）

4️⃣  分析报告
    └─ 用浏览器打开 results/*.html
    └─ 查看详细指标和配置
```

### 后续测试

```
1️⃣  修改配置（可选）
    └─ 编辑 config/test-params.json
    └─ 菜单会自动更新预估时间

2️⃣  启动测试
    └─ 双击 TEST.bat
    └─ 选择测试场景

3️⃣  分析结果
    └─ 浏览器打开报告
```

---

## 🚀 性能调优建议

### 当测试失败时（错误率或响应时间超过阈值）

1. **检查配置中的 thresholds**（性能阈值）
   ```json
   "thresholds": {
     "http_req_duration_p95": 200,    // 95% 请求响应时间 < 200ms
     "http_req_failed_rate": 0.02     // 失败率 < 2%
   }
   ```

2. **逐步降低测试强度**
   - 减少 `vus`（虚拟用户数）
   - 缩短 `duration`（运行时长）
   - 降低 `arrivalRate`（吞吐量）

3. **检查服务器状态**
   - CPU、内存、网络状况
   - 日志中是否有错误

4. **调整 timeout 和 keepalive**
   ```json
   "connectionConfig": {
     "connectionDelayMin": 2,   // 增加连接延迟
     "connectionDelayMax": 5
   }
   ```

---


