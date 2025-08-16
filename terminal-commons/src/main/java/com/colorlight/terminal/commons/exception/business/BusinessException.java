package com.colorlight.terminal.commons.exception.business;

import com.colorlight.terminal.commons.exception.BaseException;
import com.colorlight.terminal.commons.exception.ErrorCode;

/**
 * 业务逻辑异常类
 * @author Nan
 */
public class BusinessException extends BaseException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    @Override
    public boolean isBusinessException() {
        return true;
    }

    @Override
    public boolean isTechnicalException() {
        return false;
    }
}
