#!/usr/bin/env node

/**
 * K6 性能测试结果分析工具
 * 分析JSON格式的k6测试结果，生成文本格式报告
 */

const fs = require('fs');
const path = require('path');

// ==================== 工具函数 ====================

/**
 * 加载JSON数据
 */
function loadJsonResults(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    const lines = content.trim().split('\n');

    const data = lines.map(line => {
      try {
        return JSON.parse(line);
      } catch (e) {
        return null;
      }
    }).filter(item => item !== null);

    return data;
  } catch (error) {
    console.error(`❌ 无法加载结果文件: ${filePath}`);
    console.error(error.message);
    process.exit(1);
  }
}

/**
 * 计算统计指标
 */
function calculateStats(values) {
  if (!values || values.length === 0) return null;

  const sorted = [...values].sort((a, b) => a - b);
  const len = sorted.length;

  return {
    count: len,
    min: sorted[0],
    max: sorted[len - 1],
    avg: sorted.reduce((a, b) => a + b, 0) / len,
    median: sorted[Math.floor(len / 2)],
    p50: sorted[Math.floor(len * 0.5)],
    p90: sorted[Math.floor(len * 0.9)],
    p95: sorted[Math.floor(len * 0.95)],
    p99: sorted[Math.floor(len * 0.99)]
  };
}

/**
 * 评估性能等级
 */
function ratePerformance(metric, value, type = 'responseTime') {
  if (type === 'responseTime') {
    if (value < 100) return '优秀 ✅';
    if (value < 200) return '良好 ✓';
    if (value < 500) return '一般 ⚠️';
    return '差 ❌';
  }
  if (type === 'errorRate') {
    if (value < 0.5) return '优秀 ✅';
    if (value < 1) return '良好 ✓';
    if (value < 5) return '一般 ⚠️';
    return '差 ❌';
  }
  return '未知';
}

/**
 * 生成进度条
 */
function generateProgressBar(current, total, width = 40) {
  const percentage = current / total;
  const filled = Math.round(width * percentage);
  const empty = width - filled;
  return '█'.repeat(filled) + '░'.repeat(empty);
}

/**
 * 格式化时间
 */
