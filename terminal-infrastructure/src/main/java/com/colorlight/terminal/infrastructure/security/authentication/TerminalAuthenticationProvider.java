package com.colorlight.terminal.infrastructure.security.authentication;

import com.colorlight.terminal.application.dto.request.AuthRequest;
import com.colorlight.terminal.application.dto.result.AuthResult;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.inbound.auth.TerminalAuthUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * 终端设备Basic Auth认证
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalAuthenticationProvider implements AuthenticationProvider {


    private final TerminalAuthUseCase  terminalAuthUseCase;


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String accountName = authentication.getName();
        String rawPassword = authentication.getCredentials().toString();
        if (StringUtils.isBlank(accountName) || StringUtils.isBlank(rawPassword)) {
            throw new DeviceResponseException(CommonErrorCode.PARAMETER_MISSING);
        }

        // 认证
        AuthResult authResult = terminalAuthUseCase.authenticate(new AuthRequest(accountName, rawPassword));
        if (authResult.isSuccess()) {
            TerminalPrincipal principal = new TerminalPrincipal(authResult.getDeviceId(), TerminalAccountStatus.ENABLE);
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        }

        throw new AuthenticationCredentialsNotFoundException("Authentication failed");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
