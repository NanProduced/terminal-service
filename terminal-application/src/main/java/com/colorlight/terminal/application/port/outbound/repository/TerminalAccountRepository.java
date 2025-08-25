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
    
    /**
     * 立即更新设备登录时间（用于首次上线）
     * 确保firstLoginTime不丢失，同时更新lastLoginTime和lastLoginIp
     * 
     * @param deviceId 设备ID
     * @param clientIp 客户端IP
     * @param loginTime 登录时间
     * @return 更新影响的行数
     */
    int updateLoginTimeImmediate(Long deviceId, String clientIp, java.time.LocalDateTime loginTime);
    
    /**
     * 更新设备登录时间（用于批量更新）
     * 利用数据库COALESCE保护firstLoginTime，只更新lastLoginTime和lastLoginIp
     * 
     * @param deviceId 设备ID  
     * @param clientIp 客户端IP
     * @param loginTime 登录时间
     * @return 更新影响的行数
     */
    int updateLoginTime(Long deviceId, String clientIp, java.time.LocalDateTime loginTime);
}
