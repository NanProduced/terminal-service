#!/usr/bin/env node

/**
 * K6 压测时间计算工具
 * 用途：
 *   1. 计算TEST.bat菜单中的预估测试时间
 *   2. 为HTML报告提供时间相关数据
 *
 * 使用方式：
 *   node calculate-duration.js <scenario-name> [config-path]
 *   node calculate-duration.js status-report
 *   node calculate-duration.js all
 */

const fs = require('fs');
const path = require('path');

// ==================== 常量定义 ====================

const SYSTEM_OVERHEAD = {
  INITIALIZATION: 8,    // 设备初始化、连接、环境准备（分钟）
  PROCESSING: 2,        // K6运行、数据处理（分钟）
  BUFFER: 2             // 系统缓冲和波动（分钟）
};

const SCENARIO_NAMES = {
  'status-report': 'HTTP状态上报',
  'websocket-v10': 'WebSocket V10 压测',
  'websocket-v11': 'WebSocket V11 压测',
  'mixed-load': '混合场景压测'
};

// ==================== 时间解析和计算 ====================

/**
 * 将时间字符串解析为分钟
 * @param {string} timeStr - 时间字符串，如 "1m", "30s", "2h"
 * @returns {number} 分钟数
 * @example
 *   parseTimeString("1m") → 1
 *   parseTimeString("30s") → 0.5
 *   parseTimeString("2h") → 120
 */
function parseTimeString(timeStr) {
  if (!timeStr || typeof timeStr !== 'string') {
    return 0;
  }

  const match = timeStr.trim().match(/^(\d+(?:\.\d+)?)\s*([smh])$/);
  if (!match) {
    console.warn(`⚠️  无法解析时间字符串: "${timeStr}"`);
    return 0;
  }

  const value = parseFloat(match[1]);
  const unit = match[2];

  switch (unit) {
    case 's': return value / 60;       // 秒转分钟
    case 'm': return value;             // 分钟
    case 'h': return value * 60;        // 小时转分钟
    default: return 0;
  }
}

/**
 * 从配置对象中提取所有时间字段并计算总时长
 * 注意：对于mixed-load场景，HTTP和WebSocket并发执行，取最长时长而非求和
 * @param {object} config - 配置对象（整个test-params.json或单个场景）
 * @returns {number} 总时长（分钟）
 */
function calculateConfigDuration(config) {
  if (!config || typeof config !== 'object') {
    return 0;
  }

  // 检查是否为mixed-load场景（有httpScenario字段表示）
  const isMixedLoad = config.httpScenario !== undefined;

  if (isMixedLoad) {
    // mixed-load: HTTP和WebSocket并发执行，取最长时长
    const httpDuration = parseTimeString(config.httpScenario?.duration || '0m');

    // WebSocket V10的总时长
    let v10Duration = 0;
    if (config.websocketScenarios?.v10?.stages) {
      for (const stage of config.websocketScenarios.v10.stages) {
        v10Duration += parseTimeString(stage.duration || '0m');
      }
    }

    // WebSocket V11的总时长
    let v11Duration = 0;
    if (config.websocketScenarios?.v11?.stages) {
      for (const stage of config.websocketScenarios.v11.stages) {
        v11Duration += parseTimeString(stage.duration || '0m');
      }
    }

    // 峰值阶段（也是并发的）
    let peakDuration = 0;
    if (config.peakScenario?.duration) {
      peakDuration = parseTimeString(config.peakScenario.duration);
    }

    // mixed-load取最长的并发时长
    const concurrentDuration = Math.max(httpDuration, v10Duration, v11Duration, peakDuration);
    return concurrentDuration;
  }

  // 其他场景：basicLoad + peakLoad时长求和
  let totalMinutes = 0;

  // 提取basicLoad或mixedLoad的duration
  if (config.basicLoad?.duration) {
    totalMinutes += parseTimeString(config.basicLoad.duration);
  } else if (config.mixedLoad?.duration) {
    totalMinutes += parseTimeString(config.mixedLoad.duration);
  }

  // 提取peakLoad或peakBurst的stages总时长
  if (config.peakLoad?.stages || config.peakBurst?.stages) {
    const stages = config.peakLoad?.stages || config.peakBurst?.stages;
    for (const stage of stages) {
      if (stage.duration) {
        totalMinutes += parseTimeString(stage.duration);
      }
    }
  }

  return totalMinutes;
}

