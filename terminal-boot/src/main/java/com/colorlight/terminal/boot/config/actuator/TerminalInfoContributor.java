package com.colorlight.terminal.boot.config.actuator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static com.colorlight.terminal.boot.config.actuator.ActuatorConstant.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义应用信息贡献器
 * 为/actuator/info端点提供丰富的应用信息
 *
 * @author Nan
 */
@Component
public class TerminalInfoContributor implements InfoContributor {

    private final Environment environment;
    private final BuildProperties buildProperties;
    private final GitProperties gitProperties;

    public TerminalInfoContributor(Environment environment,
                                   @Autowired(required = false) BuildProperties buildProperties,
                                   @Autowired(required = false) GitProperties gitProperties) {
        this.environment = environment;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        // 应用基本信息
        Map<String, Object> appInfo = new HashMap<>();
        appInfo.put(ApplicationFields.NAME, DefaultValues.APP_NAME);
        appInfo.put(FieldNames.DESCRIPTION, DefaultValues.APP_DESCRIPTION);
        appInfo.put(ApplicationFields.VERSION, buildProperties != null ? buildProperties.getVersion() : DefaultValues.UNKNOWN_VERSION);
        appInfo.put(ApplicationFields.STARTUP_TIME, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 技术栈信息
        Map<String, Object> techStack = new HashMap<>();
        techStack.put(ApplicationFields.JAVA_VERSION, System.getProperty("java.version"));
        techStack.put(ApplicationFields.SPRING_BOOT_VERSION, buildProperties != null ? buildProperties.get("spring-boot.version") : DefaultValues.DEFAULT_SPRING_BOOT_VERSION);
        techStack.put(ApplicationFields.FRAMEWORK, DefaultValues.DEFAULT_FRAMEWORK);
        techStack.put(ApplicationFields.ARCHITECTURE, DefaultValues.DEFAULT_ARCHITECTURE);

        // 运行环境信息
        Map<String, Object> runtime = new HashMap<>();
        runtime.put(ApplicationFields.ACTIVE_PROFILES, environment.getActiveProfiles());
        runtime.put(ApplicationFields.JVM_NAME, System.getProperty("java.vm.name"));
        runtime.put(ApplicationFields.OS_NAME, System.getProperty("os.name"));
        runtime.put(ApplicationFields.PROCESSORS, Runtime.getRuntime().availableProcessors());

        // 服务端口信息
        Map<String, Object> ports = new HashMap<>();
        ports.put(ApplicationFields.HTTP_PORT, environment.getProperty("server.port", DefaultValues.DEFAULT_HTTP_PORT));
        ports.put(ApplicationFields.WEBSOCKET_PORT, environment.getProperty("terminal.websocket.port", DefaultValues.DEFAULT_WEBSOCKET_PORT));
        ports.put(ApplicationFields.MANAGEMENT_PORT, environment.getProperty("management.server.port", DefaultValues.DEFAULT_MANAGEMENT_PORT));

        // 构建信息（如果可用）
        Map<String, Object> build = new HashMap<>();
        if (buildProperties != null) {
            build.put(ApplicationFields.TIME, buildProperties.getTime());
            build.put(ApplicationFields.ARTIFACT, buildProperties.getArtifact());
            build.put(ApplicationFields.GROUP, buildProperties.getGroup());
        }

        // Git信息（如果可用）
        Map<String, Object> git = new HashMap<>();
        if (gitProperties != null) {
            git.put(ApplicationFields.BRANCH, gitProperties.getBranch());
            git.put(ApplicationFields.COMMIT_ID, gitProperties.getShortCommitId());
            git.put(ApplicationFields.COMMIT_TIME, gitProperties.getCommitTime());
        }

        // 功能特性信息
        Map<String, Object> features = new HashMap<>();
        features.put(ApplicationFields.WEBSOCKET_SUPPORT, true);
        features.put(ApplicationFields.DEVICE_MANAGEMENT, true);
        features.put(ApplicationFields.REAL_TIME_MONITORING, true);
        features.put(ApplicationFields.EVENT_DRIVEN_ARCHITECTURE, true);
        features.put(ApplicationFields.CONNECTION_POOLING, true);
        features.put(ApplicationFields.HEALTH_MONITORING, true);

        // 性能指标
        Map<String, Object> performance = new HashMap<>();
        performance.put(ApplicationFields.MAX_CONCURRENT_CONNECTIONS, DefaultValues.DEFAULT_MAX_CONCURRENT_CONNECTIONS);
        performance.put(ApplicationFields.TARGET_RESPONSE_TIME, DefaultValues.DEFAULT_TARGET_RESPONSE_TIME);
        performance.put(ApplicationFields.HEARTBEAT_INTERVAL, DefaultValues.DEFAULT_HEARTBEAT_INTERVAL);
        performance.put(ApplicationFields.OFFLINE_CHECK_INTERVAL, DefaultValues.DEFAULT_OFFLINE_CHECK_INTERVAL);

        // 组装所有信息
        builder.withDetail(ApplicationFields.APPLICATION, appInfo)
               .withDetail(ApplicationFields.TECHNOLOGY, techStack)
               .withDetail(ApplicationFields.RUNTIME, runtime)
               .withDetail(ApplicationFields.PORTS, ports)
               .withDetail(ApplicationFields.BUILD, build)
               .withDetail(ApplicationFields.GIT, git)
               .withDetail(ApplicationFields.FEATURES, features)
               .withDetail(ApplicationFields.PERFORMANCE, performance);
    }
}