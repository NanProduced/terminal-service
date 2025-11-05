package com.colorlight.terminal.application.domain;

import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TerminalAccount领域模型测试
 */
@DisplayName("TerminalAccount领域模型测试")
class TerminalAccountTest {

    @Test
    @DisplayName("Builder应正确构建账号核心字段")
    void should_build_account_with_builder() {
        LocalDateTime firstLogin = LocalDateTime.of(2024, 1, 1, 10, 30);
        LocalDateTime lastLogin = firstLogin.plusHours(2);

        TerminalAccount account = TerminalAccount.builder()
                .deviceId(12345L)
                .accountName("demo-account")
                .passwordHash("hash-value")
                .status(TerminalAccountStatus.ENABLE)
                .firstLoginTime(firstLogin)
                .lastLoginTime(lastLogin)
                .lastLoginIp("10.0.0.1")
                .build();

        assertThat(account.getDeviceId()).isEqualTo(12345L);
        assertThat(account.getAccountName()).isEqualTo("demo-account");
        assertThat(account.getStatus()).isEqualTo(TerminalAccountStatus.ENABLE);
        assertThat(account.getFirstLoginTime()).isEqualTo(firstLogin);
        assertThat(account.getLastLoginTime()).isEqualTo(lastLogin);
        assertThat(account.getLastLoginIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("应该比较账号对象的等价性")
    void should_respect_equals_and_hash_code_contracts() {
        LocalDateTime firstLogin = LocalDateTime.of(2024, 1, 2, 9, 0);
        LocalDateTime lastLogin = firstLogin.plusMinutes(30);

        TerminalAccount accountA = TerminalAccount.builder()
                .deviceId(20001L)
                .accountName("test-account")
                .passwordHash("hash")
                .status(TerminalAccountStatus.DISABLE)
                .firstLoginTime(firstLogin)
                .lastLoginTime(lastLogin)
                .lastLoginIp("192.168.0.10")
                .build();

        TerminalAccount accountB = TerminalAccount.builder()
                .deviceId(20001L)
                .accountName("test-account")
                .passwordHash("hash")
                .status(TerminalAccountStatus.DISABLE)
                .firstLoginTime(firstLogin)
                .lastLoginTime(lastLogin)
                .lastLoginIp("192.168.0.10")
                .build();

        TerminalAccount accountC = TerminalAccount.builder()
                .deviceId(20002L)
                .accountName("test-account")
                .passwordHash("hash")
                .status(TerminalAccountStatus.DISABLE)
                .firstLoginTime(firstLogin)
                .lastLoginTime(lastLogin)
                .lastLoginIp("192.168.0.10")
                .build();

        assertThat(accountA)
                .isEqualTo(accountB)
                .hasSameHashCodeAs(accountB);
        assertThat(accountA).isNotEqualTo(accountC);
    }

    @Test
    @DisplayName("toString应输出包含关键属性信息")
    void should_generate_readable_to_string() {
        TerminalAccount account = TerminalAccount.builder()
                .deviceId(30001L)
                .accountName("string-account")
                .passwordHash("hash")
                .status(TerminalAccountStatus.ENABLE)
                .firstLoginTime(LocalDateTime.of(2024, 5, 1, 8, 0))
                .lastLoginTime(LocalDateTime.of(2024, 5, 1, 9, 0))
                .lastLoginIp("172.16.0.5")
                .build();

        String toString = account.toString();

        assertThat(toString)
                .contains("TerminalAccount")
                .contains("accountName=string-account")
                .contains("status=ENABLE");
    }
}
