package com.colorlight.terminal.infrastructure.cache.redis.listener;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.port.outbound.rpc.MainServerRpcPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RedisKeysExpirationListener 单元测试
 *
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis键过期监听器测试")
class RedisKeysExpirationListenerTest {

    @Mock
    private RedisMessageListenerContainer listenerContainer;
    
    @Mock
    private DeviceOnlineStatusPort deviceOnlineStatusPort;
    
    @Mock
    private DeviceStatusEventPort deviceStatusEventPort;
    
    @Mock
    private PatternTopic keyExpirationTopic;
    
    @Mock
    private MainServerRpcPort mainServerRpcPort;
    
    @Mock
    private Message message;
    
    private RedisKeysExpirationListener listener;
    
    @BeforeEach
    void setUp() {
        listener = new RedisKeysExpirationListener(
            listenerContainer,
            deviceOnlineStatusPort,
            deviceStatusEventPort,
            keyExpirationTopic,
            mainServerRpcPort
        );
    }

    @Nested
    @DisplayName("初始化测试")
    class InitializationTest {
        
        @Test
        @DisplayName("应该正确注册消息监听器")
        void shouldRegisterMessageListener() {
            // Given
            when(keyExpirationTopic.getTopic()).thenReturn("__keyevent@*__:expired");
            
            // When
            listener.init();
            
            // Then
            verify(listenerContainer).addMessageListener(listener, keyExpirationTopic);
            verify(keyExpirationTopic).getTopic();
        }
    }

    @Nested
    @DisplayName("设备状态键过期处理测试")
    class DeviceStatusExpirationTest {
        
        @Test
        @DisplayName("应该正确处理设备状态键过期")
        void shouldHandleDeviceStatusExpiration() {
            // Given
            Long deviceId = 123L;
            String expiredKey = "device:status:" + deviceId;
            when(message.toString()).thenReturn(expiredKey);
            
            // When
            listener.onMessage(message, null);
            
            // Then
            verify(deviceOnlineStatusPort).removeDeviceIndex(deviceId);
            
            ArgumentCaptor<DeviceStatusEvent> eventCaptor = ArgumentCaptor.forClass(DeviceStatusEvent.class);
            verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
            
            DeviceStatusEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getDeviceId()).isEqualTo(deviceId);
            assertThat(capturedEvent.getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_CONFIRMED_OFFLINE);
        }
        
        @Test
        @DisplayName("应该正确处理多位数设备ID的状态键过期")
        void shouldHandleMultiDigitDeviceStatusExpiration() {
            // Given
            Long deviceId = 9876543210L;
            String expiredKey = "device:status:" + deviceId;
            when(message.toString()).thenReturn(expiredKey);
            
            // When
            listener.onMessage(message, null);
            
            // Then
            verify(deviceOnlineStatusPort).removeDeviceIndex(deviceId);
            verify(deviceStatusEventPort).publishStatusEvent(any(DeviceStatusEvent.class));
        }
        
        @Test
        @DisplayName("设备状态处理异常时应该记录错误但不抛出异常")
        void shouldHandleDeviceStatusExceptionGracefully() {
            // Given
            Long deviceId = 123L;
            String expiredKey = "device:status:" + deviceId;
            when(message.toString()).thenReturn(expiredKey);
            doThrow(new RuntimeException("模拟异常")).when(deviceOnlineStatusPort).removeDeviceIndex(deviceId);
            
            // When & Then
            assertThatCode(() -> listener.onMessage(message, null))
                .doesNotThrowAnyException();
            
            verify(deviceOnlineStatusPort).removeDeviceIndex(deviceId);
            // 异常发生时不应该发布事件
            verify(deviceStatusEventPort, never()).publishStatusEvent(any());
        }
        
