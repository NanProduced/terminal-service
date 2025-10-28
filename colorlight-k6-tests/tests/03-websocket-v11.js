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

// ==================== 辅助函数 ====================

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomFloat(min, max) {
  return Math.random() * (max - min) + min;
}

function shuffle(array) {
  for (let i = array.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [array[i], array[j]] = [array[j], array[i]];
  }
  return array;
}

// ==================== GPS 坐标生成 ====================

function generateRandomGpsCoordinates() {
  const minLat = 3.86;
  const maxLat = 53.55;
  const minLon = 73.66;
  const maxLon = 135.05;

  const latitude = parseFloat((Math.random() * (maxLat - minLat) + minLat).toFixed(5));
  const longitude = parseFloat((Math.random() * (maxLon - minLon) + minLon).toFixed(5));

  return { latitude, longitude };
}

// ==================== 消息生成 ====================

function generateTerminalStatusReport(deviceAccount) {
  const models = ['a2k', 'a200', 'a20h', 'a35', 'a500', 'a4k'];
  const model = models[randomInt(0, models.length - 1)];

  const totalMemory = randomInt(2, 8) * 1024 * 1024 * 1024;
  const usedRate = randomInt(25, 85) / 100;
  const freeMemory = Math.floor(totalMemory * (1 - usedRate));

  const totalStorage = randomInt(1, 8) * 8 * 1024 * 1024 * 1024;
  const storageUsedRate = randomInt(30, 90) / 100;
  const freeStorage = Math.floor(totalStorage * (1 - storageUsedRate));

  return {
    terminal: {
      name: deviceAccount,
      description: `test device ${deviceAccount}`
    },
    info: {
      vername: `${randomInt(1, 3)}.${randomInt(0, 99)}.${randomInt(0, 9)}.${randomInt(1000, 9999)}`,
      model: model,
      uptime: randomInt(10000, 1000000),
      memory: { total: totalMemory, free: freeMemory },
      storage: { total: totalStorage, free: freeStorage }
    }
  };
}

function generateGpsReports() {
  const coords = generateRandomGpsCoordinates();
  const now = new Date();

  return [
    {
      sensorType: 'gps',
      sensorId: randomInt(100000, 999999),
      latitude: coords.latitude,
      longitude: coords.longitude,
      accuracy: parseFloat(randomFloat(3, 10).toFixed(2)),
      altitude: parseFloat(randomFloat(10, 50).toFixed(2)),
      speed: parseFloat(randomFloat(0, 5).toFixed(2)),
      satellites: randomInt(6, 12)
    }
  ];
}

// ==================== 创建操作计划 ====================

function createOperationPlan() {
  const base = ['COMMAND', 'STATUS', 'SENSOR'];
  const extraCount = randomInt(2, 5);
  const candidates = [];
  for (let i = 0; i < extraCount; i++) {
    const choice = base[randomInt(0, base.length - 1)];
    candidates.push(choice);
  }
  return shuffle(base.concat(candidates));
}

// ==================== V11 消息发送和确认处理 ====================

function sendV11Message(socket, message, pendingRequests) {
  pendingRequests.set(message.messageId, {
    type: message.type,
    sentAt: Date.now()
  });

  socket.send(JSON.stringify(message));
  wsV11Messages.add(1);

  if (message.type === V11_TYPES.COMMAND) {
    wsV11CommandRequests.add(1);
  } else if (message.type === V11_TYPES.STATUS_REPORT) {
    wsV11StatusReports.add(1);
  } else if (message.type === V11_TYPES.MONITOR_REPORT) {
    wsV11SensorReports.add(1);
  }
}

