package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.dto.cache.TerminalAuthCache;
import com.colorlight.terminal.application.dto.request.AuthRequest;
import com.colorlight.terminal.application.dto.result.AuthResult;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.inbound.auth.TerminalAuthUseCase;
import com.colorlight.terminal.application.port.outbound.auth.EncoderPort;
import com.colorlight.terminal.application.port.outbound.cache.TerminalAuthCachePort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TerminalAuthApplicationService implements TerminalAuthUseCase {

    private final TerminalAccountRepository terminalAccountRepository;
    private final TerminalAuthCachePort terminalAuthCachePort;
    private final EncoderPort encoderPort;

    @Override
    @Transactional(readOnly = true)
    public AuthResult authenticate(AuthRequest authRequest) {
        // 先检查缓存
        Optional<TerminalAuthCache> terminalAuthCache = terminalAuthCachePort.get(authRequest.getAccountName());
        if (terminalAuthCache.isPresent()) {
            return checkAuthenticationCache(authRequest.getRawPassword(), terminalAuthCache.get());
        }

        // 缓存未命中，查询数据库进行完整认证
        TerminalAccount terminalAccount = terminalAccountRepository.findTerminalAccountByName(authRequest.getAccountName());
        // 账号不存在
        if (terminalAccount == null) {
            throw new DeviceResponseException(CommonErrorCode.ACCOUNT_NOT_FOUND, authRequest.getAccountName());
        }
        // 密码校验失败
        if (!encoderPort.matchesByPasswordEncoder(authRequest.getRawPassword(), terminalAccount.getPasswordHash())) {
            throw new DeviceResponseException(CommonErrorCode.INVALID_CREDENTIALS);
        }
        // 终端账号封禁
        if (TerminalAccountStatus.DISABLE.equals(terminalAccount.getStatus())) {
            throw new DeviceResponseException(CommonErrorCode.ACCOUNT_DISABLED);
        }

        // 缓存认证信息
        terminalAuthCachePort.cache(terminalAccount.getAccountName(), TerminalAuthCache.builder()
                        .deviceId(terminalAccount.getDeviceId())
                        .accountName(terminalAccount.getAccountName())
                        .passwordHash(terminalAccount.getPasswordHash())
                        .accountStatus(terminalAccount.getStatus())
                        .build());

        return AuthResult.success(terminalAccount.getDeviceId());
    }

    private AuthResult checkAuthenticationCache(String rawPassword, TerminalAuthCache authCache) {

        if (encoderPort.matchesByPasswordEncoder(rawPassword, authCache.getPasswordHash())) {
            return AuthResult.success(authCache.getDeviceId());
        }
        else {
            return AuthResult.failed();
        }
    }
}
