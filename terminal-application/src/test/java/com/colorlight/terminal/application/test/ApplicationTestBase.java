package com.colorlight.terminal.application.test;

import com.colorlight.terminal.commons.test.TestConfig;
import com.colorlight.terminal.commons.test.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Application层测试基础类
 * 提供应用层测试的通用配置和工具方法
 * 
 * @author Nan
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class ApplicationTestBase {
    
    // TestDataBuilder是工具类，使用静态方法
    
    /**
     * 每个测试方法执行前的准备工作
     */
    @BeforeEach
    void setUpBase() {
        // 可以在这里添加通用的测试准备工作
        prepareTestData();
    }
    
    /**
     * 准备测试数据 - 子类可以重写此方法
     */
    protected void prepareTestData() {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 创建测试用设备ID
     */
    protected Long createTestDeviceId() {
        return TestDataBuilder.createTestDeviceId();
    }
    
    /**
     * 创建测试用指令ID
     */
    protected Integer createTestCommandId() {
        return TestDataBuilder.createTestCommandId();
    }
}