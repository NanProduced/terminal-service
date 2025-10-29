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
    name: 'HTTP 请求响应时间',
    unit: 'ms',
    type: 'timing',
    description: '从发送 HTTP 请求到接收完整响应的总耗时'
  },
  'http_req_waiting': {
    name: 'HTTP 服务器处理时间',
    unit: 'ms',
    type: 'timing',
    description: '服务器处理请求并返回首字节的耗时（TTFB）'
  },
  'http_req_blocked': {
    name: 'HTTP 连接等待时间',
    unit: 'ms',
    type: 'timing',
    description: '等待可用连接或等待线程调度的时间'
  },
  'http_req_connecting': {
    name: 'HTTP 连接建立时间',
    unit: 'ms',
    type: 'timing',
    description: 'TCP 连接从发起到建立的耗时'
  },
  'http_req_tls_handshaking': {
    name: 'HTTPS TLS 握手时间',
    unit: 'ms',
    type: 'timing',
    description: 'HTTPS 连接 TLS 握手的耗时'
  },
  'http_req_sending': {
    name: 'HTTP 请求发送时间',
    unit: 'ms',
    type: 'timing',
    description: '客户端将请求体发送到服务器所需时间'
  },
  'http_req_receiving': {
    name: 'HTTP 响应接收时间',
    unit: 'ms',
    type: 'timing',
    description: '客户端接收服务器响应主体的耗时'
  },
  'http_req_failed': {
    name: 'HTTP 请求失败率',
    unit: 'percent',
    type: 'ratio',
    description: '返回非 2xx/3xx 或触发错误的请求占比'
  },
  'http_reqs': {
    name: 'HTTP 请求总数',
    unit: 'count',
    type: 'counter',
    description: '测试过程中发出的 HTTP 请求总数'
  },
  'iteration_duration': {
    name: '单次迭代耗时',
    unit: 'ms',
    type: 'timing',
    description: '一名虚拟用户完成完整脚本所需时间'
  },
  'iterations': {
    name: '迭代总数',
    unit: 'count',
    type: 'counter',
    description: '虚拟用户执行完成的脚本迭代总数'
  },
  'vus': {
    name: '当前虚拟用户数',
    unit: 'vu',
    type: 'counter',
    description: '当前正在运行的 k6 虚拟用户数量'
  },
  'vus_max': {
    name: '峰值虚拟用户数',
    unit: 'vu',
    type: 'counter',
    description: '测试执行期间达到的最大并发虚拟用户数'
  },
  'data_sent': {
    name: '发送数据总量',
    unit: 'bytes',
    type: 'storage',
    description: '客户端向服务端发送的数据总量'
  },
  'data_received': {
    name: '接收数据总量',
    unit: 'bytes',
    type: 'storage',
    description: '客户端从服务端接收的数据总量'
  },
  'checks': {
    name: '断言成功率',
    unit: 'percent',
    type: 'ratio',
    description: '所有断言检查中成功的比例'
  },
  'colorlight_status_reports_total': {
    name: '状态上报总数',
    unit: 'count',
    type: 'counter',
    description: 'Colorlight 终端上报状态的总次数'
  },
  'colorlight_status_report_failed': {
    name: '状态上报失败率',
    unit: 'percent',
    type: 'ratio',
    description: 'Colorlight 状态上报返回失败的占比'
  },
  'colorlight_status_report_duration': {
    name: '状态上报耗时',
    unit: 'ms',
    type: 'timing',
    description: '完成一次状态上报过程的耗时'
  },
  'ws_connecting': {
    name: 'WebSocket 连接建立时间',
    unit: 'ms',
    type: 'timing',
    description: 'WebSocket 连接从发起到建立完成的耗时'
  },
  'ws_session_duration': {
    name: 'WebSocket 会话持续时长',
    unit: 'ms',
    type: 'timing',
    description: 'WebSocket 会话保持连接的时长'
  },
  'ws_msg_sent': {
    name: 'WebSocket 消息发送数',
    unit: 'count',
    type: 'counter',
    description: '通过 WebSocket 发送的消息数量'
  },
  'ws_msg_received': {
    name: 'WebSocket 消息接收数',
    unit: 'count',
    type: 'counter',
    description: '通过 WebSocket 接收的消息数量'
  }
};

const BADGE_CLASS_MAP = {
  '优秀': 'success',
  '良好': 'success',
  '一般': 'warning',
  '差': 'danger',
  '暂无数据': 'warning'
};

