package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceSwitchOnRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceSwitchOnRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * MysqlDeviceSwitchOnRecordRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：保存设备开关机记录到MySQL数据库
 * 2. 数据转换：将设备ID和时间戳转换为DeviceSwitchOnRecordDO实体
 * 3. 异常处理：捕获数据库异常并转换为TechnicalException
 * 4. 时间管理：自动设置创建时间为当前时间
 * <p>
 * 测试策略：
 * - 正常保存记录场景测试
 * - 数据库异常处理测试
 * - 边界条件测试
 *
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MysqlDeviceSwitchOnRecordRepository单元测试")
class MysqlDeviceSwitchOnRecordRepositoryTest {

    @Mock
    private DeviceSwitchOnRecordMapper deviceSwitchOnRecordMapper;

    @InjectMocks
    private MysqlDeviceSwitchOnRecordRepository repository;

    private Long deviceId;
    private Long timestamp;

    @BeforeEach
    void setUp() {
        deviceId = 12345L;
        timestamp = System.currentTimeMillis();
    }

    @Nested
    @DisplayName("保存开关机记录测试")
    class SaveSwitchOnRecordTest {

        @Test
        @DisplayName("应该成功保存开关机记录")
        void should_save_switch_on_record_successfully() {
            // Given
            given(deviceSwitchOnRecordMapper.insert(any(DeviceSwitchOnRecordDO.class))).willReturn(1);

            // When
            assertDoesNotThrow(() -> {
                repository.saveSwitchOnRecord(deviceId, timestamp);
            });

            // Then
            then(deviceSwitchOnRecordMapper).should().insert(any(DeviceSwitchOnRecordDO.class));
        }

        @Test
        @DisplayName("应该在数据库异常时抛出TechnicalException")
        void should_throw_technical_exception_when_database_error_occurs() {
            // Given
            given(deviceSwitchOnRecordMapper.insert(any(DeviceSwitchOnRecordDO.class)))
                    .willThrow(new RuntimeException("Database error"));

            // When & Then
            TechnicalException exception = assertThrows(TechnicalException.class, () -> {
                repository.saveSwitchOnRecord(deviceId, timestamp);
            });

            // 验证异常码正确
            assertEquals(TechErrorCode.MYSQL_ERROR.getCode(), exception.getErrorCode());

            // 验证Mapper方法被调用
            then(deviceSwitchOnRecordMapper).should().insert(any(DeviceSwitchOnRecordDO.class));
        }

        @Test
        @DisplayName("应该正确处理null时间戳")
        void should_handle_null_timestamp() {
            // Given
            given(deviceSwitchOnRecordMapper.insert(any(DeviceSwitchOnRecordDO.class))).willReturn(1);

            // When & Then
            assertDoesNotThrow(() -> {
                repository.saveSwitchOnRecord(deviceId, null);
            });

            // Then
            then(deviceSwitchOnRecordMapper).should().insert(any(DeviceSwitchOnRecordDO.class));
        }

        @Test
        @DisplayName("应该正确处理负数设备ID")
        void should_handle_negative_device_id() {
            // Given
            Long negativeDeviceId = -123L;
            given(deviceSwitchOnRecordMapper.insert(any(DeviceSwitchOnRecordDO.class))).willReturn(1);

            // When & Then
            assertDoesNotThrow(() -> {
                repository.saveSwitchOnRecord(negativeDeviceId, timestamp);
            });

            // Then
            then(deviceSwitchOnRecordMapper).should().insert(any(DeviceSwitchOnRecordDO.class));
        }
    }
}