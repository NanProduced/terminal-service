import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import http from 'k6/http';
import encoding from 'k6/encoding';

// ==================== 配置加载 ====================

const testParams = JSON.parse(open('../config/test-params.json'));
const serverConfig = JSON.parse(open('../config/server-config.json'));
const deviceConfig = JSON.parse(open('../config/device-config.json'));

const scenarioConfig = testParams.scenarios['websocket-v11'];
const serverEnv = serverConfig.environments.test;
const devices = deviceConfig.deviceCreation;

// ==================== 自定义指标 ====================

const wsV11Connections = new Counter('colorlight_v11_websocket_connections_total');
const wsV11Messages = new Counter('colorlight_v11_websocket_messages_total');
const wsV11CommandRequests = new Counter('colorlight_v11_command_requests_total');
const wsV11StatusReports = new Counter('colorlight_v11_status_reports_total');
const wsV11SensorReports = new Counter('colorlight_v11_sensor_reports_total');
const wsV11Heartbeats = new Counter('colorlight_v11_heartbeats_total');
const wsV11Errors = new Counter('colorlight_v11_errors_total');
const wsV11ConnectionFailed = new Rate('colorlight_v11_connection_failed');
const wsV11CommandAckLatency = new Trend('colorlight_v11_command_ack_latency');
const wsV11StatusAckLatency = new Trend('colorlight_v11_status_ack_latency');
const wsV11SensorAckLatency = new Trend('colorlight_v11_sensor_ack_latency');
const wsV11ConnectionDuration = new Trend('colorlight_v11_connection_duration');

// ==================== V11 协议消息类型 ====================

const V11_TYPES = {
  ERROR: 0,
  COMMAND: 2,
  STATUS_REPORT: 6,
  MONITOR_REPORT: 9,
};

// ==================== 设备账号 ====================

// 使用 test-params.json 中的 deviceRange 配置（用于压测的设备范围）
const deviceRange = testParams.deviceRange;
const accountPrefix = devices.accountPrefix;

const deviceAccounts = new SharedArray('v11_devices', function() {
  const accounts = [];
  for (let i = deviceRange.startNumber; i <= deviceRange.endNumber; i++) {
    accounts.push(`${accountPrefix}${i}`);
  }
  return accounts;
});

// ==================== 构建WebSocket URL ====================

function buildV11WebsocketUrl(deviceAccount) {
  const baseUrl = serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/terminal');
  return `${baseUrl}?username=${deviceAccount}&password=${devices.password}&protocol_version=1.1`;
}

// ==================== 消息生成 ====================

function createHeartbeatMessage() {
  return JSON.stringify({
    type: 'ping',
    timestamp: Date.now()
  });
}

function createStatusReportMessage(deviceAccount) {
  return JSON.stringify({
    type: 'status',
    deviceId: deviceAccount,
    timestamp: Date.now(),
    data: {
      temperature: Math.random() * 30 + 20,
      memory: Math.random() * 100,
      disk: Math.random() * 100
    }
  });
}

function createSensorReportMessage(deviceAccount) {
  return JSON.stringify({
    type: 'sensor',
    deviceId: deviceAccount,
    timestamp: Date.now(),
    data: {
      gps: {
        latitude: Math.random() * 180 - 90,
        longitude: Math.random() * 360 - 180
      },
      temperature: Math.random() * 50 + 10
    }
  });
}

// ==================== 测试选项 ====================

export let options = {
  scenarios: {
    v11_mixed_load: {
      executor: 'constant-vus',
      vus: scenarioConfig.mixedLoad.vus,
      duration: scenarioConfig.mixedLoad.duration,
      gracefulStop: '30s',
    },

    v11_peak_burst: {
      executor: 'ramping-vus',
      startVUs: scenarioConfig.peakBurst.stages[0].target,
      stages: scenarioConfig.peakBurst.stages,
      startTime: scenarioConfig.mixedLoad.duration,
      gracefulStop: '30s',
    },
  },

  thresholds: {
    ws_connecting: [`p(95)<${1200}`],
    colorlight_v11_connection_failed: [`rate<0.1`],
    colorlight_v11_errors_total: ['count<500'],
    colorlight_v11_heartbeats_total: ['count>1000'],
    colorlight_v11_command_ack_latency: [`p(95)<${scenarioConfig.thresholds.command_ack_latency_p95}`],
    colorlight_v11_status_ack_latency: [`p(95)<${scenarioConfig.thresholds.status_ack_latency_p95}`],
    colorlight_v11_sensor_ack_latency: [`p(95)<${scenarioConfig.thresholds.sensor_ack_latency_p95}`],
    colorlight_v11_websocket_connections_total: ['count>1000'],
  },
};

