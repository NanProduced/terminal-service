package com.colorlight.terminal.commons.exception;

import com.colorlight.terminal.commons.exception.enums.ErrorLevel;
import com.colorlight.terminal.commons.exception.enums.HttpStatusCode;

/**
 * 自定义异常类 - 错误码接口
 *
 * @author Nan
 */
public interface ErrorCode {

    String getCode();
    String getMessage();
    ErrorLevel getLevel();
    HttpStatusCode getHttpStatus();
}
