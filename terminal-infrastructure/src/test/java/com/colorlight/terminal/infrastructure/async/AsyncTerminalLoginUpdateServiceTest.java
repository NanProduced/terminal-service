package com.colorlight.terminal.infrastructure.async;

import com.colorlight.terminal.application.dto.record.LoginUpdateRecord;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.infrastructure.event.AsyncBufferFlushEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 异步终端登录更新服务测试
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("异步终端登录更新服务测试")
class AsyncTerminalLoginUpdateServiceTest {

    @Mock
    private TerminalAccountRepository terminalAccountRepository;
    
    @Mock
    private DeviceConfigPort deviceConfigPort;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @InjectMocks
    private AsyncTerminalLoginUpdateService asyncTerminalLoginUpdateService;

    /**
     * 测试数据构建器
     */
    private static class TestDataBuilder {
        
        /**
         * 创建登录更新记录
         */
        static LoginUpdateRecord createLoginUpdateRecord(Long deviceId, String clientIp) {
            return LoginUpdateRecord.create(deviceId, clientIp);
        }
        
        /**
         * 创建指定时间的登录更新记录
         */
        static LoginUpdateRecord createLoginUpdateRecord(Long deviceId, String clientIp, LocalDateTime updateTime) {
            return LoginUpdateRecord.create(deviceId, clientIp, updateTime);
        }
        
        /**
         * 批量创建登录更新记录
         */
        static List<LoginUpdateRecord> createBatchLoginUpdateRecords(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> createLoginUpdateRecord((long) (1000 + i), "192.168.1." + (100 + i)))
                    .toList();
        }
        
        /**
         * 创建同一设备的多个登录记录（用于测试去重）
         */
        static List<LoginUpdateRecord> createDuplicateDeviceRecords(Long deviceId) {
            LocalDateTime baseTime = LocalDateTime.now();
            return IntStream.range(0, 3)
                    .mapToObj(i -> createLoginUpdateRecord(
                            deviceId, 
                            "192.168.1." + (100 + i),
                            baseTime.plusMinutes(i)
                    ))
                    .toList();
        }
        
