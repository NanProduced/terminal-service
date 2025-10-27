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

  if (response.status !== 200) {
    console.error(`❌ 设备状态上报失败: ${deviceAccount}, 状态: ${response.status}`);
  }

  sleep(1);
}

// ==================== 测试生命周期 ====================

export function setup() {
  console.log('🚀 开始 HTTP 状态上报测试');
  console.log(`   目标接口: ${serverEnv.baseUrl}/wp-json/screen/v1/status`);
  console.log(`   设备账号范围: ${devices.accountPrefix}${devices.startNumber} - ${devices.accountPrefix}${devices.endNumber}`);
  console.log(`   测试设备数量: ${deviceAccounts.length}`);
  console.log(`   VU数量: ${scenarioConfig.basicLoad.vus}`);
  console.log(`   运行时长: 基础${scenarioConfig.basicLoad.duration} + 峰值${scenarioConfig.peakLoad.stages[1].duration}`);

  const healthCheck = http.get(`${serverEnv.baseUrl}${serverConfig.environments.test.healthCheckPath}`);
  if (healthCheck.status !== 200) {
    console.warn(`⚠️  健康检查失败: ${healthCheck.status}`);
  } else {
    console.log('✅ 服务器健康检查通过');
  }

  return { startTime: new Date().toISOString() };
}

export function teardown(data) {
  console.log('📊 HTTP 状态上报测试完成');
  console.log(`   开始时间: ${data.startTime}`);
  console.log(`   结束时间: ${new Date().toISOString()}`);
}