function handleAck(message, pendingRequests) {
  if (message.receiptId === undefined || message.receiptId === null) {
    return;
  }

  const pending = pendingRequests.get(message.receiptId);
  if (!pending) {
    return;
  }

  const latency = Date.now() - pending.sentAt;

  if (pending.type === V11_TYPES.COMMAND) {
    wsV11CommandAckLatency.add(latency);
  } else if (pending.type === V11_TYPES.STATUS_REPORT) {
    wsV11StatusAckLatency.add(latency);
  } else if (pending.type === V11_TYPES.MONITOR_REPORT) {
    wsV11SensorAckLatency.add(latency);
  }

  pendingRequests.delete(message.receiptId);
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
  let sequence = 0;

  while (true) {
    const wsUrl = buildV11WebsocketUrl(deviceAccount);
    const pendingRequests = new Map();
    const connectStart = Date.now();
    let messageCount = 0;

    console.log(`🔥 设备 ${deviceAccount} 建立V11连接 (${new Date().toLocaleTimeString()})`);

    // 建立WebSocket连接
    const res = ws.connect(wsUrl, {}, function(socket) {
      wsV11Connections.add(1);
      console.log(`✅ 设备 ${deviceAccount} V11连接成功，开始执行测试操作`);

      // 设置V1.1协议心跳机制（空消息心跳，30秒间隔，规避Nginx 60秒超时）
      const heartbeatInterval = setInterval(() => {
        try {
          socket.send(''); // V1.1协议使用空消息作为心跳
          wsV11Heartbeats.add(1);
          console.log(`💓 设备 ${deviceAccount} 发送V11心跳 (${new Date().toLocaleTimeString()})`);
        } catch (error) {
          console.error(`❌ 设备 ${deviceAccount} 心跳发送失败: ${error}`);
          clearInterval(heartbeatInterval);
        }
      }, 30000);

      // 连接关闭时清理心跳定时器
      socket.on('close', () => {
        clearInterval(heartbeatInterval);
        const duration = Date.now() - connectStart;
        wsV11ConnectionDuration.add(duration);
        console.log(`🔌 设备 ${deviceAccount} V11连接关闭, 持续${Math.round(duration/1000)}秒, 总消息:${messageCount}条`);
        if (pendingRequests.size > 0) {
          console.warn(`⚠️ 设备 ${deviceAccount} 关闭时仍有${pendingRequests.size}个待确认消息`);
          pendingRequests.clear();
        }
      });

      // 处理接收到的消息
      socket.on('message', (raw) => {
        // 忽略空消息
        if (raw === '' || raw === null) {
          return;
        }

        // 解析消息
        let msg;
        try {
          msg = JSON.parse(raw);
        } catch (error) {
          wsV11Errors.add(1);
          console.error(`❌ 设备 ${deviceAccount} 收到无效JSON: ${raw}`);
          return;
        }

        // 处理错误消息
        if (msg.type === V11_TYPES.ERROR) {
          wsV11Errors.add(1);
          console.error(`❌ 设备 ${deviceAccount} 收到错误消息: ${JSON.stringify(msg)}`);
          return;
        }

        // 处理确认消息
        handleAck(msg, pendingRequests);
        console.log(`✉️ 设备 ${deviceAccount} 收到V11确认 receiptId=${msg.receiptId}, 类型=${msg.type}`);
      });

      // 处理连接错误
      socket.on('error', (error) => {
        wsV11Errors.add(1);
        console.error(`❌ 设备 ${deviceAccount} V11连接错误: ${error}`);
      });

      // 执行操作计划
      const operations = createOperationPlan();
      console.log(`📋 设备 ${deviceAccount} 计划执行 ${operations.length} 个V11操作: [${operations.join(', ')}]`);

      for (const operation of operations) {
        sequence += 1;
        messageCount += 1;
        const messageId = (__VU * 1000000) + sequence;

        // 根据操作类型发送不同消息
        if (operation === 'COMMAND') {
          const message = {
            messageId,
            type: V11_TYPES.COMMAND
          };
          sendV11Message(socket, message, pendingRequests);
          console.log(`📤 设备 ${deviceAccount} 发送V11命令请求 messageId=${messageId}`);
        } else if (operation === 'STATUS') {
          const message = {
            messageId,
            type: V11_TYPES.STATUS_REPORT,
            data: generateTerminalStatusReport(deviceAccount)
          };
          sendV11Message(socket, message, pendingRequests);
          console.log(`📊 设备 ${deviceAccount} 发送V11状态报告 messageId=${messageId}`);
        } else if (operation === 'SENSOR') {
          const message = {
            messageId,
            type: V11_TYPES.MONITOR_REPORT,
            data: generateGpsReports()
          };
          sendV11Message(socket, message, pendingRequests);
          console.log(`📍 设备 ${deviceAccount} 发送V11GPS报告 messageId=${messageId}`);
        }

        // 短间隔休眠（保持消息流活跃，避免Nginx超时）
        const sleepTime = randomFloat(1.0, 2.0);
        sleep(sleepTime);
      }

      // 操作完成后短休眠（确保心跳能够按时发送）
      sleep(randomFloat(1.5, 3.0));
    });

    // 检查连接是否成功
    const ok = check(res, {
      'V11 WebSocket连接升级成功': (r) => r && r.status === 101,
    });
    wsV11ConnectionFailed.add(!ok);

    // 处理连接失败或异常断开
    if (!ok) {
      wsV11Errors.add(1);
      console.error(`❌ 设备 ${deviceAccount} V11连接失败: 状态=${res ? res.status : 'null'}`);
      const retryDelay = randomFloat(0.5, 1.5);  // 快速重试，规避超时
      console.log(`⏱️ 设备 ${deviceAccount} ${retryDelay.toFixed(1)}秒后自动重试V11连接`);
      sleep(retryDelay);
      continue;
    }

    // 连接正常完成后的短暂等待（快速重连保持压力）
    const nextConnectionDelay = randomFloat(0.5, 1.5);
    console.log(`⏱️ 设备 ${deviceAccount} V11测试完成，${nextConnectionDelay.toFixed(1)}秒后重新连接`);
    sleep(nextConnectionDelay);
  }
}

