package com.colorlight.terminal.infrastructure.cleanup.cleaner;

import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceScreenshotRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceScreenshotRecordMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceSwitchOnRecordMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.TerminalAccountMapper;
import com.colorlight.terminal.infrastructure.storage.minio.service.MinioScreenshotStorageService;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MysqlDataStoreCleaner 单元测试
 * 测试MySQL数据存储清理器的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MySQL数据存储清理器测试")
class MysqlDataStoreCleanerTest {

    @Mock
    private DeviceScreenshotRecordMapper screenshotRecordMapper;
    
    @Mock
    private TerminalAccountMapper terminalAccountMapper;
    
    @Mock
    private DeviceSwitchOnRecordMapper deviceSwitchOnRecordMapper;
    
    @Mock
    private MinioScreenshotStorageService minioService;
    
    private MysqlDataStoreCleaner cleaner;
    
    // 测试数据
    private static final Long TEST_DEVICE_ID = 12345L;
    
    @BeforeEach
    void setUp() {
        cleaner = new MysqlDataStoreCleaner(screenshotRecordMapper, terminalAccountMapper, deviceSwitchOnRecordMapper, minioService);
    }
    
    @Nested
    @DisplayName("基础功能测试")
    class BasicFunctionTest {
        
        @Test
        @DisplayName("应该返回正确的存储类型")
        void should_return_correct_storage_type() {
            // When & Then
            assertThat(cleaner.getStorageType()).isEqualTo("MySQL");
        }
        
        @Test
        @DisplayName("应该支持MySQL数据类型")
        void should_support_mysql_data_types() {
            // Given - MySQL数据类型
            DataType[] mysqlTypes = {
                DataType.SCREENSHOT_RECORD,
                DataType.SWITCH_RECORD,
                DataType.DEVICE_ACCOUNT
            };
            
            // When & Then - 验证支持MySQL数据类型
            for (DataType dataType : mysqlTypes) {
                assertThat(cleaner.supports(dataType))
                    .as("应该支持MySQL数据类型: " + dataType)
                    .isTrue();
            }
        }
        
        @Test
        @DisplayName("应该不支持非MySQL数据类型")
        void should_not_support_non_mysql_data_types() {
            // Given - 非MySQL数据类型
            DataType[] nonMysqlTypes = {
                DataType.GPS_RECORD,
                DataType.TERMINAL_LOG,
                DataType.REDIS_CACHE
            };
            
            // When & Then - 验证不支持非MySQL数据类型
            for (DataType dataType : nonMysqlTypes) {
                assertThat(cleaner.supports(dataType))
                    .as("不应该支持非MySQL数据类型: " + dataType)
                    .isFalse();
            }
        }
    }
    
    @Nested
    @DisplayName("设备账号清理测试")
    class DeviceAccountCleanupTest {
        