function formatDuration(seconds) {
  if (seconds < 60) return `${Math.round(seconds)}s`;
  const minutes = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${minutes}m${Math.round(secs)}s`;
}

/**
 * 格式化数字
 */
function formatNumber(num) {
  if (num >= 1000000) return (num / 1000000).toFixed(2) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(2) + 'K';
  return num.toFixed(2);
}

// ==================== 分析函数 ====================

function analyzeResults(inputFile) {
  console.log('\n╔════════════════════════════════════════════════════════════════╗');
  console.log('║          K6 性能测试分析报告                                ║');
  console.log('╚════════════════════════════════════════════════════════════════╝\n');

  const data = loadJsonResults(inputFile);
  if (data.length === 0) {
    console.error('❌ 结果文件为空');
    process.exit(1);
  }

  // 提取指标数据
  const metrics = {};
  let startTime = null;
  let endTime = null;

  for (const record of data) {
    if (record.type === 'Point') {
      const timestamp = new Date(record.data.time);
      if (!startTime || timestamp < startTime) startTime = timestamp;
      if (!endTime || timestamp > endTime) endTime = timestamp;

      const metricName = record.metric;
      if (!metrics[metricName]) {
        metrics[metricName] = [];
      }
      metrics[metricName].push(record.data.value);
    }
  }

  const testDuration = endTime && startTime ? (endTime - startTime) / 1000 : 0;

  // 计算HTTP指标
  const httpDurations = metrics['http_req_duration'] || [];
  const httpFailed = metrics['http_req_failed'] || [];
  const httpReqs = metrics['http_reqs'] || [];
  const wsConnecting = metrics['ws_connecting'] || [];
  const wsSessionDuration = metrics['ws_session_duration'] || [];

  const httpStats = calculateStats(httpDurations);
  const wsStats = calculateStats(wsConnecting);

  // 计算错误率
  let errorCount = 0;
  let successCount = 0;
  for (const val of httpFailed) {
    if (val > 0) errorCount++;
    else successCount++;
  }
  const errorRate = httpFailed.length > 0 ? (errorCount / httpFailed.length) * 100 : 0;
  const successRate = 100 - errorRate;

  // 计算吞吐量
  const totalRequests = httpReqs.length > 0 ? httpReqs.reduce((a, b) => a + b, 0) : 0;
  const rps = testDuration > 0 ? totalRequests / testDuration : 0;

  // ==================== 输出报告 ====================

  console.log('📊 测试概览');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log(`文件: ${path.basename(inputFile)}`);
  console.log(`运行时长: ${formatDuration(testDuration)}`);
  console.log(`开始时间: ${startTime ? startTime.toLocaleString() : 'N/A'}`);
  console.log(`结束时间: ${endTime ? endTime.toLocaleString() : 'N/A'}`);
  console.log(`总数据点: ${data.length}`);
  console.log('');

  if (httpStats) {
    console.log('🎯 HTTP响应时间指标 (毫秒)');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log(`平均响应时间  | ${httpStats.avg.toFixed(2)}ms      | ${ratePerformance('avg', httpStats.avg, 'responseTime')}`);
    console.log(`P50响应时间   | ${httpStats.p50.toFixed(2)}ms      | ${ratePerformance('p50', httpStats.p50, 'responseTime')}`);
    console.log(`P90响应时间   | ${httpStats.p90.toFixed(2)}ms      | ${ratePerformance('p90', httpStats.p90, 'responseTime')}`);
    console.log(`P95响应时间   | ${httpStats.p95.toFixed(2)}ms      | ${ratePerformance('p95', httpStats.p95, 'responseTime')}`);
    console.log(`P99响应时间   | ${httpStats.p99.toFixed(2)}ms      | ${ratePerformance('p99', httpStats.p99, 'responseTime')}`);
    console.log(`最小响应时间  | ${httpStats.min.toFixed(2)}ms`);
    console.log(`最大响应时间  | ${httpStats.max.toFixed(2)}ms`);
    console.log('');
  }

  console.log('📈 成功率和错误率');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log(`成功率: ${successRate.toFixed(2)}%`);
  console.log(`错误率: ${errorRate.toFixed(2)}%`);
  console.log(`成功请求: ${successCount}`);
  console.log(`失败请求: ${errorCount}`);
  console.log('');

  console.log('⚡ 吞吐量指标');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log(`总请求数: ${totalRequests.toFixed(0)}`);
  console.log(`吞吐量(RPS): ${rps.toFixed(2)} req/s`);
  console.log('');

  if (wsStats) {
    console.log('🔗 WebSocket连接指标 (毫秒)');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log(`平均连接时间 | ${wsStats.avg.toFixed(2)}ms`);
    console.log(`P95连接时间  | ${wsStats.p95.toFixed(2)}ms`);
    console.log(`P99连接时间  | ${wsStats.p99.toFixed(2)}ms`);
    console.log(`最小连接时间 | ${wsStats.min.toFixed(2)}ms`);
    console.log(`最大连接时间 | ${wsStats.max.toFixed(2)}ms`);
    console.log('');
  }

  // 性能总体评估
  console.log('🏆 性能总体评估');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

  let overallRating = '未知';
  let ratingPoints = 0;

  if (httpStats) {
    if (httpStats.p95 < 100 && errorRate < 1) {
      ratingPoints += 3;
    } else if (httpStats.p95 < 200 && errorRate < 2) {
      ratingPoints += 2;
    } else if (httpStats.p95 < 500 && errorRate < 5) {
      ratingPoints += 1;
    }
  }

  if (successRate >= 99) {
    ratingPoints += 2;
  } else if (successRate >= 95) {
    ratingPoints += 1;
  }

  if (rps >= 100) {
    ratingPoints += 1;
  }

  if (ratingPoints >= 5) {
    overallRating = '优秀 🌟🌟🌟';
  } else if (ratingPoints >= 3) {
    overallRating = '良好 🌟🌟';
  } else if (ratingPoints >= 1) {
    overallRating = '一般 🌟';
  } else {
    overallRating = '需要改进 ⚠️';
  }

  console.log(`总体评分: ${overallRating}`);
  console.log('');

  // 性能建议
  console.log('💡 性能建议');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

  if (httpStats && httpStats.p95 > 200) {
    console.log('⚠️  P95响应时间较长，考虑检查：');
    console.log('   - 服务器性能是否满足');
    console.log('   - 网络延迟是否过高');
    console.log('   - 数据库查询是否优化');
  }

  if (errorRate > 2) {
    console.log('⚠️  错误率较高，建议：');
    console.log('   - 检查服务器日志');
    console.log('   - 验证设备账号是否有效');
    console.log('   - 检查网络连接');
  }

  if (rps < 100) {
    console.log('⚠️  吞吐量较低，可能：');
    console.log('   - 虚拟用户不足，增加VU数量');
    console.log('   - 服务器有性能瓶颈');
  }

  if (httpStats && httpStats.p95 <= 100 && errorRate <= 1 && successRate >= 99) {
    console.log('✅ 性能表现优秀，无需改进！');
  }

  console.log('');

  // 保存报告
  const reportsDir = path.join(__dirname, '..', 'reports');
  if (!fs.existsSync(reportsDir)) {
    fs.mkdirSync(reportsDir, { recursive: true });
  }

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const reportPath = path.join(reportsDir, `${timestamp}_analysis.txt`);

  const reportContent = `Colorlight Terminal K6性能测试分析报告
生成时间: ${new Date().toLocaleString()}
分析文件: ${path.basename(inputFile)}

=== 测试概览 ===
运行时长: ${formatDuration(testDuration)}
开始时间: ${startTime ? startTime.toLocaleString() : 'N/A'}
结束时间: ${endTime ? endTime.toLocaleString() : 'N/A'}
总数据点: ${data.length}

${httpStats ? `=== HTTP响应时间指标 (毫秒) ===
平均响应时间: ${httpStats.avg.toFixed(2)}ms
P50: ${httpStats.p50.toFixed(2)}ms
P90: ${httpStats.p90.toFixed(2)}ms
P95: ${httpStats.p95.toFixed(2)}ms
P99: ${httpStats.p99.toFixed(2)}ms
最小: ${httpStats.min.toFixed(2)}ms
最大: ${httpStats.max.toFixed(2)}ms

` : ''}=== 成功率和错误率 ===
成功率: ${successRate.toFixed(2)}%
错误率: ${errorRate.toFixed(2)}%
成功请求: ${successCount}
失败请求: ${errorCount}

=== 吞吐量指标 ===
总请求数: ${totalRequests.toFixed(0)}
吞吐量: ${rps.toFixed(2)} req/s

${wsStats ? `=== WebSocket连接指标 (毫秒) ===
平均连接时间: ${wsStats.avg.toFixed(2)}ms
P95连接时间: ${wsStats.p95.toFixed(2)}ms
P99连接时间: ${wsStats.p99.toFixed(2)}ms
最小连接时间: ${wsStats.min.toFixed(2)}ms
最大连接时间: ${wsStats.max.toFixed(2)}ms

` : ''}=== 性能总体评估 ===
评分: ${overallRating}
`;

  fs.writeFileSync(reportPath, reportContent);
  console.log(`📁 报告已保存到: ${reportPath}`);
  console.log('');
}

// ==================== 主程序 ====================

const inputFile = process.argv[2];

if (!inputFile) {
  console.error('❌ 错误: 请指定结果文件路径');
  console.error('用法: node analyze-results.js <结果文件路径>');
  process.exit(1);
}

if (!fs.existsSync(inputFile)) {
  console.error(`❌ 错误: 文件不存在 ${inputFile}`);
  process.exit(1);
}

analyzeResults(inputFile);
