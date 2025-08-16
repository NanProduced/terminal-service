package com.colorlight.terminal.commons.exception.enums;

import lombok.Getter;

/**
 * commons包不引入web依赖
 * <l>这里自定义枚举长一点HttpStatus</l>
 *
 * @author Nan
 */
@Getter
public enum HttpStatusCode {

    OK(200, "OK"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable");

    private final int value;
    private final String reasonPhrase;

    HttpStatusCode(int value, String reasonPhrase) {
        this.value = value;
        this.reasonPhrase = reasonPhrase;
    }

}