        /**
         * 创建包含null值的记录列表
         */
        static List<LoginUpdateRecord> createRecordsWithNulls() {
            return Arrays.asList(
                    createLoginUpdateRecord(1001L, "192.168.1.100"),
                    null, // null记录
                    createLoginUpdateRecord(null, "192.168.1.101"), // null设备ID
                    createLoginUpdateRecord(1002L, "192.168.1.102")
            );
        }
    }

    @BeforeEach
    void setUp() {
        // 设置默认配置返回值
        lenient().when(deviceConfigPort.getBufferPoolWindowMs()).thenReturn(5000L);
        lenient().when(deviceConfigPort.getBufferPoolMaxSize()).thenReturn(1000);
        lenient().when(deviceConfigPort.getBufferPoolBatchSize()).thenReturn(100);
        lenient().when(deviceConfigPort.getEmergencyFlushThreshold()).thenReturn(0.8);
        lenient().when(deviceConfigPort.getTaskBufferPoolDelayMs()).thenReturn(1000L);
        lenient().when(deviceConfigPort.isEmergencyFlushEnabled()).thenReturn(true);
        
        // 初始化服务
        asyncTerminalLoginUpdateService.init();
    }

    @Test
    @DisplayName("应该成功提交单个登录更新")
    void should_submit_single_login_update_successfully() {
        // Given - 准备设备登录信息
        Long deviceId = 1001L;
        String clientIp = "192.168.1.100";

        // When - 提交登录更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, clientIp);

        // Then - 验证记录被添加到缓冲池
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(eq(deviceId), eq(clientIp), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该成功提交指定时间的登录更新")
    void should_submit_login_update_with_specified_time() {
        // Given - 准备设备登录信息和指定时间
        Long deviceId = 1001L;
        String clientIp = "192.168.1.100";
        LocalDateTime specifiedTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        // When - 提交指定时间的登录更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, clientIp, specifiedTime);

        // Then - 验证使用指定时间更新
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(deviceId, clientIp, specifiedTime);
    }

    @Test
    @DisplayName("应该正确处理null设备ID的提交")
    void should_handle_null_device_id_submission() {
        // Given - null设备ID
        String clientIp = "192.168.1.100";

        // When - 提交null设备ID的更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(null, clientIp);

        // Then - 验证没有记录被处理
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, never()).updateLoginTime(any(), any(), any());
    }

    @Test
    @DisplayName("应该成功批量提交登录更新")
    void should_submit_batch_login_updates_successfully() {
        // Given - 批量登录更新记录
        List<LoginUpdateRecord> records = TestDataBuilder.createBatchLoginUpdateRecords(5);

        // When - 批量提交登录更新
        asyncTerminalLoginUpdateService.submitBatchLoginUpdate(records);

        // Then - 验证所有记录被处理
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(5)).updateLoginTime(any(Long.class), any(String.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该正确处理空批量列表提交")
    void should_handle_empty_batch_submission() {
        // Given - 空的记录列表
        List<LoginUpdateRecord> emptyList = List.of();

        // When - 提交空列表
        asyncTerminalLoginUpdateService.submitBatchLoginUpdate(emptyList);

        // Then - 验证没有记录被处理
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, never()).updateLoginTime(any(), any(), any());
    }

    @Test
    @DisplayName("应该正确过滤批量提交中的null对象")
    void should_filter_null_objects_in_batch_submission() {
        // Given - 包含null对象的记录列表
        List<LoginUpdateRecord> recordsWithNulls = TestDataBuilder.createRecordsWithNulls();

        // When - 提交包含null的列表
        asyncTerminalLoginUpdateService.submitBatchLoginUpdate(recordsWithNulls);

        // Then - 验证只有有效记录被处理（2个有效记录）
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(2)).updateLoginTime(any(Long.class), any(String.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该自动去重相同设备的登录更新")
    void should_deduplicate_same_device_login_updates() {
        // Given - 同一设备的多个登录记录
        Long deviceId = 1001L;
        List<LoginUpdateRecord> duplicateRecords = TestDataBuilder.createDuplicateDeviceRecords(deviceId);

        // When - 批量提交重复设备的记录
        asyncTerminalLoginUpdateService.submitBatchLoginUpdate(duplicateRecords);

        // Then - 验证只有最后一条记录被保留（自动去重）
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(eq(deviceId), any(String.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该在单个提交中也能去重")
    void should_deduplicate_in_single_submissions() {
        // Given - 同一设备的多次单个提交
        Long deviceId = 1001L;
        
        // When - 多次提交同一设备的更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, "192.168.1.100");
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, "192.168.1.101");
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, "192.168.1.102");

        // Then - 验证只有最后一次更新被保留
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(eq(deviceId), eq("192.168.1.102"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该成功刷新缓冲池")
    void should_flush_buffer_successfully() {
        // Given - 添加登录更新到缓冲池
        asyncTerminalLoginUpdateService.submitLoginUpdate(1001L, "192.168.1.100");
        asyncTerminalLoginUpdateService.submitLoginUpdate(1002L, "192.168.1.101");

        // When - 刷新缓冲池
        asyncTerminalLoginUpdateService.flushBuffer();

        // Then - 验证登录时间被更新
        verify(terminalAccountRepository, times(2)).updateLoginTime(any(Long.class), any(String.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该在缓冲池为空时跳过刷新")
    void should_skip_flush_when_buffer_is_empty() {
        // Given - 空的缓冲池
        // 没有添加任何记录

        // When - 刷新空缓冲池
        asyncTerminalLoginUpdateService.flushBuffer();

        // Then - 验证没有调用更新操作
        verify(terminalAccountRepository, never()).updateLoginTime(any(), any(), any());
    }

    @Test
    @DisplayName("应该按批次大小处理大量更新")
    void should_process_large_batch_by_configured_size() {
        // Given - 设置较小的批次大小
        when(deviceConfigPort.getBufferPoolBatchSize()).thenReturn(3);
        
        // 添加大量更新到缓冲池
        List<LoginUpdateRecord> largeRecordList = TestDataBuilder.createBatchLoginUpdateRecords(10);
        asyncTerminalLoginUpdateService.submitBatchLoginUpdate(largeRecordList);

        // When - 刷新缓冲池
        asyncTerminalLoginUpdateService.flushBuffer();

        // Then - 验证所有更新都被处理
        verify(terminalAccountRepository, times(10)).updateLoginTime(any(Long.class), any(String.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该在定时刷新时处理非空缓冲池")
    void should_process_non_empty_buffer_on_scheduled_flush() {
        // Given - 添加记录到缓冲池
        asyncTerminalLoginUpdateService.submitLoginUpdate(1001L, "192.168.1.100");

        // When - 执行定时刷新
        asyncTerminalLoginUpdateService.scheduledFlush();

        // Then - 验证事件被发布
        verify(eventPublisher, times(1)).publishEvent(any(AsyncBufferFlushEvent.class));
        
        // 手动执行flushBuffer来验证业务逻辑
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(eq(1001L), eq("192.168.1.100"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该在定时刷新时跳过空缓冲池")
    void should_skip_empty_buffer_on_scheduled_flush() {
        // Given - 空的缓冲池
        // 没有添加任何记录

        // When - 执行定时刷新
        asyncTerminalLoginUpdateService.scheduledFlush();

        // Then - 验证没有发布事件
        verify(eventPublisher, never()).publishEvent(any(AsyncBufferFlushEvent.class));
        verify(terminalAccountRepository, never()).updateLoginTime(any(), any(), any());
    }

    @Test
    @DisplayName("应该在服务关闭时强制刷新缓冲池")
    void should_force_flush_buffer_on_service_destroy() {
        // Given - 添加记录到缓冲池
        asyncTerminalLoginUpdateService.submitLoginUpdate(1001L, "192.168.1.100");
        asyncTerminalLoginUpdateService.submitLoginUpdate(1002L, "192.168.1.101");

        // When - 销毁服务
        asyncTerminalLoginUpdateService.destroy();

        // Then - 验证缓冲池被强制刷新（destroy直接调用flushBuffer，不是事件驱动）
        verify(terminalAccountRepository, times(2)).updateLoginTime(any(Long.class), any(String.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该在服务停止后拒绝新的更新提交")
    void should_reject_new_submissions_after_service_stopped() {
        // Given - 停止服务
        asyncTerminalLoginUpdateService.destroy();

        // When - 尝试提交新更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(1001L, "192.168.1.100");

        // Then - 验证更新未被处理
        verify(terminalAccountRepository, never()).updateLoginTime(any(), any(), any());
    }

    @Test
    @DisplayName("应该在更新失败时继续处理后续更新")
    void should_continue_processing_when_update_fails() {
        // Given - 设置更新操作抛出异常
        doThrow(new RuntimeException("模拟数据库更新失败"))
                .when(terminalAccountRepository).updateLoginTime(any(), any(), any());
        
        // 添加多个记录到缓冲池
        asyncTerminalLoginUpdateService.submitLoginUpdate(1001L, "192.168.1.100");
        asyncTerminalLoginUpdateService.submitLoginUpdate(1002L, "192.168.1.101");

        // When - 刷新缓冲池
        asyncTerminalLoginUpdateService.flushBuffer();

        // Then - 验证尽管发生异常，所有更新都被尝试处理
        verify(terminalAccountRepository, times(2)).updateLoginTime(any(Long.class), any(String.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该正确处理不同IP地址的同设备登录")
    void should_handle_same_device_with_different_ips() {
        // Given - 同一设备从不同IP登录
        Long deviceId = 1001L;
        String firstIp = "192.168.1.100";
        String secondIp = "10.0.0.50";
        
        LocalDateTime firstTime = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
        LocalDateTime secondTime = LocalDateTime.of(2024, 1, 15, 11, 0, 0);

        // When - 提交不同时间的登录更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, firstIp, firstTime);
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, secondIp, secondTime);

        // Then - 验证只保留最新的登录记录
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(deviceId, secondIp, secondTime);
        verify(terminalAccountRepository, never()).updateLoginTime(deviceId, firstIp, firstTime);
    }

    @Test
    @DisplayName("应该正确处理IPv6地址")
    void should_handle_ipv6_addresses() {
        // Given - IPv6地址
        Long deviceId = 1001L;
        String ipv6Address = "2001:db8::1";

        // When - 提交IPv6地址的登录更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, ipv6Address);

        // Then - 验证IPv6地址被正确处理
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(eq(deviceId), eq(ipv6Address), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该正确处理null客户端IP")
    void should_handle_null_client_ip() {
        // Given - null客户端IP
        Long deviceId = 1001L;

        // When - 提交null IP的登录更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, null);

        // Then - 验证null IP被正确处理
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(eq(deviceId), isNull(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该正确处理内网和外网IP地址")
    void should_handle_internal_and_external_ip_addresses() {
        // Given - 内网和外网IP地址
        Long deviceId1 = 1001L;
        Long deviceId2 = 1002L;
        String internalIp = "192.168.1.100"; // 内网IP
        String externalIp = "8.8.8.8"; // 外网IP

        // When - 提交内网和外网IP的登录更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId1, internalIp);
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId2, externalIp);

        // Then - 验证两种IP都被正确处理
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(eq(deviceId1), eq(internalIp), any(LocalDateTime.class));
        verify(terminalAccountRepository, times(1)).updateLoginTime(eq(deviceId2), eq(externalIp), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应该正确处理时间边界情况")
    void should_handle_time_boundary_cases() {
        // Given - 各种时间边界情况
        Long deviceId = 1001L;
        String clientIp = "192.168.1.100";
        
        LocalDateTime pastTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        LocalDateTime futureTime = LocalDateTime.of(2030, 12, 31, 23, 59, 59);

        // When - 提交边界时间的登录更新
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, clientIp, pastTime);
        
        // Then - 验证过去时间被正确处理
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(deviceId, clientIp, pastTime);
        
        // When - 提交未来时间的登录更新
        reset(terminalAccountRepository);
        asyncTerminalLoginUpdateService.submitLoginUpdate(deviceId, clientIp, futureTime);
        
        // Then - 验证未来时间被正确处理
        asyncTerminalLoginUpdateService.flushBuffer();
        verify(terminalAccountRepository, times(1)).updateLoginTime(deviceId, clientIp, futureTime);
    }
}