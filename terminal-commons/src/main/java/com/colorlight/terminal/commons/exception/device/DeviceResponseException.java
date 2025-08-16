package com.colorlight.terminal.commons.exception.device;

import com.colorlight.terminal.commons.exception.BaseException;
import com.colorlight.terminal.commons.exception.ErrorCode;

/**
 * 响应设备Http请求的异常类
 * <p>设备只关注响应的HttpStatus</p>
 *
 * @author Nan
 */
public class DeviceResponseException extends BaseException {

    public DeviceResponseException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DeviceResponseException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DeviceResponseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public DeviceResponseException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    @Override
    public boolean isBusinessException() {
        return false;
    }

    @Override
    public boolean isTechnicalException() {
        return false;
    }
}