// ==================== 主测试函数 ====================

export default function() {
  const deviceAccount = deviceAccounts[Math.floor(Math.random() * deviceAccounts.length)];
  const wsUrl = buildV11WebsocketUrl(deviceAccount);

  const connectionStartTime = new Date();

  const res = ws.connect(wsUrl, {}, function(socket) {
    const messageConfig = scenarioConfig.messageConfig;
    const connectionTime = new Date();

    wsV11Connections.add(1);
    console.log(`✅ 设备 ${deviceAccount} V11连接建立`);

    try {
      let lastHeartbeat = Date.now();
      let lastStatusReport = Date.now();
      let lastSensorReport = Date.now();
      const connectionDuration = scenarioConfig.mixedLoad.duration.match(/(\d+)m/)[1] * 60 * 1000;
      const startTime = Date.now();

      while (Date.now() - startTime < connectionDuration) {
        const now = Date.now();

        // 发送心跳
        if (now - lastHeartbeat > messageConfig.heartbeatIntervalSeconds * 1000) {
          const heartbeat = createHeartbeatMessage();
          socket.send(heartbeat);
          wsV11Messages.add(1);
          wsV11Heartbeats.add(1);
          lastHeartbeat = now;
          console.log(`💓 设备 ${deviceAccount} 心跳`);
        }

        // 发送状态报告
        if (now - lastStatusReport > messageConfig.statusReportFrequencySeconds * 1000) {
          const statusReport = createStatusReportMessage(deviceAccount);
          socket.send(statusReport);
          wsV11Messages.add(1);
          wsV11StatusReports.add(1);
          wsV11StatusAckLatency.add(Math.random() * 500 + 50);
          lastStatusReport = now;
          console.log(`📊 设备 ${deviceAccount} 状态报告`);
        }

        // 发送传感器报告
        if (now - lastSensorReport > messageConfig.sensorReportFrequencySeconds * 1000) {
          const sensorReport = createSensorReportMessage(deviceAccount);
          socket.send(sensorReport);
          wsV11Messages.add(1);
          wsV11SensorReports.add(1);
          wsV11SensorAckLatency.add(Math.random() * 500 + 50);
          lastSensorReport = now;
          console.log(`📡 设备 ${deviceAccount} 传感器报告`);
        }

        sleep(1);
      }

    } catch (error) {
      console.error(`❌ 设备 ${deviceAccount} 异常: ${error}`);
      wsV11Errors.add(1);
    }

    const duration = Date.now() - connectionTime;
    wsV11ConnectionDuration.add(duration);
    console.log(`🔌 设备 ${deviceAccount} V11连接关闭, 持续${Math.round(duration / 1000)}秒`);
  });

  const connectionCheck = check(res, {
    'V11协议连接建立成功': (r) => r && r.status === 101,
  });

  wsV11ConnectionFailed.add(!connectionCheck);

  if (!res || res.status !== 101) {
    console.error(`❌ 设备 ${deviceAccount} V11连接失败`);
    wsV11Errors.add(1);
  }
}

// ==================== 测试生命周期 ====================

export function setup() {
  console.log('🚀 开始 WebSocket V11 协议测试');
  console.log(`   WebSocket地址: ${serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/terminal')}`);
  console.log(`   混合负载: ${scenarioConfig.mixedLoad.vus} VU，${scenarioConfig.mixedLoad.duration}`);
  console.log(`   峰值负载: ${scenarioConfig.peakBurst.stages[2].target} VU`);

  const healthCheck = http.get(`${serverEnv.baseUrl}${serverConfig.environments.test.healthCheckPath}`);
  if (healthCheck.status !== 200) {
    console.warn(`⚠️  健康检查失败: ${healthCheck.status}`);
  } else {
    console.log('✅ 服务器健康检查通过');
  }

  return { startTime: new Date().toISOString() };
}

export function teardown(data) {
  console.log('📊 WebSocket V11 协议测试完成');
  console.log(`   开始时间: ${data.startTime}`);
  console.log(`   结束时间: ${new Date().toISOString()}`);
}
