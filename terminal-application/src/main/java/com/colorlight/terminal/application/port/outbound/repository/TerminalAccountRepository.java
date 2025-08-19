package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.TerminalAccount;

/**
 * 终端账号存储接口
 *
 * @author Nan
 */
public interface TerminalAccountRepository {

    /**
     * 根据用户名查询账号
     * @param accountName 账户名
     * @return 终端账号
     */
    TerminalAccount findTerminalAccountByName(String accountName);

    /**
     * 根据设备id查询账号
     * @param deviceId 设备id
     * @return 终端账号
     */
    TerminalAccount findTerminalAccountById(Long deviceId);

    /**
     * 检查是否存在同名账号
     * @param accountName 账号名
     * @return 是否存在
     */
    boolean ifExistTerminalAccount(String accountName);

    /**
     * 保存终端账号
     * 
     * @param terminalAccount 终端账号
     * @return 保存后的终端账号
     */
    TerminalAccount save(TerminalAccount terminalAccount);
}
