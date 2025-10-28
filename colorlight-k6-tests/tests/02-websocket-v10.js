import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import encoding from 'k6/encoding';
import http from 'k6/http';

// ==================== 配置加载 ====================

const testParams = JSON.parse(open('../config/test-params.json'));
const serverConfig = JSON.parse(open('../config/server-config.json'));
const deviceConfig = JSON.parse(open('../config/device-config.json'));

const scenarioConfig = testParams.scenarios['websocket-v10'];
const serverEnv = serverConfig.environments.test;
const devices = deviceConfig.deviceCreation;

// ==================== 自定义指标 ====================

const wsConnections = new Counter('colorlight_websocket_connections_total');
const wsConnectionDuration = new Trend('colorlight_websocket_connection_duration');
const wsMessages = new Counter('colorlight_websocket_messages_total');
const wsHeartbeats = new Counter('colorlight_websocket_heartbeats_total');
const wsGpsReports = new Counter('colorlight_websocket_gps_reports_total');
const wsErrors = new Counter('colorlight_websocket_errors_total');
const wsConnectionFailed = new Rate('colorlight_websocket_connection_failed');

// ==================== 设备账号 ====================

// 使用 test-params.json 中的 deviceRange 配置（用于压测的设备范围）
const deviceRange = testParams.deviceRange;
const accountPrefix = devices.accountPrefix;

const deviceAccounts = new SharedArray('devices', function() {
  const accounts = [];
  for (let i = deviceRange.startNumber; i <= deviceRange.endNumber; i++) {
    accounts.push(`${accountPrefix}${i}`);
  }
  return accounts;
});

// ==================== GPS坐标生成 ====================

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

function createV10HeartbeatMessage() {
  return JSON.stringify({
    content: "heartbeat"
  });
}

function createV10GpsMessage(deviceAccount) {
  const coords = generateRandomGpsCoordinates();

  // ✅ 修复: 只需创建数组，无需额外stringify
  // GPS字段应该是数组，而不是字符串
  const gpsArray = [{
    sensorType: "gps",
    latitude: coords.latitude,
    longitude: coords.longitude
  }];

  return JSON.stringify({
    name: deviceAccount,
    gps: gpsArray  // ✅ 直接传递数组，由外层JSON.stringify处理
  });
}

// ==================== 测试选项 ====================

export let options = {
  scenarios: {
    frequent_connections: {
      executor: 'constant-vus',
      vus: scenarioConfig.basicLoad.vus,
      duration: scenarioConfig.basicLoad.duration,
    },

    peak_connections: {
      executor: 'ramping-vus',
      startVUs: scenarioConfig.peakLoad.stages[0].target,
      stages: scenarioConfig.peakLoad.stages,
      startTime: scenarioConfig.peakLoad.startTime,
    },
  },

  thresholds: {
    'ws_connecting': [`p(95)<${scenarioConfig.thresholds.ws_connecting_p95}`],
    'colorlight_websocket_errors_total': ['count<1000'],
    'colorlight_websocket_connection_failed': [`rate<${scenarioConfig.thresholds.ws_connection_failed_rate}`],
    'colorlight_websocket_heartbeats_total': ['count>2000'],
    'colorlight_websocket_gps_reports_total': ['count>10000'],
    'colorlight_websocket_connections_total': ['count>2000'],
  },
};

// ==================== 主测试函数 ====================

