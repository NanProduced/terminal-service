package com.colorlight.terminal.boot.config.security.filter;

import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 终端设备认证过滤器
 * <p>继承BasicAuthenticationFilter，专门处理终端设备的Basic Auth认证</p>
 *
 * @author Nan
 */
@Slf4j
public class TerminalBasicAuthFilter extends BasicAuthenticationFilter {

    public TerminalBasicAuthFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

        // 检查是否需要认证
        if (!requiresAuthentication(request)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.toLowerCase().startsWith("basic")) {
            // 没有Basic Auth请求头，继续执行过滤链
            chain.doFilter(request, response);
            return;
        }

        try {

            UsernamePasswordAuthenticationToken authRequest = parseAuthenticationHeader(header);
            if (authRequest != null) {

                Authentication authenticate = this.getAuthenticationManager().authenticate(authRequest);

                SecurityContextHolder.getContext().setAuthentication(authenticate);

                if (log.isDebugEnabled()) log.debug("设备认证成功: deviceId={}, uri={}", authenticate.getName(), request.getRequestURI());

            }

        } catch (AuthenticationException e) {
            log.warn("设备认证失败 - uri={}, error={}", request.getRequestURI(), e.getMessage());
            // 清除安全上下文
            SecurityContextHolder.clearContext();
            throw new DeviceResponseException(CommonErrorCode.AUTHENTICATION_FAILED);
        }

        super.doFilterInternal(request, response, chain);
    }

    private boolean requiresAuthentication(HttpServletRequest request) {

        String uri = request.getRequestURI();

        return !uri.startsWith("/actuator");
    }

    private UsernamePasswordAuthenticationToken parseAuthenticationHeader(String header) {
        try {
            // 提取Base64编码部分
            String base64Token = header.substring(6); // "Basic " = 6 characters
            byte[] decoded = Base64.getDecoder().decode(base64Token);
            String token = new String(decoded, StandardCharsets.UTF_8);

            // 分割用户名和密码
            int delim = token.indexOf(":");
            if (delim == -1) {
                log.debug("解析Basic-Auth请求头：Basic Auth格式错误：缺少冒号分隔符");
                return null;
            }

            String account = token.substring(0, delim);
            String password = token.substring(delim + 1);

            // 验证设备ID和密码不为空
            if (StringUtils.isBlank(account) || StringUtils.isBlank(password)) {
                log.debug("解析Basic-Auth请求头：Basic Auth格式错误：设备ID或密码为空");
                return null;
            }

            // 创建认证令牌
            return new UsernamePasswordAuthenticationToken(account, password);
        } catch (IllegalArgumentException e) {
            log.debug("解析Basic-Auth请求头：Basic Auth解码失败", e);
            return null;
        } catch (Exception e) {
            log.warn("解析Basic-Auth请求头：解析Basic Auth头异常", e);
            return null;
        }
    }
}
