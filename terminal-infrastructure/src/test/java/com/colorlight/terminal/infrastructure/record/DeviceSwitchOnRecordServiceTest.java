package com.colorlight.terminal.infrastructure.record;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.outbound.repository.TerminalSwitchOnRecordRepository;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DeviceSwitchOnRecordService 单元测试
 * <p>
 * 业务逻辑总结：
 * DeviceSwitchOnRecordService是Application层DeviceSwitchRecordPort接口的Infrastructure层实现，
 * 负责处理设备开机时间记录，包括开机时间计算、防重复记录和Redis缓存功能。
 * <p>
 * 核心功能：
 * 1. asyncHandlerSwitchOnRecord - 异步处理设备开机记录（主入口）
 * 2. getLatestSwitchOnTime - 获取缓存中设备最近一次开机时间戳
 * 3. saveLatestSwitchOnTime - 缓存设备最近一次开机时间戳
 * <p>
 * 业务逻辑：
 * - 通过设备上报的运行时间(up)计算开机时间戳：当前时间 - 运行时间
 * - 防重复记录：与最近一次开机时间相差2分钟内不记录
 * - 异步处理：使用@Async注解进行异步处理
 * - Redis缓存：缓存最近一次开机时间（10分钟TTL）
 * <p>
 * 依赖：TerminalSwitchOnRecordRepository、RedisTemplate
 * 业务场景：设备上报状态时计算和记录开机时间
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceSwitchOnRecordService - 设备开关机记录服务测试")
class DeviceSwitchOnRecordServiceTest {

    @Mock
    private TerminalSwitchOnRecordRepository terminalSwitchOnRecordRepository;
    
    @Mock
    private RedisTemplate<String, String> stringRedisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;

    private DeviceSwitchOnRecordService deviceSwitchOnRecordService;

