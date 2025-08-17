package com.colorlight.terminal.application.port.inbound.account;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.dto.request.CreateTerminalAccountRequest;

/**
 * 终端账号用例接口
 * 
 * @author Nan
 */
public interface TerminalAccountUseCase {
    
    /**
     * 创建终端账号
     * 
     * @param request 创建请求
     * @return 终端账号
     */
    TerminalAccount createTerminalAccount(CreateTerminalAccountRequest request);
}