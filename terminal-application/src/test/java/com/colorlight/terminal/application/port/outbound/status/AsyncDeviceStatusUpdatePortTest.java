package com.colorlight.terminal.application.port.outbound.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AsyncDeviceStatusUpdatePort.BufferPoolStatus 的数据格式化测试
 */
@DisplayName("AsyncDeviceStatusUpdatePort.BufferPoolStatus 测试")
class AsyncDeviceStatusUpdatePortTest {

    @Test
    @DisplayName("应按照百分比格式化缓冲池利用率")
    void should_format_buffer_pool_status() {
        AsyncDeviceStatusUpdatePort.BufferPoolStatus status =
                new AsyncDeviceStatusUpdatePort.BufferPoolStatus(
                        5,
                        20,
                        0.375,
                        123L,
                        100L,
                        80L,
                        2L
                );

        assertThat(status.currentSize()).isEqualTo(5);
        assertThat(status.maxSize()).isEqualTo(20);
        assertThat(status.utilizationRate()).isEqualTo(0.375);
        assertThat(status.totalProcessed()).isEqualTo(100L);
        assertThat(status.totalDropped()).isEqualTo(2L);
        assertThat(status.toString())
                .as("利用率应转换成百分比并保留两位小数")
                .isEqualTo(
                        "BufferPoolStatus{currentSize=5, maxSize=20, utilizationRate=37.50%, " +
                                "lastFlushTime=123, totalProcessed=100, totalFlushed=80, totalDropped=2}"
                );
    }
}