function humanizeMetricName(name) {
  if (!name) return '';
  return name
    .split('_')
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function getMetricInfo(name) {
  return METRICS_EXPLANATIONS[name] || {
    name: humanizeMetricName(name),
    unit: '',
    description: ''
  };
}

function formatBytes(value) {
  if (value === null || value === undefined || Number.isNaN(value)) return 'N/A';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = Number(value);
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  const decimals = index === 0 ? 0 : 2;
  return `${size.toFixed(decimals)} ${units[index]}`;
}

function formatPercentage(value, decimals = 2) {
  if (value === null || value === undefined || Number.isNaN(value)) return 'N/A';
  return `${value.toFixed(decimals)}%`;
}

function formatWithUnit(value, info = {}, options = {}) {
  if (value === null || value === undefined || Number.isNaN(value)) return 'N/A';
  const unit = info.unit || '';
  let decimals;
  if (options.decimals !== undefined) {
    decimals = options.decimals;
  } else if (unit === 'count' || (typeof unit === 'string' && unit.toLowerCase() === 'vu')) {
    decimals = 0;
  } else {
    decimals = 2;
  }

  if (unit === 'ratio') {
    return formatPercentage(value * 100, options.decimals ?? 2);
  }

  if (unit === 'percent') {
    return formatPercentage(value, options.decimals ?? 2);
  }

  if (unit === 'bytes') {
    return formatBytes(value);
  }

  if (unit === 'count') {
    return Number(value).toLocaleString('zh-CN');
  }

  if (typeof unit === 'string' && unit.toLowerCase() === 'vu') {
    return `${Number(value).toLocaleString('zh-CN')} VU`;
  }

  // 时间单位动态化：ms > 1000 转换为秒
  if (unit === 'ms' && value >= 1000) {
    const seconds = value / 1000;
    return `${seconds.toFixed(2)} s`;
  }

  const formatted = formatNumber(value, decimals);
  return unit ? `${formatted} ${unit}` : formatted;
}

function getBadgeClass(level) {
  return BADGE_CLASS_MAP[level] || 'warning';
}

function getLevelClass(level) {
  switch (level) {
    case '优秀':
      return 'excellent';
    case '良好':
      return 'good';
    case '一般':
      return 'fair';
    case '差':
      return 'poor';
    default:
      return 'neutral';
  }
}

function evaluateByThreshold(value, ladder) {
  if (value === null || value === undefined || Number.isNaN(value) || !Array.isArray(ladder) || ladder.length === 0) {
    return { level: '暂无数据', score: 0 };
  }
  for (const item of ladder) {
    if (value <= item.limit) {
      return { level: item.level, score: item.score };
    }
  }
  return ladder[ladder.length - 1];
}

function determineOverallLevel(score) {
  if (!Number.isFinite(score) || score <= 0) {
    return '暂无数据';
  }
  if (score >= 90) return '优秀';
  if (score >= 80) return '良好';
  if (score >= 70) return '一般';
  return '差';
}

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
    if (!fs.existsSync(configPath)) {
      console.warn(`⚠️  配置文件不存在: ${configPath}`);
      return {};
    }
    const content = fs.readFileSync(configPath, 'utf8');
    const params = JSON.parse(content);
    return params;
  } catch (error) {
    console.error(`❌ 加载测试参数失败: ${configPath}`);
    console.error(`   错误信息: ${error.message}`);
    return {};
  }
}

/**
 * 解析metrics数据，提取关键指标
 *
 * 采样质量识别：
 * - 采样数 >= 30：高质量（使用百分位数）
 * - 采样数 10-29：中等质量（百分位数可能不准）
 * - 采样数 < 10：低质量（值会相同）
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
    const values = data.values.filter(v => typeof v === 'number' && v > 0);
    if (values.length === 0) {
      // 如果没有有效的 > 0 的值，保留原始值
      const allValues = data.values.filter(v => typeof v === 'number');
      if (allValues.length === 0) continue;

      // 计算基于所有值（包括 0）的统计
      allValues.sort((a, b) => a - b);
      const sum = allValues.reduce((a, b) => a + b, 0);
      const len = allValues.length;

      stats[name] = {
        min: allValues[0],
        max: allValues[len - 1],
        avg: sum / len,
        p50: allValues[Math.floor(len * 0.50)] || 0,
        p95: allValues[Math.floor(len * 0.95)] || 0,
        p99: allValues[Math.floor(len * 0.99)] || 0,
        count: len,
        sampling_quality: 'low' // 采样质量差
      };
      continue;
    }

    values.sort((a, b) => a - b);
    const sum = values.reduce((a, b) => a + b, 0);
    const len = values.length;

    // 评估采样质量
    let sampling_quality = 'high';
    if (len < 10) sampling_quality = 'low';
    else if (len < 30) sampling_quality = 'medium';

    stats[name] = {
      min: values[0],
      max: values[len - 1],
      avg: sum / len,
      p50: values[Math.floor(len * 0.50)] || 0,
      p95: values[Math.floor(len * 0.95)] || 0,
      p99: values[Math.floor(len * 0.99)] || 0,
      count: len,
      sampling_quality: sampling_quality // 采样质量标记
    };
  }

  return stats;
}

/**
 * 将指标按类型分类
 */