/**
 * 计算预估测试时间
 * 直接返回K6脚本的配置运行时间
 * 不考虑初始化、连接建立等额外开销
 *
 * K6脚本运行时间计算：
 *   - status-report: basicLoad(1m) + peakLoad stages(1m+1m) = 3m
 *   - websocket-v10: basicLoad(1m) + peakLoad stages(1m+30s) = 2.5m
 *   - websocket-v11: mixedLoad(1m) + peakBurst stages(1m+30s) = 2.5m
 *   - mixed-load: max(HTTP 20m, WebSocket stages 20m) = 20m
 *
 * @param {number} configMinutes - 配置中计算的总时长（分钟）
 * @param {string} scenarioName - 场景名称（暂未使用，保留接口）
 * @returns {number} K6脚本的预估运行时间（分钟）
 */
function estimateTestDuration(configMinutes, scenarioName = '') {
  // 直接返回配置时长，无需乘以任何倍数
  return Math.round(configMinutes * 10) / 10; // 保留1位小数精度
}

/**
 * 格式化持续时间为可读文本
 * @param {number} minutes - 分钟数
 * @returns {string} 格式化的时间字符串，如 "约 2 分 30 秒"、"约 3 分钟"、"约 1 小时 35 分钟"
 */
function formatDuration(minutes) {
  if (minutes < 1) {
    return `约 ${Math.round(minutes * 60)} 秒`;
  } else if (minutes >= 60) {
    const hours = Math.floor(minutes / 60);
    const mins = Math.round((minutes % 60) * 10) / 10; // 保留1位小数
    if (mins === 0) {
      return `约 ${hours} 小时`;
    } else if (Number.isInteger(mins)) {
      return `约 ${hours} 小时 ${mins} 分钟`;
    } else {
      // 比如 1 小时 35.5 分钟 显示为 1 小时 35 分 30 秒
      const minsInt = Math.floor(mins);
      const seconds = Math.round((mins - minsInt) * 60);
      return `约 ${hours} 小时 ${minsInt} 分 ${seconds} 秒`;
    }
  } else {
    // 1分钟以上60分钟以下
    // 如果是整数，显示"约 X 分钟"
    // 如果有小数，显示"约 X 分 Y 秒"
    if (Number.isInteger(minutes)) {
      return `约 ${minutes} 分钟`;
    } else {
      const minsInt = Math.floor(minutes);
      const seconds = Math.round((minutes - minsInt) * 60);
      return `约 ${minsInt} 分 ${seconds} 秒`;
    }
  }
}

// ==================== 配置加载 ====================

/**
 * 从test-params.json加载配置
 * @param {string} configPath - 配置文件路径
 * @returns {object} 配置对象
 */
function loadConfig(configPath) {
  try {
    if (!fs.existsSync(configPath)) {
      console.error(`❌ 配置文件不存在: ${configPath}`);
      return null;
    }
    const content = fs.readFileSync(configPath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`❌ 加载配置失败: ${error.message}`);
    return null;
  }
}

/**
 * 获取场景配置
 * @param {object} fullConfig - 完整的test-params.json配置
 * @param {string} scenarioName - 场景名称
 * @returns {object} 场景配置对象
 */
function getScenarioConfig(fullConfig, scenarioName) {
  if (!fullConfig || !fullConfig.scenarios) {
    return null;
  }
  return fullConfig.scenarios[scenarioName] || null;
}

// ==================== 命令行接口 ====================

