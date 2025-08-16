package com.colorlight.terminal.application.dto.cache;

import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 终端认证缓存DTO
 * 用于缓存终端设备的认证信息，支持WebSocket和HTTP Basic Auth场景
 * 
 * @author Nan
 * @version 1.0.0
 * @since 2024-12-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminalAuthCache implements Serializable {

    @Serial
    private static final long serialVersionUID = 3522388742098554377L;
    /**
     * 设备ID - 唯一标识
     */
    private Long deviceId;

    /**
     * 账户名称 - 认证用户名
     */
    private String accountName;

    /**
     * 密码哈希值 - BCrypt加密
     */
    private String passwordHash;

    /**
     * 账户状态
     */
    private TerminalAccountStatus accountStatus;
}