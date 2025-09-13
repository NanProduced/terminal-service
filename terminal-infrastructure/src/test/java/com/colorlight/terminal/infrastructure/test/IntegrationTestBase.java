package com.colorlight.terminal.infrastructure.test;

import com.colorlight.terminal.commons.test.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Infrastructure层集成测试基础类
 * 提供TestContainers的通用配置和数据库支持
 * 
 * @author Nan
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(TestConfig.class)
public abstract class IntegrationTestBase {
    
    /**
     * Redis容器
     */
    @Container
    protected static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);
    
    /**
     * MySQL容器
     */
    @Container
    protected static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("terminal_test")
            .withUsername("test")
            .withPassword("test123")
            .withReuse(true);
    
    /**
     * MongoDB容器
     */
    @Container
    protected static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer(
            DockerImageName.parse("mongo:6.0"))
            .withReuse(true);
    
    /**
     * 动态配置数据源属性
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis配置
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
        
        // MySQL配置
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        
        // MongoDB配置
        registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getReplicaSetUrl);
        
        // JPA配置
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "true");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
    }
    
    /**
     * 每个测试方法执行前的准备工作
     */
    @BeforeEach
    void setUpIntegrationTest() {
        // 可以在这里添加集成测试的准备工作
        prepareIntegrationTestData();
    }
    
    /**
     * 准备集成测试数据 - 子类可以重写此方法
     */
    protected void prepareIntegrationTestData() {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 获取Redis连接信息
     */
    protected String getRedisHost() {
        return REDIS_CONTAINER.getHost();
    }
    
    protected Integer getRedisPort() {
        return REDIS_CONTAINER.getMappedPort(6379);
    }
    
    /**
     * 获取MySQL连接信息
     */
    protected String getMySQLJdbcUrl() {
        return MYSQL_CONTAINER.getJdbcUrl();
    }
    
    /**
     * 获取MongoDB连接信息
     */
    protected String getMongoUri() {
        return MONGO_CONTAINER.getReplicaSetUrl();
    }
}