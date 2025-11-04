package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import com.colorlight.terminal.infrastructure.testutil.InfrastructureTestDataFactory;
import com.colorlight.terminal.infrastructure.testutil.RedisTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;

import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceOnlineStatusRedisService 单元测试
 * 测试Redis设备在线状态管理的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备在线状态Redis服务测试")
class DeviceOnlineStatusRedisServiceTest {

    @Mock
    private DeviceConfigPort deviceConfigPort;

    private RedisTemplate<String, Object> mockRedisTemplate;
    private DeviceOnlineStatusRedisService redisService;

    @BeforeEach
    void setUp() {
        // 创建Mock RedisTemplate
        mockRedisTemplate = RedisTestUtils.createMockRedisTemplate();

        // 配置设备配置端口的默认行为 - 使用lenient避免UnnecessaryStubbingException
        lenient().when(deviceConfigPort.getRedisStatusTtl()).thenReturn(3600L); // 1小时
        lenient().when(deviceConfigPort.getReconnectTtl()).thenReturn(120L); // 2分钟
        lenient().when(deviceConfigPort.isStreamQueryEnabled()).thenReturn(false); // 默认关闭流式查询
        lenient().when(deviceConfigPort.getStreamQueryPageSize()).thenReturn(100);
        lenient().when(deviceConfigPort.getStreamQueryMaxIterations()).thenReturn(1000);
        lenient().when(deviceConfigPort.getStreamQueryTimeoutMs()).thenReturn(30000L);

        // 创建测试对象
        redisService = spy(new DeviceOnlineStatusRedisService(mockRedisTemplate, deviceConfigPort));
    }

    @Nested
    @DisplayName("智能状态判断测试")
    class SmartDeterminedTests {

        @Test
        @DisplayName("应该保存GO_LIVE状态的设备")
        @SuppressWarnings("unchecked")
        void should_save_device_when_status_is_go_live() {
            // Given - 准备上线状态的设备
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1001L, OnlineStatus.GO_LIVE);
            
            // Mock Redis事务结果
            List<Object> mockResults = Arrays.asList("OK", true, 1L, 1L);
            when(mockRedisTemplate.execute(any(SessionCallback.class))).thenReturn(mockResults);

            // When - 执行智能判断
            redisService.smartDetermined(status);

            // Then - 验证保存操作被调用
            verify(mockRedisTemplate).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该保存RECONNECT状态的设备")
        @SuppressWarnings("unchecked")
        void should_save_device_when_status_is_reconnect() {
            // Given - 准备重连状态的设备
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1002L, OnlineStatus.RECONNECT);
            
            // Mock Redis事务结果
            List<Object> mockResults = Arrays.asList("OK", true, 1L, 1L);
            when(mockRedisTemplate.execute(any(SessionCallback.class))).thenReturn(mockResults);

            // When - 执行智能判断
            redisService.smartDetermined(status);

            // Then - 验证保存操作被调用
            verify(mockRedisTemplate).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该更新ONLINE状态的设备")
        @SuppressWarnings("unchecked")
        void should_update_device_when_status_is_online() {
            // Given - 准备在线状态的设备
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1003L, OnlineStatus.ONLINE);

            // When - 执行智能判断
            redisService.smartDetermined(status);

            // Then - 验证Pipeline更新操作被调用
            verify(mockRedisTemplate).executePipelined(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该忽略OFFLINE状态的设备")
        @SuppressWarnings("unchecked")
        void should_ignore_device_when_status_is_offline() {
            // Given - 准备离线状态的设备
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1004L, OnlineStatus.OFFLINE);

            // When - 执行智能判断
            redisService.smartDetermined(status);

            // Then - 验证没有Redis操作被调用
            verify(mockRedisTemplate, never()).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该处理状态为null的设备")
        @SuppressWarnings("unchecked")
        void should_update_device_when_status_is_null() {
            // Given - 准备状态为null的设备
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1005L, OnlineStatus.ONLINE);
            status.setStatus(null);

            // When - 执行智能判断
            redisService.smartDetermined(status);

            // Then - 验证Pipeline更新操作被调用
            verify(mockRedisTemplate).executePipelined(any(SessionCallback.class));
        }
    }

    @Nested
    @DisplayName("设备状态保存测试")
    class SaveDeviceStatusTests {

