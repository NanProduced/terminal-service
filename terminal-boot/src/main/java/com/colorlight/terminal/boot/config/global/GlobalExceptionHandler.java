package com.colorlight.terminal.boot.config.global;

import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 设备请求响应处理，设备只需要HttpStatus
     * @param ex
     * @param request
     * @return
     */
    @ExceptionHandler(DeviceResponseException.class)
    public ResponseEntity<Void> handleDeviceException( DeviceResponseException ex, HttpServletRequest request) {
        // todo: 记录异常日志
        // 返回空响应体 + HTTP状态码
        return ResponseEntity
                .status(ex.getHttpStatus().getValue())
                .build();
    }

}
