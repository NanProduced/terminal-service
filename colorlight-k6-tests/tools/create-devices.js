#!/usr/bin/env node

/**
 * Colorlight Terminal 设备创建工具
 * 批量创建测试设备账号
 */

const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');

// ==================== 配置加载 ====================

function loadConfig(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`❌ 无法加载配置文件: ${filePath}`);
    console.error(error.message);
    process.exit(1);
  }
}

const configDir = path.join(__dirname, '..', 'config');
const serverConfig = loadConfig(path.join(configDir, 'server-config.json'));
const deviceConfig = loadConfig(path.join(configDir, 'device-config.json'));

// 调试：检查配置是否正确加载
console.log('🔍 配置加载检查:');
console.log(`   terminalGroup 值: ${deviceConfig.deviceCreation.terminalGroup}`);
console.log(`   terminalGroup 类型: ${typeof deviceConfig.deviceCreation.terminalGroup}`);
console.log(`   terminalGroup 是否为空: ${!deviceConfig.deviceCreation.terminalGroup}`);
console.log(`   password 值: ${deviceConfig.deviceCreation.password}`);
console.log(`   accountPrefix 值: ${deviceConfig.deviceCreation.accountPrefix}`);

// ==================== 数据生成函数 ====================

/**
 * 生成中国车牌号
 */
function generatePlateNumber() {
  const provincePrefix = "京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领";
  const letters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  const numbersAndLetters = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  const lastChar = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ挂学警港澳";

  const province = provincePrefix[Math.floor(Math.random() * provincePrefix.length)];
  const letter = letters[Math.floor(Math.random() * letters.length)];

  let rest = "";
  for (let i = 0; i < 5; i++) {
    rest += numbersAndLetters[Math.floor(Math.random() * numbersAndLetters.length)];
  }

  if (Math.random() > 0.5) {
    rest += lastChar[Math.floor(Math.random() * lastChar.length)];
  }

  return province + letter + rest;
}

/**
 * 生成11位手机号
 */
function generatePhone() {
  const prefixes = [
    "138", "139", "130", "131", "132", "133", "134", "135", "136", "137",
    "145", "147", "149", "150", "151", "152", "153", "155", "156", "157",
    "158", "159", "166", "170", "171", "173", "175", "176", "177", "178",
    "180", "181", "182", "183", "184", "185", "186", "187", "188", "189",
    "198", "199"
  ];

  const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];
  let suffix = '';
  for (let i = 0; i < 8; i++) {
    suffix += Math.floor(Math.random() * 10);
  }
  return prefix + suffix;
}

/**
 * 生成司机名称
 */
function generateDriverName() {
  const surnames = "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季";
  const givenNames = "一二三四五六七八九十百千万甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥小大春夏天秋冬风光晴朗明亮智慧勇敢善良忠厚俊秀美玉明珠玲珑剔透清澈纯真佳颖嘉颖莹怡仪亦宜逸译芷蕾玫磊梓致倪钰瑜途璞瞿邱芙露惠徽瑰瞾浩郁妤笙柳洋茜溏襄谅康郎明黎学友富城德华鹤棣令山伟文夕千嬅秀文咏琪惠美粒杰伦俊杰霆锋菲志斌智斌纪凌尘融卫兰蔚蓝雨霏少芬俪博一诺依诺欣怡梓涵诗涵欣妍雨桐佳怡佳琪梓萱浩宇浩然宇轩宇航铭泽子墨梓豪子睿子轩梓睿靳婷涵予";

  const surname = surnames[Math.floor(Math.random() * surnames.length)];
  const given1 = givenNames[Math.floor(Math.random() * givenNames.length)];
  const given2 = givenNames[Math.floor(Math.random() * givenNames.length)];

  return surname + given1 + given2;
}

// ==================== HTTP请求函数 ====================

/**
 * 发送HTTP请求创建设备
 */
function makeRequest(url, options, postData) {
  return new Promise((resolve, reject) => {
    const isHttps = url.startsWith('https');
    const client = isHttps ? https : http;

    const req = client.request(url, options, (res) => {
      let data = '';

      res.on('data', (chunk) => {
        data += chunk;
      });

      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          resolve({
            statusCode: res.statusCode,
            headers: res.headers,
            body: parsed
          });
        } catch (error) {
          resolve({
            statusCode: res.statusCode,
            headers: res.headers,
            body: data,
            parseError: error.message
          });
        }
      });
    });

    req.on('error', (error) => {
      console.error(`\n❌ 网络连接错误:`);
      console.error(`   URL: ${url}`);
      console.error(`   错误: ${error.message}`);
      console.error(`   代码: ${error.code}`);
      reject(error);
    });

    req.on('timeout', () => {
      console.error(`\n❌ 请求超时:`);
      console.error(`   URL: ${url}`);
      console.error(`   超时: ${options.timeout}ms`);
      req.destroy();
      reject(new Error('Request timeout'));
    });

    if (postData) {
      req.write(postData);
    }

    req.end();
  });
}

