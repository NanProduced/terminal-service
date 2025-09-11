package com.colorlight.terminal.infrastructure.rpc.service;

import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.infrastructure.cleanup.DeviceDataCleanupService;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.config.DataCleanupConfigDTO;
import com.colorlight.terminal.rpc.dto.enums.CleanupMode;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import com.colorlight.terminal.rpc.dto.request.BatchDeviceDataCleanupRequestDTO;
import com.colorlight.terminal.rpc.dto.request.DeviceDataCleanupRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DeviceDataCleanupRpcServiceImpl 单元测试
 * 
 * <p>测试策略：</p>
 * <ul>
 *   <li>重点验证业务逻辑正确性，不依赖日志内容</li>
 *   <li>验证参数校验和错误处理</li>
 *   <li>验证异步调用的正确性</li>
 *   <li>验证批量操作的数量限制</li>
 *   <li>验证配置参数的传递</li>
 * </ul>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备数据清理RPC服务测试")
class DeviceDataCleanupRpcServiceImplTest {

    @Mock
    private DeviceDataCleanupService deviceDataCleanupService;

    @InjectMocks
    private DeviceDataCleanupRpcServiceImpl rpcService;

    @Captor
    private ArgumentCaptor<Long> deviceIdCaptor;

    @Captor
    private ArgumentCaptor<List<Long>> deviceIdsCaptor;

    @Captor
    private ArgumentCaptor<DataCleanupConfigDTO> configCaptor;

    // 测试常量
    private static final Long TEST_DEVICE_ID = 10001L;

    @Nested
    @DisplayName("单设备数据清理测试")
    class SingleDeviceCleanupTests {

        @Test
        @DisplayName("应该成功提交单设备清理任务")
        void should_successfully_submit_single_device_cleanup_task() {
            // Given
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID);

            // When
            RpcResult<Void> result = rpcService.cleanupDeviceDataAsync(request);

            // Then
            assertThat(result.isSuccess()).isTrue();
            verify(deviceDataCleanupService).cleanupDeviceDataAsync(TEST_DEVICE_ID, null);
        }

        @Test
        @DisplayName("应该成功提交带自定义配置的清理任务")
        void should_successfully_submit_cleanup_task_with_custom_config() {
            // Given
            DataCleanupConfigDTO customConfig = createTestCleanupConfig();
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID, customConfig);

