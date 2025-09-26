package com.colorlight.terminal.boot.config.security;

import com.colorlight.terminal.boot.config.security.filter.TerminalBasicAuthFilter;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;


/**
 * Terminal Service安全配置
 *
 * 针对终端设备每次请求都携带Basic Auth的特点进行优化：
 * 1. 完全无状态设计 - 不创建任何会话
 * 2. 高性能认证 - 本地缓存认证结果，减少数据库查询
 *
 * @author Nan
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain terminalSecurityFilterChain(HttpSecurity http,
                                                           TerminalBasicAuthFilter terminalBasicAuthFilter) throws Exception {

        http
            // 终端设备无需CSRF保护
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            // 终端无法保存Session - 完全无状态
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // HTTP安全头配置 - 兼容Spring Security 6.1+
            .headers(headers -> headers
                    // 防止页面被嵌入iframe，避免点击劫持攻击
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    // 启用MIME类型嗅探保护，防止MIME类型混淆攻击
                    .contentTypeOptions(Customizer.withDefaults())
                    // 配置HSTS，强制HTTPS连接
                    .httpStrictTransportSecurity(hsts -> hsts
                            .maxAgeInSeconds(31536000)  // 1年
                            .includeSubDomains(true))
                    // 禁用缓存敏感页面
                    .cacheControl(Customizer.withDefaults())
            )

            .authorizeHttpRequests(auth -> auth
                    // 监测端点无需认证
                    .requestMatchers("/actuator/**").permitAll()
                    // RPC接口无需认证
                    .requestMatchers("/rpc/**").permitAll()
                    // 终端Http请求需要认证
                    .requestMatchers("/wp-json/**").authenticated()
                    // todo:测试接口，记得关闭
                    .requestMatchers("/test/**").permitAll()
                    .anyRequest().authenticated()
            )
            // 使用自定义Basic Auth认证过滤
            .addFilterBefore(terminalBasicAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Basic Auth配置
            .httpBasic(basic -> basic
                    .authenticationEntryPoint(basicAuthenticationEntryPoint())
            );

        return http.build();
    }


    /**
     * 密码编码器
     * 优化强度，针对随机生成的设备凭据进行性能优化
     * 强度4对随机凭据提供充分安全性，同时大幅提升性能(~100x)
     * @return
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4);
    }

    /**
     * 认证管理器
     * @param http
     * @param authenticationProvider
     * @return
     * @throws Exception
     */
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http,
                                                       TerminalAuthenticationProvider authenticationProvider) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(authenticationProvider);
        return authenticationManagerBuilder.build();
    }

    /**
     * Basic Auth认证入口
     * @return
     */
    @Bean
    public BasicAuthenticationEntryPoint basicAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("colorlight-terminal");
        return entryPoint;
    }

    /**
     * Basic Auth认证过滤器
     * @param authenticationManager
     * @return
     */
    @Bean
    public TerminalBasicAuthFilter terminalBasicAuthFilter(AuthenticationManager authenticationManager) {
        return new TerminalBasicAuthFilter(authenticationManager);
    }
}