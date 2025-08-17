package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.TerminalAccount;

public interface TerminalAccountRepository {

    TerminalAccount findTerminalAccountByName(String accountName);

    TerminalAccount findTerminalAccountById(Long deviceId);

    boolean ifExistTerminalAccount(String accountName);

    /**
     * 保存终端账号
     * 
     * @param terminalAccount 终端账号
     * @return 保存后的终端账号
     */
    TerminalAccount save(TerminalAccount terminalAccount);
}
