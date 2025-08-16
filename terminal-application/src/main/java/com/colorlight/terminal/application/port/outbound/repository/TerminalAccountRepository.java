package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.TerminalAccount;

public interface TerminalAccountRepository {

    TerminalAccount findTerminalAccountByName(String accountName);

    TerminalAccount findTerminalAccountById(Long deviceId);
}
