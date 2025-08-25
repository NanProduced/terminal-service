package com.colorlight.terminal.commons.exception.technical;

import com.colorlight.terminal.commons.exception.ErrorCode;
import com.colorlight.terminal.commons.exception.enums.ErrorLevel;
import com.colorlight.terminal.commons.exception.enums.HttpStatusCode;
import lombok.Getter;

/**
 * 技术层异常码
 *
 * 错误码格式：TM + 模块码(2位) + 错误序号(2位)
 * - TM01XX: 通用技术错误
 *
 * @author Nan
 */
@Getter
public enum TechErrorCode implements ErrorCode {

    /**
     * JSON序列化/反序列化错误
     * <p>Jackson</p>
     */
    JSON_SERIALIZATION_EXCEPTION("TM0101", "序列化/反序列化错误", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    JSON_MERGE_EXCEPTION("TM0102", "Json合并错误", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    TIME_FORMAT_TRANSLATE_FAILED("TM0103", "Java.time时间转换失败", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR);

    /**
     * 错误码
     */
    private final String code;

    /**
     *
     * 错误消息
     */
    private final String message;

    /**
     * 错误级别
     */
    private final ErrorLevel level;

    /**
     * HTTP状态码
     */
    private final HttpStatusCode httpStatus;

    TechErrorCode(String code, String message, ErrorLevel level, HttpStatusCode httpStatus) {
        this.code = code;
        this.message = message;
        this.level = level;
        this.httpStatus = httpStatus;
    }
}
