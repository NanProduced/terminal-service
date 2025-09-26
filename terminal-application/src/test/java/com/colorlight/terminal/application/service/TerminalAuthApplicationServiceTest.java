package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.dto.cache.TerminalAuthCache;
import com.colorlight.terminal.application.dto.request.AuthRequest;
import com.colorlight.terminal.application.dto.result.AuthResult;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.outbound.auth.EncoderPort;
import com.colorlight.terminal.application.port.outbound.cache.TerminalAuthCachePort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.device.DeviceResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 终端认证应用服务单元测试
 * 
 * 测试策略：
 * 1. 覆盖缓存认证场景
 * 2. 覆盖数据库认证场景
 * 3. 验证各种异常情况处理
 * 4. 验证认证成功后缓存行为
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("终端认证服务测试")
class TerminalAuthApplicationServiceTest extends BaseApplicationServiceTest {
    
    @Mock
    private TerminalAccountRepository terminalAccountRepository;
    
    @Mock
    private TerminalAuthCachePort terminalAuthCachePort;
    
    @Mock
    private EncoderPort encoderPort;
    
    @InjectMocks
    private TerminalAuthApplicationService service;
    
    // 测试数据
    private AuthRequest authRequest;
    private TerminalAccount terminalAccount;
    private TerminalAuthCache terminalAuthCache;
    
    @BeforeEach
    void setUp() {
        authRequest = AuthRequest.builder()
                .accountName(TEST_ACCOUNT_NAME)
                .rawPassword(TEST_PASSWORD)
                .build();
        
        terminalAccount = TerminalAccount.builder()
                .deviceId(TEST_DEVICE_ID)
                .accountName(TEST_ACCOUNT_NAME)
                .passwordHash("encoded_password")
                .status(TerminalAccountStatus.ENABLE)
                .build();
        
        terminalAuthCache = TerminalAuthCache.builder()
                .deviceId(TEST_DEVICE_ID)
                .accountName(TEST_ACCOUNT_NAME)
                .credentialsHash("test_credentials_hash")
                .accountStatus(TerminalAccountStatus.ENABLE)
                .build();
    }
    
    @Nested
    @DisplayName("缓存认证测试")
    class CacheAuthenticationTests {
        
        @Test
        @DisplayName("应该在缓存命中且凭据哈希正确时快速认证成功")
        void should_authenticate_successfully_when_cache_hit_and_credentials_hash_correct() {
            // Given - 动态计算正确的凭据哈希 (SHA-256)
            String expectedHash = calculateCredentialsHash(TEST_ACCOUNT_NAME, TEST_PASSWORD);
            terminalAuthCache = TerminalAuthCache.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .accountName(TEST_ACCOUNT_NAME)
                    .credentialsHash(expectedHash)
                    .accountStatus(TerminalAccountStatus.ENABLE)
                        .build();

            when(terminalAuthCachePort.get(TEST_ACCOUNT_NAME)).thenReturn(Optional.of(terminalAuthCache));

            // When
            AuthResult result = service.authenticate(authRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDeviceId()).isEqualTo(TEST_DEVICE_ID);

            // 验证只调用了缓存，没有调用BCrypt或查询数据库
            verify(terminalAuthCachePort).get(TEST_ACCOUNT_NAME);
            verifyNoInteractions(encoderPort); // 快速路径不再使用BCrypt
            verifyNoMoreInteractions(terminalAccountRepository);
        }

    }
    
    @Nested
    @DisplayName("数据库认证测试")
    class DatabaseAuthenticationTests {
        
