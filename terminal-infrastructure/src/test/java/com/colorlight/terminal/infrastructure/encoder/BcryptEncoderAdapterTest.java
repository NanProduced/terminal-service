package com.colorlight.terminal.infrastructure.encoder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * BcryptEncoderAdapter 单元测试
 * 
 * 业务逻辑总结：
 * BcryptEncoderAdapter是Application层EncoderPort接口的Infrastructure层实现，
 * 负责提供密码编码和验证功能，主要用于终端设备认证。
 * 
 * 核心功能：
 * 1. matchesByPasswordEncoder - 验证原始密码与编码密码是否匹配
 * 2. encodeByPasswordEncoder - 编码原始密码
 * 
 * 依赖：Spring Security的PasswordEncoder（通常是BCryptPasswordEncoder）
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BcryptEncoderAdapter - 密码编码适配器测试")
class BcryptEncoderAdapterTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    private BcryptEncoderAdapter bcryptEncoderAdapter;

    @BeforeEach
    void setUp() {
        bcryptEncoderAdapter = new BcryptEncoderAdapter(passwordEncoder);
        
        // 使用lenient()避免严格模式报错，因为某些测试可能不会调用所有mock方法
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    }

    @Nested
    @DisplayName("matchesByPasswordEncoder - 密码匹配验证")
    class MatchesByPasswordEncoderTests {

        @Test
        @DisplayName("应该在密码匹配时返回true")
        void should_return_true_when_password_matches() {
            // Given - 准备匹配的密码数据
            String rawPassword = "password123";
            String encodedPassword = "$2a$10$encoded_password_hash";
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

            // When - 执行密码匹配验证
            boolean result = bcryptEncoderAdapter.matchesByPasswordEncoder(rawPassword, encodedPassword);

            // Then - 验证匹配结果
            assertThat(result).isTrue();
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }

        @Test
        @DisplayName("应该在密码不匹配时返回false")
        void should_return_false_when_password_does_not_match() {
            // Given - 准备不匹配的密码数据
            String rawPassword = "wrong_password";
            String encodedPassword = "$2a$10$encoded_password_hash";
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false);

            // When - 执行密码匹配验证
            boolean result = bcryptEncoderAdapter.matchesByPasswordEncoder(rawPassword, encodedPassword);

            // Then - 验证不匹配结果
            assertThat(result).isFalse();
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }

        @Test
        @DisplayName("应该正确处理空字符串密码")
        void should_handle_empty_password_correctly() {
            // Given - 准备空字符串密码
            String rawPassword = "";
            String encodedPassword = "$2a$10$encoded_password_hash";
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false);

            // When - 执行密码匹配验证
            boolean result = bcryptEncoderAdapter.matchesByPasswordEncoder(rawPassword, encodedPassword);

            // Then - 验证处理结果
            assertThat(result).isFalse();
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }
    }

    @Nested
    @DisplayName("encodeByPasswordEncoder - 密码编码")
    class EncodeByPasswordEncoderTests {

        @Test
        @DisplayName("应该成功编码原始密码")
        void should_encode_raw_password_successfully() {
            // Given - 准备原始密码和期望的编码结果
            String rawPassword = "password123";
            String expectedEncodedPassword = "$2a$10$encoded_password_hash";
            when(passwordEncoder.encode(rawPassword)).thenReturn(expectedEncodedPassword);

            // When - 执行密码编码
            String result = bcryptEncoderAdapter.encodeByPasswordEncoder(rawPassword);

            // Then - 验证编码结果
            assertThat(result).isEqualTo(expectedEncodedPassword);
            verify(passwordEncoder).encode(rawPassword);
        }

        @Test
        @DisplayName("应该为不同的原始密码生成不同的编码")
        void should_generate_different_encoded_passwords_for_different_raw_passwords() {
            // Given - 准备不同的原始密码
            String rawPassword1 = "password123";
            String rawPassword2 = "password456";
            String encodedPassword1 = "$2a$10$encoded_password_hash_1";
            String encodedPassword2 = "$2a$10$encoded_password_hash_2";
            
            when(passwordEncoder.encode(rawPassword1)).thenReturn(encodedPassword1);
            when(passwordEncoder.encode(rawPassword2)).thenReturn(encodedPassword2);

            // When - 执行密码编码
            String result1 = bcryptEncoderAdapter.encodeByPasswordEncoder(rawPassword1);
            String result2 = bcryptEncoderAdapter.encodeByPasswordEncoder(rawPassword2);

            // Then - 验证不同密码生成不同编码
            assertThat(result1).isEqualTo(encodedPassword1);
            assertThat(result2).isEqualTo(encodedPassword2);
            assertThat(result1).isNotEqualTo(result2);
            
            verify(passwordEncoder).encode(rawPassword1);
            verify(passwordEncoder).encode(rawPassword2);
        }

        @Test
        @DisplayName("应该正确处理空字符串密码编码")
        void should_handle_empty_password_encoding_correctly() {
            // Given - 准备空字符串密码
            String rawPassword = "";
            String expectedEncodedPassword = "$2a$10$empty_password_hash";
            when(passwordEncoder.encode(rawPassword)).thenReturn(expectedEncodedPassword);

            // When - 执行密码编码
            String result = bcryptEncoderAdapter.encodeByPasswordEncoder(rawPassword);

            // Then - 验证编码结果
            assertThat(result).isEqualTo(expectedEncodedPassword);
            verify(passwordEncoder).encode(rawPassword);
        }
    }

    @Nested
    @DisplayName("集成场景测试")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("应该支持编码后立即验证的完整流程")
        void should_support_encode_then_match_workflow() {
            // Given - 准备原始密码
            String rawPassword = "testPassword123";
            String encodedPassword = "$2a$10$encoded_test_password";
            
            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

            // When - 执行编码后验证的完整流程
            String encoded = bcryptEncoderAdapter.encodeByPasswordEncoder(rawPassword);
            boolean matches = bcryptEncoderAdapter.matchesByPasswordEncoder(rawPassword, encoded);

            // Then - 验证完整流程正确
            assertThat(encoded).isEqualTo(encodedPassword);
            assertThat(matches).isTrue();
            
            verify(passwordEncoder).encode(rawPassword);
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }
    }
}