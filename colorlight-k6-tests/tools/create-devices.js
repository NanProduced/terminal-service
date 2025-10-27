#!/usr/bin/env node

/**
 * Colorlight Terminal 设备创建工具
 * 批量创建测试设备账号
 */

const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');

// ==================== 彩色输出工具 ====================

const Colors = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  cyan: '\x1b[36m',
  white: '\x1b[37m',
  bgGreen: '\x1b[42m',
  bgRed: '\x1b[41m',
  bgBlue: '\x1b[44m'
};

function colorize(text, color) {
  return `${color}${text}${Colors.reset}`;
}

function success(text) { return colorize(text, Colors.green); }
function error(text) { return colorize(text, Colors.red); }
function info(text) { return colorize(text, Colors.blue); }
function warn(text) { return colorize(text, Colors.yellow); }
function cyan(text) { return colorize(text, Colors.cyan); }
function bold(text) { return colorize(text, Colors.bold); }

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
 * 生成中国车牌号（更加真实）
 * 格式: 省份(1字) + 城市(1字) + 5位号码
 */
function generatePlateNumber() {
  // 省份简称（常见的31个省份）
  const provinces = ['京', '津', '沪', '渝', '冀', '豫', '云', '辽', '黑', '湘',
                     '皖', '鲁', '新', '苏', '浙', '赣', '鄂', '桂', '甘', '晋',
                     '蒙', '陕', '吉', '闽', '贵', '粤', '青', '藏', '川', '宁', '琼'];

  // 城市代码（A-Y，通常 Z 不用）
  const cityLetters = 'ABCDEFGHJKLMNPQRSTUVWXY';

  // 5位号码生成：70% 纯数字，25% 4数字+1字母，5% 3数字+2字母
  const plateCodePattern = Math.random();
  let plateCode;

  if (plateCodePattern < 0.70) {
    // 70%: 5位纯数字（如 12345）
    plateCode = generateRandomNumbers(5);
  } else if (plateCodePattern < 0.95) {
    // 25%: 4位数字 + 1位字母（如 1234D）
    plateCode = generateRandomNumbers(4) + generateRandomLetter();
  } else {
    // 5%: 3位数字 + 2位字母（如 123BD）
    plateCode = generateRandomNumbers(3) + generateRandomLetters(2);
  }

  // 组合车牌号
  const province = provinces[Math.floor(Math.random() * provinces.length)];
  const city = cityLetters[Math.floor(Math.random() * cityLetters.length)];

  return province + city + plateCode;
}

/**
 * 生成指定个数的随机数字
 */
function generateRandomNumbers(count) {
  let result = '';
  for (let i = 0; i < count; i++) {
    result += Math.floor(Math.random() * 10);
  }
  return result;
}

/**
 * 生成单个随机字母（A-Z，不含 I 和 O，避免与数字混淆）
 */
function generateRandomLetter() {
  const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ'; // 去掉 I, O 以避免混淆
  return letters[Math.floor(Math.random() * letters.length)];
}

/**
 * 生成多个随机字母
 */