function classifyMetrics(stats) {
  const classified = {
    timing: {},     // 时间类：响应时间、耗时等
    counter: {},    // 计数类：请求数、次数等
    ratio: {},      // 比率类：成功率、失败率等
    storage: {},    // 存储类：数据量
    custom: {}      // 自定义业务指标
  };

  for (const [metricName, data] of Object.entries(stats)) {
    if (!data || (!data.count && data.avg === undefined)) continue; // 过滤空数据

    const info = getMetricInfo(metricName);
    const metricsType = info.type || 'counter';

    const metric = {
      key: metricName,
      name: info.name,
      unit: info.unit,
      description: info.description,
      data: data
    };

    // 按类型分类
    if (metricsType === 'timing') {
      classified.timing[metricName] = metric;
    } else if (metricsType === 'counter') {
      classified.counter[metricName] = metric;
    } else if (metricsType === 'ratio') {
      classified.ratio[metricName] = metric;
    } else if (metricsType === 'storage') {
      classified.storage[metricName] = metric;
    } else if (metricName.startsWith('colorlight_')) {
      classified.custom[metricName] = metric;
    }
  }

  return classified;
}

/**
 * 评估性能等级
 */
function evaluatePerformance(stats) {
  const items = [];

  const httpDuration = stats['http_req_duration'];
  if (httpDuration && httpDuration.p95 !== undefined) {
    const value = httpDuration.p95;
    const evaluation = evaluateByThreshold(value, [
      { limit: 100, score: 95, level: '优秀' },
      { limit: 200, score: 85, level: '良好' },
      { limit: 500, score: 70, level: '一般' },
      { limit: Infinity, score: 50, level: '差' }
    ]);

    items.push({
      metric: 'HTTP P95 响应时间',
      valueText: formatWithUnit(value, getMetricInfo('http_req_duration')),
      targetText: '目标 ≤ 500 ms',
      level: evaluation.level,
      score: evaluation.score,
      detail: `当前平均值 ${formatWithUnit(httpDuration.avg, getMetricInfo('http_req_duration'))}`
    });
  }

  const totalReqs = stats['http_reqs']?.count || 0;
  const failedReqs = stats['http_req_failed']?.count || 0;
  if (totalReqs > 0) {
    const failRate = (failedReqs / totalReqs) * 100;
    const evaluation = evaluateByThreshold(failRate, [
      { limit: 1, score: 95, level: '优秀' },
      { limit: 2, score: 85, level: '良好' },
      { limit: 5, score: 70, level: '一般' },
      { limit: Infinity, score: 50, level: '差' }
    ]);

    items.push({
      metric: 'HTTP 请求失败率',
      valueText: formatPercentage(failRate),
      targetText: '目标 ≤ 1%',
      level: evaluation.level,
      score: evaluation.score,
      detail: `失败 ${failedReqs.toLocaleString('zh-CN')} 次 / 总请求 ${totalReqs.toLocaleString('zh-CN')} 次`
    });
  }

  const statusTotal = stats['colorlight_status_reports_total']?.count || 0;
  const statusFailed = stats['colorlight_status_report_failed']?.count || 0;
  if (statusTotal > 0) {
    const statusFailRate = (statusFailed / statusTotal) * 100;
    const evaluation = evaluateByThreshold(statusFailRate, [
      { limit: 0.5, score: 95, level: '优秀' },
      { limit: 1, score: 85, level: '良好' },
      { limit: 3, score: 70, level: '一般' },
      { limit: Infinity, score: 50, level: '差' }
    ]);

    items.push({
      metric: '状态上报失败率',
      valueText: formatPercentage(statusFailRate),
      targetText: '目标 ≤ 1%',
      level: evaluation.level,
      score: evaluation.score,
      detail: `失败 ${statusFailed.toLocaleString('zh-CN')} 次 / 上报 ${statusTotal.toLocaleString('zh-CN')} 次`
    });
  }

  const statusDuration = stats['colorlight_status_report_duration']?.p95;
  if (statusDuration !== undefined) {
    const evaluation = evaluateByThreshold(statusDuration, [
      { limit: 500, score: 95, level: '优秀' },
      { limit: 800, score: 85, level: '良好' },
      { limit: 1200, score: 70, level: '一般' },
      { limit: Infinity, score: 50, level: '差' }
    ]);

    items.push({
      metric: '状态上报 P95 耗时',
      valueText: formatWithUnit(statusDuration, getMetricInfo('colorlight_status_report_duration')),
      targetText: '目标 ≤ 800 ms',
      level: evaluation.level,
      score: evaluation.score,
      detail: `平均耗时 ${formatWithUnit(stats['colorlight_status_report_duration']?.avg, getMetricInfo('colorlight_status_report_duration'))}`
    });
  }

  const hasData = items.length > 0;
  const avgScore = hasData ? items.reduce((sum, item) => sum + item.score, 0) / items.length : 0;
  const overallLevel = determineOverallLevel(avgScore);

  return {
    avgScore,
    overallLevel,
    hasData,
    items: items.map(item => ({ ...item, badgeClass: getBadgeClass(item.level) }))
  };
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
 * 生成时间类指标卡片
 */
function generateTimingMetricsSection(timingMetrics) {
  if (Object.keys(timingMetrics).length === 0) return '';

  const cards = Object.values(timingMetrics).map(metric => `
    <div class="card">
      <h3><span class="card-icon">⏱️</span>${metric.name}</h3>
      <div class="metric-row">
        <span class="metric-label">最小值</span>
        <span class="metric-value">${formatWithUnit(metric.data.min, metric)}</span>
      </div>
      <div class="metric-row">
        <span class="metric-label">平均值</span>
        <span class="metric-value">${formatWithUnit(metric.data.avg, metric)}</span>
      </div>
      <div class="metric-row">
        <span class="metric-label">50分位</span>
        <span class="metric-value">${formatWithUnit(metric.data.p50, metric)}</span>
      </div>
      <div class="metric-row">
        <span class="metric-label">95分位</span>
        <span class="metric-value">${formatWithUnit(metric.data.p95, metric)}</span>
      </div>
      <div class="metric-row">
        <span class="metric-label">99分位</span>
        <span class="metric-value">${formatWithUnit(metric.data.p99, metric)}</span>
      </div>
      <div class="metric-row">
        <span class="metric-label">最大值</span>
        <span class="metric-value">${formatWithUnit(metric.data.max, metric)}</span>
      </div>
    </div>
  `).join('');

  return `
    <div class="metrics-section">
      <h3>⏱️ 响应时间指标</h3>
      <div class="grid">${cards}</div>
    </div>
  `;
}

/**
 * 生成计数类指标卡片
 */
function generateCounterMetricsSection(counterMetrics) {
  if (Object.keys(counterMetrics).length === 0) return '';

  const cards = Object.values(counterMetrics).map(metric => `
    <div class="card">
      <h3><span class="card-icon">📊</span>${metric.name}</h3>
      <div class="metric-row">
        <span class="metric-label">总数</span>
        <span class="metric-value">${formatWithUnit(metric.data.count, { unit: 'count' })}</span>
      </div>
    </div>
  `).join('');

  return `
    <div class="metrics-section">
      <h3>📊 计数指标</h3>
      <div class="grid">${cards}</div>
    </div>
  `;
}

/**
 * 生成比率类指标卡片
 */
function generateRatioMetricsSection(ratioMetrics) {
  if (Object.keys(ratioMetrics).length === 0) return '';

  const cards = Object.values(ratioMetrics).map(metric => {
    let successRate, failureRate;

    if (metric.key === 'checks') {
      // checks 是成功率比例（0-1）
      successRate = (metric.data.avg * 100).toFixed(2);
      failureRate = (100 - successRate).toFixed(2);
    } else if (metric.key === 'http_req_failed' || metric.key === 'colorlight_status_report_failed') {
      // 这些是计数，需要计算失败率（这里使用平均值作为失败率%）
      failureRate = metric.data.avg.toFixed(2);
      successRate = (100 - failureRate).toFixed(2);
    } else {
      // 默认处理
      successRate = metric.data.avg.toFixed(2);
      failureRate = (100 - successRate).toFixed(2);
    }

    return `
      <div class="card">
        <h3><span class="card-icon">📈</span>${metric.name}</h3>
        <div class="metric-row">
          <span class="metric-label">成功率</span>
          <span class="metric-value" style="color: #10b981;">${successRate}%</span>
        </div>
        <div class="metric-row">
          <span class="metric-label">失败率</span>
          <span class="metric-value" style="color: #ef4444;">${failureRate}%</span>
        </div>
      </div>
    `;
  }).join('');

  return `
    <div class="metrics-section">
      <h3>📈 成功率指标</h3>
      <div class="grid">${cards}</div>
    </div>
  `;
}

/**
 * 生成数据量指标卡片
 */
function generateStorageMetricsSection(storageMetrics) {
  if (Object.keys(storageMetrics).length === 0) return '';

  const cards = Object.values(storageMetrics).map(metric => `
    <div class="card">
      <h3><span class="card-icon">💾</span>${metric.name}</h3>
      <div class="metric-row">
        <span class="metric-label">总量</span>
        <span class="metric-value">${formatWithUnit(metric.data.count, metric)}</span>
      </div>
    </div>
  `).join('');

  return `
    <div class="metrics-section">
      <h3>💾 数据量指标</h3>
      <div class="grid">${cards}</div>
    </div>
  `;
}

/**
 * 生成自定义业务指标卡片
 */
function generateCustomMetricsSection(customMetrics) {
  if (Object.keys(customMetrics).length === 0) return '';

  const cards = Object.values(customMetrics).map(metric => {
    // 判断指标属于哪个子类别
    let displays = [];
    if (metric.data.avg !== undefined) {
      displays.push(`
        <div class="metric-row">
          <span class="metric-label">平均值</span>
          <span class="metric-value">${formatWithUnit(metric.data.avg, metric)}</span>
        </div>
      `);
    }
    if (metric.data.p95 !== undefined) {
      displays.push(`
        <div class="metric-row">
          <span class="metric-label">95分位</span>
          <span class="metric-value">${formatWithUnit(metric.data.p95, metric)}</span>
        </div>
      `);
    }
    if (metric.data.count !== undefined && metric.data.count > 0) {
      displays.push(`
        <div class="metric-row">
          <span class="metric-label">采样数</span>
          <span class="metric-value">${formatWithUnit(metric.data.count, { unit: 'count' })}</span>
        </div>
      `);
    }

    return `
      <div class="card">
        <h3><span class="card-icon">🎯</span>${metric.name}</h3>
        ${displays.join('')}
      </div>
    `;
  }).join('');

  return `
    <div class="metrics-section">
      <h3>🎯 业务指标</h3>
      <div class="grid">${cards}</div>
    </div>
  `;
}

/**
 * 生成HTML报告
 */
function generateHtmlReport(stats, testParams, filePath, fileType) {
  const performance = evaluatePerformance(stats);
  const classified = classifyMetrics(stats);
  const timestamp = new Date().toLocaleString('zh-CN');
  const fileName = path.basename(filePath);
  const scoreValueText = performance.hasData ? performance.avgScore.toFixed(0) : '--';
  const scoreLevelText = performance.hasData ? performance.overallLevel : '暂无数据';
  const scoreLevelClass = `level-${getLevelClass(scoreLevelText)}`;

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

        .level-neutral {
            color: #6b7280;
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

        .metric-sub {
            font-size: 0.8em;
            color: #9ca3af;
            margin-top: 4px;
        }

        .metric-unit {
            font-size: 0.85em;
            color: #9ca3af;
            margin-left: 5px;
        }

        /* ==================== 配置验证提示 ==================== */
        .validation-section {
            background: #f0f4f8;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 30px;
        }

        .validation-section h3 {
            font-size: 1.3em;
            color: #f59e0b;
            margin-bottom: 15px;
            padding-bottom: 10px;
            border-bottom: 2px solid #f59e0b;
        }

        .validation-success {
            background: #d1fae5;
            border-left: 4px solid #10b981;
            padding: 15px;
            border-radius: 6px;
            color: #047857;
            font-weight: 600;
        }

        .warning-item {
            display: flex;
            gap: 15px;
            padding: 15px;
            margin-bottom: 12px;
            border-radius: 6px;
            border-left: 4px solid;
            background: white;
        }

        .warning-item.warning-warning {
            border-left-color: #ef4444;
            background: #fee2e2;
        }

        .warning-item.warning-info {
            border-left-color: #3b82f6;
            background: #dbeafe;
        }

        .warning-icon {
            font-size: 1.5em;
            flex-shrink: 0;
        }

        .warning-content {
            flex: 1;
        }

        .warning-title {
            font-weight: 600;
            margin-bottom: 5px;
            color: var(--dark);
        }

        .warning-item.warning-warning .warning-title {
            color: #991b1b;
        }

        .warning-item.warning-info .warning-title {
            color: #1e40af;
        }

        .warning-message {
            font-size: 0.95em;
            line-height: 1.5;
            color: #6b7280;
        }

        .warning-item.warning-warning .warning-message {
            color: #7c2d12;
        }

        .warning-item.warning-info .warning-message {
            color: #1e3a8a;
        }

        /* ==================== 详细配置信息 ==================== */
        .config-details {
            background: var(--light);
            border-radius: 8px;
            padding: 25px;
            margin-bottom: 40px;
        }

        .config-details h3 {
            font-size: 1.5em;
            color: var(--primary);
            margin-bottom: 20px;
            padding-bottom: 10px;
            border-bottom: 2px solid var(--primary);
        }

        .config-details details {
            background: white;
            border-radius: 6px;
            padding: 15px;
            margin-bottom: 15px;
            border-left: 4px solid var(--primary);
            cursor: pointer;
        }

        .config-details details summary {
            font-weight: 600;
            color: var(--dark);
            font-size: 1em;
            user-select: none;
            padding: 5px 0;
        }

        .config-details details[open] summary {
            margin-bottom: 15px;
        }

        .config-item {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            padding: 10px 0;
            border-bottom: 1px solid #f3f4f6;
        }

        .config-item:last-child {
            border-bottom: none;
        }

        .config-key {
            font-weight: 500;
            color: #6b7280;
            min-width: 200px;
            font-size: 0.95em;
        }

        .config-value {
            color: var(--primary);
            font-weight: 600;
            text-align: right;
            flex: 1;
            padding-left: 20px;
        }

        .stages-container {
            background: #f9fafb;
            border-radius: 4px;
            padding: 10px;
            margin: 8px 0;
        }

        .stage-item {
            padding: 8px;
            background: white;
            border-radius: 3px;
            margin-bottom: 6px;
            font-size: 0.9em;
            color: var(--dark);
            border-left: 3px solid var(--primary);
            padding-left: 10px;
        }

        .stage-item:last-child {
            margin-bottom: 0;
        }

        /* ==================== 指标分组 ==================== */
        .metrics-section {
            margin-bottom: 40px;
        }

        .metrics-section h3 {
            font-size: 1.5em;
            color: var(--primary);
            margin-bottom: 20px;
            padding-bottom: 10px;
            border-bottom: 2px solid var(--primary);
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .metrics-section .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
            gap: 20px;
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
            <p>性能指标详细分析</p>
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
                <div class="score-value">${scoreValueText}</div>
                <div class="score-level ${scoreLevelClass}">
                    ${scoreLevelText}
                </div>
            </div>

            <!-- 配置验证提示 -->
            ${generateValidationWarnings(testParams)}

            <!-- 详细配置信息 -->
            ${generateDetailedConfigSection(testParams)}

            <!-- 分类指标展示 -->
            ${generateTimingMetricsSection(classified.timing)}
            ${generateCounterMetricsSection(classified.counter)}
            ${generateRatioMetricsSection(classified.ratio)}
            ${generateStorageMetricsSection(classified.storage)}
            ${generateCustomMetricsSection(classified.custom)}

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
 * 生成详细的配置信息展示
 * 包括完整的配置树、阈值、特殊参数等
 */
function generateDetailedConfigSection(testParams) {
  if (!testParams || !testParams.scenarios) {
    return '';
  }

  const scenarios = testParams.scenarios || {};
  let currentScenario = null;
  let scenarioKey = '';

  // 找出当前启用的场景
  for (const [key, scen] of Object.entries(scenarios)) {
    if (key.startsWith('_') || typeof scen !== 'object' || scen === null) continue;
    if (scen.enabled !== false) {
      scenarioKey = key;
      currentScenario = scen;
      break;
    }
  }

  if (!currentScenario) return '';

  // 构建配置详情HTML
  let configHTML = `
    <div class="config-details">
        <h3>📋 完整配置参数</h3>
        <details open>
            <summary>基本信息</summary>
            <div class="config-item">
                <span class="config-key">场景标识:</span>
                <span class="config-value">${scenarioKey}</span>
            </div>
            <div class="config-item">
                <span class="config-key">场景名称:</span>
                <span class="config-value">${currentScenario.name || 'N/A'}</span>
            </div>
            <div class="config-item">
                <span class="config-key">设备范围:</span>
                <span class="config-value">#${testParams.deviceRange?.startNumber || 1}-${testParams.deviceRange?.endNumber || 'N/A'} (共 ${(testParams.deviceRange?.endNumber || 0) - (testParams.deviceRange?.startNumber || 1) + 1} 个)</span>
            </div>
            <div class="config-item">
                <span class="config-key">启用状态:</span>
                <span class="config-value">${currentScenario.enabled !== false ? '✅ 启用' : '❌ 禁用'}</span>
            </div>
        </details>
  `;

  // 基础负载配置
  if (currentScenario.basicLoad) {
    configHTML += `
        <details>
            <summary>基础负载阶段</summary>
            <div class="config-item">
                <span class="config-key">虚拟用户数(VU):</span>
                <span class="config-value">${currentScenario.basicLoad.vus || 'N/A'}</span>
            </div>
            <div class="config-item">
                <span class="config-key">持续时长:</span>
                <span class="config-value">${currentScenario.basicLoad.duration || 'N/A'}</span>
            </div>
            <div class="config-item">
                <span class="config-key">到达率(RPS):</span>
                <span class="config-value">${currentScenario.basicLoad.arrivalRate || 'N/A'} 请求/秒</span>
            </div>
        </details>
    `;
  } else if (currentScenario.mixedLoad) {
    configHTML += `
        <details>
            <summary>基础负载阶段</summary>
            <div class="config-item">
                <span class="config-key">虚拟用户数(VU):</span>
                <span class="config-value">${currentScenario.mixedLoad.vus || 'N/A'}</span>
            </div>
            <div class="config-item">
                <span class="config-key">持续时长:</span>
                <span class="config-value">${currentScenario.mixedLoad.duration || 'N/A'}</span>
            </div>
        </details>
    `;
  } else if (currentScenario.httpScenario) {
    configHTML += `
        <details>
            <summary>HTTP 请求配置</summary>
            <div class="config-item">
                <span class="config-key">预分配VU:</span>
                <span class="config-value">${currentScenario.httpScenario.preAllocatedVUs || 'N/A'}</span>
            </div>
            <div class="config-item">
                <span class="config-key">最大VU:</span>
                <span class="config-value">${currentScenario.httpScenario.maxVUs || 'N/A'}</span>
            </div>
            <div class="config-item">
                <span class="config-key">到达率(RPS):</span>
                <span class="config-value">${currentScenario.httpScenario.rate || 'N/A'} 请求/秒</span>
            </div>
            <div class="config-item">
                <span class="config-key">持续时长:</span>
                <span class="config-value">${currentScenario.httpScenario.duration || 'N/A'}</span>
            </div>
        </details>
    `;
  }

  // 峰值负载配置
  if (currentScenario.peakLoad) {
    const stages = currentScenario.peakLoad.stages || [];
    let stagesHTML = stages.map((s, i) =>
      `<div class="stage-item">阶段${i+1}: 时长 ${s.duration} → 目标VU ${s.target}</div>`
    ).join('');

    configHTML += `
        <details>
            <summary>峰值负载阶段</summary>
            <div class="config-item">
                <span class="config-key">开始时间:</span>
                <span class="config-value">${currentScenario.peakLoad.startTime || 'N/A'}</span>
            </div>
            <div class="config-item">
                <span class="config-key">分阶段增减VU:</span>
                <div class="stages-container">${stagesHTML}</div>
            </div>
        </details>
    `;
  } else if (currentScenario.peakBurst) {
    const stages = currentScenario.peakBurst.stages || [];
    let stagesHTML = stages.map((s, i) =>
      `<div class="stage-item">阶段${i+1}: 时长 ${s.duration} → 目标VU ${s.target}</div>`
    ).join('');

    configHTML += `
        <details>
            <summary>峰值突发阶段</summary>
            <div class="config-item">
                <span class="config-key">开始时间:</span>
                <span class="config-value">${currentScenario.peakBurst.startTime || 'N/A'}</span>
            </div>
            <div class="config-item">
                <span class="config-key">分阶段增减VU:</span>
                <div class="stages-container">${stagesHTML}</div>
            </div>
        </details>
    `;
  }

  // 性能阈值配置
  if (currentScenario.thresholds) {
    const thresholds = currentScenario.thresholds;
    configHTML += `
        <details>
            <summary>性能阈值</summary>
    `;

    for (const [key, value] of Object.entries(thresholds)) {
      if (key.startsWith('_')) continue;

      let displayValue = value;
      let displayKey = key.replace(/_/g, ' ').split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');

      if (key.includes('rate') && typeof value === 'number') {
        displayValue = `${(value * 100).toFixed(1)}%`;
      }

      configHTML += `
            <div class="config-item">
                <span class="config-key">${displayKey}:</span>
                <span class="config-value">${displayValue}</span>
            </div>
      `;
    }

    configHTML += `</details>`;
  }

  // WebSocket 特殊配置
  if (currentScenario.connectionConfig) {
    configHTML += `
        <details>
            <summary>WebSocket 连接配置</summary>
            <div class="config-item">
                <span class="config-key">每连接GPS条数:</span>
                <span class="config-value">${currentScenario.connectionConfig.gpsPerConnectionMin}-${currentScenario.connectionConfig.gpsPerConnectionMax}</span>
            </div>
            <div class="config-item">
                <span class="config-key">GPS 发送间隔:</span>
                <span class="config-value">${currentScenario.connectionConfig.gpsIntervalMin}-${currentScenario.connectionConfig.gpsIntervalMax} 秒</span>
            </div>
            <div class="config-item">
                <span class="config-key">连接间隔:</span>
                <span class="config-value">${currentScenario.connectionConfig.connectionDelayMin}-${currentScenario.connectionConfig.connectionDelayMax} 秒</span>
            </div>
        </details>
    `;
  }

  // 消息配置
  if (currentScenario.messageConfig) {
    configHTML += `
        <details>
            <summary>消息发送配置</summary>
            <div class="config-item">
                <span class="config-key">心跳间隔:</span>
                <span class="config-value">${currentScenario.messageConfig.heartbeatIntervalSeconds} 秒</span>
            </div>
            <div class="config-item">
                <span class="config-key">状态报告频率:</span>
                <span class="config-value">${currentScenario.messageConfig.statusReportFrequencySeconds} 秒</span>
            </div>
            <div class="config-item">
                <span class="config-key">传感器报告频率:</span>
                <span class="config-value">${currentScenario.messageConfig.sensorReportFrequencySeconds} 秒</span>
            </div>
        </details>
    `;
  }

  configHTML += `</div>`;
  return configHTML;
}

/**
 * 验证配置参数的合理性
 * 返回警告列表
 */
function validateConfigAndGetWarnings(testParams) {
  const warnings = [];

  if (!testParams || !testParams.scenarios) {
    return warnings;
  }

  const scenarios = testParams.scenarios || {};
  let currentScenario = null;
  let scenarioKey = '';

  // 找出当前启用的场景
  for (const [key, scen] of Object.entries(scenarios)) {
    if (key.startsWith('_') || typeof scen !== 'object' || scen === null) continue;
    if (scen.enabled !== false) {
      scenarioKey = key;
      currentScenario = scen;
      break;
    }
  }

  if (!currentScenario) return warnings;

  // 检查虚拟用户数
  let vus = 0;
  if (currentScenario.basicLoad?.vus) vus = currentScenario.basicLoad.vus;
  else if (currentScenario.mixedLoad?.vus) vus = currentScenario.mixedLoad.vus;
  else if (currentScenario.httpScenario?.preAllocatedVUs) vus = currentScenario.httpScenario.preAllocatedVUs;

  if (vus > 500) {
    warnings.push({
      level: 'warning',
      title: '虚拟用户数较大',
      message: `当前VU数为 ${vus}，可能导致服务器超载。建议 ≤ 500。`,
      icon: '⚠️'
    });
  } else if (vus > 300) {
    warnings.push({
      level: 'info',
      title: '虚拟用户数中等',
      message: `当前VU数为 ${vus}，处于中等负载。请监控服务器性能。`,
      icon: 'ℹ️'
    });
  }

  // 检查RPS（到达率）
  let rps = 0;
  if (currentScenario.basicLoad?.arrivalRate) rps = currentScenario.basicLoad.arrivalRate;
  else if (currentScenario.httpScenario?.rate) rps = currentScenario.httpScenario.rate;

  if (rps > 500) {
    warnings.push({
      level: 'warning',
      title: '请求速率较高',
      message: `当前RPS为 ${rps}，可能对服务器造成压力。建议 ≤ 500。`,
      icon: '⚠️'
    });
  }

  // 检查设备范围
  const deviceRange = testParams.deviceRange || {};
  const startNum = deviceRange.startNumber || 1;
  const endNum = deviceRange.endNumber || 500;
  const deviceCount = endNum - startNum + 1;

  if (deviceCount > 10000) {
    warnings.push({
      level: 'warning',
      title: '设备数量过多',
      message: `当前设备范围包含 ${deviceCount} 个设备，可能超过系统容量。建议 ≤ 10000。`,
      icon: '⚠️'
    });
  } else if (deviceCount > 5000) {
    warnings.push({
      level: 'info',
      title: '设备数量较大',
      message: `当前设备范围包含 ${deviceCount} 个设备，请确保系统有足够容量。`,
      icon: 'ℹ️'
    });
  }

  // 检查RPS与设备数的匹配度
  if (rps > 0 && rps > deviceCount) {
    warnings.push({
      level: 'info',
      title: 'RPS > 设备数',
      message: `RPS (${rps}) 大于设备数 (${deviceCount})，单个设备会收到多个请求。`,
      icon: 'ℹ️'
    });
  }

  return warnings;
}

/**
 * 生成配置验证警告展示
 */
function generateValidationWarnings(testParams) {
  const warnings = validateConfigAndGetWarnings(testParams);

  if (warnings.length === 0) {
    return `
      <div class="validation-section">
          <div class="validation-success">
              <span>✅ 配置验证通过</span> - 所有参数都在合理范围内
          </div>
      </div>
    `;
  }

  let warningHTML = `
    <div class="validation-section">
        <h3>⚠️ 配置提示</h3>
  `;

  for (const warning of warnings) {
    warningHTML += `
        <div class="warning-item warning-${warning.level}">
            <div class="warning-icon">${warning.icon}</div>
            <div class="warning-content">
                <div class="warning-title">${warning.title}</div>
                <div class="warning-message">${warning.message}</div>
            </div>
        </div>
    `;
  }

  warningHTML += `</div>`;
  return warningHTML;
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
