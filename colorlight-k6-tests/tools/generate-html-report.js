#!/usr/bin/env node

/**
 * K6 性能测试 - HTML报告生成器
 * 根据K6输出的metrics数据和test-params配置，生成美观的HTML测试报告
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// ==================== 常量定义 ====================

const METRICS_EXPLANATIONS = {
  'http_req_duration': {
    name: 'HTTP请求响应时间',
    unit: 'ms',
    description: '从发送HTTP请求到接收完整响应的耗时'
  },
  'http_req_failed': {
    name: 'HTTP请求失败',
    unit: 'count',
    description: '失败的HTTP请求次数'
  },
  'http_reqs': {
    name: 'HTTP总请求数',
    unit: 'count',
    description: '总共发送的HTTP请求数量'
  },
  'http_req_blocked': {
    name: 'HTTP请求阻塞时间',
    unit: 'ms',
    description: '等待可用连接的时间'
  },
  'http_req_connecting': {
    name: 'HTTP连接建立时间',
    unit: 'ms',
    description: 'TCP连接建立耗时'
  },
  'http_req_sending': {
    name: 'HTTP请求发送时间',
    unit: 'ms',
    description: '发送HTTP请求到服务器的耗时'
  },
  'http_req_receiving': {
    name: 'HTTP响应接收时间',
    unit: 'ms',
    description: '从服务器接收响应的耗时'
  },
  'ws_connecting': {
    name: 'WebSocket连接建立时间',
    unit: 'ms',
    description: 'WebSocket连接从发起到完全建立的耗时'
  },
  'ws_session_duration': {
    name: 'WebSocket会话持续时间',
    unit: 'ms',
    description: 'WebSocket连接维持的总时长'
  },
  'ws_msg_sent': {
    name: 'WebSocket消息发送数',
    unit: 'count',
    description: '通过WebSocket发送的消息总数'
  },
  'ws_msg_received': {
    name: 'WebSocket消息接收数',
    unit: 'count',
    description: '通过WebSocket接收的消息总数'
  }
};

// ==================== 工具函数 ====================

/**
 * 识别文件类型
 */
function identifyFileType(filePath) {
  if (filePath.endsWith('_metrics.ndjson')) {
    return 'metrics-ndjson';
  }
  if (filePath.endsWith('_summary.json')) {
    return 'summary-json';
  }
  return 'unknown';
}

/**
 * 加载结果文件
 */
function loadResultFile(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    const lines = content.trim().split('\n');
    return lines.map(line => {
      try {
        return JSON.parse(line);
      } catch (e) {
        return null;
      }
    }).filter(item => item !== null);
  } catch (error) {
    console.error(`❌ 加载文件失败: ${filePath}`);
    throw error;
  }
}

/**
 * 加载测试参数配置
 */
function loadTestParams() {
  const configPath = path.join(__dirname, '..', 'config', 'test-params.json');
  try {
    const content = fs.readFileSync(configPath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`❌ 加载测试参数失败: ${configPath}`);
    return {};
  }
}

/**
 * 解析metrics数据，提取关键指标
 */
function parseMetrics(data) {
  const metrics = {};
  const thresholds = {};

  for (const record of data) {
    if (record.type === 'Point' && record.metric) {
      const metricName = record.metric;
      if (!metrics[metricName]) {
        metrics[metricName] = {
          values: [],
          tags: {}
        };
      }
      metrics[metricName].values.push(record.data.value);
      if (record.data.tags) {
        metrics[metricName].tags = record.data.tags;
      }
    }
  }

  // 计算统计值
  const stats = {};
  for (const [name, data] of Object.entries(metrics)) {
    const values = data.values.filter(v => typeof v === 'number');
    if (values.length === 0) continue;

    values.sort((a, b) => a - b);
    const sum = values.reduce((a, b) => a + b, 0);
    const len = values.length;

    stats[name] = {
      min: values[0],
      max: values[len - 1],
      avg: sum / len,
      p50: values[Math.floor(len * 0.50)] || 0,
      p95: values[Math.floor(len * 0.95)] || 0,
      p99: values[Math.floor(len * 0.99)] || 0,
      count: len
    };
  }

  return stats;
}

/**
 * 评估性能等级
 */