function generateRandomLetters(count) {
  let result = '';
  for (let i = 0; i < count; i++) {
    result += generateRandomLetter();
  }
  return result;
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
async function createDevice(username, counter, onResult) {
  const serverUrl = serverConfig.environments.test.baseUrl;
  const apiPath = serverConfig.environments.test.createDeviceApiPath;
  const fullUrl = `${serverUrl}${apiPath}`;
  const token = deviceConfig.authentication.token;
  const password = deviceConfig.deviceCreation.password;
  const terminalGroup = deviceConfig.deviceCreation.terminalGroup;

  // 构造新的扁平格式 requestBody (根据最新API文档)
  const requestBody = {
    title: `k6测试-${counter}`,
    username: username,
    password: password,
    terminalGroup: terminalGroup,
    excerpt: null,
    plateNumber: generatePlateNumber(),
    driverName: generateDriverName(),
    contactPhone: generatePhone()
    // customColumn1-5 不需要填充
  };

  const startTime = Date.now();

  // 首次请求时打印详细的请求信息
  if (firstRequest) {
    console.log('\n' + bold('━'.repeat(70)));
    console.log(bold('📤 首次请求详情'));
    console.log(bold('━'.repeat(70)));
    console.log(`${cyan('URL:')} ${fullUrl}`);
    console.log(`${cyan('Token:')} ${token.substring(0, 20)}...`);
    console.log(`${cyan('请求体格式 (扁平结构):')} `);
    console.log(JSON.stringify(requestBody, null, 2));
    console.log(bold('━'.repeat(70)) + '\n');
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
    const response = await makeRequest(fullUrl, options, requestBodyStr);
    const duration = Date.now() - startTime;

    if (response.body && response.body.code === 200) {
      const result = {
        success: true,
        username: username,
        duration: duration,
        terminalId: response.body.data?.terminalId,
        terminalName: response.body.data?.terminalName,
        accountName: response.body.data?.accountName
      };

      // 实时输出成功信息
      if (onResult) {
        onResult({
          status: 'success',
          username: username,
          duration: duration,
          message: `✅ 成功 | ${username} | ${duration}ms`
        });
      }

      return result;
    } else {
      const result = {
        success: false,
        username: username,
        duration: duration,
        error: response.body?.message || `HTTP ${response.statusCode}`,
        statusCode: response.statusCode,
        fullResponse: response.body
      };

      // 实时输出失败信息
      if (onResult) {
        onResult({
          status: 'failed',
          username: username,
          duration: duration,
          error: response.body?.message,
          message: `❌ 失败 | ${username} | ${response.body?.message || 'HTTP ' + response.statusCode}`
        });
      }

      return result;
    }
  } catch (error) {
    const duration = Date.now() - startTime;
    const result = {
      success: false,
      username: username,
      duration: duration,
      error: error.message,
      stack: error.stack
    };

    // 实时输出错误信息
    if (onResult) {
      onResult({
        status: 'error',
        username: username,
        duration: duration,
        error: error.message,
        message: `⚠️  错误 | ${username} | ${error.message}`
      });
    }

    return result;
  }
}

// ==================== 主程序 ====================

// 实时日志队列，用于显示最近的结果
let recentResults = [];
const MAX_RECENT_RESULTS = 5;

// 清屏函数
function clearLines(count) {
  for (let i = 0; i < count; i++) {
    process.stdout.write('\x1b[1A\x1b[K');
  }
}

// 显示实时统计面板
function displayStatisticsPanel(current, total, successful, failed, avgDuration) {
  const percentage = Math.round((current / total) * 100);
  const progressBar = '█'.repeat(Math.floor(percentage / 2)) + '░'.repeat(50 - Math.floor(percentage / 2));

  console.log('\n' + bold('━'.repeat(70)));
  console.log(bold('📊 实时统计面板'));
  console.log(bold('━'.repeat(70)));
  console.log(`进度: [${success(progressBar)}] ${percentage}% (${current}/${total})`);
  console.log(`成功: ${success(successful)} | 失败: ${failed > 0 ? error(failed) : warn('0')} | 平均耗时: ${avgDuration}ms`);
  console.log(bold('━'.repeat(70)));

  if (recentResults.length > 0) {
    console.log(bold('📝 最近结果 (最后5条):'));
    for (const result of recentResults.slice(-MAX_RECENT_RESULTS)) {
      console.log(`   ${result.message}`);
    }
  }
}

async function main() {
  console.log('\n' + bold('╔' + '═'.repeat(68) + '╗'));
  console.log(bold('║' + ' '.repeat(68) + '║'));
  console.log(bold('║') + cyan('      Colorlight Terminal - 设备批量创建工具 v2.0'.padEnd(66)) + bold('║'));
  console.log(bold('║') + cyan('              实时交互式设备创建系统'.padEnd(66)) + bold('║'));
  console.log(bold('║' + ' '.repeat(68) + '║'));
  console.log(bold('╚' + '═'.repeat(68) + '╝\n'));

  const config = deviceConfig.deviceCreation;
  const batchConfig = deviceConfig.batchSettings;
  const serverUrl = serverConfig.environments.test.baseUrl;
  const apiPath = serverConfig.environments.test.createDeviceApiPath;
  const fullUrl = `${serverUrl}${apiPath}`;
  const token = deviceConfig.authentication.token;

  // 显示服务器配置
  console.log(bold('🔌 服务器配置:'));
  console.log(`   ${cyan('基础地址:')} ${serverUrl}`);
  console.log(`   ${cyan('API路径:')} ${apiPath}`);
  console.log(`   ${cyan('Token:')} ${token.substring(0, 20)}...${token.substring(token.length - 20)}`);
  console.log(`   ${cyan('超时设置:')} ${serverConfig.timeouts.deviceCreation}秒\n`);

  console.log(bold('📋 创建参数:'));
  console.log(`   ${cyan('账号前缀:')} ${config.accountPrefix}`);
  console.log(`   ${cyan('账号范围:')} ${config.accountPrefix}${config.startNumber} ~ ${config.accountPrefix}${config.endNumber}`);
  console.log(`   ${cyan('总数量:')} ${success(config.totalDevices)} 个账号`);
  console.log(`   ${cyan('并发数:')} ${config.concurrencyLimit || batchConfig.concurrencyLimit} 个请求`);
  console.log(`   ${cyan('密码:')} ${config.password}`);
  console.log(`   ${cyan('终端分组:')} ${config.terminalGroup}\n`);

  // 生成账号列表
  const accounts = [];
  for (let i = config.startNumber; i <= config.endNumber; i++) {
    accounts.push(`${config.accountPrefix}${i}`);
  }

  let createdCount = 0;
  let failedCount = 0;
  let totalDuration = 0;
  let failedAccounts = [];
  const startTime = Date.now();

  // 批量创建设备
  console.log(bold('🚀 开始创建设备...\n'));

  const concurrency = batchConfig.concurrencyLimit || 10;

  for (let i = 0; i < accounts.length; i += concurrency) {
    const batch = accounts.slice(i, Math.min(i + concurrency, accounts.length));
    const promises = batch.map((account, index) => {
      const counter = config.startNumber + i + index;

      // 创建带有实时回调的 Promise
      return createDevice(account, counter, (result) => {
        // 实时输出每个请求的结果
        recentResults.push(result);

        // 更新统计数据
        if (result.status === 'success') {
          createdCount++;
          totalDuration += result.duration;
        } else {
          failedCount++;
          failedAccounts.push({
            username: account,
            error: result.error || result.message
          });
        }
      });
    });

    const results = await Promise.all(promises);

    // 统计结果
    for (const result of results) {
      if (!result.success) {
        failedAccounts.push({
          username: result.username,
          error: result.error || '未知错误'
        });
      }
    }

    // 显示实时进度面板
    const processedCount = Math.min(i + concurrency, accounts.length);
    const avgDuration = createdCount > 0 ? Math.round(totalDuration / createdCount) : 0;
    displayStatisticsPanel(processedCount, accounts.length, createdCount, failedCount, avgDuration);

    // 批次间延迟
    if (i + concurrency < accounts.length) {
      await new Promise(resolve => setTimeout(resolve, batchConfig.delayBetweenBatchMs || 1000));
    }
  }

  const endTime = Date.now();
  const durationSeconds = Math.round((endTime - startTime) / 1000);
  const successRate = ((createdCount / accounts.length) * 100).toFixed(2);

  // 最终统计
  console.log('\n' + bold('╔' + '═'.repeat(68) + '╗'));
  console.log(bold('║') + (createdCount === accounts.length ? success('✅ 所有设备创建成功!'.padEnd(66)) : warn('⚠️  设备创建完成 (含失败)'.padEnd(66))) + bold('║'));
  console.log(bold('╚' + '═'.repeat(68) + '╝\n'));

  console.log(bold('📊 最终统计:'));
  console.log(`   成功: ${success(createdCount)} 个 | 失败: ${failedCount > 0 ? error(failedCount) : warn('0')} 个`);
  console.log(`   成功率: ${successRate >= 100 ? success(successRate + '%') : warn(successRate + '%')}`);
  console.log(`   平均耗时: ${avgDuration}ms | 总耗时: ${durationSeconds}秒 (${Math.round(durationSeconds / 60)}分钟)`);
  console.log(`   吞吐量: ${(accounts.length / durationSeconds).toFixed(2)} 个/秒\n`);

  // 显示失败的账号
  if (failedCount > 0) {
    console.log(bold('❌ 失败的账号列表:'));
    const failedToShow = failedAccounts.slice(0, 20);
    for (const failed of failedToShow) {
      console.log(`   ${error('✗')} ${failed.username}: ${failed.error}`);
    }
    if (failedCount > 20) {
      console.log(warn(`   ... 还有 ${failedCount - 20} 个失败账号\n`));
    }
    console.log();
  }

  // 保存设备列表
  const deviceListPath = path.join(__dirname, '..', 'devices', 'device-list.txt');
  const deviceListDir = path.dirname(deviceListPath);

  if (!fs.existsSync(deviceListDir)) {
    fs.mkdirSync(deviceListDir, { recursive: true });
  }

  const listContent = `# Colorlight Terminal 测试设备列表
# 创建时间: ${new Date().toLocaleString()}
# 成功: ${createdCount} / 失败: ${failedCount} / 成功率: ${successRate}%
# 总耗时: ${durationSeconds}秒

${accounts.join('\n')}
`;
  fs.writeFileSync(deviceListPath, listContent);

  console.log(`📁 设备列表已保存到: ${info(deviceListPath)}\n`);

  // 设置退出码
  process.exit(failedCount > 0 ? 1 : 0);
}

main().catch(error => {
  console.error('❌ 执行失败:', error.message);
  process.exit(1);
});