// ==================== 测试生命周期 ====================

export function setup() {
  const setupStartTime = new Date().toISOString();
  console.log('');
  console.log('🚀 开始 WebSocket V11 协议测试');
  console.log(`WebSocket地址: ${serverEnv.websocketUrl.replace('/websocket', '/ColorWebSocket/terminal')}`);
  console.log(`设备账号范围: ${devices.accountPrefix}${deviceRange.startNumber} - ${devices.accountPrefix}${deviceRange.endNumber}`);
  console.log(`测试设备数量: ${deviceAccounts.length}`);
  console.log('');
  console.log('🔧 V11协议特性:');
  console.log('- 协议版本: 1.1 (新终端协议)');
  console.log('- 连接路径: /ColorWebSocket/terminal');
  console.log('- 心跳机制: 空消息心跳，30秒间隔');
  console.log('- 消息格式: 结构化JSON (messageId/type/receiptId/data)');
  console.log('- 支持类型: 命令请求、状态报告、传感器数据、错误处理');
  console.log('- 数据类型: GPS坐标、设备状态、系统信息');
  console.log('');
  console.log('📊 测试场景配置:');
  console.log(`  混合负载: ${scenarioConfig.mixedLoad.vus} VU × ${scenarioConfig.mixedLoad.duration}`);
  console.log(`  峰值突发: ${scenarioConfig.peakBurst.stages[0].target}-${scenarioConfig.peakBurst.stages[scenarioConfig.peakBurst.stages.length - 1].target} VU`);
  console.log('');
  console.log('📋 每次连接执行的操作:');
  console.log('  - COMMAND：命令请求 (type=2)');
  console.log('  - STATUS：设备状态报告 (type=6) - 包含内存、存储、型号等信息');
  console.log('  - SENSOR：GPS传感器数据 (type=9) - 包含坐标、精度、速度等');
  console.log('');
  console.log('性能阈值:');
  console.log(`  命令ACK延迟 P95: ${scenarioConfig.thresholds.command_ack_latency_p95}ms`);
  console.log(`  状态ACK延迟 P95: ${scenarioConfig.thresholds.status_ack_latency_p95}ms`);
  console.log(`  传感器ACK延迟 P95: ${scenarioConfig.thresholds.sensor_ack_latency_p95}ms`);
  console.log('');

  // 健康检查
  const healthCheck = http.get(`${serverEnv.baseUrl}${serverConfig.environments.test.healthCheckPath}`);
  if (healthCheck.status !== 200) {
    console.warn(`⚠️  服务器健康检查失败: ${healthCheck.status}，但继续执行WebSocket V11测试`);
  } else {
    console.log('✅ 服务器健康检查通过');
  }

  console.log('');
  console.log('📋 V11协议消息格式示例:');
  console.log('  心跳消息: "" (空字符串)');
  console.log('  命令请求: {"messageId": 123, "type": 2}');
  console.log('  状态报告: {"messageId": 123, "type": 6, "data": {info: {...}}}');
  console.log('  传感器报告: {"messageId": 123, "type": 9, "data": [{sensorType: "gps", ...}]}');
  console.log('');
  console.log('开始执行测试脚本，实时输出连接、消息和确认信息');
  console.log('');

  return {
    startTime: setupStartTime,
    connectionCount: 0,
    messageCount: 0,
    errorCount: 0
  };
}

export function teardown(data) {
  console.log('');
  console.log('📊 WebSocket V11 协议测试完成');
  console.log(`开始时间: ${data.startTime}`);
  console.log(`结束时间: ${new Date().toISOString()}`);
  console.log('');
  console.log('🔧 V11协议性能指标建议检查：');
  console.log('- WebSocket连接建立成功率和延迟 (目标>90%)');
  console.log('- V11心跳保活机制效果 (30秒间隔)');
  console.log('- 命令请求确认延迟 (目标<800ms P95)');
  console.log('- 状态报告确认延迟 (目标<600ms P95)');
  console.log('- GPS传感器确认延迟 (目标<600ms P95)');
  console.log('- 消息确认机制完整性 (receiptId匹配率)');
  console.log('- 连接持续时间分布');
  console.log('- 服务器资源使用情况 (CPU、内存、网络)');
  console.log('');
  console.log('⚠️ V11协议关键监控点：');
  console.log('- 连接失败率应 < 10%');
  console.log('- 心跳保活成功率应 > 95%');
  console.log('- 消息确认完整率应 > 90%');
  console.log('- 空消息心跳响应时间');
  console.log('- V11协议错误率分析');
  console.log('- messageId和receiptId的匹配率');
  console.log('');
  console.log('📈 性能优化建议：');
  console.log('- 如果心跳失败率高，检查网络稳定性');
  console.log('- 如果确认延迟高，检查服务器处理能力');
  console.log('- 如果连接失败率高，检查V11协议兼容性');
  console.log('- 监控GPS数据处理性能');
  console.log('');
}
