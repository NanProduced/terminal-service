package com.colorlight.terminal.commons.exception.technical;

import com.colorlight.terminal.commons.exception.BaseException;
import com.colorlight.terminal.commons.exception.ErrorCode;

/**
 * 技术实现、三方、中间件异常类
 *
 * @author Nan
 */
public class TechnicalException extends BaseException {

    public TechnicalException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TechnicalException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TechnicalException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public TechnicalException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    @Override
    public boolean isBusinessException() {
        return false;
    }

    @Override
    public boolean isTechnicalException() {
        return true;
    }
}
