package com.colorlight.terminal.application.dto.websocket.v11;

import lombok.Getter;

/**
 * V11协议 - 错误类型
 *
 * @author Nan
 */
@Getter
public enum V11WebsocketErrorEnum {

    /**
     * 表示参数无效的错误类型。
     * 当请求中包含不合法或缺失必要的参数时，系统将返回此错误。
     */
    INVALID_PARAMS(1, "INVALID_PARAMS"),

    /**
     * 表示账户无效的错误类型。
     * 当尝试使用无效或不存在的账户进行操作时，系统将返回此错误。
     */
    INVALID_ACCOUNT(2, "INVALID_ACCOUNT"),

    /**
     * 表示远程过程调用超时的错误类型。
     * 当RPC请求在预设的时间内未收到响应时，系统将返回此错误。
     */
    RPC_TIME_OUT(3, "RPC_TIME_OUT"),

    /**
     * 表示服务器错误的错误类型。
     * 当系统内部发生未预期的错误或异常，导致无法正常处理请求时，将返回此错误。
     */
    SERVER_ERROR(4, "SERVER_ERROR"),

    /**
     * 表示消息类型无效的错误类型。
     * 当接收到的消息类型不在预定义的消息类型范围内时，系统将返回此错误。
     */
    INVALID_MESSAGE_TYPE(5, "INVALID_MESSAGE_TYPE"),

    /**
     * 表示消息数据无效的错误类型。
     * 当接收到的消息中包含的数据格式不正确、数据缺失或无法解析时，系统将返回此错误。
     */
    INVALID_MESSAGE_DATA(6, "INVALID_MESSAGE_DATA"),

    /**
     * 表示消息ID无效的错误类型。
     * 当接收到的消息ID不在预定义的有效范围内或无法识别时，系统将返回此错误。
     */
    INVALID_MESSAGE_ID(7, "INVALID_MESSAGE_ID"),

    /**
     * 表示指令ID无效的错误类型。
     * 当接收到的消息中包含的指令ID不在预定义的有效范围内或无法识别时，系统将返回此错误。
     */
    INVALID_COMMENT_ID(8, "INVALID_COMMENT_ID");

    private final Integer id;

    private final String type;

    V11WebsocketErrorEnum(Integer id, String type) {
        this.id = id;
        this.type = type;
    }
}
