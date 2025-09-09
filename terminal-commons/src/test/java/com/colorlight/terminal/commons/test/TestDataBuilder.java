package com.colorlight.terminal.commons.test;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 测试数据构建器 - 提供统一的测试数据创建方法
 * 
 * @author Nan
 */
public class TestDataBuilder {
    
    private static final Random RANDOM = new Random();
    
    /**
     * 生成测试用设备ID
     */
    public static Long createTestDeviceId() {
        return 10000L + RANDOM.nextInt(90000);
    }
    
    /**
     * 生成测试用设备序列号
     */
    public static String createTestDeviceSerial() {
        return "TEST_DEVICE_" + (1000 + RANDOM.nextInt(9000));
    }
    
    /**
     * 生成测试用指令ID
     */
    public static Integer createTestCommandId() {
        return 1000 + RANDOM.nextInt(9000);
    }
    
    /**
     * 生成测试用时间戳
     */
    public static LocalDateTime createTestDateTime() {
        return LocalDateTime.now().minusSeconds(RANDOM.nextInt(3600));
    }
    
    /**
     * 生成测试用JSON字符串
     */
    public static String createTestJsonData() {
        return String.format("""
            {
                "deviceId": "%s",
                "timestamp": "%s",
                "status": "online",
                "data": "test_data_%d"
            }
            """, 
            createTestDeviceSerial(),
            createTestDateTime(),
            RANDOM.nextInt(1000)
        );
    }
    
    /**
     * 生成测试用LED状态数据
     */
    public static String createTestLedStatusData() {
        return String.format("""
            {
                "brightness": %d,
                "temperature": %d,
                "power": %s,
                "errors": []
            }
            """,
            50 + RANDOM.nextInt(50),
            20 + RANDOM.nextInt(30),
            RANDOM.nextBoolean() ? "on" : "off"
        );
    }
    
    /**
     * 生成测试用认证信息
     */
    public static String createTestAuthCredentials(String deviceId, String password) {
        return java.util.Base64.getEncoder()
            .encodeToString((deviceId + ":" + password).getBytes());
    }
    
    /**
     * 私有构造函数，防止实例化
     */
    private TestDataBuilder() {
        // 工具类不需要实例化
    }
}