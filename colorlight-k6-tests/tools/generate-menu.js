#!/usr/bin/env node

/**
 * K6 压测菜单生成工具
 * 用于TEST.bat动态生成菜单，显示每个场景的预估执行时间
 *
 * 使用方式：
 *   node generate-menu.js [config-path]
 */

const fs = require('fs');
const path = require('path');

// 导入计算时间的函数
const durationCalc = require('./calculate-duration.js');

function main() {
  const configPath = process.argv[2] || path.join(__dirname, '..', 'config', 'test-params.json');

  try {
    const results = durationCalc.calculateAllDurations(configPath);

    if (!results || !results.scenarios || results.scenarios.length === 0) {
      console.error('❌ 无法计算场景时间');
      process.exit(1);
    }

    // 输出菜单项
    for (let i = 0; i < results.scenarios.length; i++) {
      const scenario = results.scenarios[i];
      console.log(`[${i + 1}] ${scenario.scenarioName} - ${scenario.formatted}`);
    }

    // 输出基准测试项
    const baselineNum = results.scenarios.length + 1;
    console.log(`[${baselineNum}] 压测基准 - ${results.benchmarkFormatted}`);

  } catch (error) {
    console.error(`❌ 生成菜单失败: ${error.message}`);
    process.exit(1);
  }
}

if (require.main === module) {
  main();
}