        @Test
        @DisplayName("应该成功清理存在的设备账号")
        void should_cleanup_existing_device_account_successfully() {
            // Given - 存在设备账号
            TerminalAccountDO existingAccount = createTestTerminalAccount();
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(existingAccount);
            when(terminalAccountMapper.deleteById(any(Long.class))).thenReturn(1);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.DEVICE_ACCOUNT));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(1);
            verify(terminalAccountMapper).selectById(TEST_DEVICE_ID);
            verify(terminalAccountMapper).deleteById(any(Long.class));
        }
        
        @Test
        @DisplayName("当设备账号不存在时应该返回0")
        void should_return_zero_when_device_account_not_exists() {
            // Given - 设备账号不存在
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(null);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.DEVICE_ACCOUNT));
            
            // Then - 验证清理结果
            assertThat(result).isZero();
            verify(terminalAccountMapper).selectById(TEST_DEVICE_ID);
            verify(terminalAccountMapper, never()).deleteById(any(Long.class));
        }
        
        @Test
        @DisplayName("设备账号清理失败时应该抛出异常")
        void should_throw_exception_when_device_account_cleanup_fails() {
            // Given - 设备账号存在，但删除失败
            TerminalAccountDO existingAccount = createTestTerminalAccount();
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(existingAccount);
            when(terminalAccountMapper.deleteById(any(Long.class)))
                .thenThrow(new RuntimeException("数据库连接失败"));
            
            // When & Then - 执行清理操作应该抛出异常
            EnumSet<DataType> dataTypes = EnumSet.of(DataType.DEVICE_ACCOUNT);
            assertThatThrownBy(() -> cleaner.cleanup(TEST_DEVICE_ID, dataTypes))
                .isInstanceOf(BusinessException.class)
                .hasMessage("设备账号信息清理失败")
                .hasCauseInstanceOf(RuntimeException.class);
        }
        
        @Test
        @DisplayName("设备账号应该始终被清理，不受数据类型配置影响")
        void should_always_cleanup_device_account_regardless_of_data_types() {
            // Given - 设备账号存在，但数据类型中不包含DEVICE_ACCOUNT
            TerminalAccountDO existingAccount = createTestTerminalAccount();
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(existingAccount);
            when(terminalAccountMapper.deleteById(any(Long.class))).thenReturn(1);
            
            // When - 执行清理操作，数据类型只包含SCREENSHOT_RECORD
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.SCREENSHOT_RECORD));
            
            // Then - 验证设备账号仍然被清理
            assertThat(result).isEqualTo(1);
            verify(terminalAccountMapper).selectById(TEST_DEVICE_ID);
            verify(terminalAccountMapper).deleteById(any(Long.class));
        }
    }
    
    @Nested
    @DisplayName("截图记录清理测试")
    class ScreenshotRecordCleanupTest {
        
        @Test
        @DisplayName("应该成功清理截图记录和MinIO文件")
        void should_cleanup_screenshot_records_and_minio_files_successfully() {
            // Given - 存在截图记录
            List<DeviceScreenshotRecordDO> screenshots = Arrays.asList(
                createTestScreenshotRecord("screenshot1.jpg"),
                createTestScreenshotRecord("screenshot2.jpg"),
                createTestScreenshotRecord("screenshot3.jpg")
            );
            
            when(screenshotRecordMapper.selectList(any())).thenReturn(screenshots);
            when(screenshotRecordMapper.delete(any())).thenReturn(3);
            
            // Mock设备账号不存在，避免影响测试结果
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(null);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.SCREENSHOT_RECORD));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(3); // 只统计数据库删除的记录数
            
            // 验证MinIO文件删除
            verify(minioService, times(3)).deleteObject(anyString());
            verify(minioService).deleteObject("screenshot1.jpg");
            verify(minioService).deleteObject("screenshot2.jpg");
            verify(minioService).deleteObject("screenshot3.jpg");
            
            // 验证数据库记录删除
            verify(screenshotRecordMapper).delete(any());
        }
        
        @Test
        @DisplayName("当截图记录不存在时应该返回0")
        void should_return_zero_when_no_screenshot_records_exist() {
            // Given - 无截图记录
            when(screenshotRecordMapper.selectList(any()))
                .thenReturn(Collections.emptyList());
            
            // Mock设备账号不存在
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(null);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.SCREENSHOT_RECORD));
            
            // Then - 验证清理结果
            assertThat(result).isZero();
            verify(minioService, never()).deleteObject(any());
            verify(screenshotRecordMapper, never()).delete(any());
        }
        
        @Test
        @DisplayName("MinIO文件删除失败时应该继续删除数据库记录")
        void should_continue_cleanup_when_minio_deletion_fails() {
            // Given - 存在截图记录，但MinIO删除失败
            List<DeviceScreenshotRecordDO> screenshots = Arrays.asList(
                createTestScreenshotRecord("failed_screenshot.jpg"),
                createTestScreenshotRecord("success_screenshot.jpg")
            );
            
            when(screenshotRecordMapper.selectList(any())).thenReturn(screenshots);
            when(screenshotRecordMapper.delete(any())).thenReturn(2);
            
            // Mock MinIO删除：第一个失败，第二个成功
            doThrow(new RuntimeException("MinIO连接失败")).when(minioService).deleteObject("failed_screenshot.jpg");
            doNothing().when(minioService).deleteObject("success_screenshot.jpg");
            
            // Mock设备账号不存在
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(null);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.SCREENSHOT_RECORD));
            
            // Then - 验证仍然删除了数据库记录
            assertThat(result).isEqualTo(2);
            verify(minioService).deleteObject("failed_screenshot.jpg");
            verify(minioService).deleteObject("success_screenshot.jpg");
            verify(screenshotRecordMapper).delete(any());
        }
        
        @Test
        @DisplayName("应该处理空的objectKey")
        void should_handle_empty_object_key() {
            // Given - 存在截图记录，但objectKey为空
            List<DeviceScreenshotRecordDO> screenshots = Arrays.asList(
                createTestScreenshotRecord(null),
                createTestScreenshotRecord(""),
                createTestScreenshotRecord("  "),
                createTestScreenshotRecord("valid_screenshot.jpg")
            );
            
            when(screenshotRecordMapper.selectList(any())).thenReturn(screenshots);
            when(screenshotRecordMapper.delete(any())).thenReturn(4);
            
            // Mock设备账号不存在
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(null);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.SCREENSHOT_RECORD));
            
            // Then - 验证只删除有效的MinIO文件
            assertThat(result).isEqualTo(4);
            verify(minioService, times(1)).deleteObject("valid_screenshot.jpg");
            verify(minioService, never()).deleteObject(isNull());
            verify(minioService, never()).deleteObject("");
            verify(minioService, never()).deleteObject("  ");
        }
        
        @Test
        @DisplayName("数据库删除失败时应该抛出异常")
        void should_throw_exception_when_database_deletion_fails() {
            // Given - 存在截图记录，但数据库删除失败
            List<DeviceScreenshotRecordDO> screenshots = List.of(
                    createTestScreenshotRecord("test_screenshot.jpg")
            );
            
            when(screenshotRecordMapper.selectList(any())).thenReturn(screenshots);
            when(screenshotRecordMapper.delete(any()))
                .thenThrow(new RuntimeException("数据库删除失败"));
            
            // Mock设备账号不存在
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(null);
            
            // When & Then - 执行清理操作应该抛出异常
            EnumSet<DataType> enumSet = EnumSet.of(DataType.SCREENSHOT_RECORD);
            assertThatThrownBy(() -> cleaner.cleanup(TEST_DEVICE_ID, enumSet))
                .isInstanceOf(BusinessException.class)
                .hasMessage("截图记录清理失败")
                .hasCauseInstanceOf(RuntimeException.class);
            
            // 验证仍然尝试删除MinIO文件
            verify(minioService).deleteObject("test_screenshot.jpg");
        }
    }
    
    @Nested
    @DisplayName("开机记录清理测试")
    class SwitchRecordCleanupTest {
        
        @Test
        @DisplayName("开机记录清理应该调用mapper但返回0")
        void should_call_mapper_but_return_zero_for_switch_record_cleanup() {
            // Given - Mock设备账号不存在，开机记录清理
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(null);
            when(deviceSwitchOnRecordMapper.delete(any())).thenReturn(5);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(DataType.SWITCH_RECORD));
            
            // Then - 验证清理结果（当前实现调用mapper但返回0）
            assertThat(result).isZero();
            verify(deviceSwitchOnRecordMapper).delete(any());
        }
        
        @Test
        @DisplayName("开机记录清理失败时应该抛出BusinessException")
        void should_throw_business_exception_when_switch_record_cleanup_fails() {
            // Given - Mock设备账号不存在，开机记录清理失败
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(null);
            when(deviceSwitchOnRecordMapper.delete(any()))
                .thenThrow(new RuntimeException("数据库连接失败"));
            
            // When & Then - 执行清理操作应该抛出异常
            EnumSet<DataType> enumSet = EnumSet.of(DataType.SWITCH_RECORD);
            assertThatThrownBy(() -> cleaner.cleanup(TEST_DEVICE_ID, enumSet))
                .isInstanceOf(BusinessException.class)
                .hasMessage("开机记录清理失败")
                .hasCauseInstanceOf(RuntimeException.class);
            
            verify(deviceSwitchOnRecordMapper).delete(any());
        }
    }
    
    @Nested
    @DisplayName("综合清理测试")
    class ComprehensiveCleanupTest {
        
        @Test
        @DisplayName("应该清理多种数据类型")
        void should_cleanup_multiple_data_types() {
            // Given - 设置所有类型的测试数据
            // 设备账号
            TerminalAccountDO account = createTestTerminalAccount();
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(account);
            when(terminalAccountMapper.deleteById(any(Long.class))).thenReturn(1);
            
            // 截图记录
            List<DeviceScreenshotRecordDO> screenshots = Arrays.asList(
                createTestScreenshotRecord("test1.jpg"),
                createTestScreenshotRecord("test2.jpg")
            );
            when(screenshotRecordMapper.selectList(any())).thenReturn(screenshots);
            when(screenshotRecordMapper.delete(any())).thenReturn(2);
            
            // When - 执行清理操作
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(
                DataType.DEVICE_ACCOUNT,
                DataType.SCREENSHOT_RECORD,
                DataType.SWITCH_RECORD
            ));
            
            // Then - 验证清理结果
            assertThat(result).isEqualTo(3); // 账号1条 + 截图2条 + 开机0条
            
            // 验证设备账号清理
            verify(terminalAccountMapper).selectById(TEST_DEVICE_ID);
            verify(terminalAccountMapper).deleteById(any(Long.class));
            
            // 验证截图记录清理
            verify(screenshotRecordMapper).selectList(any());
            verify(screenshotRecordMapper).delete(any());
            verify(minioService, times(2)).deleteObject(anyString());
        }
        
        @Test
        @DisplayName("当数据类型不包含MySQL类型时应该只清理设备账号")
        void should_only_cleanup_device_account_when_no_mysql_types_specified() {
            // Given - 设备账号存在
            TerminalAccountDO account = createTestTerminalAccount();
            when(terminalAccountMapper.selectById(TEST_DEVICE_ID)).thenReturn(account);
            when(terminalAccountMapper.deleteById(any(Long.class))).thenReturn(1);
            
            // When - 执行清理操作，只包含非MySQL数据类型
            int result = cleaner.cleanup(TEST_DEVICE_ID, EnumSet.of(
                DataType.GPS_RECORD,
                DataType.TERMINAL_LOG,
                DataType.REDIS_CACHE
            ));
            
            // Then - 验证只清理了设备账号
            assertThat(result).isEqualTo(1);
            verify(terminalAccountMapper).selectById(TEST_DEVICE_ID);
            verify(terminalAccountMapper).deleteById(any(Long.class));
            
            // 验证没有清理其他数据
            verify(screenshotRecordMapper, never()).selectList(any());
            verify(screenshotRecordMapper, never()).delete(any());
            verify(minioService, never()).deleteObject(any());
        }
    }
    
    // ===================== 测试数据构建辅助方法 =====================
    
    /**
     * 创建测试用的终端账号
     */
    private TerminalAccountDO createTestTerminalAccount() {
        TerminalAccountDO account = new TerminalAccountDO();
        account.setDeviceId(TEST_DEVICE_ID);
        account.setAccount("device_" + TEST_DEVICE_ID);
        account.setPassword("$2a$10$encrypted_password");
        account.setAccountStatus((byte) 1);
        account.setFirstLoginTime(LocalDateTime.now().minusDays(7));
        account.setLastLoginTime(LocalDateTime.now());
        account.setLastLoginIp("192.168.1.100");
        account.setCreateTime(LocalDateTime.now().minusDays(30));
        account.setUpdateTime(LocalDateTime.now());
        return account;
    }
    
    /**
     * 创建测试用的截图记录
     */
    private DeviceScreenshotRecordDO createTestScreenshotRecord(String objectKey) {
        DeviceScreenshotRecordDO screenshotRecordDO = new DeviceScreenshotRecordDO();
        screenshotRecordDO.setDeviceId(TEST_DEVICE_ID);
        screenshotRecordDO.setObjectKey(objectKey);
        screenshotRecordDO.setUploadTime(LocalDateTime.now());
        screenshotRecordDO.setSize(1024L);
        screenshotRecordDO.setUpdateTime(LocalDateTime.now());
        return screenshotRecordDO;
    }
}