package com.colorlight.terminal.boot.interceptor;

import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.infrastructure.security.authentication.TerminalPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 设备状态更新拦截器
 * 自动拦截设备HTTP请求并更新在线状态
 * 
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStatusUpdateInterceptor implements HandlerInterceptor {
    
    private final DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;
    
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                           @NonNull HttpServletResponse response,
                           @NonNull Object handler) throws Exception {
        
        try {
            // 获取当前认证的设备信息
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            
            if (principal instanceof TerminalPrincipal terminalPrincipal) {
                Long deviceId = terminalPrincipal.getDeviceId();
                String clientIp = getClientIp(request);
                
                // 更新设备最后上报时间
                deviceOnlineStatusUseCase.updateLastReportTime(deviceId, ReportSource.HTTP, clientIp);
                
                log.debug("Interceptor - HTTP请求更新设备状态: deviceId={}, uri={}, clientIp={}",
                        deviceId, request.getRequestURI(), clientIp);
            }
            
        } catch (Exception e) {
            // 不要因为状态更新失败而影响业务请求
            log.error("Interceptor - HTTP请求状态更新失败: uri={}", request.getRequestURI(), e);
        }
        
        return true;
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        // 依次检查各种代理头
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 取第一个IP（可能是逗号分隔的多个IP）
                return ip.split(",")[0].trim();
            }
        }
        
        // 最后使用 RemoteAddr
        return request.getRemoteAddr();
    }
}