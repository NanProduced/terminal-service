import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import encoding from 'k6/encoding';

// ==================== 配置加载 ====================

const testParams = JSON.parse(open('../config/test-params.json'));
const serverConfig = JSON.parse(open('../config/server-config.json'));
const deviceConfig = JSON.parse(open('../config/device-config.json'));

const scenarioConfig = testParams.scenarios['mixed-load'];
const serverEnv = serverConfig.environments.test;
const devices = deviceConfig.deviceCreation;

// ==================== 指标定义 ====================

const httpStatusReports = new Counter('colorlight_mixed_http_status_reports');
const httpStatusReportFailed = new Rate('colorlight_mixed_http_status_report_failed');
const httpStatusReportDuration = new Trend('colorlight_mixed_http_status_report_duration');

const wsV10Connections = new Counter('colorlight_mixed_v10_connections');
const wsV10Messages = new Counter('colorlight_mixed_v10_messages');
const wsV10Errors = new Counter('colorlight_mixed_v10_errors');
const wsV10ConnectionFailed = new Rate('colorlight_mixed_v10_connection_failed');

const wsV11Connections = new Counter('colorlight_mixed_v11_connections');
const wsV11Messages = new Counter('colorlight_mixed_v11_messages');
const wsV11Errors = new Counter('colorlight_mixed_v11_errors');
const wsV11ConnectionFailed = new Rate('colorlight_mixed_v11_connection_failed');

// ==================== 设备账号池 ====================

// 使用 test-params.json 中的 deviceRange 配置（用于压测的设备范围）
const deviceRange = testParams.deviceRange;
const accountPrefix = devices.accountPrefix;

// 对三个协议均匀分配设备账号范围
const totalDevices = deviceRange.endNumber - deviceRange.startNumber + 1;
const devicesPerProtocol = Math.floor(totalDevices / 3);

const httpStart = deviceRange.startNumber;
const httpEnd = httpStart + devicesPerProtocol - 1;

const wsV10Start = httpEnd + 1;
const wsV10End = wsV10Start + devicesPerProtocol - 1;

const wsV11Start = wsV10End + 1;
const wsV11End = deviceRange.endNumber;

const httpDeviceAccounts = new SharedArray('http_devices', () => {
  const accounts = [];
  for (let i = httpStart; i <= httpEnd; i++) {
    accounts.push(`${accountPrefix}${i}`);
  }
  return accounts;
});

const wsV10DeviceAccounts = new SharedArray('v10_devices', () => {
  const accounts = [];
  for (let i = wsV10Start; i <= wsV10End; i++) {
    accounts.push(`${accountPrefix}${i}`);
  }
  return accounts;
});

