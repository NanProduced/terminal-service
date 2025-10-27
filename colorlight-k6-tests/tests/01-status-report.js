import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import encoding from 'k6/encoding';

// ==================== 配置加载 ====================

// 加载参数配置
const testParams = JSON.parse(open('../config/test-params.json'));
const serverConfig = JSON.parse(open('../config/server-config.json'));
const deviceConfig = JSON.parse(open('../config/device-config.json'));

// 获取当前场景的参数
const scenarioConfig = testParams.scenarios['status-report'];
const serverEnv = serverConfig.environments.test;
const devices = deviceConfig.deviceCreation;

// ==================== 自定义指标 ====================

const statusReports = new Counter('colorlight_status_reports_total');
const statusReportFailed = new Rate('colorlight_status_report_failed');
const statusReportDuration = new Trend('colorlight_status_report_duration');

// ==================== 设备账号列表 ====================

// 使用 test-params.json 中的 deviceRange 配置（用于压测的设备范围）
// 而不是 device-config.json 中的 deviceCreation（创建时的范围）
const deviceRange = testParams.deviceRange;
const accountPrefix = devices.accountPrefix;

const deviceAccounts = new SharedArray('devices', function() {
  const accounts = [];
  for (let i = deviceRange.startNumber; i <= deviceRange.endNumber; i++) {
    accounts.push(`${accountPrefix}${i}`);
  }
  return accounts;
});

// ==================== 数据生成函数 ====================

const modelList = ['a2k', 'a200', 'a20h', 'a35', 'a500', 'a4k'];

const resolutions = [
  { width: 1920, height: 1080 },
  { width: 1366, height: 768 },
  { width: 1280, height: 720 },
  { width: 3840, height: 2160 },
  { width: 2560, height: 1440 },
  { width: 1600, height: 900 }
];

function generateVersionName() {
  const major = Math.floor(Math.random() * 3) + 1;
  const minor = Math.floor(Math.random() * 100);
  const patch = Math.floor(Math.random() * 10);
  const build = Math.floor(Math.random() * 9999) + 1000;
  return `${major}.${minor}.${patch}.${build}`;
}

function generateSerialNo(model) {
  const randomNum = String(Math.floor(Math.random() * 1000000)).padStart(6, '0');
  return `CLC${model.toUpperCase()}${randomNum}`;
}

function generateRandomIP() {
  return `192.168.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 254) + 1}`;
}

function generateRandomMAC() {
  const chars = '0123456789ABCDEF';
  let mac = '';
  for (let i = 0; i < 6; i++) {
    if (i > 0) mac += ':';
    mac += chars[Math.floor(Math.random() * chars.length)];
    mac += chars[Math.floor(Math.random() * chars.length)];
  }
  return mac;
}

function generateTerminalStatusReport(deviceAccount) {
  const model = modelList[Math.floor(Math.random() * modelList.length)];
  const resolution = resolutions[Math.floor(Math.random() * resolutions.length)];
  const currentTime = Date.now();

  const totalMemory = (Math.floor(Math.random() * 7) + 1) * 1024 * 1024 * 1024;
  const memoryUsagePercent = Math.floor(Math.random() * 70) + 20;
  const freeMemory = Math.floor(totalMemory * (100 - memoryUsagePercent) / 100);

  const totalStorage = (Math.floor(Math.random() * 7) + 1) * 8 * 1024 * 1024 * 1024;
  const storageUsagePercent = Math.floor(Math.random() * 60) + 30;
  const freeStorage = Math.floor(totalStorage * (100 - storageUsagePercent) / 100);

  return {
    info: {
      info: {
        vername: generateVersionName(),
        serialno: generateSerialNo(model),
        model: model,
        up: Math.floor(Math.random() * 86400 * 30),
        mem: {
          total: totalMemory,
          free: freeMemory
        },
        storage: {
          total: totalStorage,
          free: freeStorage
        },
        playing: null
      },
      _report_time: currentTime
    },
    dimension: {
      dclk: Math.floor(Math.random() * 200000000) + 50000000,
      fps: Math.random() > 0.5 ? 60 : 30,
      height: resolution.height,
      hsync: Math.floor(Math.random() * 100) + 50,
      real_dclk: Math.floor(Math.random() * 200000000) + 50000000,
      real_height: resolution.height,
      real_width: resolution.width,
      width: resolution.width,
      _report_time: currentTime
    }
  };
}

// ==================== 测试选项 ====================

export let options = {
  scenarios: {
    status_reporting: {
      executor: 'constant-vus',
      vus: scenarioConfig.basicLoad.vus,
      duration: scenarioConfig.basicLoad.duration,
    },

    peak_status_reporting: {
      executor: 'ramping-vus',
      startVUs: scenarioConfig.peakLoad.startVUs,
      stages: scenarioConfig.peakLoad.stages,
      startTime: scenarioConfig.peakLoad.startTime,
    },
  },

  thresholds: {
    'http_req_duration': [
      `p(95)<${scenarioConfig.thresholds.http_req_duration_p95}`,
      `p(99)<${scenarioConfig.thresholds.http_req_duration_p99}`
    ],
    'http_req_failed': [`rate<${scenarioConfig.thresholds.http_req_failed_rate}`],
    'colorlight_status_reports_total': [`count>${scenarioConfig.thresholds.http_reqs_min}`],
    'colorlight_status_report_duration': [`p(95)<${scenarioConfig.thresholds.http_req_duration_p95}`],
  },
};

