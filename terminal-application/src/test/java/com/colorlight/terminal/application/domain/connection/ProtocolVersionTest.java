package com.colorlight.terminal.application.domain.connection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProtocolVersion 枚举的业务校验测试
 */
@DisplayName("ProtocolVersion 枚举测试")
class ProtocolVersionTest {

    @Test
    @DisplayName("应根据版本字符串返回对应的协议枚举")
    void should_resolve_protocol_version_from_string() {
        assertThat(ProtocolVersion.fromVersion("1.1")).isEqualTo(ProtocolVersion.V1_1);
        assertThat(ProtocolVersion.fromVersion(" 1.0 "))
                .as("带空格的版本也应正确解析")
                .isEqualTo(ProtocolVersion.V1_0);
    }

    @Test
    @DisplayName("缺失或未知的版本应回退到默认协议")
    void should_fallback_to_default_when_version_missing() {
        assertThat(ProtocolVersion.fromVersion(null)).isEqualTo(ProtocolVersion.V1_0);
        assertThat(ProtocolVersion.fromVersion("")).isEqualTo(ProtocolVersion.V1_0);
        assertThat(ProtocolVersion.fromVersion("unknown")).isEqualTo(ProtocolVersion.V1_0);
    }

    @Test
    @DisplayName("应暴露协议的元数据配置")
    void should_expose_protocol_metadata() {
        assertThat(ProtocolVersion.V1_0.isSupported()).isTrue();
        assertThat(ProtocolVersion.V1_1.isSupported()).isFalse();
        assertThat(ProtocolVersion.V1_0.getProcessorClassName())
                .as("确保仍指向旧协议的处理器实现")
                .contains("V10ProtocolMessageProcessor");
    }
}