        @Test
        @DisplayName("事件发布异常时应该记录错误但不影响状态清理")
        void shouldHandleEventPublishExceptionGracefully() {
            // Given
            Long deviceId = 123L;
            String expiredKey = "device:status:" + deviceId;
            when(message.toString()).thenReturn(expiredKey);
            doThrow(new RuntimeException("事件发布异常")).when(deviceStatusEventPort).publishStatusEvent(any());
            
            // When & Then
            assertThatCode(() -> listener.onMessage(message, null))
                .doesNotThrowAnyException();
            
            // 设备状态清理仍应执行
            verify(deviceOnlineStatusPort).removeDeviceIndex(deviceId);
            verify(deviceStatusEventPort).publishStatusEvent(any(DeviceStatusEvent.class));
        }
    }

    @Nested
    @DisplayName("边界条件和异常情况测试")
    class EdgeCasesTest {
        
        @Test
        @DisplayName("应该忽略不匹配的键")
        void shouldIgnoreUnmatchedKeys() {
            // Given
            String expiredKey = "some:other:key:123";
            when(message.toString()).thenReturn(expiredKey);
            
            // When
            listener.onMessage(message, null);
            
            // Then
            verify(deviceOnlineStatusPort, never()).removeDeviceIndex(any());
            verify(deviceStatusEventPort, never()).publishStatusEvent(any());
        }
        
        @Test
        @DisplayName("应该忽略格式错误的设备状态键")
        void shouldIgnoreMalformedDeviceStatusKeys() {
            // Given
            String expiredKey = "device:status:invalid_id";
            when(message.toString()).thenReturn(expiredKey);
            
            // When
            listener.onMessage(message, null);
            
            // Then
            verify(deviceOnlineStatusPort, never()).removeDeviceIndex(any());
            verify(deviceStatusEventPort, never()).publishStatusEvent(any());
        }
        
        @Test
        @DisplayName("空消息应该被忽略")
        void shouldHandleEmptyMessage() {
            // Given
            when(message.toString()).thenReturn("");
            
            // When & Then
            assertThatCode(() -> listener.onMessage(message, null))
                .doesNotThrowAnyException();
            
            verify(deviceOnlineStatusPort, never()).removeDeviceIndex(any());
            verify(deviceStatusEventPort, never()).publishStatusEvent(any());
        }
        
        @Test
        @DisplayName("null消息应该被处理而不抛出异常")
        void shouldHandleNullMessage() {
            // Given
            when(message.toString()).thenReturn(null);
            
            // When & Then
            assertThatCode(() -> listener.onMessage(message, null))
                .doesNotThrowAnyException();
            
            verify(deviceOnlineStatusPort, never()).removeDeviceIndex(any());
            verify(deviceStatusEventPort, never()).publishStatusEvent(any());
        }
        
        @Test
        @DisplayName("消息解析异常应该被捕获")
        void shouldHandleMessageParsingException() {
            // Given
            when(message.toString()).thenThrow(new RuntimeException("消息解析异常"));
            
            // When & Then
            assertThatCode(() -> listener.onMessage(message, null))
                .doesNotThrowAnyException();
            
            verify(deviceOnlineStatusPort, never()).removeDeviceIndex(any());
            verify(deviceStatusEventPort, never()).publishStatusEvent(any());
        }
        
        @Test
        @DisplayName("应该正确处理最小值设备ID")
        void shouldHandleMinimumDeviceId() {
            // Given
            Long deviceId = 1L;
            String expiredKey = "device:status:" + deviceId;
            when(message.toString()).thenReturn(expiredKey);
            
            // When
            listener.onMessage(message, null);
            
            // Then
            verify(deviceOnlineStatusPort).removeDeviceIndex(deviceId);
            verify(deviceStatusEventPort).publishStatusEvent(any(DeviceStatusEvent.class));
        }
        
        @Test
        @DisplayName("应该正确处理最大值设备ID")
        void shouldHandleMaximumDeviceId() {
            // Given
            Long deviceId = Long.MAX_VALUE;
            String expiredKey = "device:status:" + deviceId;
            when(message.toString()).thenReturn(expiredKey);
            
            // When
            listener.onMessage(message, null);
            
            // Then
            verify(deviceOnlineStatusPort).removeDeviceIndex(deviceId);
            verify(deviceStatusEventPort).publishStatusEvent(any(DeviceStatusEvent.class));
        }
    }
}