const wsV11DeviceAccounts = new SharedArray('v11_devices', () => {
  const accounts = [];
  for (let i = wsV11Start; i <= wsV11End; i++) {
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
        mem: { total: totalMemory, free: freeMemory },
        storage: { total: totalStorage, free: freeStorage },
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

function generateRandomGpsCoordinates() {
  const minLat = 3.86;
  const maxLat = 53.55;
  const minLon = 73.66;
  const maxLon = 135.05;

  const latitude = (Math.random() * (maxLat - minLat) + minLat).toFixed(4);
  const longitude = (Math.random() * (maxLon - minLon) + minLon).toFixed(4);

  return {
    latitude: parseFloat(latitude),
    longitude: parseFloat(longitude)
  };
}

function createV10GpsMessage(deviceAccount) {
  const coords = generateRandomGpsCoordinates();
  const gpsData = JSON.stringify([{
    sensorType: "gps",
    latitude: coords.latitude,
    longitude: coords.longitude
  }]);

  return JSON.stringify({
    name: deviceAccount,
    gps: gpsData
  });
}

// ==================== 测试选项 ====================

export const options = {
  scenarios: {
    http_status_reporting: {
      executor: 'constant-arrival-rate',
      rate: scenarioConfig.httpScenario.rate,
      timeUnit: scenarioConfig.httpScenario.timeUnit,
      duration: scenarioConfig.httpScenario.duration,
      preAllocatedVUs: scenarioConfig.httpScenario.preAllocatedVUs,
      maxVUs: scenarioConfig.httpScenario.maxVUs,
      exec: 'httpStatusReporting',
      tags: { scenario: 'http' },
    },

    websocket_v10_connections: {
      executor: 'ramping-vus',
      startVUs: scenarioConfig.websocketScenarios.v10.startVUs,
      stages: scenarioConfig.websocketScenarios.v10.stages,
      exec: 'websocketV10Connection',
      tags: { scenario: 'v10' },
    },

    websocket_v11_connections: {
      executor: 'ramping-vus',
      startVUs: scenarioConfig.websocketScenarios.v11.startVUs,
      stages: scenarioConfig.websocketScenarios.v11.stages,
      exec: 'websocketV11Connection',
      tags: { scenario: 'v11' },
    },

    mixed_peak_load: {
      executor: 'ramping-arrival-rate',
      startRate: scenarioConfig.peakScenario.startRate,
      timeUnit: '1s',
      stages: [
        { duration: '2m', target: scenarioConfig.peakScenario.startRate },
        { duration: '5m', target: scenarioConfig.peakScenario.peakRate },
        { duration: '3m', target: scenarioConfig.peakScenario.peakRate },
        { duration: '5m', target: scenarioConfig.peakScenario.startRate },
      ],
      startTime: scenarioConfig.peakScenario.startTime,
      preAllocatedVUs: scenarioConfig.peakScenario.preAllocatedVUs,
      maxVUs: scenarioConfig.peakScenario.maxVUs,
      exec: 'mixedPeakLoad',
      tags: { scenario: 'peak' },
    },
  },

  thresholds: {
    'http_req_failed': [`rate<${scenarioConfig.thresholds.http_failed_rate}`],
    'colorlight_mixed_v10_connection_failed': [`rate<${scenarioConfig.thresholds.v10_failed_rate}`],
    'colorlight_mixed_v11_connection_failed': [`rate<${scenarioConfig.thresholds.v11_failed_rate}`],
  },
};

// ==================== 测试函数 ====================

export function httpStatusReporting() {
  const deviceAccount = httpDeviceAccounts[Math.floor(Math.random() * httpDeviceAccounts.length)];
  const authHeader = 'Basic ' + encoding.b64encode(`${deviceAccount}:${devices.password}`);
  const statusReportData = generateTerminalStatusReport(deviceAccount);

  const response = http.put(
    `${serverEnv.baseUrl}/wp-json/screen/v1/status`,
    JSON.stringify(statusReportData),
    {
      headers: {
        'Authorization': authHeader,
        'Content-Type': 'application/json',
      },
      timeout: '15s',
    }
  );

  check(response, {
    'HTTP状态200': (r) => r.status === 200,
  });

  httpStatusReports.add(1);
  httpStatusReportFailed.add(response.status !== 200);
  httpStatusReportDuration.add(response.timings.duration);
}

export function websocketV10Connection() {
  const deviceAccount = wsV10DeviceAccounts[Math.floor(Math.random() * wsV10DeviceAccounts.length)];
  const wsUrl = `${serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/websocket/chat')}?username=${deviceAccount}&password=${devices.password}`;

  const res = ws.connect(wsUrl, {}, function(socket) {
    wsV10Connections.add(1);

    try {
      for (let i = 0; i < 5; i++) {
        const gpsMessage = createV10GpsMessage(deviceAccount);
        socket.send(gpsMessage);
        wsV10Messages.add(1);
        sleep(Math.random() * 2 + 1);
      }
    } catch (error) {
      wsV10Errors.add(1);
    }
  });

  wsV10ConnectionFailed.add(!res || res.status !== 101);
}

export function websocketV11Connection() {
  const deviceAccount = wsV11DeviceAccounts[Math.floor(Math.random() * wsV11DeviceAccounts.length)];
  const wsUrl = `${serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/terminal')}?username=${deviceAccount}&password=${devices.password}&protocol_version=1.1`;

  const res = ws.connect(wsUrl, {}, function(socket) {
    wsV11Connections.add(1);

    try {
      socket.send(JSON.stringify({ type: 'ping' }));
      wsV11Messages.add(1);
      sleep(Math.random() * 3 + 2);
      socket.send(JSON.stringify({ type: 'status', data: {} }));
      wsV11Messages.add(1);
      sleep(2);
    } catch (error) {
      wsV11Errors.add(1);
    }
  });

  wsV11ConnectionFailed.add(!res || res.status !== 101);
}

export function mixedPeakLoad() {
  const rand = Math.random();

  if (rand < 0.5) {
    // HTTP请求
    const deviceAccount = httpDeviceAccounts[Math.floor(Math.random() * httpDeviceAccounts.length)];
    const authHeader = 'Basic ' + encoding.b64encode(`${deviceAccount}:${devices.password}`);
    const statusReportData = generateTerminalStatusReport(deviceAccount);

    const response = http.put(
      `${serverEnv.baseUrl}/wp-json/screen/v1/status`,
      JSON.stringify(statusReportData),
      { headers: { 'Authorization': authHeader, 'Content-Type': 'application/json' }, timeout: '15s' }
    );

    check(response, { 'HTTP成功': (r) => r.status === 200 });
    httpStatusReports.add(1);
  } else {
    // WebSocket连接
    const isV10 = Math.random() < 0.5;
    const accounts = isV10 ? wsV10DeviceAccounts : wsV11DeviceAccounts;
    const deviceAccount = accounts[Math.floor(Math.random() * accounts.length)];
    const wsUrl = isV10
      ? `${serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/websocket/chat')}?username=${deviceAccount}&password=${devices.password}`
      : `${serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/terminal')}?username=${deviceAccount}&password=${devices.password}&protocol_version=1.1`;

    const res = ws.connect(wsUrl, {}, function(socket) {
      try {
        if (isV10) {
          wsV10Connections.add(1);
          socket.send(createV10GpsMessage(deviceAccount));
          wsV10Messages.add(1);
        } else {
          wsV11Connections.add(1);
          socket.send(JSON.stringify({ type: 'ping' }));
          wsV11Messages.add(1);
        }
        sleep(1);
      } catch (error) {
        if (isV10) {
          wsV10Errors.add(1);
        } else {
          wsV11Errors.add(1);
        }
      }
    });

    if (isV10) {
      wsV10ConnectionFailed.add(!res || res.status !== 101);
    } else {
      wsV11ConnectionFailed.add(!res || res.status !== 101);
    }
  }
}

// ==================== 测试生命周期 ====================

export function setup() {
  console.log('🚀 开始混合负载测试');
  console.log(`   HTTP RPS: ${scenarioConfig.httpScenario.rate}`);
  console.log(`   WebSocket V10: ${scenarioConfig.websocketScenarios.v10.startVUs} VU`);
  console.log(`   WebSocket V11: ${scenarioConfig.websocketScenarios.v11.startVUs} VU`);
  console.log(`   总运行时长: ${scenarioConfig.httpScenario.duration}`);

  return { startTime: new Date().toISOString() };
}

export function teardown(data) {
  console.log('📊 混合负载测试完成');
}
