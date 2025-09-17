package com.colorlight.terminal.boot.config.metrics;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于创建MeterRegistryCustomizer、TimedAspect和CountedAspect实例
 *
 * @author Nan
 */
@Configuration
@EnableConfigurationProperties
public class MetricsConfig {

    /**
     * 创建一个MeterRegistryCustomizer bean，用于为所有指标添加通用标签
     * 
     * @return MeterRegistryCustomizer对象，配置了应用名称和版本号作为通用标签
     */
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "terminal-service",
                "version", getClass().getPackage().getImplementationVersion());
    }

    /**
     * 创建TimedAspect实例，用于支持@Timed注解进行方法执行时间统计
     * 
     * @param registry MeterRegistry对象，用于注册和管理指标
     * @return TimedAspect实例
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry){
        return new TimedAspect(registry);
    }

    /**
     * 创建CountedAspect实例，用于支持@Counted注解进行方法调用计数统计
     * 
     * @param registry MeterRegistry对象，用于注册和管理指标
     * @return CountedAspect实例
     */
    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }

    
}
