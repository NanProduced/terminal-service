package com.colorlight.terminal.commons.exception.enums;

import lombok.Getter;

/**
 * 自定义异常 - 错误级别枚举
 *
 * @author Nan
 */
@Getter
public enum ErrorLevel {

    INFO(1, "信息"),
    WARN(2, "警告"),
    ERROR(3, "错误"),
    CRITICAL(4, "严重"),
    FATAL(5, "致命");

    private final int code;
    private final String description;

    ErrorLevel(int code, String description) {
        this.code = code;
        this.description = description;
    }

}
