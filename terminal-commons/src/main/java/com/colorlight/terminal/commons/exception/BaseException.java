package com.colorlight.terminal.commons.exception;

import com.colorlight.terminal.commons.exception.enums.ErrorLevel;
import com.colorlight.terminal.commons.exception.enums.HttpStatusCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义异常 - 基础异常类
 * @author Nan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseException extends RuntimeException {

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 错误级别
     */
    private final ErrorLevel level;

    /**
     * HTTP状态码
     */
    private final HttpStatusCode httpStatus;

    /**
     * 异常发生时间
     */
    private final Instant timestamp;

    /**
     * 上下文信息 - 额外的补充信息
     */
    private final Map<String, Object> context;

    /**
     * 判断是否为业务异常
     */
    public abstract boolean isBusinessException();

    /**
     * 判断是否为技术异常
     */
    public abstract boolean isTechnicalException();

    /*======================= 构造函数 =======================*/

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
    }

    protected BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
    }

    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
    }

    protected BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
    }

    /*======================= 额外上下文信息补充 =======================*/
    /**
     * 添加上下文信息
     */
    public BaseException addContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    /**
     * 批量添加上下文信息
     */
    public BaseException addContext(Map<String, Object> contextMap) {
        if (contextMap != null) {
            this.context.putAll(contextMap);
        }
        return this;
    }

    /*======================= 格式化 =======================*/

    /**
     * 获取格式化的异常信息
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorCode).append("] ");
        sb.append(getMessage());

        if (!context.isEmpty()) {
            sb.append(" - Context: ").append(context);
        }

        return sb.toString();
    }


}
