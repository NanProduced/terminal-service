package com.colorlight.terminal.infrastructure.persistence.mysql.converter;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TerminalAccountConverter单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：设备账号领域对象与数据对象转换
 * 2. 字段映射：account↔accountName, accountStatus↔status, password↔passwordHash
 * 3. 自定义转换：状态枚举与Byte值之间的双向转换
 * 4. 忽略字段：toDO时忽略createTime、updateTime、version（由数据库管理）
 * 5. 异常处理：状态转换中的非法值处理
 * <p>
 * 测试策略：
 * - 正常转换场景测试（DO→Domain、Domain→DO）
 * - 空值处理测试
 * - 状态枚举转换边界测试
 * - 非法状态值异常测试
 * 
 * @author Generated Test
 */
@DisplayName("TerminalAccountConverter单元测试")
class TerminalAccountConverterTest {

    private TerminalAccountConverter converter;
    
    @BeforeEach
    void setUp() {
        converter = Mappers.getMapper(TerminalAccountConverter.class);
    }

    @Test
    @DisplayName("DO转换为领域对象 - 正常场景")
    void toDomain_Success() {
        // Given: 准备TerminalAccountDO
        TerminalAccountDO accountDO = new TerminalAccountDO();
        accountDO.setDeviceId(123456L);
        accountDO.setAccount("testDevice001");
        accountDO.setPassword("encodedPassword123");
        accountDO.setAccountStatus((byte) 1); // 对应DISABLE状态

        // When: 转换为领域对象
        TerminalAccount terminalAccount = converter.toDomain(accountDO);

        // Then: 验证转换结果
        assertNotNull(terminalAccount);
        assertEquals(123456L, terminalAccount.getDeviceId());
        assertEquals("testDevice001", terminalAccount.getAccountName());
        assertEquals("encodedPassword123", terminalAccount.getPasswordHash());
        assertEquals(TerminalAccountStatus.DISABLE, terminalAccount.getStatus());
    }

    @Test
    @DisplayName("DO转换为领域对象 - null对象处理")
    void toDomain_NullInput() {
        // When: 传入null
        TerminalAccount result = converter.toDomain(null);

        // Then: 返回null
        assertNull(result);
    }

    @Test
    @DisplayName("DO转换为领域对象 - null状态处理")
    void toDomain_NullStatus() {
        // Given: 状态为null的DO
        TerminalAccountDO accountDO = new TerminalAccountDO();
        accountDO.setDeviceId(123456L);
        accountDO.setAccount("testDevice001");
        accountDO.setPassword("encodedPassword123");
        accountDO.setAccountStatus(null);

        // When: 转换为领域对象
        TerminalAccount terminalAccount = converter.toDomain(accountDO);

        // Then: 状态应为null
        assertNotNull(terminalAccount);
        assertNull(terminalAccount.getStatus());
    }

    @Test
    @DisplayName("领域对象转换为DO - 正常场景")
    void toDO_Success() {
        // Given: 准备TerminalAccount
        TerminalAccount terminalAccount = TerminalAccount.builder()
                .deviceId(789012L)
                .accountName("device002")
                .passwordHash("hashedPassword456")
                .status(TerminalAccountStatus.DISABLE)
                .build();

        // When: 转换为DO
        TerminalAccountDO accountDO = converter.toDO(terminalAccount);

        // Then: 验证转换结果
        assertNotNull(accountDO);
        assertEquals(789012L, accountDO.getDeviceId());
        assertEquals("device002", accountDO.getAccount());
        assertEquals("hashedPassword456", accountDO.getPassword());
        assertEquals((byte) 1, accountDO.getAccountStatus()); // DISABLE对应1
        
        // 验证忽略字段为null（由数据库管理）
        assertNull(accountDO.getCreateTime());
        assertNull(accountDO.getUpdateTime());
        assertNull(accountDO.getVersion());
    }

    @Test
    @DisplayName("领域对象转换为DO - null对象处理")
    void toDO_NullInput() {
        // When: 传入null
        TerminalAccountDO result = converter.toDO(null);

        // Then: 返回null
        assertNull(result);
    }

    @Test
    @DisplayName("领域对象转换为DO - null状态处理")
    void toDO_NullStatus() {
        // Given: 状态为null的领域对象
        TerminalAccount terminalAccount = TerminalAccount.builder()
                .deviceId(789012L)
                .accountName("device002")
                .passwordHash("hashedPassword456")
                .status(null)
                .build();

        // When: 转换为DO
        TerminalAccountDO accountDO = converter.toDO(terminalAccount);

        // Then: 状态应为null
        assertNotNull(accountDO);
        assertNull(accountDO.getAccountStatus());
    }

    @Test
    @DisplayName("字节转换为状态枚举 - 所有有效状态")
    void byteToStatus_AllValidStatuses() {
        // 测试所有有效状态值
        for (TerminalAccountStatus status : TerminalAccountStatus.values()) {
            // Given: 状态对应的byte值
            Byte statusByte = status.getStatus().byteValue();

            // When: 转换为枚举
            TerminalAccountStatus result = converter.byteToStatus(statusByte);

            // Then: 应该匹配对应的状态
            assertEquals(status, result);
        }
    }

    @Test
    @DisplayName("字节转换为状态枚举 - null值处理")
    void byteToStatus_NullInput() {
        // When: 传入null
        TerminalAccountStatus result = converter.byteToStatus(null);

        // Then: 返回null
        assertNull(result);
    }

    @Test
    @DisplayName("字节转换为状态枚举 - 非法状态值异常")
    void byteToStatus_InvalidStatus() {
        // Given: 不存在的状态值
        Byte invalidStatus = (byte) 99;

        // When & Then: 应该抛出IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.byteToStatus(invalidStatus)
        );
        
        assertTrue(exception.getMessage().contains("未知的账号状态"));
        assertTrue(exception.getMessage().contains("99"));
    }

    @Test
    @DisplayName("状态枚举转换为字节 - 所有有效状态")
    void statusToByte_AllValidStatuses() {
        // 测试所有状态枚举
        for (TerminalAccountStatus status : TerminalAccountStatus.values()) {
            // When: 转换为byte
            Byte result = converter.statusToByte(status);

            // Then: 应该匹配状态的byte值
            assertEquals(status.getStatus().byteValue(), result.byteValue());
        }
    }

    @Test
    @DisplayName("状态枚举转换为字节 - null值处理")
    void statusToByte_NullInput() {
        // When: 传入null
        Byte result = converter.statusToByte(null);

        // Then: 返回null
        assertNull(result);
    }

    @Test
    @DisplayName("双向转换一致性验证")
    void bidirectionalConversion_Consistency() {
        // Given: 完整的TerminalAccountDO
        TerminalAccountDO originalDO = new TerminalAccountDO();
        originalDO.setDeviceId(111111L);
        originalDO.setAccount("consistencyTest");
        originalDO.setPassword("testPassword");
        originalDO.setAccountStatus((byte) 1);

        // When: DO → Domain → DO
        TerminalAccount domain = converter.toDomain(originalDO);
        TerminalAccountDO convertedDO = converter.toDO(domain);

        // Then: 关键字段应保持一致（忽略时间和版本字段）
        assertEquals(originalDO.getDeviceId(), convertedDO.getDeviceId());
        assertEquals(originalDO.getAccount(), convertedDO.getAccount());
        assertEquals(originalDO.getPassword(), convertedDO.getPassword());
        assertEquals(originalDO.getAccountStatus(), convertedDO.getAccountStatus());
    }
}