        @Test
        @DisplayName("应该在缓存未命中但数据库认证成功时认证成功")
        void should_authenticate_successfully_when_cache_miss_but_database_auth_success() {
            // Given
            when(terminalAuthCachePort.get(TEST_ACCOUNT_NAME)).thenReturn(Optional.empty());
            when(terminalAccountRepository.findTerminalAccountByName(TEST_ACCOUNT_NAME)).thenReturn(terminalAccount);
            when(encoderPort.matchesByPasswordEncoder(TEST_PASSWORD, "encoded_password")).thenReturn(true);
            
            // When
            AuthResult result = service.authenticate(authRequest);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
            
            // 验证调用流程
            verify(terminalAuthCachePort).get(TEST_ACCOUNT_NAME);
            verify(terminalAccountRepository).findTerminalAccountByName(TEST_ACCOUNT_NAME);
            verify(encoderPort).matchesByPasswordEncoder(TEST_PASSWORD, "encoded_password");
            verify(terminalAuthCachePort).cache(anyString(), any(TerminalAuthCache.class));
        }
        
        @Test
        @DisplayName("应该在账号不存在时抛出ACCOUNT_NOT_FOUND异常")
        void should_throw_account_not_found_exception_when_account_not_exist() {
            // Given
            when(terminalAuthCachePort.get(TEST_ACCOUNT_NAME)).thenReturn(Optional.empty());
            when(terminalAccountRepository.findTerminalAccountByName(TEST_ACCOUNT_NAME)).thenReturn(null);
            
            // When & Then
            assertThatThrownBy(() -> service.authenticate(authRequest))
                    .isInstanceOf(DeviceResponseException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.ACCOUNT_NOT_FOUND.getCode());
            
            // 验证调用流程
            verify(terminalAuthCachePort).get(TEST_ACCOUNT_NAME);
            verify(terminalAccountRepository).findTerminalAccountByName(TEST_ACCOUNT_NAME);
            verifyNoInteractions(encoderPort);
        }
        
        @Test
        @DisplayName("应该在密码错误时抛出INVALID_CREDENTIALS异常")
        void should_throw_invalid_credentials_exception_when_password_incorrect() {
            // Given
            when(terminalAuthCachePort.get(TEST_ACCOUNT_NAME)).thenReturn(Optional.empty());
            when(terminalAccountRepository.findTerminalAccountByName(TEST_ACCOUNT_NAME)).thenReturn(terminalAccount);
            when(encoderPort.matchesByPasswordEncoder(TEST_PASSWORD, "encoded_password")).thenReturn(false);
            
            // When & Then
            assertThatThrownBy(() -> service.authenticate(authRequest))
                    .isInstanceOf(DeviceResponseException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.INVALID_CREDENTIALS.getCode());
            
            // 验证调用流程
            verify(terminalAuthCachePort).get(TEST_ACCOUNT_NAME);
            verify(terminalAccountRepository).findTerminalAccountByName(TEST_ACCOUNT_NAME);
            verify(encoderPort).matchesByPasswordEncoder(TEST_PASSWORD, "encoded_password");
        }
        
        @Test
        @DisplayName("应该在账号被禁用时抛出ACCOUNT_DISABLED异常")
        void should_throw_account_disabled_exception_when_account_disabled() {
            // Given
            terminalAccount.setStatus(TerminalAccountStatus.DISABLE);
            when(terminalAuthCachePort.get(TEST_ACCOUNT_NAME)).thenReturn(Optional.empty());
            when(terminalAccountRepository.findTerminalAccountByName(TEST_ACCOUNT_NAME)).thenReturn(terminalAccount);
            when(encoderPort.matchesByPasswordEncoder(TEST_PASSWORD, "encoded_password")).thenReturn(true);
            
            // When & Then
            assertThatThrownBy(() -> service.authenticate(authRequest))
                    .isInstanceOf(DeviceResponseException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.ACCOUNT_DISABLED.getCode());
            
            // 验证调用流程
            verify(terminalAuthCachePort).get(TEST_ACCOUNT_NAME);
            verify(terminalAccountRepository).findTerminalAccountByName(TEST_ACCOUNT_NAME);
            verify(encoderPort).matchesByPasswordEncoder(TEST_PASSWORD, "encoded_password");
        }
    }

    /**
     * 计算凭据哈希值 - SHA-256(username:password)
     * 与业务代码中的逻辑保持一致
     */
    private String calculateCredentialsHash(String accountName, String rawPassword) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String credentials = accountName + ":" + rawPassword;
            byte[] hashBytes = digest.digest(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));

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
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }
}