function evaluatePerformance(stats, thresholds) {
  const scores = [];

  // P95响应时间评分
  const p95Duration = stats['http_req_duration']?.p95 || stats['ws_connecting']?.p95 || 0;
  if (p95Duration < 100) {
    scores.push({ metric: 'P95响应时间', score: 95, level: '优秀' });
  } else if (p95Duration < 200) {
    scores.push({ metric: 'P95响应时间', score: 85, level: '良好' });
  } else if (p95Duration < 500) {
    scores.push({ metric: 'P95响应时间', score: 70, level: '一般' });
  } else {
    scores.push({ metric: 'P95响应时间', score: 50, level: '差' });
  }

  // 错误率评分
  const failedRate = (stats['http_req_failed']?.count || 0) / (stats['http_reqs']?.count || 1);
  if (failedRate < 0.005) {
    scores.push({ metric: '错误率', score: 95, level: '优秀' });
  } else if (failedRate < 0.01) {
    scores.push({ metric: '错误率', score: 85, level: '良好' });
  } else if (failedRate < 0.05) {
    scores.push({ metric: '错误率', score: 70, level: '一般' });
  } else {
    scores.push({ metric: '错误率', score: 50, level: '差' });
  }

  const avgScore = scores.reduce((sum, s) => sum + s.score, 0) / scores.length;
  let overallLevel = '优秀';
  if (avgScore < 80) overallLevel = '良好';
  if (avgScore < 70) overallLevel = '一般';
  if (avgScore < 60) overallLevel = '差';

  return { avgScore, overallLevel, scores };
}

/**
 * 格式化数字
 */
function formatNumber(num, decimals = 2) {
  if (num === null || num === undefined) return 'N/A';
  if (typeof num !== 'number') return num;
  if (num > 1000000) return (num / 1000000).toFixed(decimals) + 'M';
  if (num > 1000) return (num / 1000).toFixed(decimals) + 'K';
  return num.toFixed(decimals);
}

/**
 * 生成HTML报告
 */