// ==================== 主测试函数 ====================

export default function() {
  const deviceAccount = deviceAccounts[Math.floor(Math.random() * deviceAccounts.length)];
  const authHeader = 'Basic ' + encoding.b64encode(`${deviceAccount}:${devices.password}`);
  const statusReportData = generateTerminalStatusReport(deviceAccount);

  const response = http.put(
    `${serverEnv.baseUrl}/wp-json/screen/v1/status`,
    JSON.stringify(statusReportData),
    {
      headers: {
        'Authorization': authHeader,
        'Content-Type': 'application/json',
        'User-Agent': `ColorlightTerminal/${statusReportData.info.info.vername} (${statusReportData.info.info.model})`,
        'X-Device-ID': deviceAccount,
        'X-Device-Model': statusReportData.info.info.model,
        'X-Report-Time': new Date().toISOString(),
      },
      timeout: '15s',
    }
  );

  const responseCheck = check(response, {
    '状态上报接口状态200': (r) => r.status === 200,
    '状态上报响应时间<200ms': (r) => r.timings.duration < 200,
    '状态上报响应时间<500ms': (r) => r.timings.duration < 500,
    '状态上报返回JSON': (r) => {
      try {
        const body = JSON.parse(r.body);
        return typeof body === 'object';
      } catch {
        return false;
      }
    },
    '状态上报无服务器错误': (r) => r.status < 500,
  });

  statusReports.add(1);
  statusReportFailed.add(response.status !== 200);
  statusReportDuration.add(response.timings.duration);

  // 详细的日志输出
  const duration = Math.round(response.timings.duration);
  const model = statusReportData.info.info.model;
  const version = statusReportData.info.info.vername;

  if (response.status === 200) {
    // 成功的请求输出成功日志（参考 .k6 脚本方式）
    console.log(`✅ 设备状态上报成功: ${deviceAccount}, 型号: ${model} v${version}, 响应时间: ${duration}ms`);
  } else {
    // 失败的请求输出失败日志
    const errorDetail = response.body ? response.body.substring(0, 100) : '无响应内容';
    console.error(`❌ 设备状态上报失败: ${deviceAccount}, 状态: ${response.status}, 型号: ${model}, 响应: ${errorDetail}`);
  }

  sleep(1);
}

// ==================== 测试生命周期 ====================

export function setup() {
  const setupStartTime = new Date().toISOString();
  console.log('');
  console.log('🚀 开始 HTTP 状态上报测试');
  console.log(`目标接口: ${serverEnv.baseUrl}/wp-json/screen/v1/status`);
  console.log(`设备账号范围: ${devices.accountPrefix}${devices.startNumber} - ${devices.accountPrefix}${devices.endNumber}`);
  console.log(`测试设备数量: ${deviceAccounts.length}`);
  console.log(`支持的终端型号: ${modelList.join(', ')}`);
  console.log(`VU数量: ${scenarioConfig.basicLoad.vus}`);
  console.log(`基础负载运行时长: ${scenarioConfig.basicLoad.duration}`);
  console.log(`峰值负载运行时长: ${scenarioConfig.peakLoad.stages[1].duration}`);
  console.log(`设备密码: ${devices.password}`);
  console.log('');

  // 健康检查
  const healthCheck = http.get(`${serverEnv.baseUrl}${serverConfig.environments.test.healthCheckPath}`);
  if (healthCheck.status !== 200) {
    console.warn(`⚠️  服务器健康检查失败: ${healthCheck.status}，但继续执行状态上报测试`);
  } else {
    console.log('✅ 服务器健康检查通过');
  }

  // 测试设备账号有效性（验证第一个账号）
  const testAccount = deviceAccounts[0];
  const testAuth = 'Basic ' + encoding.b64encode(`${testAccount}:${devices.password}`);
  const testStatusData = generateTerminalStatusReport(testAccount);

  const deviceTest = http.put(
    `${serverEnv.baseUrl}/wp-json/screen/v1/status`,
    JSON.stringify(testStatusData),
    {
      headers: {
        'Authorization': testAuth,
        'Content-Type': 'application/json',
        'User-Agent': `ColorlightTerminal/${testStatusData.info.info.vername} (${testStatusData.info.info.model})`,
      },
      timeout: '10s',
    }
  );

  if (deviceTest.status !== 200) {
    throw new Error(`❌ 设备账号验证失败: ${testAccount}, 状态: ${deviceTest.status}, 响应: ${deviceTest.body}. 请确保接口可用且设备账号有效`);
  }
  console.log(`✅ 设备账号有效性验证通过: ${testAccount}`);
  console.log('');
  console.log('开始执行测试脚本，每个请求都会输出日志信息');
  console.log('');

  return {
    startTime: setupStartTime,
    testCount: 0,
    successCount: 0,
    failureCount: 0
  };
}

export function teardown(data) {
  console.log('');
  console.log('📊 HTTP 状态上报测试完成');
  console.log(`开始时间: ${data.startTime}`);
  console.log(`结束时间: ${new Date().toISOString()}`);
  console.log('');
  console.log('建议检查的指标：');
  console.log('- 状态上报响应时间分布（P50、P95、P99）');
  console.log('- 设备状态上报成功率');
  console.log('- 不同终端型号的上报情况');
  console.log('- 服务器处理能力和资源使用情况');
  console.log('- 数据库写入性能');
  console.log('');
}
