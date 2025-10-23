package com.colorlight.terminal.infrastructure.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BoundedDropOldestQueue 功能测试")
class BoundedDropOldestQueueTest {

    @Test
    @DisplayName("容量参数必须为正数")
    void should_reject_non_positive_capacity() {
        assertThatThrownBy(() -> new BoundedDropOldestQueue<>(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最大容量");
    }

    @Test
    @DisplayName("容量满时应丢弃最旧元素并更新统计")
    void should_drop_oldest_element_when_capacity_reached() {
        BoundedDropOldestQueue<String> queue = new BoundedDropOldestQueue<>(2, "statusQueue");

        assertThat(queue.offer("A")).isTrue();
        assertThat(queue.offer("B")).isTrue();
        assertThat(queue.offer("C")).isTrue();

        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.getDroppedCount()).isEqualTo(1);
        assertThat(queue.poll()).isEqualTo("B");
        assertThat(queue.poll()).isEqualTo("C");
    }

    @Test
    @DisplayName("批量插入与状态信息应保持一致")
    void should_offer_collection_and_expose_status() {
        BoundedDropOldestQueue<Integer> queue = new BoundedDropOldestQueue<>(3, "metricQueue");

        int added = queue.offerAll(List.of(1, 2, 3, 4));
        assertThat(added).isEqualTo(4);
        assertThat(queue.getDroppedCount()).isEqualTo(1);

        BoundedDropOldestQueue.QueueStatus status = queue.getStatus();
        assertThat(status.currentSize()).isEqualTo(3);
        assertThat(status.maxCapacity()).isEqualTo(3);
        assertThat(status.utilizationRate()).isEqualTo(1.0);
        assertThat(status.droppedCount()).isEqualTo(1);

        queue.clear();
        assertThat(queue.isEmpty()).isTrue();
    }
}