        @Test
        @DisplayName("应该成功保存完整的设备状态")
        @SuppressWarnings("unchecked")
        void should_save_complete_device_status_successfully() {
            // Given - 准备完整的设备状态
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1001L, OnlineStatus.GO_LIVE);
            
            // Mock Redis事务结果
            List<Object> mockResults = Arrays.asList("OK", true, 1L, 1L);
            when(mockRedisTemplate.execute(any(SessionCallback.class))).thenReturn(mockResults);

            // When - 保存设备状态
            assertThatCode(() -> redisService.saveDeviceStatus(status))
                .doesNotThrowAnyException();

            // Then - 验证Redis事务操作被调用
            verify(mockRedisTemplate).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该在保存失败时抛出异常")
        @SuppressWarnings("unchecked")
        void should_throw_exception_when_save_fails() {
            // Given - 准备设备状态和模拟异常
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1002L, OnlineStatus.GO_LIVE);
            when(mockRedisTemplate.execute(any(SessionCallback.class))).thenThrow(new RuntimeException("Redis connection failed"));

            // When & Then - 验证异常被抛出
            assertThatThrownBy(() -> redisService.saveDeviceStatus(status))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Redis connection failed");
        }
    }

        @Test
        @DisplayName("保存状态时 SessionCallback 应执行 Redis 多指令")
        @SuppressWarnings({"unchecked", "rawtypes"})
        void should_execute_session_callback_when_saving_status() {
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1006L, OnlineStatus.GO_LIVE);

            when(mockRedisTemplate.execute(any(SessionCallback.class))).thenAnswer(invocation -> {
                SessionCallback callback = invocation.getArgument(0);
                RedisOperations<String, Object> operations = mock(RedisOperations.class);
                HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
                SetOperations<String, Object> setOps = mock(SetOperations.class);
                ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
                when(operations.opsForHash()).thenReturn(hashOps);
                when(operations.opsForSet()).thenReturn(setOps);
                when(operations.opsForValue()).thenReturn(valueOps);
                when(operations.exec()).thenReturn(List.of("OK", true, 1L, 1L));
                callback.execute(operations);
                verify(operations).multi();
                verify(hashOps).putAll(argThat(key -> key.startsWith("device:status:")), anyMap());
                verify(operations).expire(argThat(key -> key.startsWith("device:status:")), any());
                verify(setOps).add(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY, status.getDeviceId());
                verify(valueOps).increment(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY);
                return List.of("OK", true, 1L, 1L);
            });

            redisService.saveDeviceStatus(status);
        }

    @Nested
    @DisplayName("设备状态更新测试")
    class UpdateDeviceStatusTests {

        @Test
        @DisplayName("应该成功更新设备状态")
        @SuppressWarnings("unchecked")
        void should_update_device_status_successfully() {
            // Given - 准备更新的设备状态
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1001L, OnlineStatus.ONLINE);

            // When - 更新设备状态
            assertThatCode(() -> redisService.updateDeviceStatus(status))
                .doesNotThrowAnyException();

            // Then - 验证Redis Pipeline操作被调用
            verify(mockRedisTemplate).executePipelined(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该在更新失败时不抛出异常")
        @SuppressWarnings("unchecked")
        void should_throw_exception_when_update_fails() {
            // Given - 准备设备状态和模拟异常
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1002L, OnlineStatus.ONLINE);
            lenient().when(mockRedisTemplate.executePipelined(any(SessionCallback.class))).thenThrow(new RuntimeException("Redis update failed"));

            // When & Then - 验证不会抛出异常（方法捕获异常但不重新抛出）
            assertThatCode(() -> redisService.updateDeviceStatus(status))
                .doesNotThrowAnyException();
        }
    }

        @Test
        @DisplayName("更新状态时 Pipeline SessionCallback 应刷新 TTL")
        @SuppressWarnings({"unchecked", "rawtypes"})
        void should_execute_session_callback_when_updating_status() {
            DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(1007L, OnlineStatus.ONLINE);

            lenient().when(mockRedisTemplate.executePipelined(any(SessionCallback.class))).thenAnswer(invocation -> {
                SessionCallback callback = invocation.getArgument(0);
                RedisOperations<String, Object> operations = mock(RedisOperations.class);
                HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
                when(operations.opsForHash()).thenReturn(hashOps);
                callback.execute(operations);
                // Pipeline 不使用 multi()/exec()，直接执行
                verify(hashOps).putAll(argThat(key -> key.startsWith("device:status:")), anyMap());
                verify(operations).expire(argThat(key -> key.startsWith("device:status:")), any());
                return List.of("OK");
            });

            redisService.updateDeviceStatus(status);
        }

    @Nested
    @DisplayName("设备状态获取测试")
    class GetDeviceStatusTests {

        @Test
        @DisplayName("应该成功获取存在的设备状态")
        void should_get_existing_device_status_successfully() {
            // Given - 准备设备状态和Redis数据
            Long deviceId = 1001L;
            DeviceOnlineStatus expectedStatus = InfrastructureTestDataFactory.createDeviceOnlineStatus(deviceId, OnlineStatus.ONLINE);
            Map<Object, Object> redisData = InfrastructureTestDataFactory.createRedisHashObjectData(expectedStatus);
            
            when(mockRedisTemplate.opsForHash().entries(anyString())).thenReturn(redisData);

            // When - 获取设备状态
            Optional<DeviceOnlineStatus> result = redisService.getDeviceStatus(deviceId);

            // Then - 验证结果
            assertThat(result).isPresent();
            DeviceOnlineStatus actualStatus = result.get();
            assertThat(actualStatus.getDeviceId()).isEqualTo(deviceId);
            assertThat(actualStatus.getStatus()).isEqualTo(OnlineStatus.ONLINE);
        }

        @Test
        @DisplayName("应该返回空值当设备不存在时")
        void should_return_empty_when_device_not_exists() {
            // Given - 设备不存在
            Long deviceId = 1002L;
            when(mockRedisTemplate.opsForHash().entries(anyString())).thenReturn(Collections.emptyMap());

            // When - 获取设备状态
            Optional<DeviceOnlineStatus> result = redisService.getDeviceStatus(deviceId);

            // Then - 验证返回空值
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该在获取异常时返回空值")
        void should_return_empty_when_get_throws_exception() {
            // Given - 模拟Redis异常
            Long deviceId = 1003L;
            when(mockRedisTemplate.opsForHash().entries(anyString())).thenThrow(new RuntimeException("Redis error"));

            // When - 获取设备状态
            Optional<DeviceOnlineStatus> result = redisService.getDeviceStatus(deviceId);

            // Then - 验证返回空值
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("批量设备状态获取测试")
    class BatchGetDeviceStatusTests {

        @Test
        @DisplayName("应该成功批量获取设备状态")
        @SuppressWarnings("unchecked")
        void should_batch_get_device_status_successfully() {
            // Given - 准备设备ID列表
            List<Long> deviceIds = Arrays.asList(1001L, 1002L, 1003L);
            
            // Mock Pipeline返回结果
            List<Map<Object, Object>> pipelineResults = new ArrayList<>();
            for (Long deviceId : deviceIds) {
                DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(deviceId, OnlineStatus.ONLINE);
                pipelineResults.add(InfrastructureTestDataFactory.createRedisHashObjectData(status));
            }
            
            when(mockRedisTemplate.executePipelined(any(SessionCallback.class))).thenReturn((List<Object>) (List<?>) pipelineResults);

            // When - 批量获取设备状态
            Map<Long, DeviceOnlineStatus> result = redisService.batchGetDeviceStatus(deviceIds);

            // Then - 验证结果
            assertThat(result).hasSize(3).containsKeys(1001L, 1002L, 1003L);
            
            result.values().forEach(status -> {
                assertThat(status.getStatus()).isEqualTo(OnlineStatus.ONLINE);
                assertThat(status.getDeviceId()).isIn(deviceIds);
            });
        }

        @Test
        @DisplayName("应该处理空的设备ID列表")
        void should_handle_empty_device_id_list() {
            // Given - 空的设备ID列表
            List<Long> emptyList = Collections.emptyList();

            // When - 批量获取设备状态
            Map<Long, DeviceOnlineStatus> result = redisService.batchGetDeviceStatus(emptyList);

            // Then - 验证返回空Map
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该处理null的设备ID列表")
        void should_handle_null_device_id_list() {
            // When - 批量获取设备状态
            Map<Long, DeviceOnlineStatus> result = redisService.batchGetDeviceStatus(null);

            // Then - 验证返回空Map
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该在批量获取异常时返回空Map")
        void should_return_empty_map_when_batch_get_throws_exception() {
            // Given - 准备设备ID列表和模拟异常
            List<Long> deviceIds = Arrays.asList(1001L, 1002L);
            when(mockRedisTemplate.executePipelined(any(SessionCallback.class))).thenThrow(new RuntimeException("Pipeline failed"));

            // When - 批量获取设备状态
            Map<Long, DeviceOnlineStatus> result = redisService.batchGetDeviceStatus(deviceIds);

            // Then - 验证返回空Map
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("设备列表获取测试")
    class GetAllDeviceIdsTests {

        @Test
        @DisplayName("应该使用传统方式获取所有设备ID")
        void should_get_all_device_ids_with_traditional_method() {
            // Given - 配置使用传统查询方式
            when(deviceConfigPort.isStreamQueryEnabled()).thenReturn(false);
            
            Set<Object> deviceIdObjs = Set.of(1001L, 1002L, 1003L);
            when(mockRedisTemplate.opsForSet().members(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY))
                .thenReturn(deviceIdObjs);

            // When - 获取所有设备ID
            Set<Long> result = redisService.getAllDeviceIds();

            // Then - 验证结果
            assertThat(result).hasSize(3).containsExactlyInAnyOrder(1001L, 1002L, 1003L);
        }

        @Test
        @DisplayName("应该处理获取设备ID异常")
        void should_handle_exception_when_getting_device_ids() {
            // Given - 模拟异常
            when(deviceConfigPort.isStreamQueryEnabled()).thenReturn(false);
            when(mockRedisTemplate.opsForSet().members(anyString()))
                .thenThrow(new RuntimeException("Redis error"));

            // When - 获取所有设备ID
            Set<Long> result = redisService.getAllDeviceIds();

            // Then - 验证返回空集合
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该使用流式方式获取设备ID")
        void should_get_device_ids_with_stream_method() {
            // Given - 配置使用流式查询
            when(deviceConfigPort.isStreamQueryEnabled()).thenReturn(true);
            when(deviceConfigPort.getStreamQueryPageSize()).thenReturn(100);
            when(deviceConfigPort.getStreamQueryMaxIterations()).thenReturn(1000);
            when(deviceConfigPort.getStreamQueryTimeoutMs()).thenReturn(30000L);

            // Mock Cursor行为
            List<Object> deviceData = Arrays.asList(1001L, 1002L, 1003L);
            Cursor<Object> mockCursor = RedisTestUtils.createMockCursor(deviceData);
            
            @SuppressWarnings("unchecked")
            SetOperations<String, Object> mockSetOps = mock(SetOperations.class);
            when(mockRedisTemplate.opsForSet()).thenReturn(mockSetOps);
            when(mockSetOps.scan(anyString(), any())).thenReturn(mockCursor);

            // When - 获取所有设备ID
            Set<Long> result = redisService.getAllDeviceIds();

            // Then - 验证结果
            assertThat(result).hasSize(3).containsExactlyInAnyOrder(1001L, 1002L, 1003L);
        }
    }

    @Nested
    @DisplayName("分布式锁测试")
    class DistributedLockTests {

        @Test
        @DisplayName("应该成功获取分布式锁")
        void should_acquire_distributed_lock_successfully() {
            // Given - 准备设备ID和超时时间
            Long deviceId = 1001L;
            Long timeoutMs = 5000L;
            
            when(mockRedisTemplate.opsForValue().setIfAbsent(anyString(), eq("locked"), any(Duration.class)))
                .thenReturn(true);

            // When - 尝试获取锁
            Boolean result = redisService.tryAcquireDeviceUpdateLock(deviceId, timeoutMs);

            // Then - 验证锁获取成功
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("应该获取分布式锁失败当锁已存在")
        void should_fail_to_acquire_lock_when_already_exists() {
            // Given - 准备设备ID和超时时间，锁已存在
            Long deviceId = 1002L;
            Long timeoutMs = 5000L;
            
            when(mockRedisTemplate.opsForValue().setIfAbsent(anyString(), eq("locked"), any(Duration.class)))
                .thenReturn(false);

            // When - 尝试获取锁
            Boolean result = redisService.tryAcquireDeviceUpdateLock(deviceId, timeoutMs);

            // Then - 验证锁获取失败
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该在锁操作异常时返回false")
        void should_return_false_when_lock_operation_throws_exception() {
            // Given - 准备设备ID和模拟异常
            Long deviceId = 1003L;
            Long timeoutMs = 5000L;

            when(mockRedisTemplate.opsForValue().setIfAbsent(anyString(), eq("locked"), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis lock error"));

            // When - 尝试获取锁
            Boolean result = redisService.tryAcquireDeviceUpdateLock(deviceId, timeoutMs);

            // Then - 验证返回false
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该在setIfAbsent返回null时返回false")
        void should_return_false_when_setIfAbsent_returns_null() {
            // Given - 准备设备ID，setIfAbsent返回null
            Long deviceId = 1004L;
            Long timeoutMs = 5000L;

            when(mockRedisTemplate.opsForValue().setIfAbsent(anyString(), eq("locked"), any(Duration.class)))
                .thenReturn(null);

            // When - 尝试获取锁
            Boolean result = redisService.tryAcquireDeviceUpdateLock(deviceId, timeoutMs);

            // Then - 验证返回false
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该成功释放分布式锁")
        void should_release_distributed_lock_successfully() {
            // Given - 准备设备ID
            Long deviceId = 1001L;
            when(mockRedisTemplate.delete(anyString())).thenReturn(true);

            // When & Then - 验证不抛出异常
            assertThatCode(() -> redisService.releaseDeviceUpdateLock(deviceId))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("释放锁异常时不应该抛出异常")
        void should_not_throw_exception_when_release_lock_fails() {
            // Given - 准备设备ID和模拟异常
            Long deviceId = 1002L;
            when(mockRedisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis delete error"));

            // When & Then - 验证不抛出异常
            assertThatCode(() -> redisService.releaseDeviceUpdateLock(deviceId))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("在线设备计数测试")
    class OnlineDeviceCountTests {

        @Test
        @DisplayName("应该获取正确的在线设备数量")
        void should_get_correct_online_device_count() {
            // Given - Mock在线设备数量
            Integer expectedCount = 150;
            when(mockRedisTemplate.opsForValue().get(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY))
                .thenReturn(expectedCount);

            // When - 获取在线设备数量
            int result = redisService.getOnlineDeviceCount();

            // Then - 验证结果
            assertThat(result).isEqualTo(expectedCount);
        }

        @Test
        @DisplayName("应该在数量为null时返回0")
        void should_return_zero_when_count_is_null() {
            // Given - Mock返回null
            when(mockRedisTemplate.opsForValue().get(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY))
                .thenReturn(null);

            // When - 获取在线设备数量
            int result = redisService.getOnlineDeviceCount();

            // Then - 验证返回0
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("应该在获取异常时返回0")
        void should_return_zero_when_get_count_throws_exception() {
            // Given - Mock异常
            when(mockRedisTemplate.opsForValue().get(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY))
                .thenThrow(new RuntimeException("Redis error"));

            // When - 获取在线设备数量
            int result = redisService.getOnlineDeviceCount();

            // Then - 验证返回0
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("应该成功设置在线设备数量")
        void should_set_online_device_count_successfully() {
            // Given - 准备设置的数量
            int count = 200;

            // When & Then - 验证不抛出异常
            assertThatCode(() -> redisService.setOnlineDeviceCount(count))
                .doesNotThrowAnyException();
            
            verify(mockRedisTemplate.opsForValue()).set(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY, count);
        }
    }

    @Nested
    @DisplayName("批量离线标记测试")
    class BatchMarkOfflineAndResetTtlTests {

        @Test
        @DisplayName("应该批量标记设备离线成功")
        void should_batch_mark_devices_offline_successfully() {
            // Given - 准备在线设备数据
            List<Long> deviceIds = Arrays.asList(1001L, 1002L, 1003L);

            Map<Long, DeviceOnlineStatus> currentStatuses = new HashMap<>();
            for (Long deviceId : deviceIds) {
                DeviceOnlineStatus status = InfrastructureTestDataFactory.createDeviceOnlineStatus(deviceId, OnlineStatus.ONLINE);
                currentStatuses.put(deviceId, status);
            }

            // Mock批量获取方法
            doReturn(currentStatuses).when(redisService).batchGetDeviceStatus(deviceIds);
            
            // Mock Pipeline执行结果
            List<Object> mockPipelineResults = new ArrayList<>();
            // 为每个设备添加4个操作结果
            for (int i = 0; i < 3; i++) { // 3个设备
                mockPipelineResults.add("OK");      // HSET STATUS
                mockPipelineResults.add(System.currentTimeMillis()); // HSET STATUS_CHANGE_TIME
                mockPipelineResults.add(true);      // EXPIRE
                mockPipelineResults.add(1L);        // SREM
            }
            mockPipelineResults.add(1L); // DECR
            
            when(mockRedisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(mockPipelineResults);

            // When - 批量标记离线
            List<DeviceOnlineStatus> result = redisService.batchMarkOfflineAndResetTtl(deviceIds);

            // Then - 验证结果

            // 验证所有返回的状态都已标记为离线
            assertThat(result).hasSize(3).allMatch(status -> status.getStatus() == OnlineStatus.OFFLINE);

            // 验证调用了Pipeline执行
            verify(mockRedisTemplate).executePipelined(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该过滤掉无效设备并只处理有效设备")
        void should_filter_invalid_devices_and_process_valid_only() {
            // Given - 准备混合状态的设备数据
            List<Long> deviceIds = Arrays.asList(10001L, 10002L, 10003L, 10004L);

            Map<Long, DeviceOnlineStatus> currentStatuses = new HashMap<>();
            // 10001: 在线设备 - 有效
            currentStatuses.put(10001L, InfrastructureTestDataFactory.createDeviceOnlineStatus(10001L, OnlineStatus.ONLINE));
            // 10002: 已离线设备 - 无效
            currentStatuses.put(10002L, InfrastructureTestDataFactory.createDeviceOnlineStatus(10002L, OnlineStatus.OFFLINE));
            // 10003: 设备不存在 - 无效
            currentStatuses.put(10003L, null);
            // 10004: 在线设备 - 有效
            currentStatuses.put(10004L, InfrastructureTestDataFactory.createDeviceOnlineStatus(10004L, OnlineStatus.ONLINE));

            doReturn(currentStatuses).when(redisService).batchGetDeviceStatus(deviceIds);
            
            // Mock Pipeline执行结果
            List<Object> mockPipelineResults = new ArrayList<>();
            // 为每个有效设备添加4个操作结果
            for (int i = 0; i < 2; i++) { // 假设有2个有效设备
                mockPipelineResults.add("OK");      // HSET STATUS
                mockPipelineResults.add(System.currentTimeMillis()); // HSET STATUS_CHANGE_TIME
                mockPipelineResults.add(true);      // EXPIRE
                mockPipelineResults.add(1L);        // SREM
            }
            mockPipelineResults.add(1L); // DECR
            
            when(mockRedisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(mockPipelineResults);

            // When - 批量标记离线
            List<DeviceOnlineStatus> result = redisService.batchMarkOfflineAndResetTtl(deviceIds);

            // Then - 只有有效的在线设备被处理
            assertThat(result).hasSize(2);
            assertThat(result).extracting(DeviceOnlineStatus::getDeviceId)
                    .containsExactlyInAnyOrder(10001L, 10004L);

            // 验证调用了Pipeline执行
            verify(mockRedisTemplate).executePipelined(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该处理空设备列表")
        void should_handle_empty_device_list() {
            // Given - 空设备列表
            List<Long> emptyDeviceIds = Collections.emptyList();

            // When - 批量标记离线
            List<DeviceOnlineStatus> result = redisService.batchMarkOfflineAndResetTtl(emptyDeviceIds);

            // Then - 返回空列表
            assertThat(result).isEmpty();

            // 验证没有调用Redis操作
            verify(mockRedisTemplate, never()).executePipelined(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该处理null设备列表")
        void should_handle_null_device_list() {
            // When - null设备列表
            List<DeviceOnlineStatus> result = redisService.batchMarkOfflineAndResetTtl(null);

            // Then - 返回空列表
            assertThat(result).isEmpty();

            // 验证没有调用Redis操作
            verify(mockRedisTemplate, never()).executePipelined(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该在批量获取状态失败时返回空列表")
        void should_return_empty_list_when_batch_get_fails() {
            // Given - 批量获取失败
            List<Long> deviceIds = Arrays.asList(10001L, 10002L);
            doThrow(new RuntimeException("Redis连接失败")).when(redisService).batchGetDeviceStatus(deviceIds);

            // When - 批量标记离线
            List<DeviceOnlineStatus> result = redisService.batchMarkOfflineAndResetTtl(deviceIds);

            // Then - 返回空列表
            assertThat(result).isEmpty();
        }

    }
}
