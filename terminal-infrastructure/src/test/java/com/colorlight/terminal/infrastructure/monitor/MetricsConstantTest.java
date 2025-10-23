package com.colorlight.terminal.infrastructure.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MetricsConstant 常量校验")
class MetricsConstantTest {

    @Test
    @DisplayName("关键常量应保持既定命名")
    void should_expose_expected_constant_values() throws Exception {
        Constructor<MetricsConstant> constructor = MetricsConstant.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);

        assertThat(MetricsConstant.TERMINAL_SYSTEM_METRICS).isEqualTo("terminal_system_metrics");
        assertThat(MetricsConstant.TagKey.POOL).isEqualTo("pool");
        assertThat(MetricsConstant.ProtocolVersions.ACTUAL_VERSIONS)
                .containsExactly("v1.0", "v1.1");
        assertThat(MetricsConstant.AlertLevel.WARNING).isEqualTo("warning");
        assertThat(MetricsConstant.DEVICE_SCHEDULER_POOL).isEqualTo("devicescheduler");
    }
}