    @BeforeEach
    void setUp() {
        deviceSwitchOnRecordService = new DeviceSwitchOnRecordService(
            terminalSwitchOnRecordRepository, 
            stringRedisTemplate
        );
        
        // 使用lenient()避免严格模式报错，某些测试可能不会调用所有mock方法
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Nested
    @DisplayName("asyncHandlerSwitchOnRecord - 异步处理设备开机记录")
    class AsyncHandlerSwitchOnRecordTests {

        @Test
        @DisplayName("应该成功处理有效的开机记录")
        void should_handle_valid_switch_on_record_successfully() {
            // Given - 准备有效的开机记录数据
            Long deviceId = 12345L;
            TerminalStatusReport.InfoWrapper infoWrapper = createInfoWrapper(300000L); // 5分钟运行时间
            
            // 设置当前时间（模拟）和缓存中的最近开机时间
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            long expectedSwitchOnTime = currentTimeSeconds - 300; // 5分钟前开机
            long lastSwitchOnTime = expectedSwitchOnTime - 3600; // 1小时前的上次开机时间（超过2分钟间隔）
            
            when(valueOperations.get(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId)))
                .thenReturn(String.valueOf(lastSwitchOnTime));

            // When - 执行业务方法
            deviceSwitchOnRecordService.asyncHandlerSwitchOnRecord(deviceId, infoWrapper);

            // Then - 验证开机记录被保存和缓存
            verify(terminalSwitchOnRecordRepository).saveSwitchOnRecord(eq(deviceId), anyLong());
            verify(valueOperations).set(
                eq(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId)),
                anyString(),
                eq(600L),
                eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("应该跳过重复的开机记录（2分钟内）")
        void should_skip_duplicate_switch_on_record_within_2_minutes() {
            // Given - 准备重复的开机记录数据
            Long deviceId = 12345L;
            TerminalStatusReport.InfoWrapper infoWrapper = createInfoWrapper(60000L); // 1分钟运行时间
            
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            long expectedSwitchOnTime = currentTimeSeconds - 60; // 1分钟前开机
            long lastSwitchOnTime = expectedSwitchOnTime - 60; // 只相差1分钟（小于2分钟间隔）
            
            when(valueOperations.get(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId)))
                .thenReturn(String.valueOf(lastSwitchOnTime));

            // When - 执行业务方法
            deviceSwitchOnRecordService.asyncHandlerSwitchOnRecord(deviceId, infoWrapper);

            // Then - 验证跳过保存（不调用repository和缓存）
            verify(terminalSwitchOnRecordRepository, never()).saveSwitchOnRecord(anyLong(), anyLong());
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("应该处理InfoWrapper为null的情况")
        void should_handle_null_info_wrapper() {
            // Given - InfoWrapper为null
            Long deviceId = 12345L;

            // When - 执行业务方法
            deviceSwitchOnRecordService.asyncHandlerSwitchOnRecord(deviceId, null);

            // Then - 验证跳过处理（switchOnUtc为0）
            verify(terminalSwitchOnRecordRepository, never()).saveSwitchOnRecord(anyLong(), anyLong());
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("应该处理Info为null的情况")
        void should_handle_null_info() {
            // Given - Info为null的InfoWrapper
            Long deviceId = 12345L;
            TerminalStatusReport.InfoWrapper infoWrapper = TerminalStatusReport.InfoWrapper.builder()
                .info(null)
                .reportTime(System.currentTimeMillis())
                .build();

            // When - 执行业务方法
            deviceSwitchOnRecordService.asyncHandlerSwitchOnRecord(deviceId, infoWrapper);

            // Then - 验证跳过处理
            verify(terminalSwitchOnRecordRepository, never()).saveSwitchOnRecord(anyLong(), anyLong());
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("应该处理运行时间为0的情况")
        void should_handle_zero_uptime() {
            // Given - 运行时间为0
            Long deviceId = 12345L;
            TerminalStatusReport.InfoWrapper infoWrapper = createInfoWrapper(0L);

            // When - 执行业务方法
            deviceSwitchOnRecordService.asyncHandlerSwitchOnRecord(deviceId, infoWrapper);

            // Then - 验证跳过处理（switchOnUtc为当前时间，不为0但可能触发其他逻辑）
            // 这种情况下switchOnUtc = currentTime - 0 = currentTime，应该会被处理
            verify(terminalSwitchOnRecordRepository).saveSwitchOnRecord(eq(deviceId), anyLong());
        }

        @Test
        @DisplayName("应该在首次开机时正确处理（缓存为空）")
        void should_handle_first_switch_on_correctly() {
            // Given - 首次开机，缓存为空
            Long deviceId = 12345L;
            TerminalStatusReport.InfoWrapper infoWrapper = createInfoWrapper(600000L); // 10分钟运行时间
            
            when(valueOperations.get(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId)))
                .thenReturn(null); // 缓存为空

            // When - 执行业务方法
            deviceSwitchOnRecordService.asyncHandlerSwitchOnRecord(deviceId, infoWrapper);

            // Then - 验证首次开机被正确处理
            verify(terminalSwitchOnRecordRepository).saveSwitchOnRecord(eq(deviceId), anyLong());
            verify(valueOperations).set(
                eq(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId)),
                anyString(),
                eq(600L),
                eq(TimeUnit.SECONDS)
            );
        }
    }

    @Nested
    @DisplayName("getLatestSwitchOnTime - 获取最近开机时间")
    class GetLatestSwitchOnTimeTests {

        @Test
        @DisplayName("应该从缓存中获取最近开机时间")
        void should_get_latest_switch_on_time_from_cache() {
            // Given - 准备缓存中的开机时间
            Long deviceId = 12345L;
            Long expectedTimestamp = 1640995200L; // 2022-01-01 00:00:00 UTC
            String redisKey = String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId);
            
            when(valueOperations.get(redisKey)).thenReturn(String.valueOf(expectedTimestamp));

            // When - 执行获取方法
            Long result = deviceSwitchOnRecordService.getLatestSwitchOnTime(deviceId);

            // Then - 验证返回正确的时间戳
            assertThat(result).isEqualTo(expectedTimestamp);
            verify(valueOperations).get(redisKey);
        }

        @Test
        @DisplayName("应该在缓存为空时返回0")
        void should_return_zero_when_cache_is_empty() {
            // Given - 缓存为空
            Long deviceId = 12345L;
            String redisKey = String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId);
            
            when(valueOperations.get(redisKey)).thenReturn(null);

            // When - 执行获取方法
            Long result = deviceSwitchOnRecordService.getLatestSwitchOnTime(deviceId);

            // Then - 验证返回0
            assertThat(result).isZero();
            verify(valueOperations).get(redisKey);
        }

