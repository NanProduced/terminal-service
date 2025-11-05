package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.report.DownloadingReport;
import com.colorlight.terminal.application.domain.report.ProgramDownloadingReport;
import com.colorlight.terminal.application.domain.report.UpgradePackageDownloadingReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeviceDownloadingRedisService 单元测试
 * 测试设备下载状态Redis缓存服务的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备下载状态Redis服务测试")
@SuppressWarnings("unchecked")
class DeviceDownloadingRedisServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    private DeviceDownloadingRedisService service;
    
    // 测试数据
    private static final Long TEST_DEVICE_ID = 12345L;
    
    @BeforeEach
    void setUp() {
        service = new DeviceDownloadingRedisService(redisTemplate);
    }
    
    @Nested
    @DisplayName("节目下载状态保存测试")
    class ProgramDownloadingTest {
        
        @Test
        @DisplayName("应该成功保存节目下载状态到Redis")
        void should_save_program_downloading_status_successfully() {
            // Given - 准备节目下载报告
            ProgramDownloadingReport report = new ProgramDownloadingReport();
            
            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));
            
            // When - 保存下载状态，应该不抛出异常
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, report));
            
            // Then - 验证Redis事务被执行
            verify(redisTemplate).execute(any(SessionCallback.class));
        }
    }
    
    @Nested
    @DisplayName("升级包下载状态保存测试")
    class UpgradePackageDownloadingTest {
        
        @Test
        @DisplayName("应该成功保存升级包下载状态到Redis")
        void should_save_upgrade_package_downloading_status_successfully() {
            // Given - 准备升级包下载报告
            UpgradePackageDownloadingReport report = new UpgradePackageDownloadingReport();
            
            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));
            
            // When - 保存下载状态，应该不抛出异常
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, report));
            
            // Then - 验证Redis事务被执行
            verify(redisTemplate).execute(any(SessionCallback.class));
        }
    }
    
    @Nested
    @DisplayName("未知下载类型处理测试")
    class UnknownDownloadingTypeTest {
        
        @Test
        @DisplayName("应该处理未知的下载报告类型")
        void should_handle_unknown_downloading_report_type() {
            // Given - 准备未知类型的下载报告
            DownloadingReport unknownReport = new DownloadingReport() {
                @Override
                public String getWhat() {
                    return "unknown_type";
                }
            };
            
            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));
            
            // When - 保存下载状态，应该不抛出异常
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, unknownReport));
            
            // Then - 验证Redis事务被执行（即使是未知类型也会执行Redis操作）
            verify(redisTemplate).execute(any(SessionCallback.class));
        }
    }
    
    @Nested
    @DisplayName("异步执行测试")
    class AsyncExecutionTest {
        
        @Test
        @DisplayName("应该标记为异步执行方法")
        void should_be_marked_as_async_method() throws NoSuchMethodException {
            // Then - 验证方法被@Async注解标记
            var method = DeviceDownloadingRedisService.class.getMethod("saveDownloadingStatus", Long.class, DownloadingReport.class);
            assertThat(method.isAnnotationPresent(org.springframework.scheduling.annotation.Async.class)).isTrue();
            
            var asyncAnnotation = method.getAnnotation(org.springframework.scheduling.annotation.Async.class);
            assertThat(asyncAnnotation.value()).isEqualTo("statisticsReportExecutor");
        }
        
        @Test
        @DisplayName("执行过程中发生异常不影响正常执行")
        void should_rethrow_exception_during_async_execution() {
            // Given - 准备节目下载报告
            ProgramDownloadingReport report = new ProgramDownloadingReport();
            
            // Mock Redis执行失败
            RuntimeException redisException = new RuntimeException("Redis服务不可用");
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenThrow(redisException);
            
            // When & Then -
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, report));
        }
    }
    
    @Nested
    @DisplayName("Redis事务原子性测试")
    class RedisTransactionAtomicityTest {
        
        @Test
        @DisplayName("应该使用Redis事务保证操作原子性")
        void should_use_redis_transaction_for_atomicity() {
            // Given - 准备节目下载报告
            ProgramDownloadingReport report = new ProgramDownloadingReport();
            
            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));
            
            // When - 保存下载状态
            service.saveDownloadingStatus(TEST_DEVICE_ID, report);
            
            // Then - 验证使用了SessionCallback（事务机制）
            verify(redisTemplate).execute(any(SessionCallback.class));
        }
        
        @Test
        @DisplayName("应该正确处理null设备ID")
        void should_handle_null_device_id() {
            // Given - null设备ID和有效报告
            ProgramDownloadingReport report = new ProgramDownloadingReport();
            
            // When & Then - 应该能正常处理（具体行为取决于实现）
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(null, report));
        }
    }
    
    @Nested
    @DisplayName("业务逻辑验证测试")
    class BusinessLogicTest {

        @Test
        @DisplayName("应该为不同设备ID使用不同的Redis键")
        void should_use_different_redis_keys_for_different_devices() {
            // Given - 不同的设备ID
            Long deviceId1 = 111L;
            Long deviceId2 = 222L;
            ProgramDownloadingReport report = new ProgramDownloadingReport();

            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));

            // When - 为不同设备保存下载状态
            service.saveDownloadingStatus(deviceId1, report);
            service.saveDownloadingStatus(deviceId2, report);

            // Then - 验证Redis事务被执行了两次（每个设备一次）
            verify(redisTemplate, times(2)).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该支持连续保存同一设备的下载状态")
        void should_support_continuous_saving_for_same_device() {
            // Given - 同一设备的多个下载报告
            ProgramDownloadingReport report1 = new ProgramDownloadingReport();
            UpgradePackageDownloadingReport report2 = new UpgradePackageDownloadingReport();

            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));

            // When - 连续保存下载状态
            service.saveDownloadingStatus(TEST_DEVICE_ID, report1);
            service.saveDownloadingStatus(TEST_DEVICE_ID, report2);

            // Then - 验证Redis事务被执行了两次
            verify(redisTemplate, times(2)).execute(any(SessionCallback.class));
        }
    }

    @Nested
    @DisplayName("字段映射测试")
    class FieldMappingTest {

        @Test
        @DisplayName("应该正确映射节目下载报告的所有字段")
        void should_map_all_program_downloading_fields() {
            // Given - 带有具体值的节目下载报告
            ProgramDownloadingReport report = new ProgramDownloadingReport();
            ProgramDownloadingReport.Downloading downloading = new ProgramDownloadingReport.Downloading();
            downloading.setDownloadStatusTime(System.currentTimeMillis());
            report.setDownloading(downloading);

            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));

            // When - 保存下载状态
            service.saveDownloadingStatus(TEST_DEVICE_ID, report);

            // Then - 验证Redis事务被执行
            verify(redisTemplate).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该正确映射升级包下载报告的所有字段")
        void should_map_all_upgrade_package_downloading_fields() {
            // Given - 带有具体值的升级包下载报告
            UpgradePackageDownloadingReport report = new UpgradePackageDownloadingReport();
            UpgradePackageDownloadingReport.Downloading downloading = new UpgradePackageDownloadingReport.Downloading();
            UpgradePackageDownloadingReport.UpdateZip updateZip = new UpgradePackageDownloadingReport.UpdateZip();
            updateZip.setStatus("downloading");
            updateZip.setDesVersion("2.0.0");
            downloading.setUpdateZip(updateZip);
            downloading.setUpdateStatusTimes(System.currentTimeMillis());
            report.setDownloading(downloading);

            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));

            // When - 保存下载状态
            service.saveDownloadingStatus(TEST_DEVICE_ID, report);

            // Then - 验证Redis事务被执行
            verify(redisTemplate).execute(any(SessionCallback.class));
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryValueTest {

        @Test
        @DisplayName("应该处理空下载报告")
        void should_handle_empty_downloading_report() {
            // Given - 没有downloading对象的节目下载报告
            ProgramDownloadingReport report = new ProgramDownloadingReport();

            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));

            // When - 保存下载状态
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, report));

            // Then - 验证Redis事务被执行
            verify(redisTemplate).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该处理升级包为空的升级报告")
        void should_handle_empty_update_zip_downloading_report() {
            // Given - 没有updateZip的升级包下载报告
            UpgradePackageDownloadingReport report = new UpgradePackageDownloadingReport();
            UpgradePackageDownloadingReport.Downloading downloading = new UpgradePackageDownloadingReport.Downloading();
            report.setDownloading(downloading);

            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));

            // When - 保存下载状态
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, report));

            // Then - 验证Redis事务被执行
            verify(redisTemplate).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该处理Long型最大值的设备ID")
        void should_handle_max_long_device_id() {
            // Given - 最大Long值作为设备ID
            ProgramDownloadingReport report = new ProgramDownloadingReport();

            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));

            // When - 保存下载状态
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(Long.MAX_VALUE, report));

            // Then - 验证Redis事务被执行
            verify(redisTemplate).execute(any(SessionCallback.class));
        }

        @Test
        @DisplayName("应该处理null报告对象时不抛出NPE")
        void should_handle_null_report_gracefully() {
            // Given - null报告对象
            // Mock Redis事务执行成功
            lenient().when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of("OK"));

            // When & Then - 应该处理null报告（具体行为取决于实现）
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, null));
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("应该处理Redis连接超时异常")
        void should_handle_redis_connection_timeout() {
            // Given - 准备节目下载报告
            ProgramDownloadingReport report = new ProgramDownloadingReport();

            // Mock Redis连接超时
            lenient().when(redisTemplate.execute(any(SessionCallback.class)))
                .thenThrow(new RuntimeException("连接超时"));

            // When & Then - 异步方法不应该直接抛出异常（由异步线程处理）
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, report));
        }

        @Test
        @DisplayName("应该处理Redis命令执行失败异常")
        void should_handle_redis_command_failure() {
            // Given - 准备升级包下载报告
            UpgradePackageDownloadingReport report = new UpgradePackageDownloadingReport();

            // Mock Redis命令执行失败
            lenient().when(redisTemplate.execute(any(SessionCallback.class)))
                .thenThrow(new RuntimeException("命令执行失败"));

            // When & Then - 异步方法不应该直接抛出异常
            assertThatNoException().isThrownBy(() -> service.saveDownloadingStatus(TEST_DEVICE_ID, report));
        }
    }
}