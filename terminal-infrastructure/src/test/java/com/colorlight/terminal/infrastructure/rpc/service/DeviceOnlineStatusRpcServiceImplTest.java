package com.colorlight.terminal.infrastructure.rpc.service;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.port.inbound.status.DeviceOnlineStatusUseCase;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.rpc.dto.RpcResult;
import com.colorlight.terminal.rpc.dto.request.BatchDeviceStatusRequestDTO;
import com.colorlight.terminal.rpc.dto.result.BatchDeviceStatusResultDTO;
import com.colorlight.terminal.rpc.dto.status.DeviceOnlineStatusDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeviceOnlineStatusRpcServiceImpl 单元测试
 * 
 * <p>测试策略：</p>
 * <ul>
 *   <li>重点验证业务逻辑正确性，不依赖日志内容</li>
 *   <li>验证参数校验和错误处理</li>
 *   <li>验证数据转换的准确性</li>
 *   <li>验证批量查询的性能限制</li>
 *   <li>验证异常处理和错误码映射</li>
 * </ul>
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备在线状态RPC服务测试")
class DeviceOnlineStatusRpcServiceImplTest {

    @Mock
    private DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;

    @InjectMocks
    private DeviceOnlineStatusRpcServiceImpl rpcService;

    // 测试常量
    private static final Long TEST_DEVICE_ID = 10001L;
    private static final String TEST_CLIENT_IP = "192.168.1.100";

    @Nested
    @DisplayName("单设备在线状态查询测试")
    class SingleDeviceOnlineStatusTests {

