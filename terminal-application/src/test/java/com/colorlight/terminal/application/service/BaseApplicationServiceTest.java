package com.colorlight.terminal.application.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 应用服务测试基类
 * 
 * <p>提供通用的测试配置和工具方法，所有ApplicationService测试都应继承此类</p>
 * 
 * <h3>提供的功能</h3>
 * <ul>
 *   <li>Mockito扩展配置</li>
 *   <li>通用测试工具方法</li>
 *   <li>统一的测试规范</li>
 * </ul>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseApplicationServiceTest {
    
    /**
     * 通用测试常量
     */
    protected static final Long TEST_DEVICE_ID = 10001L;
    protected static final String TEST_ACCOUNT_NAME = "test_account";
    protected static final String TEST_PASSWORD = "test_password";
    protected static final String TEST_CLIENT_IP = "192.168.1.100";
    
    /**
     * 创建测试用的时间戳
     * @return 当前时间戳
     */
    protected long createTestTimestamp() {
        return System.currentTimeMillis();
    }
    
    /**
     * 创建测试用的设备ID
     * @param suffix 后缀
     * @return 测试设备ID
     */
    protected Long createTestDeviceId(int suffix) {
        return 10000L + suffix;
    }
}