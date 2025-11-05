package com.colorlight.terminal.infrastructure.websocket.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EventLoopAlertEvent单元测试
 *
 * 业务逻辑总结：
 * EventLoopAlertEvent是EventLoop监控发现性能问题时发出的Spring应用事件。
 * 它用于通知系统EventLoop可能存在阻塞或性能下降。
 * 支持两个告警级别：WARNING（告警）和CRITICAL（严重）。
 *
 * 主要业务流程：
 * 1. 通过warning()静态工厂方法创建WARNING级别告警事件
 * 2. 通过critical()静态工厂方法创建CRITICAL级别告警事件
 * 3. 包含告警的详细信息：待处理任务数、EventExecutor信息、告警阈值、消息
 * 4. 记录告警发生的时间戳
 *
 * @author Nan
 */
@DisplayName("EventLoopAlertEvent单元测试")
class EventLoopAlertEventTest {

    @Nested
    @DisplayName("AlertLevel枚举测试")
    class AlertLevelTests {

        @Test
        @DisplayName("应该正确定义WARNING告警级别")
        void should_define_warning_alert_level() {
            // When - 获取WARNING级别
            EventLoopAlertEvent.AlertLevel level = EventLoopAlertEvent.AlertLevel.WARNING;

            // Then - 验证告警级别
            assertThat(level).isEqualTo(EventLoopAlertEvent.AlertLevel.WARNING);
            assertThat(level.getDescription()).isEqualTo("告警");
        }

        @Test
        @DisplayName("应该正确定义CRITICAL告警级别")
        void should_define_critical_alert_level() {
            // When - 获取CRITICAL级别
            EventLoopAlertEvent.AlertLevel level = EventLoopAlertEvent.AlertLevel.CRITICAL;

            // Then - 验证告警级别
            assertThat(level).isEqualTo(EventLoopAlertEvent.AlertLevel.CRITICAL);
            assertThat(level.getDescription()).isEqualTo("严重");
        }

        @Test
        @DisplayName("应该能够访问告警级别的描述")
        void should_get_alert_level_description() {
            // Given - 告警级别
            EventLoopAlertEvent.AlertLevel warningLevel = EventLoopAlertEvent.AlertLevel.WARNING;
            EventLoopAlertEvent.AlertLevel criticalLevel = EventLoopAlertEvent.AlertLevel.CRITICAL;

            // Then - 验证描述信息
            assertThat(warningLevel.getDescription()).isNotEmpty();
            assertThat(criticalLevel.getDescription()).isNotEmpty();
            assertThat(warningLevel.getDescription()).isNotEqualTo(criticalLevel.getDescription());
        }

        @Test
        @DisplayName("应该能够比较告警级别")
        void should_compare_alert_levels() {
            // When - 比较告警级别
            boolean isSame = EventLoopAlertEvent.AlertLevel.WARNING == EventLoopAlertEvent.AlertLevel.WARNING;
            boolean isDifferent = EventLoopAlertEvent.AlertLevel.WARNING != EventLoopAlertEvent.AlertLevel.CRITICAL;

            // Then - 验证比较结果
            assertThat(isSame).isTrue();
            assertThat(isDifferent).isTrue();
        }
    }

    @Nested
    @DisplayName("warning()静态工厂方法测试")
    class WarningFactoryTests {

        @Test
        @DisplayName("应该创建WARNING级别的告警事件")
        void should_create_warning_alert_event() {
            // Given - 告警参数
            long pendingTasks = 1500;
            String eventExecutorInfo = "EventLoop-1";
            long threshold = 1000;

            // When - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(pendingTasks, eventExecutorInfo, threshold);

            // Then - 验证事件属性
            assertThat(event).isNotNull();
            assertThat(event.getAlertLevel()).isEqualTo(EventLoopAlertEvent.AlertLevel.WARNING);
            assertThat(event.getPendingTasks()).isEqualTo(pendingTasks);
            assertThat(event.getEventExecutorInfo()).isEqualTo(eventExecutorInfo);
            assertThat(event.getThreshold()).isEqualTo(threshold);
        }

