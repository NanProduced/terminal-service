package com.colorlight.terminal.boot.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class HttpUtils {

    private HttpUtils() {}

    public static String getClientIp(HttpServletRequest request) {
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

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (!ObjectUtils.isEmpty(requestAttributes)) {
            return requestAttributes.getRequest();
        } else {
            return null;
        }
    }

    public static String getClientIp() {
        HttpServletRequest request = getRequest();
        if (request != null) {
            return getClientIp(request);
        } else {
            return null;
        }
    }
}