// 全局标志用于首次请求
let firstRequest = true;

/**
 * 创建单个设备
 */
async function createDevice(username, counter) {
  const serverUrl = serverConfig.environments.test.baseUrl;
  const apiPath = serverConfig.environments.test.createDeviceApiPath;
  const fullUrl = `${serverUrl}${apiPath}`;
  const token = deviceConfig.authentication.token;
  const password = deviceConfig.deviceCreation.password;
  const terminalGroup = deviceConfig.deviceCreation.terminalGroup;

  // 构造新的扁平格式 requestBody (根据最新API文档)
  const requestBody = {
    title: `测试终端-${counter}`,
    username: username,
    password: password,
    terminalGroup: terminalGroup,
    excerpt: null,
    plateNumber: generatePlateNumber(),
    driverName: generateDriverName(),
    contactPhone: generatePhone()
    // customColumn1-5 不需要填充
  };

  // 首次请求时打印详细的请求信息
  if (firstRequest) {
    const requestBodyJson = JSON.stringify(requestBody);
    console.log('\n📤 首次请求详情:');
    console.log(`   URL: ${fullUrl}`);
    console.log(`   方法: POST`);
    console.log(`   请求头:`);
    console.log(`      token: ${token.substring(0, 20)}...`);
    console.log(`      Content-Type: application/json`);
    console.log(`      User-Agent: Colorlight-K6-DeviceCreator/1.0`);
    console.log(`\n   请求体 (扁平格式):`);
    console.log(JSON.stringify(requestBody, null, 2));
    console.log(`\n   JSON序列化验证:`);
    console.log(`   ${requestBodyJson}`);
    console.log(`\n   字段值检查:`);
    console.log(`   - title: "${requestBody.title}" (类型: ${typeof requestBody.title})`);
    console.log(`   - username: "${requestBody.username}" (类型: ${typeof requestBody.username})`);
    console.log(`   - password: "${requestBody.password}" (类型: ${typeof requestBody.password})`);
    console.log(`   - terminalGroup: ${requestBody.terminalGroup} (类型: ${typeof requestBody.terminalGroup})`);
    console.log(`   - plateNumber: "${requestBody.plateNumber}" (类型: ${typeof requestBody.plateNumber})`);
    console.log(`   - contactPhone: "${requestBody.contactPhone}" (类型: ${typeof requestBody.contactPhone})\n`);
    firstRequest = false;
  }

  const options = {
    method: 'POST',
    headers: {
      'token': token,
      'Content-Type': 'application/json',
      'User-Agent': 'Colorlight-K6-DeviceCreator/1.0'
    },
    timeout: parseInt(serverConfig.timeouts.deviceCreation) * 1000
  };

  try {
    const requestBodyStr = JSON.stringify(requestBody);

    // 前几次请求时也打印 JSON 字符串，验证序列化是否正确
    if (counter <= 5) {
      console.log(`\n[请求 #${counter}] ${username}`);
      console.log(`   实际发送的JSON: ${requestBodyStr}`);
      console.log(`   JSON长度: ${requestBodyStr.length} 字符`);
    }

    const response = await makeRequest(fullUrl, options, requestBodyStr);

    if (response.body && response.body.code === 200) {
      return {
        success: true,
        username: username,
        terminalId: response.body.data?.terminalId,
        terminalName: response.body.data?.terminalName,
        accountName: response.body.data?.accountName
      };
    } else {
      console.error(`\n❌ 创建设备失败 [${username}]`);
      console.error(`   URL: ${fullUrl}`);
      console.error(`   HTTP状态码: ${response.statusCode}`);
      console.error(`   响应码: ${response.body?.code || 'N/A'}`);
      console.error(`   错误信息: ${response.body?.message || '无详细信息'}`);
      console.error(`   完整响应: ${JSON.stringify(response.body, null, 2)}`);

      return {
        success: false,
        username: username,
        error: response.body?.message || `HTTP ${response.statusCode}`,
        statusCode: response.statusCode,
        fullResponse: response.body
      };
    }
  } catch (error) {
    console.error(`\n❌ 网络错误或异常 [${username}]`);
    console.error(`   错误信息: ${error.message}`);
    console.error(`   错误堆栈: ${error.stack}`);

    return {
      success: false,
      username: username,
      error: error.message,
      stack: error.stack
    };
  }
}

// ==================== 主程序 ====================