function generateHtmlReport(stats, testParams, filePath, fileType) {
  const performance = evaluatePerformance(stats, {});
  const timestamp = new Date().toLocaleString('zh-CN');
  const fileName = path.basename(filePath);

  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Colorlight K6 性能测试报告</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        :root {
            --primary: #2563eb;
            --primary-dark: #1e40af;
            --success: #10b981;
            --warning: #f59e0b;
            --danger: #ef4444;
            --dark: #1f2937;
            --light: #f9fafb;
            --border: #e5e7eb;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: var(--dark);
            padding: 20px;
            min-height: 100vh;
        }

        .container {
            max-width: 1400px;
            margin: 0 auto;
            background: white;
            border-radius: 12px;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
            overflow: hidden;
        }

        /* ==================== 页头 ==================== */
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }

        .header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
            font-weight: 700;
        }

        .header p {
            font-size: 1.1em;
            opacity: 0.9;
            margin-bottom: 20px;
        }

        .header-meta {
            display: flex;
            justify-content: center;
            gap: 40px;
            flex-wrap: wrap;
            font-size: 0.95em;
            opacity: 0.85;
        }

        .header-meta span {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        /* ==================== 主内容 ==================== */
        .content {
            padding: 40px;
        }

        /* ==================== 评分卡片 ==================== */
        .score-card {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border-radius: 12px;
            padding: 30px;
            text-align: center;
            margin-bottom: 40px;
            box-shadow: 0 10px 30px rgba(102, 126, 234, 0.2);
        }

        .score-value {
            font-size: 3.5em;
            font-weight: 700;
            margin: 10px 0;
        }

        .score-level {
            font-size: 1.5em;
            font-weight: 600;
            margin-top: 10px;
            text-transform: uppercase;
            letter-spacing: 2px;
        }

        .level-excellent {
            color: #10b981;
        }

        .level-good {
            color: #3b82f6;
        }

        .level-fair {
            color: #f59e0b;
        }

        .level-poor {
            color: #ef4444;
        }

        /* ==================== 网格布局 ==================== */
        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }

        .grid-2 {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }

        /* ==================== 卡片 ==================== */
        .card {
            background: white;
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 25px;
            transition: all 0.3s ease;
        }

        .card:hover {
            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
            border-color: var(--primary);
        }

        .card h3 {
            font-size: 1.3em;
            margin-bottom: 15px;
            color: var(--primary);
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .card-icon {
            font-size: 1.5em;
        }

        .metric-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 12px 0;
            border-bottom: 1px solid var(--border);
        }

        .metric-row:last-child {
            border-bottom: none;
        }

        .metric-label {
            display: flex;
            align-items: center;
            gap: 8px;
            color: #6b7280;
            font-size: 0.95em;
        }

        .metric-value {
            font-weight: 600;
            font-size: 1.1em;
            color: var(--primary);
        }

        .metric-unit {
            font-size: 0.85em;
            color: #9ca3af;
            margin-left: 5px;
        }

        /* ==================== 参数配置卡片 ==================== */
        .params-section {
            background: var(--light);
            border-radius: 8px;
            padding: 25px;
            margin-bottom: 40px;
        }

        .params-section h2 {
            font-size: 1.5em;
            color: var(--primary);
            margin-bottom: 20px;
            padding-bottom: 10px;
            border-bottom: 2px solid var(--primary);
        }

        .param-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
        }

        .param-item {
            background: white;
            padding: 15px;
            border-radius: 6px;
            border-left: 4px solid var(--primary);
        }

        .param-key {
            font-weight: 600;
            color: var(--dark);
            font-size: 0.95em;
            margin-bottom: 5px;
        }

        .param-value {
            color: var(--primary);
            font-size: 1.2em;
            font-weight: 700;
        }

        /* ==================== 指标详解表 ==================== */
        .metrics-table-section {
            background: var(--light);
            border-radius: 8px;
            padding: 25px;
            margin-bottom: 40px;
            overflow-x: auto;
        }

        .metrics-table-section h2 {
            font-size: 1.5em;
            color: var(--primary);
            margin-bottom: 20px;
            padding-bottom: 10px;
            border-bottom: 2px solid var(--primary);
        }

        table {
            width: 100%;
            border-collapse: collapse;
            background: white;
            border-radius: 8px;
            overflow: hidden;
        }

        thead {
            background: var(--primary);
            color: white;
        }

        th {
            padding: 15px;
            text-align: left;
            font-weight: 600;
            white-space: nowrap;
        }

        td {
            padding: 12px 15px;
            border-bottom: 1px solid var(--border);
        }

        tbody tr:hover {
            background: #f3f4f6;
        }

        /* ==================== 响应式 ==================== */
        @media (max-width: 768px) {
            .container {
                border-radius: 0;
            }

            .header {
                padding: 30px 20px;
            }

            .header h1 {
                font-size: 1.8em;
            }

            .header-meta {
                flex-direction: column;
                gap: 15px;
            }

            .content {
                padding: 20px;
            }

            .score-card {
                padding: 20px;
            }

            .score-value {
                font-size: 2.5em;
            }

            .grid, .grid-2 {
                grid-template-columns: 1fr;
            }

            th, td {
                padding: 10px;
                font-size: 0.9em;
            }

            .metric-label {
                flex-direction: column;
                align-items: flex-start;
            }
        }

        /* ==================== 页脚 ==================== */
        .footer {
            background: var(--light);
            border-top: 1px solid var(--border);
            padding: 20px 40px;
            text-align: center;
            color: #6b7280;
            font-size: 0.9em;
        }

        .footer a {
            color: var(--primary);
            text-decoration: none;
        }

        .footer a:hover {
            text-decoration: underline;
        }

        /* ==================== 工具类 ==================== */
        .badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 0.85em;
            font-weight: 600;
        }

        .badge-success {
            background: #d1fae5;
            color: #065f46;
        }

        .badge-warning {
            background: #fef3c7;
            color: #92400e;
        }

        .badge-danger {
            background: #fee2e2;
            color: #7f1d1d;
        }

        .info-tip {
            background: #eff6ff;
            border-left: 4px solid var(--primary);
            padding: 12px 15px;
            margin-bottom: 15px;
            border-radius: 4px;
            color: #1e40af;
            font-size: 0.95em;
        }

        .hidden {
            display: none;
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- 页头 -->
        <div class="header">
            <h1>🎯 Colorlight Terminal K6 性能测试报告</h1>
            <p>性能指标详细分析与优化建议</p>
            <div class="header-meta">
                <span>📅 ${timestamp}</span>
                <span>📊 ${fileType === 'metrics-ndjson' ? '完整指标' : '摘要数据'}</span>
                <span>📁 ${fileName}</span>
            </div>
        </div>

        <!-- 主内容 -->
        <div class="content">
            <!-- 总体评分卡片 -->
            <div class="score-card">
                <div>综合评分</div>
                <div class="score-value">${performance.avgScore.toFixed(0)}</div>
                <div class="score-level level-${performance.overallLevel === '优秀' ? 'excellent' : performance.overallLevel === '良好' ? 'good' : performance.overallLevel === '一般' ? 'fair' : 'poor'}">
                    ${performance.overallLevel}
                </div>
            </div>

            <!-- 测试参数 -->
            ${generateParamsSection(testParams)}

            <!-- 关键性能指标 -->
            <div class="grid-2">
                ${generateMetricsCard('HTTP 指标', stats)}
                ${generateWebSocketMetricsCard('WebSocket 指标', stats)}
            </div>

            <!-- 详细指标表 -->
            ${generateDetailedMetricsTable(stats)}

            <!-- 性能评分 -->
            <div class="params-section">
                <h2>📊 性能评估结果</h2>
                <div class="param-grid">
                    ${performance.scores.map(s => `
                        <div class="param-item">
                            <div class="param-key">${s.metric}</div>
                            <div class="param-value">${s.score}/100</div>
                            <div style="font-size: 0.9em; color: #6b7280; margin-top: 5px;">
                                <span class="badge badge-${s.score >= 80 ? 'success' : s.score >= 70 ? 'warning' : 'danger'}">${s.level}</span>
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>

            <!-- 建议 -->
            <div class="info-tip">
                <strong>💡 优化建议：</strong>
                ${generateRecommendations(stats, performance)}
            </div>
        </div>

        <!-- 页脚 -->
        <div class="footer">
            <p>生成于 ${timestamp} | Colorlight Terminal K6 压测工具 | <a href="#">查看文档</a></p>
        </div>
    </div>

    <script>
        // 自动打开浏览器预览（如果有window对象）
        window.addEventListener('load', function() {
            console.log('📊 性能测试报告已生成');
        });
    </script>
</body>
</html>`;

  return html;
}

/**
 * 生成参数配置部分
 */
function generateParamsSection(testParams) {
  // 提取第一个启用的场景参数
  const scenarios = testParams.scenarios || {};
  let scenarioName = '未知场景';
  let basicLoad = {};
  let peakLoad = {};

  for (const [key, scenario] of Object.entries(scenarios)) {
    if (scenario.enabled !== false) {
      scenarioName = scenario.name || key;
      basicLoad = scenario.basicLoad || scenario.mixedLoad || {};
      peakLoad = scenario.peakLoad || scenario.peakBurst || {};
      break;
    }
  }

  const deviceRange = testParams.deviceRange || {};

  return `
    <div class="params-section">
        <h2>⚙️ 测试配置参数</h2>
        <div class="param-grid">
            <div class="param-item">
                <div class="param-key">测试场景</div>
                <div class="param-value">${scenarioName}</div>
            </div>
            <div class="param-item">
                <div class="param-key">虚拟用户数</div>
                <div class="param-value">${basicLoad.vus || 'N/A'}</div>
            </div>
            <div class="param-item">
                <div class="param-key">运行时长</div>
                <div class="param-value">${basicLoad.duration || 'N/A'}</div>
            </div>
            <div class="param-item">
                <div class="param-key">设备范围</div>
                <div class="param-value">#${deviceRange.startNumber || 1}-${deviceRange.endNumber || 'N/A'}</div>
            </div>
            <div class="param-item">
                <div class="param-key">到达率(RPS)</div>
                <div class="param-value">${basicLoad.arrivalRate || basicLoad.rate || 'N/A'}</div>
            </div>
            <div class="param-item">
                <div class="param-key">峰值VU</div>
                <div class="param-value">${peakLoad.peakVUs || peakLoad.maxVUs || 'N/A'}</div>
            </div>
        </div>
    </div>
  `;
}

/**
 * 生成HTTP指标卡片
 */
function generateMetricsCard(title, stats) {
  const duration = stats['http_req_duration'] || {};
  const reqs = stats['http_reqs'] || {};
  const failed = stats['http_req_failed'] || {};

  const totalReqs = reqs.count || 0;
  const failedCount = failed.count || 0;
  const failRate = totalReqs > 0 ? ((failedCount / totalReqs) * 100).toFixed(2) : 0;

  return `
    <div class="card">
        <h3><span class="card-icon">📡</span>${title}</h3>
        <div class="metric-row">
            <span class="metric-label">平均响应时间</span>
            <span class="metric-value">${formatNumber(duration.avg)}<span class="metric-unit">ms</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">P50响应时间</span>
            <span class="metric-value">${formatNumber(duration.p50)}<span class="metric-unit">ms</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">P95响应时间</span>
            <span class="metric-value">${formatNumber(duration.p95)}<span class="metric-unit">ms</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">P99响应时间</span>
            <span class="metric-value">${formatNumber(duration.p99)}<span class="metric-unit">ms</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">总请求数</span>
            <span class="metric-value">${formatNumber(totalReqs, 0)}<span class="metric-unit">count</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">失败率</span>
            <span class="metric-value">${failRate}<span class="metric-unit">%</span></span>
        </div>
    </div>
  `;
}

/**
 * 生成WebSocket指标卡片
 */
function generateWebSocketMetricsCard(title, stats) {
  const wsConnecting = stats['ws_connecting'] || {};
  const wsMsgSent = stats['ws_msg_sent'] || {};
  const wsMsgReceived = stats['ws_msg_received'] || {};

  return `
    <div class="card">
        <h3><span class="card-icon">🔌</span>${title}</h3>
        <div class="metric-row">
            <span class="metric-label">连接建立时间(平均)</span>
            <span class="metric-value">${formatNumber(wsConnecting.avg)}<span class="metric-unit">ms</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">连接建立时间(P95)</span>
            <span class="metric-value">${formatNumber(wsConnecting.p95)}<span class="metric-unit">ms</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">消息发送数</span>
            <span class="metric-value">${formatNumber(wsMsgSent.count, 0)}<span class="metric-unit">count</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">消息接收数</span>
            <span class="metric-value">${formatNumber(wsMsgReceived.count, 0)}<span class="metric-unit">count</span></span>
        </div>
        <div class="metric-row">
            <span class="metric-label">连接成功数</span>
            <span class="metric-value">${formatNumber(wsConnecting.count, 0)}<span class="metric-unit">count</span></span>
        </div>
    </div>
  `;
}

/**
 * 生成详细指标表
 */
function generateDetailedMetricsTable(stats) {
  const rows = [];

  for (const [metricName, data] of Object.entries(stats)) {
    if (!data) continue;

    const info = METRICS_EXPLANATIONS[metricName] || { name: metricName, unit: '', description: '' };
    const isTime = data.avg && data.avg > 0;

    rows.push(`
        <tr>
            <td><strong>${info.name}</strong></td>
            <td>${info.description}</td>
            <td>${formatNumber(data.avg)}</td>
            <td>${formatNumber(data.p95)}</td>
            <td>${formatNumber(data.p99)}</td>
            <td>${formatNumber(data.count, 0)}</td>
        </tr>
    `);
  }

  return `
    <div class="metrics-table-section">
        <h2>📈 详细指标统计</h2>
        <table>
            <thead>
                <tr>
                    <th>指标名称</th>
                    <th>指标说明</th>
                    <th>平均值</th>
                    <th>P95</th>
                    <th>P99</th>
                    <th>样本数</th>
                </tr>
            </thead>
            <tbody>
                ${rows.join('')}
            </tbody>
        </table>
    </div>
  `;
}

/**
 * 生成建议
 */
function generateRecommendations(stats, performance) {
  const recommendations = [];

  const p95Duration = stats['http_req_duration']?.p95 || 0;
  if (p95Duration > 500) {
    recommendations.push('📌 响应时间较长，建议检查数据库查询性能和网络延迟');
  }

  const failRate = (stats['http_req_failed']?.count || 0) / (stats['http_reqs']?.count || 1);
  if (failRate > 0.05) {
    recommendations.push('📌 失败率较高，建议检查服务稳定性和错误日志');
  }

  if (recommendations.length === 0) {
    recommendations.push('✅ 性能表现良好，无重大优化建议');
  }

  return recommendations.map(r => `<br/>${r}`).join('');
}

// ==================== 主函数 ====================

function main() {
  const inputFile = process.argv[2];

  if (!inputFile) {
    console.error('❌ 用法: node generate-html-report.js <metrics-file>');
    process.exit(1);
  }

  if (!fs.existsSync(inputFile)) {
    console.error(`❌ 文件不存在: ${inputFile}`);
    process.exit(1);
  }

  console.log('📊 开始生成HTML报告...');
  console.log(`   📁 输入文件: ${inputFile}`);

  // 加载数据
  const fileType = identifyFileType(inputFile);
  const rawData = loadResultFile(inputFile);
  const testParams = loadTestParams();

  // 解析指标
  const stats = parseMetrics(rawData);

  // 生成HTML
  const htmlContent = generateHtmlReport(stats, testParams, inputFile, fileType);

  // 保存HTML文件
  const outputPath = inputFile.replace(/\.[^.]+$/, '.html');
  fs.writeFileSync(outputPath, htmlContent, 'utf8');

  console.log(`✅ HTML报告已生成: ${outputPath}`);

  // 自动打开浏览器
  try {
    const platform = process.platform;
    if (platform === 'win32') {
      execSync(`start "" "${outputPath}"`, { stdio: 'ignore' });
    } else if (platform === 'darwin') {
      execSync(`open "${outputPath}"`, { stdio: 'ignore' });
    } else if (platform === 'linux') {
      execSync(`xdg-open "${outputPath}"`, { stdio: 'ignore' });
    }
    console.log('🌐 已在浏览器中打开报告');
  } catch (error) {
    console.log(`ℹ️  请手动打开文件: ${outputPath}`);
  }

  process.exit(0);
}

main();
