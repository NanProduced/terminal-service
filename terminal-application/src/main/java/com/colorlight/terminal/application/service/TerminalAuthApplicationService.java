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
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Slf4j
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
            AuthResult cacheResult = checkAuthenticationCache(authRequest.getAccountName(), authRequest.getRawPassword(), terminalAuthCache.get());
            if (cacheResult.isSuccess()) {
                return cacheResult;
            }
            // 验证失败，清除缓存并继续完整认证
            terminalAuthCachePort.remove(authRequest.getAccountName());
        }

        // 缓存未命中，查询数据库进行完整认证
        TerminalAccount terminalAccount = terminalAccountRepository.findTerminalAccountByName(authRequest.getAccountName());
        // 账号不存在
        if (terminalAccount == null) {
            log.warn("ApplicationService - 设备账号不存在: accountName={}", authRequest.getAccountName());
            throw new DeviceResponseException(CommonErrorCode.ACCOUNT_NOT_FOUND);
        }
        // 密码校验失败
        if (!encoderPort.matchesByPasswordEncoder(authRequest.getRawPassword(), terminalAccount.getPasswordHash())) {
            throw new DeviceResponseException(CommonErrorCode.INVALID_CREDENTIALS);
        }
        // 终端账号封禁
        if (TerminalAccountStatus.DISABLE.equals(terminalAccount.getStatus())) {
            throw new DeviceResponseException(CommonErrorCode.ACCOUNT_DISABLED);
        }

        // 缓存认证信息 - 使用SHA-256快速验证
        String credentialsHash = calculateCredentialsHash(authRequest.getAccountName(), authRequest.getRawPassword());
        terminalAuthCachePort.cache(terminalAccount.getAccountName(), TerminalAuthCache.builder()
                        .deviceId(terminalAccount.getDeviceId())
                        .accountName(terminalAccount.getAccountName())
                        .credentialsHash(credentialsHash)
                        .accountStatus(terminalAccount.getStatus())
                        .build());

        return AuthResult.success(terminalAccount.getDeviceId());
    }

    private AuthResult checkAuthenticationCache(String accountName, String rawPassword, TerminalAuthCache authCache) {
        String credentialsHash = calculateCredentialsHash(accountName, rawPassword);
        if (credentialsHash.equals(authCache.getCredentialsHash())) {
            log.debug("快速认证成功: accountName={}, deviceId={}", accountName, authCache.getDeviceId());
            return AuthResult.success(authCache.getDeviceId());
        }
        else {
            log.debug("快速认证失败: accountName={}", accountName);
            return AuthResult.failed();
        }
    }

    /**
     * 计算凭据哈希值 - SHA-256(username:password)
     * 用于快速认证缓存验证，避免昂贵的BCrypt操作
     *
     * @param accountName 账户名
     * @param rawPassword 原始密码
     * @return SHA-256哈希值的十六进制字符串
     */
    private String calculateCredentialsHash(String accountName, String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String credentials = accountName + ":" + rawPassword;
            byte[] hashBytes = digest.digest(credentials.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256算法不可用", e);
            throw new TechnicalException(TechErrorCode.ALGORITHM_ERROR, "SHA-256算法不可用", e);
        }
    }
}
