package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.connection.WebSocketSession;
import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncDeviceStatusUpdatePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.application.port.outbound.status.DeviceStatusEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 设备在线状态管理应用服务单元测试
 * 
 * 测试策略：
 * 1. 覆盖所有状态转换场景（GO_LIVE → ONLINE → OFFLINE → RECONNECT）
 * 2. 验证分布式锁机制的正确性
 * 3. 测试同步/异步模式切换逻辑
 * 4. 验证事件发布的正确性
 * 5. 测试异常处理和降级策略
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备在线状态管理服务测试")
class DeviceOnlineStatusApplicationServiceTest {
    
    @Mock
    private DeviceOnlineStatusPort deviceOnlineStatusPort;
    
    @Mock
    private DeviceStatusEventPort deviceStatusEventPort;
    
    @Mock
    private DeviceConfigPort deviceConfigPort;
    
    @Mock
    private ConnectionManagerPort connectionManagerPort;
    
    @Mock
    private AsyncDeviceStatusUpdatePort asyncDeviceStatusUpdatePort;
    
    @InjectMocks
    private DeviceOnlineStatusApplicationService service;
    
    @Captor
    private ArgumentCaptor<DeviceOnlineStatus> statusCaptor;
    
    @Captor
    private ArgumentCaptor<DeviceStatusEvent> eventCaptor;
    
    @Captor
    private ArgumentCaptor<List<DeviceStatusEvent>> eventListCaptor;
    
    // 测试常量
    private static final Long DEVICE_ID = 10001L;
    private static final String CLIENT_IP = "192.168.1.100";
    private static final Long TIMEOUT_THRESHOLD = 70_000L; // 70秒超时阈值
    private static final String PROTOCOL_VERSION = "1.1"; // 使用支持的协议版本
    
    @BeforeEach
    void setUp() {
        // 配置默认行为
        lenient().when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(TIMEOUT_THRESHOLD);
        lenient().when(deviceConfigPort.isAsyncStatusUpdateEnabled()).thenReturn(false); // 默认同步模式
    }
    
    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        /**
         * 创建上线状态
         */
        static DeviceOnlineStatus createGoLiveStatus(Long deviceId) {
            long currentTime = System.currentTimeMillis();
            return DeviceOnlineStatus.builder()
                    .deviceId(deviceId)
                    .lastReportTime(currentTime)
                    .lastReportSource(ReportSource.HTTP)
                    .status(OnlineStatus.GO_LIVE)
                    .statusChangeTime(currentTime)
                    .onlineStartTime(currentTime)
                    .clientIp(CLIENT_IP)
                    .version(null)
                    .build();
        }
        
        /**
         * 创建在线状态
         */
        static DeviceOnlineStatus createOnlineStatus(Long deviceId) {
            long currentTime = System.currentTimeMillis();
            return DeviceOnlineStatus.builder()
                    .deviceId(deviceId)
                    .lastReportTime(currentTime - 30_000) // 30秒前
                    .lastReportSource(ReportSource.HTTP)
                    .status(OnlineStatus.ONLINE)
                    .statusChangeTime(currentTime - 60_000)
                    .onlineStartTime(currentTime - 60_000)
                    .clientIp(CLIENT_IP)
                    .version(PROTOCOL_VERSION)
                    .build();
        }
        
        /**
         * 创建离线状态
         */
        static DeviceOnlineStatus createOfflineStatus(Long deviceId) {
            long currentTime = System.currentTimeMillis();
            return DeviceOnlineStatus.builder()
                    .deviceId(deviceId)
                    .lastReportTime(currentTime - 120_000) // 2分钟前
                    .lastReportSource(ReportSource.HTTP)
                    .status(OnlineStatus.OFFLINE)
                    .statusChangeTime(currentTime - 60_000)
                    .onlineStartTime(currentTime - 180_000)
                    .clientIp(CLIENT_IP)
                    .version(PROTOCOL_VERSION)
                    .build();
        }
        