        @Test
        @DisplayName("应该正确处理不同设备ID的缓存键")
        void should_handle_different_device_ids_correctly() {
            // Given - 准备不同设备的缓存数据
            Long deviceId1 = 11111L;
            Long deviceId2 = 22222L;
            Long timestamp1 = 1640995200L;
            Long timestamp2 = 1640995800L;
            
            String redisKey1 = String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId1);
            String redisKey2 = String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId2);
            
            when(valueOperations.get(redisKey1)).thenReturn(String.valueOf(timestamp1));
            when(valueOperations.get(redisKey2)).thenReturn(String.valueOf(timestamp2));

            // When - 分别获取不同设备的开机时间
            Long result1 = deviceSwitchOnRecordService.getLatestSwitchOnTime(deviceId1);
            Long result2 = deviceSwitchOnRecordService.getLatestSwitchOnTime(deviceId2);

            // Then - 验证返回对应的时间戳
            assertThat(result1).isEqualTo(timestamp1);
            assertThat(result2).isEqualTo(timestamp2);
            verify(valueOperations).get(redisKey1);
            verify(valueOperations).get(redisKey2);
        }
    }

    @Nested
    @DisplayName("saveLatestSwitchOnTime - 保存最近开机时间")
    class SaveLatestSwitchOnTimeTests {

        @Test
        @DisplayName("应该正确保存开机时间到缓存")
        void should_save_switch_on_time_to_cache_correctly() {
            // Given - 准备保存数据
            Long deviceId = 12345L;
            Long timestamp = 1640995200L;
            String redisKey = String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId);

            // When - 执行保存方法
            deviceSwitchOnRecordService.saveLatestSwitchOnTime(deviceId, timestamp);

            // Then - 验证保存到缓存（10分钟TTL）
            verify(valueOperations).set(
                redisKey,
                String.valueOf(timestamp),
                600L,
                TimeUnit.SECONDS
            );
        }

        @Test
        @DisplayName("应该正确处理不同设备ID和时间戳的保存")
        void should_handle_different_devices_and_timestamps_correctly() {
            // Given - 准备不同设备的保存数据
            Long deviceId1 = 11111L;
            Long deviceId2 = 22222L;
            Long timestamp1 = 1640995200L;
            Long timestamp2 = 1640995800L;
            
            String redisKey1 = String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId1);
            String redisKey2 = String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId2);

            // When - 分别保存不同设备的开机时间
            deviceSwitchOnRecordService.saveLatestSwitchOnTime(deviceId1, timestamp1);
            deviceSwitchOnRecordService.saveLatestSwitchOnTime(deviceId2, timestamp2);

            // Then - 验证分别保存到对应的缓存键
            verify(valueOperations).set(
                redisKey1,
                String.valueOf(timestamp1),
                600L,
                TimeUnit.SECONDS
            );
            verify(valueOperations).set(
                redisKey2,
                String.valueOf(timestamp2),
                600L,
                TimeUnit.SECONDS
            );
        }

        @Test
        @DisplayName("应该使用正确的TTL时间（10分钟）")
        void should_use_correct_ttl_time() {
            // Given - 准备保存数据
            Long deviceId = 12345L;
            Long timestamp = 1640995200L;

            // When - 执行保存方法
            deviceSwitchOnRecordService.saveLatestSwitchOnTime(deviceId, timestamp);

            // Then - 验证使用600秒（10分钟）的TTL
            verify(valueOperations).set(
                anyString(),
                anyString(),
                eq(600L), // 10分钟 = 600秒
                eq(TimeUnit.SECONDS)
            );
        }
    }

    @Nested
    @DisplayName("边界情况和集成测试")
    class EdgeCaseAndIntegrationTests {

        @Test
        @DisplayName("应该正确处理边界时间间隔（恰好2分钟）")
        void should_handle_boundary_time_interval_correctly() {
            // Given - 准备恰好2分钟间隔的数据
            Long deviceId = 12345L;
            TerminalStatusReport.InfoWrapper infoWrapper = createInfoWrapper(180000L); // 3分钟运行时间
            
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            long expectedSwitchOnTime = currentTimeSeconds - 180; // 3分钟前开机
            long lastSwitchOnTime = expectedSwitchOnTime - 120; // 恰好2分钟间隔
            
            when(valueOperations.get(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId)))
                .thenReturn(String.valueOf(lastSwitchOnTime));

            // When - 执行业务方法
            deviceSwitchOnRecordService.asyncHandlerSwitchOnRecord(deviceId, infoWrapper);

            // Then - 验证恰好2分钟间隔不被跳过（>=2分钟才跳过）
            verify(terminalSwitchOnRecordRepository).saveSwitchOnRecord(eq(deviceId), anyLong());
        }

        @Test
        @DisplayName("应该正确处理大的运行时间值")
        void should_handle_large_uptime_values_correctly() {
            // Given - 准备大的运行时间值（7天）
            Long deviceId = 12345L;
            long sevenDaysInMillis = 7L * 24 * 60 * 60 * 1000; // 7天的毫秒数
            TerminalStatusReport.InfoWrapper infoWrapper = createInfoWrapper(sevenDaysInMillis);
            
            when(valueOperations.get(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId)))
                .thenReturn("0"); // 首次开机

            // When - 执行业务方法
            deviceSwitchOnRecordService.asyncHandlerSwitchOnRecord(deviceId, infoWrapper);

            // Then - 验证大运行时间值被正确处理
            verify(terminalSwitchOnRecordRepository).saveSwitchOnRecord(eq(deviceId), anyLong());
        }

        @Test
        @DisplayName("应该正确处理Redis缓存异常情况")
        void should_handle_redis_cache_exceptions_correctly() {
            // Given - 模拟Redis异常
            Long deviceId = 12345L;
            
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection failed"));

            // When & Then - 执行业务方法，验证异常被正确处理
            assertThatThrownBy(() -> 
                deviceSwitchOnRecordService.getLatestSwitchOnTime(deviceId)
            ).isInstanceOf(RuntimeException.class);
        }
    }

    // 测试数据构建方法
    private TerminalStatusReport.InfoWrapper createInfoWrapper(Long uptimeMillis) {
        TerminalStatusReport.Info info = TerminalStatusReport.Info.builder()
            .vername("v1.0.0")
            .serialno("SN12345")
            .model("LED-001")
            .up(uptimeMillis) // 运行时间（毫秒）
            .build();
        
        return TerminalStatusReport.InfoWrapper.builder()
            .info(info)
            .reportTime(System.currentTimeMillis())
            .build();
    }
}