package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.rpc.dto.config.DataCleanupConfigDTO;
import com.colorlight.terminal.rpc.dto.enums.CleanupMode;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异步缓冲区刷新事件测试
 * 
 * @author Nan
 */
@DisplayName("异步缓冲区刷新事件测试")
class AsyncBufferFlushEventTest {

    @Test
    @DisplayName("应该使用Builder正确创建事件")
    void should_create_event_with_builder() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();
        Integer bufferSize = 100;
        Long eventTime = System.currentTimeMillis();

        // When - 使用Builder创建事件
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.builder()
                .bufferType(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS)
                .serviceInstance(serviceInstance)
                .eventTime(eventTime)
                .bufferSize(bufferSize)
                .build();

        // Then - 验证事件属性
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getEventTime()).isEqualTo(eventTime);
        assertThat(event.getBufferSize()).isEqualTo(bufferSize);
    }

    @Test
    @DisplayName("应该正确创建设备状态刷新事件")
    void should_create_device_status_flush_event() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();
        Integer bufferSize = 50;
        long beforeTime = System.currentTimeMillis();

        // When - 创建设备状态刷新事件
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createDeviceStatusFlushEvent(serviceInstance, bufferSize);
        long afterTime = System.currentTimeMillis();

        // Then - 验证事件属性
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getBufferSize()).isEqualTo(bufferSize);
        assertThat(event.getEventTime()).isBetween(beforeTime, afterTime);
    }

    @Test
    @DisplayName("应该正确创建GPS记录刷新事件")
    void should_create_gps_record_flush_event() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();
        Integer bufferSize = 75;
        long beforeTime = System.currentTimeMillis();

        // When - 创建GPS记录刷新事件
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createGpsRecordFlushEvent(serviceInstance, bufferSize);
        long afterTime = System.currentTimeMillis();

        // Then - 验证事件属性
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.GPS_RECORD);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getBufferSize()).isEqualTo(bufferSize);
        assertThat(event.getEventTime()).isBetween(beforeTime, afterTime);
    }

    @Test
    @DisplayName("应该正确创建登录更新刷新事件")
    void should_create_login_update_flush_event() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();
        Integer bufferSize = 25;
        long beforeTime = System.currentTimeMillis();

        // When - 创建登录更新刷新事件
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createLoginUpdateFlushEvent(serviceInstance, bufferSize);
        long afterTime = System.currentTimeMillis();

        // Then - 验证事件属性
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.LOGIN_UPDATE);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getBufferSize()).isEqualTo(bufferSize);
        assertThat(event.getEventTime()).isBetween(beforeTime, afterTime);
    }

    @Test
    @DisplayName("应该支持null值的服务实例")
    void should_support_null_service_instance() {
        // When - 创建事件时传入null服务实例
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createDeviceStatusFlushEvent(null, 100);

        // Then - 验证事件可以正常创建
        assertThat(event.getServiceInstance()).isNull();
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS);
        assertThat(event.getBufferSize()).isEqualTo(100);
        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("应该支持null值的缓冲区大小")
    void should_support_null_buffer_size() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();

        // When - 创建事件时传入null缓冲区大小
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createGpsRecordFlushEvent(serviceInstance, null);

        // Then - 验证事件可以正常创建
        assertThat(event.getBufferSize()).isNull();
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.GPS_RECORD);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("应该支持零值的缓冲区大小")
    void should_support_zero_buffer_size() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();

        // When - 创建事件时传入0缓冲区大小
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createLoginUpdateFlushEvent(serviceInstance, 0);

        // Then - 验证事件可以正常创建
        assertThat(event.getBufferSize()).isZero();
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.LOGIN_UPDATE);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("应该支持负值的缓冲区大小")
    void should_support_negative_buffer_size() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();

        // When - 创建事件时传入负数缓冲区大小
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createDeviceStatusFlushEvent(serviceInstance, -1);

        // Then - 验证事件可以正常创建
        assertThat(event.getBufferSize()).isEqualTo(-1);
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("BufferType枚举应该有正确的描述")
    void should_have_correct_buffer_type_descriptions() {
        // Then - 验证枚举描述
        assertThat(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS.getDescription()).isEqualTo("设备状态更新");
        assertThat(AsyncBufferFlushEvent.BufferType.GPS_RECORD.getDescription()).isEqualTo("GPS记录");
        assertThat(AsyncBufferFlushEvent.BufferType.LOGIN_UPDATE.getDescription()).isEqualTo("终端登录更新");
        assertThat(AsyncBufferFlushEvent.BufferType.DEVICE_CLEANUP.getDescription()).isEqualTo("设备数据清理");
    }

    @Test
    @DisplayName("BufferType枚举应该包含所有必要的类型")
    void should_contain_all_necessary_buffer_types() {
        // Then - 验证枚举包含所有类型
        AsyncBufferFlushEvent.BufferType[] types = AsyncBufferFlushEvent.BufferType.values();

        assertThat(types).hasSize(4)
                .containsExactlyInAnyOrder(
                AsyncBufferFlushEvent.BufferType.DEVICE_STATUS,
                AsyncBufferFlushEvent.BufferType.GPS_RECORD,
                AsyncBufferFlushEvent.BufferType.LOGIN_UPDATE,
                AsyncBufferFlushEvent.BufferType.DEVICE_CLEANUP
        );
    }

    @Test
    @DisplayName("事件应该正确实现equals和hashCode")
    void should_implement_equals_and_hashcode_correctly() {
        // Given - 创建两个相同的事件
        Object serviceInstance = new Object();
        Integer bufferSize = 100;
        Long eventTime = 1000L;

        AsyncBufferFlushEvent event1 = AsyncBufferFlushEvent.builder()
                .bufferType(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS)
                .serviceInstance(serviceInstance)
                .eventTime(eventTime)
                .bufferSize(bufferSize)
                .build();

        AsyncBufferFlushEvent event2 = AsyncBufferFlushEvent.builder()
                .bufferType(AsyncBufferFlushEvent.BufferType.DEVICE_STATUS)
                .serviceInstance(serviceInstance)
                .eventTime(eventTime)
                .bufferSize(bufferSize)
                .build();

        // Then - 验证equals和hashCode
        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).hasSameHashCodeAs(event2.hashCode());
    }

    @Test
    @DisplayName("事件应该正确实现toString")
    void should_implement_toString_correctly() {
        // Given - 创建事件
        Object serviceInstance = new Object();
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createDeviceStatusFlushEvent(serviceInstance, 100);

        // When - 调用toString
        String toString = event.toString();

        // Then - 验证toString包含关键信息
        assertThat(toString)
                .contains("AsyncBufferFlushEvent")
                .contains("DEVICE_STATUS")
                .contains("100");
    }

    @Test
    @DisplayName("应该正确处理大数值的缓冲区大小")
    void should_handle_large_buffer_size() {
        // Given - 准备大数值
        Object serviceInstance = new Object();
        Integer largeBufferSize = Integer.MAX_VALUE;

        // When - 创建事件
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createGpsRecordFlushEvent(serviceInstance, largeBufferSize);

        // Then - 验证事件正确创建
        assertThat(event.getBufferSize()).isEqualTo(Integer.MAX_VALUE);
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.GPS_RECORD);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
    }

    @Test
    @DisplayName("应该正确处理最小数值的缓冲区大小")
    void should_handle_minimum_buffer_size() {
        // Given - 准备最小数值
        Object serviceInstance = new Object();
        Integer minBufferSize = Integer.MIN_VALUE;

        // When - 创建事件
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createLoginUpdateFlushEvent(serviceInstance, minBufferSize);

        // Then - 验证事件正确创建
        assertThat(event.getBufferSize()).isEqualTo(Integer.MIN_VALUE);
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.LOGIN_UPDATE);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
    }

    @Test
    @DisplayName("不同工厂方法创建的事件应该有不同的类型")
    void should_create_different_types_with_factory_methods() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();
        Integer bufferSize = 100;

        // When - 使用不同工厂方法创建事件
        AsyncBufferFlushEvent deviceEvent = AsyncBufferFlushEvent.createDeviceStatusFlushEvent(serviceInstance, bufferSize);
        AsyncBufferFlushEvent gpsEvent = AsyncBufferFlushEvent.createGpsRecordFlushEvent(serviceInstance, bufferSize);
        AsyncBufferFlushEvent loginEvent = AsyncBufferFlushEvent.createLoginUpdateFlushEvent(serviceInstance, bufferSize);

        // Then - 验证事件类型不同
        assertThat(deviceEvent.getBufferType()).isNotEqualTo(gpsEvent.getBufferType());
        assertThat(gpsEvent.getBufferType()).isNotEqualTo(loginEvent.getBufferType());
        assertThat(loginEvent.getBufferType()).isNotEqualTo(deviceEvent.getBufferType());

        // 验证其他属性相同
        assertThat(deviceEvent.getServiceInstance()).isSameAs(gpsEvent.getServiceInstance());
        assertThat(gpsEvent.getServiceInstance()).isSameAs(loginEvent.getServiceInstance());
        assertThat(deviceEvent.getBufferSize()).isEqualTo(gpsEvent.getBufferSize());
        assertThat(gpsEvent.getBufferSize()).isEqualTo(loginEvent.getBufferSize());
    }

    @Test
    @DisplayName("事件时间应该在合理范围内")
    void should_have_reasonable_event_time() {
        // Given - 记录创建前的时间
        long beforeTime = System.currentTimeMillis();

        // When - 创建多个事件
        AsyncBufferFlushEvent event1 = AsyncBufferFlushEvent.createDeviceStatusFlushEvent(new Object(), 100);
        AsyncBufferFlushEvent event2 = AsyncBufferFlushEvent.createGpsRecordFlushEvent(new Object(), 100);
        
        // 记录创建后的时间
        long afterTime = System.currentTimeMillis();

        // Then - 验证时间在合理范围内
        assertThat(event1.getEventTime()).isBetween(beforeTime, afterTime);
        assertThat(event2.getEventTime()).isBetween(beforeTime, afterTime);
    }

    @Test
    @DisplayName("应该正确创建设备数据清理刷新事件")
    void should_create_device_cleanup_flush_event() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();
        Long deviceId = 12345L;
        DataCleanupConfigDTO customConfig = new DataCleanupConfigDTO();
        customConfig.setMode(CleanupMode.INCLUDE);
        customConfig.setDataTypes(Set.of(DataType.DEVICE_ACCOUNT, DataType.GPS_RECORD));
        long beforeTime = System.currentTimeMillis();

        // When - 创建设备数据清理刷新事件
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createDeviceCleanupFlushEvent(
                serviceInstance, deviceId, customConfig);
        long afterTime = System.currentTimeMillis();

        // Then - 验证事件属性
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.DEVICE_CLEANUP);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getBufferSize()).isEqualTo(1); // 单个设备清理
        assertThat(event.getDeviceId()).isEqualTo(deviceId);
        assertThat(event.getCustomConfig()).isSameAs(customConfig);
        assertThat(event.getEventTime()).isBetween(beforeTime, afterTime);
    }

    @Test
    @DisplayName("应该支持null自定义配置的设备清理事件")
    void should_create_device_cleanup_event_with_null_config() {
        // Given - 准备测试数据
        Object serviceInstance = new Object();
        Long deviceId = 67890L;

        // When - 创建设备数据清理刷新事件，配置为null
        AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createDeviceCleanupFlushEvent(
                serviceInstance, deviceId, null);

        // Then - 验证事件可以正常创建
        assertThat(event.getBufferType()).isEqualTo(AsyncBufferFlushEvent.BufferType.DEVICE_CLEANUP);
        assertThat(event.getServiceInstance()).isSameAs(serviceInstance);
        assertThat(event.getDeviceId()).isEqualTo(deviceId);
        assertThat(event.getCustomConfig()).isNull();
        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("设备清理事件应该正确实现equals和hashCode")
    void should_implement_equals_and_hashcode_for_device_cleanup_event() {
        // Given - 创建两个相同的设备清理事件
        Object serviceInstance = new Object();
        Long deviceId = 11111L;
        DataCleanupConfigDTO customConfig = new DataCleanupConfigDTO();
        customConfig.setMode(CleanupMode.ALL);
        Long eventTime = 2000L;

        AsyncBufferFlushEvent event1 = AsyncBufferFlushEvent.builder()
                .bufferType(AsyncBufferFlushEvent.BufferType.DEVICE_CLEANUP)
                .serviceInstance(serviceInstance)
                .eventTime(eventTime)
                .bufferSize(1)
                .deviceId(deviceId)
                .customConfig(customConfig)
                .build();

        AsyncBufferFlushEvent event2 = AsyncBufferFlushEvent.builder()
                .bufferType(AsyncBufferFlushEvent.BufferType.DEVICE_CLEANUP)
                .serviceInstance(serviceInstance)
                .eventTime(eventTime)
                .bufferSize(1)
                .deviceId(deviceId)
                .customConfig(customConfig)
                .build();

        // Then - 验证equals和hashCode
        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).hasSameHashCodeAs(event2.hashCode());
    }
}