        @Test
        @DisplayName("应该成功查询设备在线状态")
        void should_successfully_check_device_online_status() {
            // Given
            when(deviceOnlineStatusUseCase.isDeviceOnline(TEST_DEVICE_ID)).thenReturn(true);

            // When
            RpcResult<Boolean> result = rpcService.isDeviceOnline(TEST_DEVICE_ID);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData()).isTrue()
            );
            verify(deviceOnlineStatusUseCase).isDeviceOnline(TEST_DEVICE_ID);
        }

        @Test
        @DisplayName("应该正确返回设备离线状态")
        void should_correctly_return_device_offline_status() {
            // Given
            when(deviceOnlineStatusUseCase.isDeviceOnline(TEST_DEVICE_ID)).thenReturn(false);

            // When
            RpcResult<Boolean> result = rpcService.isDeviceOnline(TEST_DEVICE_ID);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData()).isFalse()
            );
        }

        @Test
        @DisplayName("应该拒绝空设备ID参数")
        void should_reject_null_device_id() {
            // When
            RpcResult<Boolean> result = rpcService.isDeviceOnline(null);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("设备ID不能为空")
            );
        }

        @Test
        @DisplayName("应该处理业务层异常")
        void should_handle_business_layer_exception() {
            // Given
            when(deviceOnlineStatusUseCase.isDeviceOnline(TEST_DEVICE_ID))
                    .thenThrow(new RuntimeException("Redis连接失败"));

            // When
            RpcResult<Boolean> result = rpcService.isDeviceOnline(TEST_DEVICE_ID);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("系统错误")
            );
        }
    }

    @Nested
    @DisplayName("设备状态详情查询测试")
    class DeviceStatusDetailTests {

        @Test
        @DisplayName("应该成功获取设备状态详情")
        void should_successfully_get_device_status_detail() {
            // Given
            DeviceOnlineStatus mockStatus = createMockDeviceOnlineStatus();
            when(deviceOnlineStatusUseCase.getDeviceStatus(TEST_DEVICE_ID)).thenReturn(Optional.of(mockStatus));

            // When
            RpcResult<DeviceOnlineStatusDTO> result = rpcService.getDeviceStatus(TEST_DEVICE_ID);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData()).isNotNull(),
                    () -> assertThat(result.getData().getDeviceId()).isEqualTo(TEST_DEVICE_ID),
                    () -> assertThat(result.getData().getStatus()).isEqualTo(OnlineStatus.ONLINE.name()),
                    () -> assertThat(result.getData().getLastReportSource()).isEqualTo(ReportSource.WEBSOCKET.name()),
                    () -> assertThat(result.getData().getOnline()).isTrue()
            );
        }

        @Test
        @DisplayName("应该正确转换枚举类型")
        void should_correctly_convert_enum_types() {
            // Given - 创建离线状态的设备
            DeviceOnlineStatus offlineStatus = DeviceOnlineStatus.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .status(OnlineStatus.OFFLINE)
                    .lastReportSource(ReportSource.WEBSOCKET)
                    .lastReportTime(System.currentTimeMillis())
                    .clientIp(TEST_CLIENT_IP)
                    .build();
            
            when(deviceOnlineStatusUseCase.getDeviceStatus(TEST_DEVICE_ID)).thenReturn(Optional.of(offlineStatus));

            // When
            RpcResult<DeviceOnlineStatusDTO> result = rpcService.getDeviceStatus(TEST_DEVICE_ID);

            // Then - 验证枚举转换
            DeviceOnlineStatusDTO statusDTO = result.getData();
            assertAll(
                    () -> assertThat(statusDTO.getStatus()).isEqualTo("OFFLINE"),
                    () -> assertThat(statusDTO.getLastReportSource()).isEqualTo("WEBSOCKET"),
                    () -> assertThat(statusDTO.getOnline()).isFalse()
            );
        }

        @Test
        @DisplayName("应该处理空的lastReportSource")
        void should_handle_null_last_report_source() {
            // Given - lastReportSource为null
            DeviceOnlineStatus statusWithNullSource = DeviceOnlineStatus.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .status(OnlineStatus.ONLINE)
                    .lastReportSource(null)
                    .lastReportTime(System.currentTimeMillis())
                    .clientIp(TEST_CLIENT_IP)
                    .build();
            
            when(deviceOnlineStatusUseCase.getDeviceStatus(TEST_DEVICE_ID)).thenReturn(Optional.of(statusWithNullSource));

            // When
            RpcResult<DeviceOnlineStatusDTO> result = rpcService.getDeviceStatus(TEST_DEVICE_ID);

            // Then
            assertThat(result.getData().getLastReportSource()).isNull();
        }

        @Test
        @DisplayName("应该拒绝空设备ID参数")
        void should_reject_null_device_id_for_status_detail() {
            // When
            RpcResult<DeviceOnlineStatusDTO> result = rpcService.getDeviceStatus(null);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode())
            );
        }
    }

    @Nested
    @DisplayName("批量设备状态查询测试")
    class BatchDeviceStatusQueryTests {

        @Test
        @DisplayName("应该成功执行批量在线状态查询")
        void should_successfully_batch_query_online_status() {
            // Given
            List<Long> deviceIds = Arrays.asList(10001L, 10002L, 10003L);
            Map<Long, Boolean> onlineStatusMap = Map.of(
                    10001L, true,
                    10002L, false,
                    10003L, true
            );
            
            BatchDeviceStatusRequestDTO request = new BatchDeviceStatusRequestDTO(deviceIds, false);
            when(deviceOnlineStatusUseCase.batchCheckOnline(deviceIds)).thenReturn(onlineStatusMap);

            // When
            RpcResult<BatchDeviceStatusResultDTO> result = rpcService.batchQueryDeviceStatus(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData().getOnlineStatusMap()).isEqualTo(onlineStatusMap),
                    () -> assertThat(result.getData().getStatistics().getRequestedCount()).isEqualTo(3),
                    () -> assertThat(result.getData().getStatistics().getOnlineCount()).isEqualTo(2),
                    () -> assertThat(result.getData().getStatistics().getOfflineCount()).isEqualTo(1),
                    () -> assertThat(result.getData().getDetailStatusMap()).isNull() // 不包含详情
            );
        }

        @Test
        @DisplayName("应该成功执行批量状态查询包含详情")
        void should_successfully_batch_query_with_details() {
            // Given
            List<Long> deviceIds = Arrays.asList(10001L, 10002L);
            Map<Long, Boolean> onlineStatusMap = Map.of(10001L, true, 10002L, false);
            Map<Long, DeviceOnlineStatus> detailMap = Map.of(
                    10001L, createMockDeviceOnlineStatus(),
                    10002L, createMockDeviceOnlineStatus()
            );
            
            BatchDeviceStatusRequestDTO request = new BatchDeviceStatusRequestDTO(deviceIds, true);
            when(deviceOnlineStatusUseCase.batchCheckOnline(deviceIds)).thenReturn(onlineStatusMap);
            when(deviceOnlineStatusUseCase.batchGetDeviceStatus(deviceIds)).thenReturn(detailMap);

            // When
            RpcResult<BatchDeviceStatusResultDTO> result = rpcService.batchQueryDeviceStatus(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData().getDetailStatusMap()).isNotNull(),
                    () -> assertThat(result.getData().getDetailStatusMap()).hasSize(2),
                    () -> assertThat(result.getData().getDetailStatusMap().get(10001L).getDeviceId()).isEqualTo(10001L)
            );
            
            verify(deviceOnlineStatusUseCase).batchGetDeviceStatus(deviceIds);
        }

        @Test
        @DisplayName("应该限制批量查询设备数量")
        void should_limit_batch_query_device_count() {
            // Given - 超过1000个设备
            List<Long> tooManyDeviceIds = new ArrayList<>();
            for (long i = 1; i <= 1500; i++) {
                tooManyDeviceIds.add(i);
            }
            
            // Mock只返回前1000个设备的结果
            Map<Long, Boolean> limitedOnlineStatusMap = new HashMap<>();
            for (long i = 1; i <= 1000; i++) {
                limitedOnlineStatusMap.put(i, true);
            }
            
            BatchDeviceStatusRequestDTO request = new BatchDeviceStatusRequestDTO(tooManyDeviceIds, false);
            when(deviceOnlineStatusUseCase.batchCheckOnline(any())).thenReturn(limitedOnlineStatusMap);

            // When
            RpcResult<BatchDeviceStatusResultDTO> result = rpcService.batchQueryDeviceStatus(request);

            // Then - 应该成功但只处理前1000个
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData().getStatistics().getRequestedCount()).isEqualTo(1000),
                    () -> assertThat(result.getData().getStatistics().getOnlineCount()).isEqualTo(1000)
            );
        }

        @Test
        @DisplayName("应该拒绝空设备ID列表")
        void should_reject_null_device_ids_list() {
            // When
            BatchDeviceStatusRequestDTO request = new BatchDeviceStatusRequestDTO(null, false);
            RpcResult<BatchDeviceStatusResultDTO> result = rpcService.batchQueryDeviceStatus(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("设备ID列表不能为空")
            );
        }

        @Test
        @DisplayName("应该拒绝空的设备ID列表")
        void should_reject_empty_device_ids_list() {
            // When
            BatchDeviceStatusRequestDTO request = new BatchDeviceStatusRequestDTO(Collections.emptyList(), false);
            RpcResult<BatchDeviceStatusResultDTO> result = rpcService.batchQueryDeviceStatus(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER.getCode())
            );
        }

        @Test
        @DisplayName("应该正确计算统计信息")
        void should_correctly_calculate_statistics() {
            // Given - 混合在线离线状态
            List<Long> deviceIds = Arrays.asList(10001L, 10002L, 10003L, 10004L, 10005L);
            Map<Long, Boolean> onlineStatusMap = Map.of(
                    10001L, true,
                    10002L, false,
                    10003L, true,
                    10004L, false,
                    10005L, false
            );
            
            BatchDeviceStatusRequestDTO request = new BatchDeviceStatusRequestDTO(deviceIds, false);
            when(deviceOnlineStatusUseCase.batchCheckOnline(deviceIds)).thenReturn(onlineStatusMap);

            // When
            RpcResult<BatchDeviceStatusResultDTO> result = rpcService.batchQueryDeviceStatus(request);

            // Then - 验证统计信息
            BatchDeviceStatusResultDTO resultData = result.getData();
            assertAll(
                    () -> assertThat(resultData.getStatistics().getRequestedCount()).isEqualTo(5),
                    () -> assertThat(resultData.getStatistics().getOnlineCount()).isEqualTo(2),
                    () -> assertThat(resultData.getStatistics().getOfflineCount()).isEqualTo(3),
                    () -> assertThat(resultData.getStatistics().getQueryTimeMs()).isGreaterThan(0L) // 查询时间应该大于0
            );
        }

        @Test
        @DisplayName("应该处理业务层异常")
        void should_handle_business_layer_exception_in_batch_query() {
            // Given
            List<Long> deviceIds = Arrays.asList(10001L, 10002L);
            BatchDeviceStatusRequestDTO request = new BatchDeviceStatusRequestDTO(deviceIds, false);
            when(deviceOnlineStatusUseCase.batchCheckOnline(anyList()))
                    .thenThrow(new RuntimeException("Redis故障"));

            // When
            RpcResult<BatchDeviceStatusResultDTO> result = rpcService.batchQueryDeviceStatus(request);

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode()),
                    () -> assertThat(result.getErrorMessage()).isEqualTo("系统错误")
            );
        }
    }

    @Nested
    @DisplayName("在线设备统计测试")
    class OnlineDeviceStatisticsTests {

        @Test
        @DisplayName("应该成功获取在线设备数量")
        void should_successfully_get_online_device_count() {
            // Given
            int expectedCount = 150;
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(expectedCount);

            // When
            RpcResult<Integer> result = rpcService.getOnlineDeviceCount();

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData()).isEqualTo(expectedCount)
            );
        }

        @Test
        @DisplayName("应该处理零在线设备的情况")
        void should_handle_zero_online_devices() {
            // Given
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount()).thenReturn(0);

            // When
            RpcResult<Integer> result = rpcService.getOnlineDeviceCount();

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isTrue(),
                    () -> assertThat(result.getData()).isZero()
            );
        }

        @Test
        @DisplayName("应该处理获取在线设备数量时的异常")
        void should_handle_exception_when_getting_online_device_count() {
            // Given
            when(deviceOnlineStatusUseCase.getOnlineDeviceCount())
                    .thenThrow(new RuntimeException("统计服务不可用"));

            // When
            RpcResult<Integer> result = rpcService.getOnlineDeviceCount();

            // Then
            assertAll(
                    () -> assertThat(result.isSuccess()).isFalse(),
                    () -> assertThat(result.getErrorCode()).isEqualTo(CommonErrorCode.SYSTEM_ERROR.getCode())
            );
        }
    }

    @Nested
    @DisplayName("数据转换准确性测试")
    class DataConversionAccuracyTests {

        @Test
        @DisplayName("应该正确转换所有设备状态枚举")
        void should_correctly_convert_all_device_status_enums() {
            // 测试所有可能的设备状态枚举值
            OnlineStatus[] allStatuses = OnlineStatus.values();
            
            for (OnlineStatus status : allStatuses) {
                // Given
                DeviceOnlineStatus mockStatus = DeviceOnlineStatus.builder()
                        .deviceId(TEST_DEVICE_ID)
                        .status(status)
                        .lastReportTime(System.currentTimeMillis())
                        .clientIp(TEST_CLIENT_IP)
                        .build();
                
                when(deviceOnlineStatusUseCase.getDeviceStatus(TEST_DEVICE_ID)).thenReturn(Optional.of(mockStatus));

                // When
                RpcResult<DeviceOnlineStatusDTO> result = rpcService.getDeviceStatus(TEST_DEVICE_ID);

                // Then - 验证状态转换正确
                assertThat(result.getData().getStatus()).isEqualTo(status.name());
            }
        }

        @Test
        @DisplayName("应该正确转换所有上报源枚举")
        void should_correctly_convert_all_report_source_enums() {
            // 测试所有可能的上报源枚举值
            ReportSource[] allSources = ReportSource.values();
            
            for (ReportSource source : allSources) {
                // Given
                DeviceOnlineStatus mockStatus = DeviceOnlineStatus.builder()
                        .deviceId(TEST_DEVICE_ID)
                        .status(OnlineStatus.ONLINE)
                        .lastReportSource(source)
                        .lastReportTime(System.currentTimeMillis())
                        .clientIp(TEST_CLIENT_IP)
                        .build();
                
                when(deviceOnlineStatusUseCase.getDeviceStatus(TEST_DEVICE_ID)).thenReturn(Optional.of(mockStatus));

                // When
                RpcResult<DeviceOnlineStatusDTO> result = rpcService.getDeviceStatus(TEST_DEVICE_ID);

                // Then - 验证上报源转换正确
                assertThat(result.getData().getLastReportSource()).isEqualTo(source.name());
            }
        }
    }

    /**
     * 创建测试用的DeviceOnlineStatus Mock对象
     */
    private DeviceOnlineStatus createMockDeviceOnlineStatus() {
        return DeviceOnlineStatus.builder()
                .deviceId(TEST_DEVICE_ID)
                .status(OnlineStatus.ONLINE)
                .lastReportSource(ReportSource.WEBSOCKET)
                .lastReportTime(System.currentTimeMillis())
                .clientIp(TEST_CLIENT_IP)
                .build();
    }
}