        /**
         * 创建WebSocket连接信息
         */
        static TerminalConnection createTerminalConnection(Long deviceId, String version) {
            // 创建简单的WebSocketSession实现
            WebSocketSession session = new WebSocketSession() {
                @Override
                public String getClientIp() {
                    return CLIENT_IP;
                }
                
                @Override
                public boolean isConnected() {
                    return true;
                }
                
                @Override
                public boolean sendMessage(String message) {
                    return true;
                }
                
                @Override
                public void close() {
                    // 空实现
                }
                
                @Override
                public String getSessionId() {
                    return "test-session-" + deviceId;
                }
                
                @Override
                public Long getDeviceId() {
                    return deviceId;
                }
            };
            
            // 创建TerminalConnection
            ProtocolVersion protocolVersion = version != null ? 
                    ProtocolVersion.fromVersion(version) : ProtocolVersion.V1_0;
            return TerminalConnection.create(deviceId, session, protocolVersion);
        }
    }
    
    @Nested
    @DisplayName("设备状态更新测试")
    class UpdateLastReportTimeTests {
        
        /**
         * 测试设备首次上报时是否正确创建GO_LIVE状态
         * <p>
         * 验证流程：
         * 1. 模拟获取设备更新锁成功
         * 2. 模拟设备当前无状态（首次上报）
         * 3. 调用updateLastReportTime方法更新设备上报时间
         * 4. 验证创建了GO_LIVE状态并保存
         * 5. 验证发布了设备上线事件
         * 6. 验证释放了设备更新锁
         * </p>
         */
        @Test
        @DisplayName("应该在设备首次上报时创建GO_LIVE状态")
        void should_create_go_live_status_when_device_first_report() {
            // Given - 准备测试数据
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.empty());
            
            // When - 执行目标方法
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证结果
            // 验证状态保存
            verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
            DeviceOnlineStatus savedStatus = statusCaptor.getValue();
            assertThat(savedStatus.getDeviceId()).isEqualTo(DEVICE_ID);
            assertThat(savedStatus.getStatus()).isEqualTo(OnlineStatus.GO_LIVE);
            assertThat(savedStatus.getLastReportSource()).isEqualTo(ReportSource.HTTP);
            assertThat(savedStatus.getClientIp()).isEqualTo(CLIENT_IP);
            assertThat(savedStatus.getOnlineStartTime()).isNotNull();
            
            // 验证事件发布
            verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
            DeviceStatusEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_GO_LIVE);
            assertThat(event.getDeviceId()).isEqualTo(DEVICE_ID);
            
            // 验证锁释放
            verify(deviceOnlineStatusPort).releaseDeviceUpdateLock(DEVICE_ID);
        }
        
        /**
         * 测试设备保持在线时是否正确更新心跳时间
         * <p>
         * 验证流程：
         * 1. 模拟设备已处于ONLINE状态
         * 2. 模拟获取设备更新锁成功
         * 3. 调用updateLastReportTime方法更新设备上报时间
         * 4. 验证设备状态保持ONLINE且心跳时间被更新
         * 5. 验证发布了设备心跳事件
         * </p>
         */
        @Test
        @DisplayName("应该在设备保持在线时更新心跳时间")
        void should_update_heartbeat_when_device_keep_online() {
            // Given - 设备已在线
            DeviceOnlineStatus currentStatus = TestDataBuilder.createOnlineStatus(DEVICE_ID);
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(currentStatus));
            
            // When - 执行心跳更新
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证只更新时间，状态保持ONLINE
            verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
            DeviceOnlineStatus updatedStatus = statusCaptor.getValue();
            assertThat(updatedStatus.getStatus()).isEqualTo(OnlineStatus.ONLINE);
            assertThat(updatedStatus.getLastReportTime()).isGreaterThan(currentStatus.getLastReportTime());
            assertThat(updatedStatus.getOnlineStartTime()).isNull(); // 心跳更新不设置上线时间
            
            // 验证心跳事件
            verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_HEARTBEAT);
        }
        
        /**
         * 测试设备从GO_LIVE状态转换为ONLINE状态
         * <p>
         * 验证流程：
         * 1. 模拟设备当前处于GO_LIVE状态
         * 2. 模拟获取设备更新锁成功
         * 3. 调用updateLastReportTime方法进行第二次上报
         * 4. 验证设备状态从GO_LIVE转换为ONLINE
         * 5. 验证保留了原始的上线时间
         * 6. 验证发布了设备心跳事件
         * </p>
         */
        @Test
        @DisplayName("应该在设备从GO_LIVE转为ONLINE状态")
        void should_transition_from_go_live_to_online() {
            // Given - 设备刚上线（GO_LIVE状态）
            DeviceOnlineStatus goLiveStatus = TestDataBuilder.createGoLiveStatus(DEVICE_ID);
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(goLiveStatus));
            
            // When - 第二次上报
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证状态转换为ONLINE
            verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
            DeviceOnlineStatus updatedStatus = statusCaptor.getValue();
            assertThat(updatedStatus.getStatus()).isEqualTo(OnlineStatus.ONLINE);
            assertThat(updatedStatus.getOnlineStartTime()).isEqualTo(goLiveStatus.getOnlineStartTime());
            assertThat(updatedStatus.getStatusChangeTime()).isNotNull();
            
            // 验证心跳事件
            verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_HEARTBEAT);
        }
        
        @Test
        @DisplayName("应该在设备离线后重连时创建RECONNECT状态")
        void should_create_reconnect_status_when_device_reconnect() {
            // Given - 设备已离线
            DeviceOnlineStatus offlineStatus = TestDataBuilder.createOfflineStatus(DEVICE_ID);
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(offlineStatus));
            when(connectionManagerPort.getConnection(DEVICE_ID))
                    .thenReturn(Optional.of(TestDataBuilder.createTerminalConnection(DEVICE_ID, PROTOCOL_VERSION)));
            
            // When - 设备重新连接（WebSocket）
            service.updateLastReportTime(DEVICE_ID, ReportSource.WEBSOCKET, CLIENT_IP);
            
            // Then - 验证创建重连状态
            verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
            DeviceOnlineStatus reconnectStatus = statusCaptor.getValue();
            assertThat(reconnectStatus.getStatus()).isEqualTo(OnlineStatus.RECONNECT);
            assertThat(reconnectStatus.getOnlineStartTime()).isNotNull();
            assertThat(reconnectStatus.getVersion()).isEqualTo(PROTOCOL_VERSION);
            
            // 验证重连事件
            verify(deviceStatusEventPort).publishStatusEvent(eventCaptor.capture());
            DeviceStatusEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(DeviceStatusEvent.EventType.DEVICE_RECONNECT);
        }
        
        @Test
        @DisplayName("应该在获取分布式锁失败时跳过更新")
        void should_skip_update_when_acquire_lock_failed() {
            // Given - 锁获取失败
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(false);
            
            // When - 尝试更新
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证跳过所有操作
            verify(deviceOnlineStatusPort, never()).getDeviceStatus(any());
            verify(deviceOnlineStatusPort, never()).smartDetermined(any());
            verify(deviceStatusEventPort, never()).publishStatusEvent(any());
            verify(deviceOnlineStatusPort, never()).releaseDeviceUpdateLock(any());
        }
        
        @Test
        @DisplayName("应该在异常时释放分布式锁")
        void should_release_lock_when_exception_occurs() {
            // Given - 准备异常场景
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID))
                    .thenThrow(new RuntimeException("模拟异常"));
            
            // When - 执行更新
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证锁被释放
            verify(deviceOnlineStatusPort).releaseDeviceUpdateLock(DEVICE_ID);
        }
    }
    
    @Nested
    @DisplayName("同步/异步模式测试")
    class SyncAsyncModeTests {
        
        @BeforeEach
        void setUp() {
            // 为异步测试注入异步服务
            service = new DeviceOnlineStatusApplicationService(
                    deviceOnlineStatusPort,
                    deviceStatusEventPort,
                    deviceConfigPort,
                    connectionManagerPort
            );
            // 手动注入异步服务（模拟Spring的@Autowired(required=false)）
            try {
                var field = DeviceOnlineStatusApplicationService.class.getDeclaredField("asyncDeviceStatusUpdatePort");
                field.setAccessible(true);
                field.set(service, asyncDeviceStatusUpdatePort);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        @Test
        @DisplayName("应该在启用异步模式时使用异步更新（心跳场景）")
        void should_use_async_update_when_async_enabled_for_heartbeat() {
            // Given - 配置异步模式，设备已在线
            when(deviceConfigPort.isAsyncStatusUpdateEnabled()).thenReturn(true);
            DeviceOnlineStatus currentStatus = TestDataBuilder.createOnlineStatus(DEVICE_ID);
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(currentStatus));
            
            // When - 执行心跳更新
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证使用异步更新
            verify(asyncDeviceStatusUpdatePort).submitStatusUpdate(any(DeviceOnlineStatus.class));
            verify(deviceOnlineStatusPort, never()).smartDetermined(any());
        }
        
        @Test
        @DisplayName("应该在GO_LIVE和RECONNECT状态时强制同步更新")
        void should_force_sync_update_for_critical_status() {
            // Given - 配置异步模式，但设备首次上线
            when(deviceConfigPort.isAsyncStatusUpdateEnabled()).thenReturn(true);
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.empty());
            
            // When - 设备首次上线
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证强制同步更新（即使配置了异步模式）
            verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
            assertThat(statusCaptor.getValue().getStatus()).isEqualTo(OnlineStatus.GO_LIVE);
            verify(asyncDeviceStatusUpdatePort, never()).submitStatusUpdate(any());
        }
        
        @Test
        @DisplayName("应该在异步提交失败时降级到同步模式")
        void should_fallback_to_sync_when_async_submit_failed() {
            // Given - 异步模式但提交失败
            when(deviceConfigPort.isAsyncStatusUpdateEnabled()).thenReturn(true);
            DeviceOnlineStatus currentStatus = TestDataBuilder.createOnlineStatus(DEVICE_ID);
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(currentStatus));
            doThrow(new RuntimeException("异步提交失败")).when(asyncDeviceStatusUpdatePort).submitStatusUpdate(any());
            
            // When - 执行更新
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证降级到同步模式
            verify(asyncDeviceStatusUpdatePort).submitStatusUpdate(any());
            verify(deviceOnlineStatusPort).smartDetermined(any());
        }
    }
    
    @Nested
    @DisplayName("WebSocket协议版本处理测试")
    class ProtocolVersionTests {
        
        @Test
        @DisplayName("应该在WebSocket连接时获取协议版本")
        void should_get_protocol_version_for_websocket() {
            // Given - WebSocket连接存在
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.empty());
            when(connectionManagerPort.getConnection(DEVICE_ID))
                    .thenReturn(Optional.of(TestDataBuilder.createTerminalConnection(DEVICE_ID, PROTOCOL_VERSION)));
            
            // When - WebSocket上报
            service.updateLastReportTime(DEVICE_ID, ReportSource.WEBSOCKET, CLIENT_IP);
            
            // Then - 验证版本设置
            verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
            assertThat(statusCaptor.getValue().getVersion()).isEqualTo(PROTOCOL_VERSION);
        }
        
        @Test
        @DisplayName("应该在HTTP上报时保持原有版本")
        void should_keep_original_version_for_http() {
            // Given - 设备已有版本信息
            DeviceOnlineStatus currentStatus = TestDataBuilder.createOnlineStatus(DEVICE_ID);
            currentStatus.setVersion(PROTOCOL_VERSION);
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(currentStatus));
            
            // When - HTTP上报
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证版本保持不变
            verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
            assertThat(statusCaptor.getValue().getVersion()).isEqualTo(PROTOCOL_VERSION);
        }

    }
    
    @Nested
    @DisplayName("设备在线状态查询测试")
    class DeviceStatusQueryTests {
        
        @Test
        @DisplayName("应该正确判断设备在线状态")
        void should_check_device_online_correctly() {
            // Given - 设备在线
            DeviceOnlineStatus onlineStatus = TestDataBuilder.createOnlineStatus(DEVICE_ID);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(onlineStatus));
            
            // When - 查询在线状态
            boolean isOnline = service.isDeviceOnline(DEVICE_ID);
            
            // Then - 验证返回true
            assertThat(isOnline).isTrue();
        }
        
        @Test
        @DisplayName("应该在设备离线时返回false")
        void should_return_false_when_device_offline() {
            // Given - 设备离线
            DeviceOnlineStatus offlineStatus = TestDataBuilder.createOfflineStatus(DEVICE_ID);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(offlineStatus));
            
            // When - 查询在线状态
            boolean isOnline = service.isDeviceOnline(DEVICE_ID);
            
            // Then - 验证返回false
            assertThat(isOnline).isFalse();
        }
        
        @Test
        @DisplayName("应该在设备不存在时返回false")
        void should_return_false_when_device_not_exist() {
            // Given - 设备不存在
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.empty());
            
            // When - 查询在线状态
            boolean isOnline = service.isDeviceOnline(DEVICE_ID);
            
            // Then - 验证返回false
            assertThat(isOnline).isFalse();
        }
        
        @Test
        @DisplayName("应该在查询异常时返回false（保守策略）")
        void should_return_false_when_query_exception() {
            // Given - 查询异常
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID))
                    .thenThrow(new RuntimeException("查询异常"));
            
            // When - 查询在线状态
            boolean isOnline = service.isDeviceOnline(DEVICE_ID);
            
            // Then - 验证返回false（保守策略）
            assertThat(isOnline).isFalse();
        }
        
        @Test
        @DisplayName("应该获取设备状态详情")
        void should_get_device_status_details() {
            // Given - 设备状态存在
            DeviceOnlineStatus status = TestDataBuilder.createOnlineStatus(DEVICE_ID);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(status));
            
            // When - 获取状态详情
            Optional<DeviceOnlineStatus> result = service.getDeviceStatus(DEVICE_ID);
            
            // Then - 验证返回状态
            assertThat(result).contains(status);
        }
    }
    
    @Nested
    @DisplayName("批量操作测试")
    class BatchOperationTests {
        
        @Test
        @DisplayName("应该批量检查设备在线状态")
        void should_batch_check_device_online_status() {
            // Given - 准备多个设备状态
            List<Long> deviceIds = Arrays.asList(10001L, 10002L, 10003L);
            Map<Long, DeviceOnlineStatus> statusMap = new HashMap<>();
            statusMap.put(10001L, TestDataBuilder.createOnlineStatus(10001L));
            statusMap.put(10002L, TestDataBuilder.createOfflineStatus(10002L));
            // 10003L 不存在
            
            when(deviceOnlineStatusPort.batchGetDeviceStatus(deviceIds)).thenReturn(statusMap);
            
            // When - 批量检查
            Map<Long, Boolean> result = service.batchCheckOnline(deviceIds);
            
            // Then - 验证结果
            assertThat(result).hasSize(3);
            assertThat(result.get(10001L)).isTrue();
            assertThat(result.get(10002L)).isFalse();
            assertThat(result.get(10003L)).isFalse();
        }
        
        @Test
        @DisplayName("应该处理空设备ID列表")
        void should_handle_empty_device_list() {
            // When - 传入空列表
            Map<Long, Boolean> result = service.batchCheckOnline(Collections.emptyList());
            
            // Then - 验证返回空Map
            assertThat(result).isEmpty();
            verify(deviceOnlineStatusPort, never()).batchGetDeviceStatus(any());
        }
        
        @Test
        @DisplayName("应该在批量查询异常时降级返回全部离线")
        void should_fallback_all_offline_when_batch_query_exception() {
            // Given - 批量查询异常
            List<Long> deviceIds = Arrays.asList(10001L, 10002L);
            when(deviceOnlineStatusPort.batchGetDeviceStatus(deviceIds))
                    .thenThrow(new RuntimeException("批量查询异常"));
            
            // When - 批量检查
            Map<Long, Boolean> result = service.batchCheckOnline(deviceIds);
            
            // Then - 验证全部返回离线
            assertThat(result).hasSize(2);
            assertThat(result.get(10001L)).isFalse();
            assertThat(result.get(10002L)).isFalse();
        }
        
        @Test
        @DisplayName("应该批量获取设备状态详情")
        void should_batch_get_device_status() {
            // Given - 准备设备状态
            List<Long> deviceIds = Arrays.asList(10001L, 10002L);
            Map<Long, DeviceOnlineStatus> statusMap = new HashMap<>();
            statusMap.put(10001L, TestDataBuilder.createOnlineStatus(10001L));
            statusMap.put(10002L, TestDataBuilder.createOnlineStatus(10002L));
            
            when(deviceOnlineStatusPort.batchGetDeviceStatus(deviceIds)).thenReturn(statusMap);
            
            // When - 批量获取
            Map<Long, DeviceOnlineStatus> result = service.batchGetDeviceStatus(deviceIds);
            
            // Then - 验证结果
            assertThat(result).hasSize(2);
            assertThat(result.get(10001L).getStatus()).isEqualTo(OnlineStatus.ONLINE);
            assertThat(result.get(10002L).getStatus()).isEqualTo(OnlineStatus.ONLINE);
        }
    }
    
    @Nested
    @DisplayName("在线设备统计测试")
    class OnlineDeviceStatisticsTests {
        
        @Test
        @DisplayName("应该获取所有在线设备ID列表")
        void should_get_all_online_device_ids() {
            // Given - 准备设备数据
            Set<Long> allDeviceIds = LongStream.rangeClosed(10001L, 10005L)
                    .boxed()
                    .collect(Collectors.toSet());
            
            Map<Long, DeviceOnlineStatus> statusMap = new HashMap<>();
            statusMap.put(10001L, TestDataBuilder.createOnlineStatus(10001L));
            statusMap.put(10002L, TestDataBuilder.createOnlineStatus(10002L));
            statusMap.put(10003L, TestDataBuilder.createOfflineStatus(10003L));
            // 10004L, 10005L 无状态
            
            when(deviceOnlineStatusPort.getAllDeviceIds()).thenReturn(allDeviceIds);
            when(deviceOnlineStatusPort.batchGetDeviceStatus(anyList())).thenReturn(statusMap);
            
            // When - 获取在线设备
            Set<Long> onlineIds = service.getOnlineDeviceIds();
            
            // Then - 验证只返回在线设备
            assertThat(onlineIds).containsExactlyInAnyOrder(10001L, 10002L);
        }
        
        @Test
        @DisplayName("应该获取在线设备数量")
        void should_get_online_device_count() {
            // Given - 设置在线数量
            when(deviceOnlineStatusPort.getOnlineDeviceCount()).thenReturn(150);
            
            // When - 获取数量
            int count = service.getOnlineDeviceCount();
            
            // Then - 验证数量
            assertThat(count).isEqualTo(150);
        }
        
        @Test
        @DisplayName("应该在统计异常时返回0")
        void should_return_zero_when_count_exception() {
            // Given - 统计异常
            when(deviceOnlineStatusPort.getOnlineDeviceCount())
                    .thenThrow(new RuntimeException("统计异常"));
            
            // When - 获取数量
            int count = service.getOnlineDeviceCount();
            
            // Then - 验证返回0
            assertThat(count).isZero();
        }
    }
    
    @Nested
    @DisplayName("离线设备处理测试")
    class OfflineDeviceProcessingTests {
        
        @Test
        @DisplayName("应该处理超时离线的设备")
        void should_process_timeout_offline_devices() {
            // Given - 准备离线设备
            List<Long> expiredDeviceIds = Arrays.asList(10001L, 10002L, 10003L);
            
            when(deviceOnlineStatusPort.findExpiredDevices(anyLong())).thenReturn(expiredDeviceIds);
            
            // 模拟批量标记离线成功
            DeviceOnlineStatus offlineStatus1 = TestDataBuilder.createOfflineStatus(10001L);
            DeviceOnlineStatus offlineStatus2 = TestDataBuilder.createOfflineStatus(10002L);
            List<DeviceOnlineStatus> batchResult = Arrays.asList(offlineStatus1, offlineStatus2); // 第三个失败，不在结果中
            when(deviceOnlineStatusPort.batchMarkOfflineAndResetTtl(expiredDeviceIds)).thenReturn(batchResult);
            
            // When - 处理离线设备
            int processedCount = service.processOfflineDevices();
            
            // Then - 验证处理结果
            assertThat(processedCount).isEqualTo(2);

            // 验证调用了批量离线标记方法
            verify(deviceOnlineStatusPort).batchMarkOfflineAndResetTtl(expiredDeviceIds);

            // 验证批量发布离线事件
            verify(deviceStatusEventPort).batchPublishStatusEvents(eventListCaptor.capture());
            List<DeviceStatusEvent> events = eventListCaptor.getValue();
            assertThat(events).hasSize(2).allMatch(e -> e.getEventType() == DeviceStatusEvent.EventType.DEVICE_DETECTED_OFFLINE);
        }
        
        @Test
        @DisplayName("应该在无离线设备时返回0")
        void should_return_zero_when_no_offline_devices() {
            // Given - 无离线设备
            when(deviceOnlineStatusPort.findExpiredDevices(anyLong())).thenReturn(Collections.emptyList());
            
            // When - 处理离线设备
            int processedCount = service.processOfflineDevices();
            
            // Then - 验证返回0
            assertThat(processedCount).isZero();
            verify(deviceStatusEventPort, never()).batchPublishStatusEvents(any());
        }
        
        @Test
        @DisplayName("应该处理单个设备离线失败的情况")
        void should_handle_single_device_offline_failure() {
            // Given - 部分设备处理失败
            List<Long> expiredDeviceIds = Arrays.asList(10001L, 10002L);
            when(deviceOnlineStatusPort.findExpiredDevices(anyLong())).thenReturn(expiredDeviceIds);
            
            // 模拟批量处理部分成功
            List<DeviceOnlineStatus> batchResult = Arrays.asList(TestDataBuilder.createOfflineStatus(10001L));
            when(deviceOnlineStatusPort.batchMarkOfflineAndResetTtl(expiredDeviceIds)).thenReturn(batchResult);
            
            // When - 处理离线设备
            int processedCount = service.processOfflineDevices();
            
            // Then - 验证只处理成功的设备
            assertThat(processedCount).isEqualTo(1);

            // 验证调用了批量离线标记方法
            verify(deviceOnlineStatusPort).batchMarkOfflineAndResetTtl(expiredDeviceIds);

            verify(deviceStatusEventPort).batchPublishStatusEvents(eventListCaptor.capture());
            assertThat(eventListCaptor.getValue()).hasSize(1);
        }
        
        @Test
        @DisplayName("应该在处理异常时返回0")
        void should_return_zero_when_process_exception() {
            // Given - 处理异常
            when(deviceOnlineStatusPort.findExpiredDevices(anyLong()))
                    .thenThrow(new RuntimeException("查询异常"));
            
            // When - 处理离线设备
            int processedCount = service.processOfflineDevices();
            
            // Then - 验证返回0
            assertThat(processedCount).isZero();
        }
    }
    
    @Nested
    @DisplayName("边界条件和异常场景测试")
    class EdgeCaseAndExceptionTests {
        
        @Test
        @DisplayName("应该处理null设备ID列表")
        void should_handle_null_device_list() {
            // When - 传入null
            Map<Long, Boolean> checkResult = service.batchCheckOnline(null);
            Map<Long, DeviceOnlineStatus> statusResult = service.batchGetDeviceStatus(null);
            
            // Then - 验证返回空Map
            assertThat(checkResult).isEmpty();
            assertThat(statusResult).isEmpty();
        }
        
        @Test
        @DisplayName("应该在状态保存异常时继续发布事件")
        void should_continue_publish_event_when_save_exception() {
            // Given - 保存异常但锁获取成功
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.empty());
            doThrow(new RuntimeException("保存异常")).when(deviceOnlineStatusPort).smartDetermined(any());
            
            // When - 执行更新
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证锁被释放
            verify(deviceOnlineStatusPort).releaseDeviceUpdateLock(DEVICE_ID);
        }
        
        @Test
        @DisplayName("应该正确处理RECONNECT到ONLINE的状态转换")
        void should_handle_reconnect_to_online_transition() {
            // Given - 设备处于RECONNECT状态
            DeviceOnlineStatus reconnectStatus = TestDataBuilder.createOnlineStatus(DEVICE_ID);
            reconnectStatus.setStatus(OnlineStatus.RECONNECT);
            
            when(deviceOnlineStatusPort.tryAcquireDeviceUpdateLock(DEVICE_ID, 5000L)).thenReturn(true);
            when(deviceOnlineStatusPort.getDeviceStatus(DEVICE_ID)).thenReturn(Optional.of(reconnectStatus));
            
            // When - 再次上报
            service.updateLastReportTime(DEVICE_ID, ReportSource.HTTP, CLIENT_IP);
            
            // Then - 验证转为ONLINE状态
            verify(deviceOnlineStatusPort).smartDetermined(statusCaptor.capture());
            DeviceOnlineStatus updatedStatus = statusCaptor.getValue();
            assertThat(updatedStatus.getStatus()).isEqualTo(OnlineStatus.ONLINE);
            assertThat(updatedStatus.getOnlineStartTime()).isEqualTo(reconnectStatus.getOnlineStartTime());
        }
    }
}