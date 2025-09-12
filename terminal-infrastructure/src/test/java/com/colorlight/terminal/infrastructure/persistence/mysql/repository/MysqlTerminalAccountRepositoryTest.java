package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.infrastructure.persistence.mysql.converter.TerminalAccountConverter;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.TerminalAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * MysqlTerminalAccountRepository单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：基于MySQL的终端账号CRUD操作
 * 2. 查询功能：按账号名查询、按ID查询、存在性检查
 * 3. 保存功能：新增/更新判断逻辑（基于deviceId是否为null）
 * 4. 登录时间更新：立即更新和批量更新两种模式
 * 5. 数据转换：使用TerminalAccountConverter进行DO与Domain转换
 * 6. 时间管理：自动设置createTime和updateTime
 * <p>
 * 测试策略：
 * - 查询操作的正常和边界场景
 * - 保存操作的新增/更新逻辑
 * - 登录时间更新的成功和异常场景
 * - 转换器和Mapper的交互验证
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MysqlTerminalAccountRepository单元测试")
class MysqlTerminalAccountRepositoryTest {

    @Mock
    private TerminalAccountMapper terminalAccountMapper;
    
    @Mock
    private TerminalAccountConverter terminalAccountConverter;
    
    @InjectMocks
    private MysqlTerminalAccountRepository mysqlTerminalAccountRepository;
    
    private TerminalAccountDO sampleDO;
    private TerminalAccount sampleDomain;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        sampleDO = new TerminalAccountDO();
        sampleDO.setDeviceId(12345L);
        sampleDO.setAccount("testDevice");
        sampleDO.setPassword("hashedPassword");
        sampleDO.setAccountStatus((byte) 1);
        
        sampleDomain = TerminalAccount.builder()
                .deviceId(12345L)
                .accountName("testDevice")
                .passwordHash("hashedPassword")
                .status(TerminalAccountStatus.DISABLE)
                .build();
        