export default function() {
  const deviceAccount = deviceAccounts[Math.floor(Math.random() * deviceAccounts.length)];

  while (true) {
    const connectionStartTime = new Date();
    const wsUrl = `${serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/websocket/chat')}?username=${deviceAccount}&password=${devices.password}`;

    console.log(`🔥 设备 ${deviceAccount} 建立连接`);

    const res = ws.connect(wsUrl, {}, function(socket) {
      const connConfig = scenarioConfig.connectionConfig;
      const targetGpsCount = Math.floor(Math.random() * (connConfig.gpsPerConnectionMax - connConfig.gpsPerConnectionMin + 1)) + connConfig.gpsPerConnectionMin;
      let messageCount = 0;
      let gpsCount = 0;
      const connectTime = new Date();

      console.log(`✅ 设备 ${deviceAccount} 连接成功，目标GPS: ${targetGpsCount}条`);
      wsConnections.add(1);

      try {
        const heartbeatMessage = createV10HeartbeatMessage();
        socket.send(heartbeatMessage);
        wsMessages.add(1);
        wsHeartbeats.add(1);
        messageCount++;

        for (let i = 0; i < targetGpsCount; i++) {
          const gpsDelay = Math.random() * (connConfig.gpsIntervalMax - connConfig.gpsIntervalMin) + connConfig.gpsIntervalMin;
          sleep(gpsDelay);

          const gpsMessage = createV10GpsMessage(deviceAccount);
          socket.send(gpsMessage);
          wsMessages.add(1);
          wsGpsReports.add(1);
          messageCount++;
          gpsCount++;

          console.log(`📍 设备 ${deviceAccount} GPS ${gpsCount}/${targetGpsCount}`);
        }

        const randomWait = Math.random() * 6 + 2;
        console.log(`🔚 设备 ${deviceAccount} 等待${randomWait.toFixed(1)}秒后关闭连接`);
        sleep(randomWait);

        const connectionDuration = new Date() - connectTime;
        wsConnectionDuration.add(connectionDuration);
        console.log(`🔌 设备 ${deviceAccount} 连接完成, 持续${Math.round(connectionDuration/1000)}秒`);

      } catch (error) {
        console.error(`❌ 设备 ${deviceAccount} 异常: ${error}`);
        wsErrors.add(1);
      }
    });

    const connectionCheck = check(res, {
      'V10协议连接建立成功': (r) => r && r.status === 101,
    });

    wsConnectionFailed.add(!connectionCheck);

    if (!res || res.status !== 101) {
      console.error(`❌ 设备 ${deviceAccount} 连接失败`);
      wsErrors.add(1);
    }

    const nextConnectionDelay = Math.random() * (scenarioConfig.connectionConfig.connectionDelayMax - scenarioConfig.connectionConfig.connectionDelayMin) + scenarioConfig.connectionConfig.connectionDelayMin;
    sleep(nextConnectionDelay);
  }
}

// ==================== 测试生命周期 ====================

export function setup() {
  const setupStartTime = new Date().toISOString();
  console.log('');
  console.log('🚀 开始 WebSocket V10 频繁连接测试');
  console.log(`WebSocket地址: ${serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/websocket/chat')}`);
  console.log(`设备账号范围: ${devices.accountPrefix}${devices.startNumber} - ${devices.accountPrefix}${devices.endNumber}`);
  console.log(`测试设备数量: ${deviceAccounts.length}`);
  console.log(`VU数量: ${scenarioConfig.basicLoad.vus}`);
  console.log(`基础负载运行时长: ${scenarioConfig.basicLoad.duration}`);
  console.log(`峰值负载运行时长: ${scenarioConfig.peakLoad.stages[1].duration}`);
  console.log(`GPS数据范围: 每个连接 ${scenarioConfig.connectionConfig.gpsPerConnectionMin}-${scenarioConfig.connectionConfig.gpsPerConnectionMax} 条`);
  console.log(`连接间隔: ${scenarioConfig.connectionConfig.connectionDelayMin}-${scenarioConfig.connectionConfig.connectionDelayMax} 秒`);
  console.log(`GPS发送间隔: ${scenarioConfig.connectionConfig.gpsIntervalMin}-${scenarioConfig.connectionConfig.gpsIntervalMax} 秒`);
  console.log(`设备密码: ${devices.password}`);
  console.log('');

  // 健康检查
  const healthCheck = http.get(`${serverEnv.baseUrl}${serverConfig.environments.test.healthCheckPath}`);
  if (healthCheck.status !== 200) {
    console.warn(`⚠️  服务器健康检查失败: ${healthCheck.status}，但继续执行WebSocket连接测试`);
  } else {
    console.log('✅ 服务器健康检查通过');
  }

  console.log('');
  console.log('开始执行测试脚本，每个连接和GPS发送都会输出日志信息');
  console.log('');

  return {
    startTime: setupStartTime,
    connectionCount: 0,
    gpsCount: 0,
    errorCount: 0
  };
}

export function teardown(data) {
  console.log('');
  console.log('📊 WebSocket V10 频繁连接测试完成');
  console.log(`开始时间: ${data.startTime}`);
  console.log(`结束时间: ${new Date().toISOString()}`);
  console.log('');
  console.log('建议检查的指标：');
  console.log('- WebSocket连接建立时间和成功率');
  console.log('- GPS数据发送频率和成功率');
  console.log('- 设备连接保持时间分布');
  console.log('- 心跳包发送情况');
  console.log('- 连接失败和错误情况');
  console.log('- 服务器处理WebSocket连接的能力');
  console.log('');
}
