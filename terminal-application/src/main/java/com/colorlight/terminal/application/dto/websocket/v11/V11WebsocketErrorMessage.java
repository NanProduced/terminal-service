package com.colorlight.terminal.application.dto.websocket.v11;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V11协议 - 错误信息
 *
 * @author Nan
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class V11WebsocketErrorMessage {

    private Integer errorType;

    private String message;
}
