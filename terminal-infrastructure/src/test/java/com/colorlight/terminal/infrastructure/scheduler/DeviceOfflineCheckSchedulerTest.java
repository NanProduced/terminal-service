package com.colorlight.terminal.infrastructure.scheduler;

import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.util.Pair;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.colorlight.terminal.application.domain.CommonConstant.Device.LAST_REPORT_TIME;
import static com.colorlight.terminal.application.domain.CommonConstant.Device.STATUS;
import static com.colorlight.terminal.application.domain.CommonConstant.Device.STATUS_CHANGE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * DeviceOfflineCheckScheduler 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceOfflineCheckScheduler 单测")
class DeviceOfflineCheckSchedulerTest {

    @Mock
    private DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;

    @Mock
    private DeviceConfigPort deviceConfigPort;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    private DeviceOfflineCheckScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);

        scheduler = new DeviceOfflineCheckScheduler(deviceOnlineStatusUseCase, deviceConfigPort, redisTemplate);
    }

    @Test
    @DisplayName("成功拿到维护锁时应执行离线检测流程")
    void should_execute_offline_check_when_lock_acquired() {
        when(valueOperations.setIfAbsent(eq(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY), eq("locked"), any(Duration.class)))
                .thenReturn(true);
        when(redisTemplate.delete(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY)).thenReturn(true);
        when(deviceOnlineStatusUseCase.processOfflineDevices()).thenReturn(2);

        scheduler.checkOfflineDevices();

        verify(deviceOnlineStatusUseCase, times(1)).processOfflineDevices();
        verify(redisTemplate, times(1)).delete(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY);
    }

    @Test
    @DisplayName("无法获取维护锁时应直接返回不执行业务")
    void should_abort_when_lock_cannot_be_acquired() {
        when(valueOperations.setIfAbsent(eq(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY), eq("locked"), any(Duration.class)))
                .thenReturn(false, false, false);

        scheduler.checkOfflineDevices();

        verify(deviceOnlineStatusUseCase, never()).processOfflineDevices();
        verify(redisTemplate, never()).delete(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY);
    }

    @Test
    @DisplayName("维护锁获取失败时只执行统计流程")
    void should_run_statistic_only_when_calibration_lock_missing() {
        when(valueOperations.setIfAbsent(eq(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY), eq("locked"), any(Duration.class)))
                .thenReturn(false);
        when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(15);

        scheduler.calibrationAndStatistic();

        verify(deviceOnlineStatusUseCase, times(1)).getOnlineDeviceCount();
        verify(redisTemplate, never()).delete(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY);
    }

    @Test
    @DisplayName("维护锁获取成功时应完成校准与统计")
    void should_perform_calibration_when_lock_acquired() {
        when(valueOperations.setIfAbsent(eq(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY), eq("locked"), any(Duration.class)))
                .thenReturn(true);
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(8);
        when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(60000L);

        Cursor<String> emptyCursor = Mockito.mock(Cursor.class);
        when(emptyCursor.hasNext()).thenReturn(false);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(emptyCursor);

        scheduler.calibrationAndStatistic();

        verify(deviceOnlineStatusUseCase, times(1)).getOnlineDeviceCount();
        verify(redisTemplate, times(1)).delete(RedisKeyConstant.DEVICE_STATUS_INDEX_KEY);
        verify(redisTemplate, times(1)).delete(RedisKeyConstant.DEVICE_CACHE_MAINTENANCE_LOCK_KEY);
        verify(valueOperations, times(1)).set(RedisKeyConstant.ONLINE_DEVICE_COUNT_KEY, 0);
        verifyNoMoreInteractions(setOperations);
    }

    @Test
    @DisplayName("扫描 Redis 状态数据时应区分在线与离线设备")
    void should_classify_devices_during_scan() throws Exception {
        long now = System.currentTimeMillis();
        long offlineThreshold = 1000L;
        when(deviceConfigPort.getOfflineTimeoutThreshold()).thenReturn(offlineThreshold);

        Map<String, Map<Object, Object>> statusStore = new HashMap<>();
        statusStore.put("device:status:1001", Map.of(
                LAST_REPORT_TIME, String.valueOf(now),
                STATUS, OnlineStatus.ONLINE.name()
        ));
        statusStore.put("device:status:1002", Map.of(
                LAST_REPORT_TIME, String.valueOf(now - 5000),
                STATUS, OnlineStatus.ONLINE.name()
        ));

        when(hashOperations.get(anyString(), eq(LAST_REPORT_TIME))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return statusStore.getOrDefault(key, Collections.emptyMap()).get(LAST_REPORT_TIME);
        });
        when(hashOperations.get(anyString(), eq(STATUS))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return statusStore.getOrDefault(key, Collections.emptyMap()).get(STATUS);
        });

        Cursor<String> cursor = Mockito.mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, true, false);
        when(cursor.next()).thenReturn("device:status:1001", "device:status:1002", "device:status:index");
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        Method method = DeviceOfflineCheckScheduler.class.getDeclaredMethod("scanAndClassifyDeviceStatus");
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Pair<Set<Long>, Set<Long>> result = (Pair<Set<Long>, Set<Long>>) method.invoke(scheduler);

        assertThat(result.getFirst()).containsExactly(1001L);
        assertThat(result.getSecond()).containsExactly(1002L);
    }

    @Test
    @DisplayName("校准流程应正确将设备标记为离线并刷新 TTL")
    void should_mark_device_offline_during_calibration() throws Exception {
        String statusKey = String.format(RedisKeyConstant.DEVICE_STATUS_KEY, 2001L);
        Map<Object, Object> statusMap = new HashMap<>();
        statusMap.put(STATUS, OnlineStatus.ONLINE.name());
        statusMap.put(LAST_REPORT_TIME, String.valueOf(System.currentTimeMillis() - 10_000));

        when(hashOperations.entries(statusKey)).thenReturn(statusMap);
        when(deviceConfigPort.getReconnectTtl()).thenReturn(300L);
        when(redisTemplate.execute(any(SessionCallback.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            SessionCallback<Object> callback = invocation.getArgument(0);
            RedisOperations<String, Object> operations = Mockito.mock(RedisOperations.class);
            HashOperations<String, Object, Object> innerHashOps = Mockito.mock(HashOperations.class);

            when(operations.opsForHash()).thenReturn(innerHashOps);
            doAnswer(inv -> {
                String key = inv.getArgument(0);
                Object field = inv.getArgument(1);
                Object value = inv.getArgument(2);
                if (statusKey.equals(key)) {
                    statusMap.put(field, value);
                }
                return null;
            }).when(innerHashOps).put(anyString(), any(), any());

            when(operations.expire(eq(statusKey), any(Duration.class))).thenReturn(true);
            when(operations.exec()).thenReturn(Collections.emptyList());

            callback.execute(operations);
            return null;
        });

        Method method = DeviceOfflineCheckScheduler.class.getDeclaredMethod("markDeviceOfflineInCalibration", Long.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(scheduler, 2001L);

        assertThat(result).isTrue();
        assertThat(statusMap.get(STATUS)).isEqualTo(OnlineStatus.OFFLINE.name());
        assertThat(statusMap).containsKey(STATUS_CHANGE_TIME);
    }
}
