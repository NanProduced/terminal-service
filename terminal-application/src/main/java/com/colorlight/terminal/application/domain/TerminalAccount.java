package com.colorlight.terminal.application.domain;

import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TerminalAccount {

    /**
     * 设备Id
     */
    private Long deviceId;

    /**
     * 账号名称
     */
    private String accountName;

    /**
     * 密码
     */
    private String passwordHash;

    /**
     * 账号状态
     */
    private TerminalAccountStatus status;

    /**
     * 上云时间
     */
    private LocalDateTime firstLoginTime;

    /**
     * 最后连接时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后连接IP
     */
    private String lastLoginIp;

}
