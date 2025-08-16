package com.colorlight.terminal.application.port.inbound.auth;

import com.colorlight.terminal.application.dto.request.AuthRequest;
import com.colorlight.terminal.application.dto.result.AuthResult;

/**
 * 终端身份验证用例接口
 *
 * @author Nan
 */
public interface TerminalAuthUseCase {

    AuthResult authenticate(AuthRequest authRequest);
}
