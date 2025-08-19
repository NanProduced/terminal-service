package com.colorlight.terminal.boot.config.web;

import com.colorlight.terminal.boot.interceptor.DeviceStatusUpdateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 * 
 * @author Nan
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final DeviceStatusUpdateInterceptor deviceStatusUpdateInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册设备状态更新拦截器 每次http请求都刷新最后在线时间
        registry.addInterceptor(deviceStatusUpdateInterceptor)
                .addPathPatterns("/wp-json/**")  // 播放盒交互API路径
                .excludePathPatterns(
                        "/actuator/**",          // 监控端点
                        "/error",                // 错误页面
                        "/health",               // 健康检查
                        "/swagger-ui/**",        // API文档
                        "/v3/api-docs/**"        // API文档
                );
    }
}