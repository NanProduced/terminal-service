package com.colorlight.terminal.application.enums;

import lombok.Getter;

/**
 * 设备账号状态枚举
 *
 * @author Nan
 */
@Getter
public enum TerminalAccountStatus {

    ENABLE(0),

    DISABLE(1);


    private final Integer status;

    TerminalAccountStatus(Integer status) {
        this.status = status;
    }
}