/**
 * 计算单个场景的时间
 * @param {string} scenarioName - 场景名称
 * @param {string} configPath - 配置文件路径
 * @returns {object} { configDuration, estimatedDuration, formatted }
 */
function calculateScenarioDuration(scenarioName, configPath) {
  const config = loadConfig(configPath);
  if (!config) {
    return null;
  }

  const scenarioConfig = getScenarioConfig(config, scenarioName);
  if (!scenarioConfig) {
    console.warn(`⚠️  未找到场景: ${scenarioName}`);
    return null;
  }

  const configMinutes = calculateConfigDuration(scenarioConfig);
  const estimatedMinutes = estimateTestDuration(configMinutes, scenarioName);  // 传递scenarioName
  const formatted = formatDuration(estimatedMinutes);

  return {
    scenario: scenarioName,
    scenarioName: SCENARIO_NAMES[scenarioName] || scenarioName,
    configDuration: configMinutes,
    estimatedDuration: estimatedMinutes,
    formatted: formatted
  };
}

/**
 * 计算所有场景的时间
 * @param {string} configPath - 配置文件路径
 * @returns {object} 所有场景的时间数据
 */
function calculateAllDurations(configPath) {
  const config = loadConfig(configPath);
  if (!config || !config.scenarios) {
    return [];
  }

  const results = [];
  let totalEstimated = 0;

  for (const [scenarioName, scenarioConfig] of Object.entries(config.scenarios)) {
    // 跳过注释和非对象字段
    if (scenarioName.startsWith('_') || typeof scenarioConfig !== 'object' || !scenarioConfig.name) {
      continue;
    }

    const configMinutes = calculateConfigDuration(scenarioConfig);
    const estimatedMinutes = estimateTestDuration(configMinutes, scenarioName);  // 传递scenarioName

    results.push({
      scenario: scenarioName,
      scenarioName: scenarioConfig.name,
      configDuration: configMinutes,
      estimatedDuration: estimatedMinutes,
      formatted: formatDuration(estimatedMinutes)
    });

    totalEstimated += estimatedMinutes;
  }

  // 计算基准测试总时间（所有场景顺序执行 + 3分钟恢复时间间隔）
  const recoveryTime = (results.length - 1) * 3; // 场景间的3分钟恢复时间
  const benchmarkTotal = totalEstimated + recoveryTime;

  return {
    scenarios: results,
    total: totalEstimated,
    benchmark: benchmarkTotal,
    benchmarkFormatted: formatDuration(benchmarkTotal)
  };
}

// ==================== 导出接口 ====================

module.exports = {
  parseTimeString,
  calculateConfigDuration,
  estimateTestDuration,
  formatDuration,
  loadConfig,
  getScenarioConfig,
  calculateScenarioDuration,
  calculateAllDurations,
  SCENARIO_NAMES
};

// ==================== 命令行执行 ====================

// 当作为命令行工具运行时
if (require.main === module) {
  const args = process.argv.slice(2);
  const scenarioName = args[0];
  const configPath = args[1] || path.join(__dirname, '..', 'config', 'test-params.json');

  if (!scenarioName) {
    console.error('❌ 用法: node calculate-duration.js <scenario-name|all> [config-path]');
    console.error('');
    console.error('示例:');
    console.error('  node calculate-duration.js status-report');
    console.error('  node calculate-duration.js all');
    process.exit(1);
  }

  if (scenarioName === 'all') {
    const results = calculateAllDurations(configPath);
    if (results && results.scenarios.length > 0) {
      console.log('📊 所有场景预估时间:');
      console.log('');
      for (const item of results.scenarios) {
        console.log(`[${item.scenario}] ${item.scenarioName}: ${item.formatted}`);
      }
      console.log('');
      console.log(`[5] 压测基准 - ${results.benchmarkFormatted} 或更长`);
    }
  } else {
    const result = calculateScenarioDuration(scenarioName, configPath);
    if (result) {
      // 输出纯数据（供TEST.bat解析）
      console.log(result.formatted);
    } else {
      process.exit(1);
    }
  }
}