        @Test
        @DisplayName("WARNING事件应该包含正确的消息内容")
        void should_contain_correct_message_for_warning() {
            // Given - 告警参数
            long pendingTasks = 2000;
            String eventExecutorInfo = "EventLoop-2";
            long threshold = 1000;

            // When - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(pendingTasks, eventExecutorInfo, threshold);

            // Then - 验证消息内容
            assertThat(event.getMessage()).contains("可能阻塞");
            assertThat(event.getMessage()).contains(eventExecutorInfo);
            assertThat(event.getMessage()).contains(String.valueOf(pendingTasks));
            assertThat(event.getMessage()).contains(String.valueOf(threshold));
        }

        @Test
        @DisplayName("WARNING事件应该记录告警时间戳")
        void should_record_alert_timestamp_for_warning() {
            // Given - 告警参数
            long beforeTime = System.currentTimeMillis();

            // When - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);

            long afterTime = System.currentTimeMillis();

            // Then - 验证时间戳
            assertThat(event.getAlertTimestamp()).isGreaterThanOrEqualTo(beforeTime);
            assertThat(event.getAlertTimestamp()).isLessThanOrEqualTo(afterTime);
        }

        @Test
        @DisplayName("WARNING事件应该接受不同的参数值")
        void should_create_warning_with_various_parameters() {
            // Given - 不同的参数值
            long[] pendingTasksValues = {100, 1000, 5000, 10000};
            String[] executorInfos = {"EventLoop-1", "EventLoop-2", "EventLoop-16"};
            long[] thresholds = {500, 1000, 5000};

            // When/Then - 验证可以创建不同参数的事件
            for (long pendingTasks : pendingTasksValues) {
                for (String info : executorInfos) {
                    for (long threshold : thresholds) {
                        EventLoopAlertEvent event = EventLoopAlertEvent.warning(pendingTasks, info, threshold);
                        assertThat(event).isNotNull();
                        assertThat(event.getPendingTasks()).isEqualTo(pendingTasks);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("critical()静态工厂方法测试")
    class CriticalFactoryTests {

        @Test
        @DisplayName("应该创建CRITICAL级别的告警事件")
        void should_create_critical_alert_event() {
            // Given - 告警参数
            long pendingTasks = 6000;
            String eventExecutorInfo = "EventLoop-3";
            long threshold = 5000;

            // When - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.critical(pendingTasks, eventExecutorInfo, threshold);

            // Then - 验证事件属性
            assertThat(event).isNotNull();
            assertThat(event.getAlertLevel()).isEqualTo(EventLoopAlertEvent.AlertLevel.CRITICAL);
            assertThat(event.getPendingTasks()).isEqualTo(pendingTasks);
            assertThat(event.getEventExecutorInfo()).isEqualTo(eventExecutorInfo);
            assertThat(event.getThreshold()).isEqualTo(threshold);
        }

        @Test
        @DisplayName("CRITICAL事件应该包含正确的消息内容")
        void should_contain_correct_message_for_critical() {
            // Given - 告警参数
            long pendingTasks = 7000;
            String eventExecutorInfo = "EventLoop-4";
            long threshold = 5000;

            // When - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.critical(pendingTasks, eventExecutorInfo, threshold);

            // Then - 验证消息内容
            assertThat(event.getMessage()).contains("严重阻塞");
            assertThat(event.getMessage()).contains(eventExecutorInfo);
            assertThat(event.getMessage()).contains(String.valueOf(pendingTasks));
            assertThat(event.getMessage()).contains(String.valueOf(threshold));
        }

        @Test
        @DisplayName("CRITICAL事件应该记录告警时间戳")
        void should_record_alert_timestamp_for_critical() {
            // Given - 告警参数
            long beforeTime = System.currentTimeMillis();

            // When - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.critical(6000, "EventLoop-3", 5000);

            long afterTime = System.currentTimeMillis();

            // Then - 验证时间戳
            assertThat(event.getAlertTimestamp()).isGreaterThanOrEqualTo(beforeTime);
            assertThat(event.getAlertTimestamp()).isLessThanOrEqualTo(afterTime);
        }

        @Test
        @DisplayName("CRITICAL事件应该区别于WARNING事件")
        void should_differ_from_warning_events() {
            // When - 分别创建WARNING和CRITICAL事件
            EventLoopAlertEvent warningEvent = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);
            EventLoopAlertEvent criticalEvent = EventLoopAlertEvent.critical(6000, "EventLoop-3", 5000);

            // Then - 验证事件级别不同
            assertThat(warningEvent.getAlertLevel()).isNotEqualTo(criticalEvent.getAlertLevel());
            assertThat(warningEvent.getMessage()).doesNotContain("严重");
            assertThat(criticalEvent.getMessage()).contains("严重");
        }
    }

    @Nested
    @DisplayName("事件属性获取测试")
    class EventPropertyTests {

        @Test
        @DisplayName("应该正确获取告警级别")
        void should_get_alert_level_correctly() {
            // Given - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);

            // When - 获取告警级别
            EventLoopAlertEvent.AlertLevel level = event.getAlertLevel();

            // Then - 验证告警级别
            assertThat(level).isEqualTo(EventLoopAlertEvent.AlertLevel.WARNING);
        }

        @Test
        @DisplayName("应该正确获取待处理任务数")
        void should_get_pending_tasks_correctly() {
            // Given - 创建告警事件
            long pendingTasks = 2500;
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(pendingTasks, "EventLoop-1", 1000);

            // When - 获取待处理任务数
            long tasks = event.getPendingTasks();

            // Then - 验证待处理任务数
            assertThat(tasks).isEqualTo(pendingTasks);
        }

        @Test
        @DisplayName("应该正确获取EventExecutor信息")
        void should_get_event_executor_info_correctly() {
            // Given - 创建告警事件
            String executorInfo = "io.netty.channel.MultithreadEventLoopGroup$1@123456";
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(1500, executorInfo, 1000);

            // When - 获取EventExecutor信息
            String info = event.getEventExecutorInfo();

            // Then - 验证EventExecutor信息
            assertThat(info).isEqualTo(executorInfo);
        }

        @Test
        @DisplayName("应该正确获取告警阈值")
        void should_get_threshold_correctly() {
            // Given - 创建告警事件
            long threshold = 1000;
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(1500, "EventLoop-1", threshold);

            // When - 获取阈值
            long thresholdValue = event.getThreshold();

            // Then - 验证阈值
            assertThat(thresholdValue).isEqualTo(threshold);
        }

        @Test
        @DisplayName("应该正确获取告警消息")
        void should_get_message_correctly() {
            // Given - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);

            // When - 获取消息
            String message = event.getMessage();

            // Then - 验证消息
            assertThat(message).isNotEmpty();
            assertThat(message).isNotBlank();
        }

        @Test
        @DisplayName("应该能从事件中获取Spring ApplicationEvent的source")
        void should_have_event_source() {
            // Given - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);

            // When - 获取事件源
            Object source = event.getSource();

            // Then - 验证事件源不为null
            assertThat(source).isNotNull();
        }
    }

    @Nested
    @DisplayName("toString()方法测试")
    class ToStringTests {

        @Test
        @DisplayName("应该生成有意义的toString表示")
        void should_generate_meaningful_toString() {
            // Given - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);

            // When - 调用toString方法
            String result = event.toString();

            // Then - 验证toString内容
            assertThat(result).contains("EventLoopAlertEvent");
            assertThat(result).contains("WARNING");
            assertThat(result).contains("1500");
            assertThat(result).contains("1000");
            assertThat(result).contains("EventLoop-1");
        }

        @Test
        @DisplayName("CRITICAL事件的toString应该包含严重级别标识")
        void should_include_critical_level_in_toString() {
            // Given - 创建CRITICAL告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.critical(6000, "EventLoop-3", 5000);

            // When - 调用toString方法
            String result = event.toString();

            // Then - 验证toString包含CRITICAL标识
            assertThat(result).contains("CRITICAL");
        }

        @Test
        @DisplayName("toString应该包含完整的事件信息")
        void should_contain_complete_event_information() {
            // Given - 创建告警事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(2500, "EventLoop-2", 1000);

            // When - 调用toString方法
            String result = event.toString();

            // Then - 验证toString包含所有关键信息
            assertThat(result).contains("level=");
            assertThat(result).contains("pendingTasks=");
            assertThat(result).contains("threshold=");
            assertThat(result).contains("eventExecutor=");
            assertThat(result).contains("alertTimestamp=");
            assertThat(result).contains("message=");
        }
    }

    @Nested
    @DisplayName("序列化ID测试")
    class SerializationTests {

        @Test
        @DisplayName("应该具有serialVersionUID字段")
        void should_have_serial_version_uid() throws NoSuchFieldException {
            // When - 检查serialVersionUID字段
            java.lang.reflect.Field field = EventLoopAlertEvent.class.getDeclaredField("serialVersionUID");

            // Then - 验证字段存在
            assertThat(field).isNotNull();
            field.setAccessible(true);
            Object value = null;
            try {
                value = field.get(null);
            } catch (IllegalAccessException e) {
                // 忽略访问异常
            }
            assertThat(value).isNotNull();
        }
    }

    @Nested
    @DisplayName("事件场景测试")
    class EventScenarioTests {

        @Test
        @DisplayName("应该能处理边界情况：待处理任务数为0")
        void should_handle_zero_pending_tasks() {
            // When - 创建待处理任务数为0的事件
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(0, "EventLoop-1", 1000);

            // Then - 验证事件创建成功
            assertThat(event.getPendingTasks()).isEqualTo(0);
        }

        @Test
        @DisplayName("应该能处理大数值的待处理任务数")
        void should_handle_large_pending_tasks() {
            // When - 创建待处理任务数很大的事件
            long largePendingTasks = Long.MAX_VALUE;
            EventLoopAlertEvent event = EventLoopAlertEvent.critical(largePendingTasks, "EventLoop-1", 5000);

            // Then - 验证事件创建成功
            assertThat(event.getPendingTasks()).isEqualTo(largePendingTasks);
        }

        @Test
        @DisplayName("应该能处理多个事件对象独立记录时间戳")
        void should_handle_multiple_events_with_independent_timestamps() throws InterruptedException {
            // When - 创建两个事件
            EventLoopAlertEvent event1 = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);
            Thread.sleep(10); // 模拟时间间隔
            EventLoopAlertEvent event2 = EventLoopAlertEvent.warning(1500, "EventLoop-1", 1000);

            // Then - 验证时间戳递增
            assertThat(event2.getAlertTimestamp()).isGreaterThanOrEqualTo(event1.getAlertTimestamp());
        }

        @Test
        @DisplayName("应该能处理特殊字符的EventExecutor信息")
        void should_handle_special_characters_in_executor_info() {
            // When - 创建包含特殊字符的EventExecutor信息
            String specialInfo = "EventLoop@[192.168.1.1]:8443#worker-1";
            EventLoopAlertEvent event = EventLoopAlertEvent.warning(1500, specialInfo, 1000);

            // Then - 验证事件创建成功
            assertThat(event.getEventExecutorInfo()).isEqualTo(specialInfo);
            assertThat(event.getMessage()).contains(specialInfo);
        }
    }
}