async function main() {
  console.log('\n╔════════════════════════════════════════════════════════════════╗');
  console.log('║      Colorlight Terminal - 测试设备创建工具 v1.0              ║');
  console.log('║              批量创建测试设备账号                            ║');
  console.log('╚════════════════════════════════════════════════════════════════╝\n');

  const config = deviceConfig.deviceCreation;
  const batchConfig = deviceConfig.batchSettings;
  const serverUrl = serverConfig.environments.test.baseUrl;
  const apiPath = serverConfig.environments.test.createDeviceApiPath;
  const fullUrl = `${serverUrl}${apiPath}`;
  const token = deviceConfig.authentication.token;

  // 显示服务器配置
  console.log('🔌 服务器配置:');
  console.log(`   基础地址: ${serverUrl}`);
  console.log(`   API路径: ${apiPath}`);
  console.log(`   完整URL: ${fullUrl}`);
  console.log(`   Token: ${token.substring(0, 20)}...${token.substring(token.length - 20)}`);
  console.log(`   设备创建超时: ${serverConfig.timeouts.deviceCreation}\n`);

  console.log('📋 创建参数:');
  console.log(`   账号前缀: ${config.accountPrefix}`);
  console.log(`   账号范围: ${config.accountPrefix}${config.startNumber} ~ ${config.accountPrefix}${config.endNumber}`);
  console.log(`   总数量: ${config.totalDevices} 个账号`);
  console.log(`   并发数: ${batchConfig.concurrencyLimit} 个请求`);
  console.log(`   密码: ${config.password}`);
  console.log(`   终端分组: ${config.terminalGroup}\n`);

  // 生成账号列表
  const accounts = [];
  for (let i = config.startNumber; i <= config.endNumber; i++) {
    accounts.push(`${config.accountPrefix}${i}`);
  }

  let createdCount = 0;
  let failedCount = 0;
  let failedAccounts = [];
  const startTime = Date.now();

  // 批量创建设备
  console.log('🚀 开始创建设备...\n');

  for (let i = 0; i < accounts.length; i += batchConfig.concurrencyLimit) {
    const batch = accounts.slice(i, Math.min(i + batchConfig.concurrencyLimit, accounts.length));
    const promises = batch.map((account, index) =>
      createDevice(account, config.startNumber + i + index)
    );

    const results = await Promise.all(promises);

    // 统计结果
    for (const result of results) {
      if (result.success) {
        createdCount++;
      } else {
        failedCount++;
        failedAccounts.push({
          username: result.username,
          error: result.error
        });
      }
    }

    // 显示进度
    const processedCount = Math.min(i + batchConfig.concurrencyLimit, accounts.length);
    const percentage = Math.round((processedCount / accounts.length) * 100);
    const progressBar = '█'.repeat(Math.floor(percentage / 2)) + '░'.repeat(50 - Math.floor(percentage / 2));
    process.stdout.write(`\r[${progressBar}] ${percentage}% (${processedCount}/${accounts.length})`);

    // 批次间延迟
    if (i + batchConfig.concurrencyLimit < accounts.length) {
      await new Promise(resolve => setTimeout(resolve, batchConfig.delayBetweenBatchMs));
    }
  }

  const endTime = Date.now();
  const durationSeconds = Math.round((endTime - startTime) / 1000);

  // 完成统计
  console.log('\n\n✅ 设备创建完成!\n');
  console.log('📊 创建统计:');
  console.log(`   成功: ${createdCount} 个`);
  console.log(`   失败: ${failedCount} 个`);
  console.log(`   总耗时: ${durationSeconds} 秒 (${Math.round(durationSeconds / 60)} 分钟)`);
  console.log(`   成功率: ${((createdCount / accounts.length) * 100).toFixed(2)}%\n`);

  if (failedCount > 0 && failedCount <= 20) {
    console.log('⚠️  失败的账号:');
    for (const failed of failedAccounts.slice(0, 20)) {
      console.log(`   - ${failed.username}: ${failed.error}`);
    }
    if (failedCount > 20) {
      console.log(`   ... 还有 ${failedCount - 20} 个失败\n`);
    }
  }

  // 保存设备列表
  const deviceListPath = path.join(__dirname, '..', 'devices', 'device-list.txt');
  const deviceListDir = path.dirname(deviceListPath);

  if (!fs.existsSync(deviceListDir)) {
    fs.mkdirSync(deviceListDir, { recursive: true });
  }

  const listContent = `# Colorlight Terminal 测试设备列表\n# 创建时间: ${new Date().toLocaleString()}\n# 总数: ${createdCount}\n\n${accounts.join('\n')}\n`;
  fs.writeFileSync(deviceListPath, listContent);

  console.log(`📁 设备列表已保存到: ${deviceListPath}\n`);

  // 设置退出码
  process.exit(failedCount > 0 ? 1 : 0);
}

main().catch(error => {
  console.error('❌ 执行失败:', error.message);
  process.exit(1);
});