            // When
            RpcResult<Void> result = rpcService.cleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> verify(deviceDataCleanupService).cleanupDeviceDataAsync(
                            deviceIdCaptor.capture(), configCaptor.capture())
            );

            // 验证传递的参数
            assertAll(
                    () -> assertThat(deviceIdCaptor.getValue()).isEqualTo(TEST_DEVICE_ID),
                    () -> assertThat(configCaptor.getValue()).isEqualTo(customConfig)
            );
        }

        @Test
        @DisplayName("应该拒绝空设备ID")
        void should_reject_null_device_id() {
            // Given
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO();
            request.setDeviceId(null);

            // When
            RpcResult<Void> result = rpcService.cleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("设备ID不能为空")
            );
            
            // 验证没有调用清理服务
            verifyNoInteractions(deviceDataCleanupService);
        }

        @Test
        @DisplayName("应该处理参数校验异常")
        void should_handle_illegal_argument_exception() {
            // Given
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID);
            doThrow(new IllegalArgumentException("无效的数据类型配置"))
                    .when(deviceDataCleanupService).cleanupDeviceDataAsync(any(), any());

            // When
            RpcResult<Void> result = rpcService.cleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("无效的数据类型配置")
            );
        }

        @Test
        @DisplayName("应该处理系统异常")
        void should_handle_system_exception() {
            // Given
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID);
            doThrow(new RuntimeException("数据库连接失败"))
                    .when(deviceDataCleanupService).cleanupDeviceDataAsync(any(), any());

            // When
            RpcResult<Void> result = rpcService.cleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("系统错误")
            );
        }

        @Test
        @DisplayName("应该正确传递空配置")
        void should_correctly_pass_null_config() {
            // Given
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID, null);

            // When
            rpcService.cleanupDeviceDataAsync(request);

            // Then
            verify(deviceDataCleanupService).cleanupDeviceDataAsync(TEST_DEVICE_ID, null);
        }
    }

    @Nested
    @DisplayName("批量设备数据清理测试")
    class BatchDeviceCleanupTests {

        @Test
        @DisplayName("应该成功提交批量设备清理任务")
        void should_successfully_submit_batch_device_cleanup_task() {
            // Given
            List<Long> deviceIds = Arrays.asList(10001L, 10002L, 10003L);
            BatchDeviceDataCleanupRequestDTO request = new BatchDeviceDataCleanupRequestDTO(deviceIds);

            // When
            RpcResult<Void> result = rpcService.batchCleanupDeviceDataAsync(request);

            // Then
            assertThat(result.isSuccess()).isTrue();
            verify(deviceDataCleanupService).batchCleanupDeviceDataAsync(deviceIds, null);
        }

        @Test
        @DisplayName("应该成功提交带自定义配置的批量清理任务")
        void should_successfully_submit_batch_cleanup_task_with_custom_config() {
            // Given
            List<Long> deviceIds = Arrays.asList(10001L, 10002L);
            DataCleanupConfigDTO customConfig = createTestCleanupConfig();
            BatchDeviceDataCleanupRequestDTO request = new BatchDeviceDataCleanupRequestDTO(deviceIds, customConfig);

            // When
            RpcResult<Void> result = rpcService.batchCleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> verify(deviceDataCleanupService).batchCleanupDeviceDataAsync(
                            deviceIdsCaptor.capture(), configCaptor.capture())
            );

            // 验证传递的参数
            assertAll(
                    () -> assertThat(deviceIdsCaptor.getValue()).isEqualTo(deviceIds),
                    () -> assertThat(configCaptor.getValue()).isEqualTo(customConfig)
            );
        }

        @Test
        @DisplayName("应该拒绝空设备ID列表")
        void should_reject_null_device_ids_list() {
            // Given
            BatchDeviceDataCleanupRequestDTO request = new BatchDeviceDataCleanupRequestDTO();
            request.setDeviceIds(null);

            // When
            RpcResult<Void> result = rpcService.batchCleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("设备ID列表不能为空")
            );
            
            verifyNoInteractions(deviceDataCleanupService);
        }

        @Test
        @DisplayName("应该拒绝空的设备ID列表")
        void should_reject_empty_device_ids_list() {
            // Given
            BatchDeviceDataCleanupRequestDTO request = new BatchDeviceDataCleanupRequestDTO(Collections.emptyList());

            // When
            RpcResult<Void> result = rpcService.batchCleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("设备ID列表不能为空")
            );
            
            verifyNoInteractions(deviceDataCleanupService);
        }

        @Test
        @DisplayName("应该限制批量清理设备数量")
        void should_limit_batch_cleanup_device_count() {
            // Given - 超过100个设备
            List<Long> tooManyDeviceIds = generateDeviceIds(150);
            BatchDeviceDataCleanupRequestDTO request = new BatchDeviceDataCleanupRequestDTO(tooManyDeviceIds);

            // When
            RpcResult<Void> result = rpcService.batchCleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).contains("批量清理设备数量不能超过100个"),
                    () -> assertThat(result.getErrorMessage()).contains("当前: 150")
            );
            
            // 验证没有调用清理服务
            verifyNoInteractions(deviceDataCleanupService);
        }

        @Test
        @DisplayName("应该接受恰好100个设备的批量请求")
        void should_accept_exactly_100_devices_batch_request() {
            // Given - 恰好100个设备
            List<Long> exactlyHundredDeviceIds = generateDeviceIds(100);
            BatchDeviceDataCleanupRequestDTO request = new BatchDeviceDataCleanupRequestDTO(exactlyHundredDeviceIds);

            // When
            RpcResult<Void> result = rpcService.batchCleanupDeviceDataAsync(request);

            // Then
            assertThat(result.isSuccess()).isTrue();
            verify(deviceDataCleanupService).batchCleanupDeviceDataAsync(exactlyHundredDeviceIds, null);
        }

        @Test
        @DisplayName("应该处理批量清理的参数校验异常")
        void should_handle_illegal_argument_exception_in_batch_cleanup() {
            // Given
            List<Long> deviceIds = Arrays.asList(10001L, 10002L);
            BatchDeviceDataCleanupRequestDTO request = new BatchDeviceDataCleanupRequestDTO(deviceIds);
            doThrow(new IllegalArgumentException("批量操作配置错误"))
                    .when(deviceDataCleanupService).batchCleanupDeviceDataAsync(any(), any());

            // When
            RpcResult<Void> result = rpcService.batchCleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("批量操作配置错误")
            );
        }

        @Test
        @DisplayName("应该处理批量清理的系统异常")
        void should_handle_system_exception_in_batch_cleanup() {
            // Given
            List<Long> deviceIds = Arrays.asList(10001L, 10002L);
            BatchDeviceDataCleanupRequestDTO request = new BatchDeviceDataCleanupRequestDTO(deviceIds);
            doThrow(new RuntimeException("线程池已满"))
                    .when(deviceDataCleanupService).batchCleanupDeviceDataAsync(any(), any());

            // When
            RpcResult<Void> result = rpcService.batchCleanupDeviceDataAsync(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("系统错误")
            );
        }
    }

    @Nested
    @DisplayName("配置参数传递测试")
    class ConfigurationParameterTests {

        @Test
        @DisplayName("应该正确传递完整的清理配置")
        void should_correctly_pass_complete_cleanup_config() {
            // Given
            Set<DataType> dataTypes = new HashSet<>(Arrays.asList(DataType.STATUS_REPORT, DataType.TERMINAL_LOG));
            DataCleanupConfigDTO config = DataCleanupConfigDTO.builder()
                    .mode(CleanupMode.INCLUDE)
                    .dataTypes(dataTypes)
                    .build();
            
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID, config);

            // When
            rpcService.cleanupDeviceDataAsync(request);

            // Then
            verify(deviceDataCleanupService).cleanupDeviceDataAsync(
                    eq(TEST_DEVICE_ID), configCaptor.capture());
            
            DataCleanupConfigDTO capturedConfig = configCaptor.getValue();
            assertAll(
                    () -> assertThat(capturedConfig.getMode()).isEqualTo(CleanupMode.INCLUDE),
                    () -> assertThat(capturedConfig.getDataTypes()).containsExactlyInAnyOrder(
                            DataType.STATUS_REPORT, DataType.TERMINAL_LOG)
            );
        }

        @Test
        @DisplayName("应该正确传递不同的清理模式")
        void should_correctly_pass_different_cleanup_modes() {
            // 测试所有清理模式
            CleanupMode[] allModes = CleanupMode.values();
            
            for (CleanupMode mode : allModes) {
                // Given
                DataCleanupConfigDTO config = DataCleanupConfigDTO.builder()
                        .mode(mode)
                        .build();
                
                DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID, config);

                // When
                rpcService.cleanupDeviceDataAsync(request);

                // Then
                verify(deviceDataCleanupService).cleanupDeviceDataAsync(
                        eq(TEST_DEVICE_ID), configCaptor.capture());
                
                assertThat(configCaptor.getValue().getMode()).isEqualTo(mode);
                
                // 重置mock以便下次验证
                reset(deviceDataCleanupService);
            }
        }

        @Test
        @DisplayName("应该正确传递不同的数据类型组合")
        void should_correctly_pass_different_data_type_combinations() {
            // Given - 不同的数据类型组合
            List<Set<DataType>> dataTypeCombinations = Arrays.asList(
                    new HashSet<>(List.of(DataType.STATUS_REPORT)),
                    new HashSet<>(Arrays.asList(DataType.TERMINAL_LOG, DataType.MEDIA_PLAY_RECORD)),
                    new HashSet<>(Arrays.asList(DataType.PROGRAM_PLAY_RECORD, DataType.ONLINE_TIME, DataType.ABNORMAL_RECONNECT)),
                    new HashSet<>(List.of(DataType.REDIS_CACHE))
            );
            
            for (Set<DataType> dataTypes : dataTypeCombinations) {
                // Given
                DataCleanupConfigDTO config = DataCleanupConfigDTO.builder()
                        .dataTypes(dataTypes)
                        .build();
                
                DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID, config);

                // When
                rpcService.cleanupDeviceDataAsync(request);

                // Then
                verify(deviceDataCleanupService).cleanupDeviceDataAsync(
                        eq(TEST_DEVICE_ID), configCaptor.capture());
                
                assertThat(configCaptor.getValue().getDataTypes())
                        .containsExactlyInAnyOrderElementsOf(dataTypes);
                
                // 重置mock以便下次验证
                reset(deviceDataCleanupService);
            }
        }
    }

    @Nested
    @DisplayName("异步调用验证测试")
    class AsynchronousCallVerificationTests {

        @Test
        @DisplayName("应该调用异步清理方法而不是同步方法")
        void should_call_async_cleanup_method_not_sync() {
            // Given
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID);

            // When
            rpcService.cleanupDeviceDataAsync(request);

            // Then - 验证调用的是异步方法
            verify(deviceDataCleanupService).cleanupDeviceDataAsync(TEST_DEVICE_ID, null);
            
            // 确保没有调用其他方法（如果存在同步方法的话）
            verifyNoMoreInteractions(deviceDataCleanupService);
        }

        @Test
        @DisplayName("RPC调用应该立即返回不等待异步结果")
        void should_return_immediately_without_waiting_for_async_result() {
            // Given
            DeviceDataCleanupRequestDTO request = new DeviceDataCleanupRequestDTO(TEST_DEVICE_ID);
            
            // 模拟异步方法执行（实际上不会阻塞RPC调用）
            doNothing().when(deviceDataCleanupService).cleanupDeviceDataAsync(any(), any());

            // When - 记录执行时间
            long startTime = System.currentTimeMillis();
            RpcResult<Void> result = rpcService.cleanupDeviceDataAsync(request);
            long executionTime = System.currentTimeMillis() - startTime;

            // Then - 应该立即返回成功，执行时间很短
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(executionTime).isLessThan(100) // 执行时间应该很短
            );
        }
    }

    /**
     * 创建测试用的清理配置
     */
    private DataCleanupConfigDTO createTestCleanupConfig() {
        Set<DataType> dataTypes = new HashSet<>(Arrays.asList(DataType.STATUS_REPORT, DataType.TERMINAL_LOG));
        return DataCleanupConfigDTO.builder()
                .mode(CleanupMode.INCLUDE)
                .dataTypes(dataTypes)
                .build();
    }

    /**
     * 生成指定数量的设备ID列表
     */
    private List<Long> generateDeviceIds(int count) {
        List<Long> deviceIds = new java.util.ArrayList<>();
        for (long i = 1; i <= count; i++) {
            deviceIds.add(i);
        }
        return deviceIds;
    }
}