        // 配置converter的默认行为（宽松模式）
        lenient().when(terminalAccountConverter.toDomain(any(TerminalAccountDO.class)))
                .thenReturn(sampleDomain);
        lenient().when(terminalAccountConverter.toDO(any(TerminalAccount.class)))
                .thenReturn(sampleDO);
    }

    @Test
    @DisplayName("按账号名查询终端账号 - 成功找到")
    void findTerminalAccountByName_Found() {
        // Given: 账号名存在
        String accountName = "testDevice";
        given(terminalAccountMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(sampleDO);
        given(terminalAccountConverter.toDomain(sampleDO))
                .willReturn(sampleDomain);

        // When: 按账号名查询
        TerminalAccount result = mysqlTerminalAccountRepository.findTerminalAccountByName(accountName);

        // Then: 验证结果和调用
        assertNotNull(result);
        assertEquals(sampleDomain, result);
        then(terminalAccountMapper).should().selectOne(any(LambdaQueryWrapper.class));
        then(terminalAccountConverter).should().toDomain(sampleDO);
    }

    @Test
    @DisplayName("按账号名查询终端账号 - 未找到")
    void findTerminalAccountByName_NotFound() {
        // Given: 账号名不存在
        String accountName = "nonExistentDevice";
        given(terminalAccountMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(null);
        given(terminalAccountConverter.toDomain(null))
                .willReturn(null);

        // When: 按账号名查询
        TerminalAccount result = mysqlTerminalAccountRepository.findTerminalAccountByName(accountName);

        // Then: 返回null
        assertNull(result);
        then(terminalAccountConverter).should().toDomain(null);
    }

    @Test
    @DisplayName("按ID查询终端账号 - 成功找到")
    void findTerminalAccountById_Found() {
        // Given: ID存在
        Long deviceId = 12345L;
        given(terminalAccountMapper.selectById(deviceId))
                .willReturn(sampleDO);
        given(terminalAccountConverter.toDomain(sampleDO))
                .willReturn(sampleDomain);

        // When: 按ID查询
        TerminalAccount result = mysqlTerminalAccountRepository.findTerminalAccountById(deviceId);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(sampleDomain, result);
        then(terminalAccountMapper).should().selectById(deviceId);
    }

    @Test
    @DisplayName("检查终端账号是否存在 - 存在")
    void ifExistTerminalAccount_Exists() {
        // Given: 账号存在
        String accountName = "testDevice";
        given(terminalAccountMapper.exists(any(LambdaQueryWrapper.class)))
                .willReturn(true);

        // When: 检查存在性
        boolean result = mysqlTerminalAccountRepository.ifExistTerminalAccount(accountName);

        // Then: 返回true
        assertTrue(result);
        then(terminalAccountMapper).should().exists(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("检查终端账号是否存在 - 不存在")
    void ifExistTerminalAccount_NotExists() {
        // Given: 账号不存在
        String accountName = "nonExistentDevice";
        given(terminalAccountMapper.exists(any(LambdaQueryWrapper.class)))
                .willReturn(false);

        // When: 检查存在性
        boolean result = mysqlTerminalAccountRepository.ifExistTerminalAccount(accountName);

        // Then: 返回false
        assertFalse(result);
    }

    @Test
    @DisplayName("保存终端账号 - 新增场景")
    void save_Insert() {
        // Given: 没有deviceId的新账号
        TerminalAccount newAccount = TerminalAccount.builder()
                .accountName("newDevice")
                .passwordHash("newPassword")
                .status(TerminalAccountStatus.DISABLE)
                .build();
        
        TerminalAccountDO newDO = new TerminalAccountDO();
        newDO.setAccount("newDevice");
        newDO.setPassword("newPassword");
        newDO.setAccountStatus((byte) 1);
        // deviceId为null，表示新增
        
        given(terminalAccountConverter.toDO(newAccount)).willReturn(newDO);
        given(terminalAccountConverter.toDomain(newDO)).willReturn(newAccount);

        // When: 保存账号
        TerminalAccount result = mysqlTerminalAccountRepository.save(newAccount);

        // Then: 验证新增逻辑
        assertNotNull(result);
        
        // 验证插入操作和时间设置
        ArgumentCaptor<TerminalAccountDO> captor = ArgumentCaptor.forClass(TerminalAccountDO.class);
        then(terminalAccountMapper).should().insert(captor.capture());
        TerminalAccountDO capturedDO = captor.getValue();
        assertNotNull(capturedDO.getCreateTime());
        assertNotNull(capturedDO.getUpdateTime());
        
        then(terminalAccountMapper).should(never()).updateById((TerminalAccountDO) any());
    }

    @Test
    @DisplayName("保存终端账号 - 更新场景")
    void save_Update() {
        // Given: 有deviceId的现有账号
        TerminalAccount existingAccount = TerminalAccount.builder()
                .deviceId(12345L)
                .accountName("existingDevice")
                .passwordHash("updatedPassword")
                .status(TerminalAccountStatus.DISABLE)
                .build();
        
        TerminalAccountDO existingDO = new TerminalAccountDO();
        existingDO.setDeviceId(12345L);
        existingDO.setAccount("existingDevice");
        existingDO.setPassword("updatedPassword");
        existingDO.setAccountStatus((byte) 1);
        
        given(terminalAccountConverter.toDO(existingAccount)).willReturn(existingDO);
        given(terminalAccountConverter.toDomain(existingDO)).willReturn(existingAccount);

        // When: 保存账号
        TerminalAccount result = mysqlTerminalAccountRepository.save(existingAccount);

        // Then: 验证更新逻辑
        assertNotNull(result);
        
        // 验证更新操作和时间设置
        ArgumentCaptor<TerminalAccountDO> captor = ArgumentCaptor.forClass(TerminalAccountDO.class);
        then(terminalAccountMapper).should().updateById(captor.capture());
        TerminalAccountDO capturedDO = captor.getValue();
        assertNotNull(capturedDO.getUpdateTime());
        
        then(terminalAccountMapper).should(never()).insert((TerminalAccountDO) any());
    }

    @Test
    @DisplayName("立即更新登录时间 - 成功")
    void updateLoginTimeImmediate_Success() {
        // Given: 更新参数
        Long deviceId = 12345L;
        String clientIp = "192.168.1.100";
        LocalDateTime loginTime = LocalDateTime.now();
        given(terminalAccountMapper.updateLoginTimeImmediate(deviceId, clientIp, loginTime))
                .willReturn(1);

        // When: 立即更新登录时间
        int result = mysqlTerminalAccountRepository.updateLoginTimeImmediate(deviceId, clientIp, loginTime);

        // Then: 验证成功
        assertEquals(1, result);
        then(terminalAccountMapper).should().updateLoginTimeImmediate(deviceId, clientIp, loginTime);
    }

    @Test
    @DisplayName("立即更新登录时间 - 设备不存在")
    void updateLoginTimeImmediate_DeviceNotFound() {
        // Given: 设备不存在，返回0行影响
        Long deviceId = 99999L;
        String clientIp = "192.168.1.100";
        LocalDateTime loginTime = LocalDateTime.now();
        given(terminalAccountMapper.updateLoginTimeImmediate(deviceId, clientIp, loginTime))
                .willReturn(0);

        // When: 立即更新登录时间
        int result = mysqlTerminalAccountRepository.updateLoginTimeImmediate(deviceId, clientIp, loginTime);

        // Then: 返回0但不抛异常
        assertEquals(0, result);
    }

    @Test
    @DisplayName("立即更新登录时间 - 数据库异常")
    void updateLoginTimeImmediate_DatabaseException() {
        // Given: 数据库抛出异常
        Long deviceId = 12345L;
        String clientIp = "192.168.1.100";
        LocalDateTime loginTime = LocalDateTime.now();
        RuntimeException dbException = new RuntimeException("数据库连接失败");
        given(terminalAccountMapper.updateLoginTimeImmediate(deviceId, clientIp, loginTime))
                .willThrow(dbException);

        // When & Then: 异常应该被重新抛出
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> mysqlTerminalAccountRepository.updateLoginTimeImmediate(deviceId, clientIp, loginTime));
        
        assertEquals(dbException, exception);
    }

    @Test
    @DisplayName("批量更新登录时间 - 成功")
    void updateLoginTime_Success() {
        // Given: 批量更新参数
        Long deviceId = 12345L;
        String clientIp = "192.168.1.100";
        LocalDateTime loginTime = LocalDateTime.now();
        given(terminalAccountMapper.updateLoginTime(deviceId, clientIp, loginTime))
                .willReturn(1);

        // When: 批量更新登录时间
        int result = mysqlTerminalAccountRepository.updateLoginTime(deviceId, clientIp, loginTime);

        // Then: 验证成功
        assertEquals(1, result);
        then(terminalAccountMapper).should().updateLoginTime(deviceId, clientIp, loginTime);
    }

    @Test
    @DisplayName("批量更新登录时间 - 数据库异常")
    void updateLoginTime_DatabaseException() {
        // Given: 数据库抛出异常
        Long deviceId = 12345L;
        String clientIp = "192.168.1.100";
        LocalDateTime loginTime = LocalDateTime.now();
        RuntimeException dbException = new RuntimeException("批量更新失败");
        given(terminalAccountMapper.updateLoginTime(deviceId, clientIp, loginTime))
                .willThrow(dbException);

        // When & Then: 异常应该被重新抛出
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> mysqlTerminalAccountRepository.updateLoginTime(deviceId, clientIp, loginTime));
        
        assertEquals(dbException, exception);
    }
}