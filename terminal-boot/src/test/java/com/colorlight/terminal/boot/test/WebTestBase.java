package com.colorlight.terminal.boot.test;

import com.colorlight.terminal.commons.test.TestConfig;
import com.colorlight.terminal.commons.test.TestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * Boot层Web测试基础类
 * 提供MockMvc和Spring Security测试的通用配置
 * 
 * @author Nan
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class WebTestBase {
    
    @Autowired
    protected MockMvc mockMvc;
    
    @Autowired
    protected ObjectMapper objectMapper;
    
    /**
     * 测试用设备认证信息
     */
    protected static final String TEST_DEVICE_ID = "TEST_DEVICE_12345";
    protected static final String TEST_DEVICE_PASSWORD = "test_password";
    
    /**
     * 每个测试方法执行前的准备工作
     */
    @BeforeEach
    void setUpWebTest() {
        prepareWebTestData();
    }
    
    /**
     * 准备Web测试数据 - 子类可以重写此方法
     */
    protected void prepareWebTestData() {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 创建带Basic认证的GET请求
     */
    protected MockHttpServletRequestBuilder authenticatedGet(String urlTemplate, Object... uriVars) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get(urlTemplate, uriVars)
            .with(httpBasic(TEST_DEVICE_ID, TEST_DEVICE_PASSWORD));
    }
    
    /**
     * 创建带Basic认证的POST请求
     */
    protected MockHttpServletRequestBuilder authenticatedPost(String urlTemplate, Object... uriVars) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post(urlTemplate, uriVars)
            .with(httpBasic(TEST_DEVICE_ID, TEST_DEVICE_PASSWORD));
    }
    
    /**
     * 创建带Basic认证的JSON POST请求
     */
    protected MockHttpServletRequestBuilder authenticatedJsonPost(String urlTemplate, Object requestBody) {
        try {
            return authenticatedPost(urlTemplate)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }
    
    /**
     * 创建带Basic认证的纯文本POST请求
     */
    protected MockHttpServletRequestBuilder authenticatedTextPost(String urlTemplate, String content) {
        return authenticatedPost(urlTemplate)
            .contentType(MediaType.TEXT_PLAIN)
            .content(content);
    }
    
    /**
     * 创建测试用设备认证
     */
    protected SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor deviceAuth() {
        return SecurityMockMvcRequestPostProcessors.user(TEST_DEVICE_ID)
            .password(TEST_DEVICE_PASSWORD)
            .roles("DEVICE");
    }
    
    /**
     * 获取测试用Basic认证头
     */
    protected String getTestAuthHeader() {
        return TestDataBuilder.createTestAuthCredentials(TEST_DEVICE_ID, TEST_DEVICE_PASSWORD);
    }
    
    /**
     * 将对象转换为JSON字符串
     */
    protected String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }
    
    /**
     * 将JSON字符串转换为对象
     */
    protected <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to object", e);
        }
    }
}