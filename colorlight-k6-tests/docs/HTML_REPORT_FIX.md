# K6性能测试 - HTML报告生成故障修复

## 问题描述

### 症状
当运行TEST.bat脚本进行K6性能测试时，生成HTML报告时失败：

```
Error: Cannot create a string longer than 0x1fffffe8 characters
at Object.readFileSync (node:fs:448:20)
```

### 根本原因
Node.js的字符串大小限制为 **0x1fffffe8** 字符（约**536MB**）。当metrics NDJSON文件超过这个限制时，直接用 `fs.readFileSync()` 一次性读取整个文件到内存会失败。

你的测试生成了 **645MB** 的metrics文件，超过了此限制。

---

## 解决方案

### 实现方式：流式读取
已修改 `tools/generate-html-report.js` 中的 `loadResultFile()` 函数：

#### 前（同步读取）
```javascript
function loadResultFile(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');  // 一次性读取整个文件
  const lines = content.trim().split('\n');
  return lines.map(line => JSON.parse(line)).filter(item => item !== null);
}
```

#### 后（流式读取）
```javascript
function loadResultFile(filePath) {
  const readline = require('readline');
  return new Promise((resolve, reject) => {
    const fileStream = fs.createReadStream(filePath);
    const rl = readline.createInterface({ input: fileStream });
    const results = [];

    rl.on('line', (line) => {
      try {
        results.push(JSON.parse(line));
      } catch (e) { }
    });

    rl.on('close', () => resolve(results));
  });
}
```

### 优势
- ✅ **内存高效**：逐行读取，不加载整个文件到内存
- ✅ **支持超大文件**：可处理任意大小的metrics文件
- ✅ **实时反馈**：每处理50000行输出一次进度
- ✅ **错误恢复**：自动跳过损坏的JSON行

---

## 当前测试配置分析

### 测试规模（来自 test-params.json）

| 场景 | VU数 | 持续时间 | 设备数 | 概览 |
|------|------|--------|--------|------|
| HTTP状态上报 | 150 | 13分钟 | 500 | 10K设备/分钟上报 |
| WebSocket V10 | 400 | 10分钟 | 500 | 高频连接测试 |
| WebSocket V11 | 450 | 9分钟 | 500 | 长连接稳定性 |
| 混合负载 | 650+ | 20分钟 | 500 | 三协议并发 |

### 你当前的运行
```
基础负载：20 VU × 1分钟
峰值负载：150 VU × 6分钟（总共13分钟）
设备范围：1-500（500个虚拟设备）
```

### 为什么生成了645MB的文件
- **测试时长**：13分钟的持续测试
- **VU数量**：150个并发用户
- **数据密集**：每个请求都记录完整的性能指标
- **指标项目**：K6默认记录20+个指标（响应时间、连接建立时间、TLS握手等）

**粗略计算**：
```
RPS（平均）= 20-150 VU × 请求频率 ≈ 2000-15000 RPS
总请求数 = 大约 121935 次（从日志看）
每条NDJSON行 ≈ 500-1000 字节
总数据量 = 121935 × 1000 ≈ 645MB ✓
```

---

## 预防措施

### 方案 1：减少测试规模（最简单）
编辑 `config/test-params.json` 中的 `status-report` 场景：

```json
"basicLoad": {
  "duration": "30s",      // 从 1m 改为 30s
  "vus": 10              // 从 20 改为 10
},
"peakLoad": {
  "peakVUs": 75          // 从 150 改为 75
}
```

**预期结果**：metrics文件约 ≈ 100-150MB

### 方案 2：分割测试
运行多个较小的测试，而不是一个大的测试：

```bash
# 运行3个5分钟的测试，而不是1个15分钟的测试
TEST.bat --duration 5m --scenario status-report --run 1
TEST.bat --duration 5m --scenario status-report --run 2
TEST.bat --duration 5m --scenario status-report --run 3
```

### 方案 3：数据压缩
可以在K6脚本中配置metrics收集采样率（仅收集10%的数据）：

```javascript
// 在k6脚本中
export const options = {
  ext: {
    loadimpact: {
      compression: 'gzip',  // 压缩metrics数据
      aggregationCalcInterval: '10s'  // 减少上报频率
    }
  }
};
```

---

## 验证修复

运行以下命令测试新的报告生成脚本：

```bash
cd colorlight-k6-tests

# 方式1：直接运行报告生成器（如果你有旧的metrics文件）
node tools/generate-html-report.js results/your_metrics.ndjson

# 方式2：运行完整的测试流程（更推荐）
# Windows
TEST.bat

# Linux/Mac
bash test.sh
```

### 预期输出
```
📊 开始生成HTML报告...
   📁 输入文件: results/20251029-142603_scenario1_metrics.ndjson
   📖 已处理 50000 行...
   📖 已处理 100000 行...
   ✓ 共处理 121935 行，解析 121935 条记录
   ✅ HTML报告已生成: results/20251029-142603_scenario1_metrics.html
```

---

## 技术细节

### Node.js字符串限制的由来

Node.js使用V8 JavaScript引擎，V8中的字符串最大长度受到以下限制：

```
MAX_STRING_LENGTH = 2^29 - 24 = 536870888 字符
                               ≈ 536 MB (UTF-8)
```

这是JavaScript引擎层面的硬限制，不能在Node.js配置中调整。

### 流式读取的优势

| 方法 | 内存占用 | 读取速度 | 适用场景 |
|------|--------|--------|--------|
| readFileSync | O(n) 全文 | 快（一次） | <500MB文件 |
| readline流式 | O(1) 单行 | 中等 | 任意大小 |
| 内存数据库 | 高 | 最快 | 频繁查询 |

对于这个场景，**readline流式读取**是最优选择。

---

## 相关配置文件

- **测试参数**：`config/test-params.json`
- **HTTP测试脚本**：`tests/01-status-report.js`
- **WebSocket测试脚本**：`tests/02-websocket-v10.js`, `tests/03-websocket-v11.js`
- **混合负载脚本**：`tests/04-mixed-load.js`
- **报告生成器**：`tools/generate-html-report.js`（已修复）

---

## 后续建议

1. **监控metrics文件大小**
   - 在TEST.bat中添加文件大小检查
   - 如果超过500MB，自动警告或切换为压缩模式

2. **优化metrics收集**
   - 使用K6的 `summary` API 而不是导出所有metrics
   - 配置合理的 `aggregationCalcInterval` 减少数据点

3. **增加磁盘空间预留**
   - 运行完整测试前检查磁盘剩余空间
   - 至少预留1GB用于metrics和HTML报告

4. **自动清理旧报告**
   - 在TEST.bat中添加逻辑自动删除7天前的报告
   - 防止results目录无限增长

---

## 联系支持

如果仍然遇到问题：
1. 检查 `results/` 目录的磁盘空间
2. 验证Node.js版本（建议 v14.0+）
3. 查看TEST.bat的完整输出日志
4. 检查防杀毒软件是否拦截文件操作

