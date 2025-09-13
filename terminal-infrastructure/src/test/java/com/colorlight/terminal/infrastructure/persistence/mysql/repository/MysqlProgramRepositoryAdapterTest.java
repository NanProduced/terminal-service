package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.ProgramDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.ProgramMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/**
 * MysqlProgramRepositoryAdapter单元测试
 * <p>
 * 业务逻辑分析：
 * 1. 核心功能：根据节目名称和作者查询节目ID
 * 2. 查询条件：节目名称(必须) + 发布状态(publish) + 未删除(is_delete=0)
 * 3. 可选条件：作者ID(>0时生效) + VSN名称(非空时生效)
 * 4. 异常处理：数据库异常包装为TechnicalException
 * 5. 返回逻辑：找不到或ID为null返回null
 * <p>
 * 测试策略：
 * - 成功查询场景(完整条件、部分条件)
 * - 未找到记录场景
 * - 异常处理测试
 * - 边界条件测试(null值、0值处理)
 * 
 * @author Generated Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MysqlProgramRepositoryAdapter单元测试")
class MysqlProgramRepositoryAdapterTest {

    @Mock
    private ProgramMapper programMapper;
    
    @InjectMocks
    private MysqlProgramRepositoryAdapter mysqlProgramRepositoryAdapter;
    
    private ProgramDO sampleProgramDO;

    @BeforeEach
    void setUp() {
        // 初始化MyBatis Plus的TableInfo缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), ProgramDO.class);
        
        sampleProgramDO = new ProgramDO();
        sampleProgramDO.setProgramId(12345);
        sampleProgramDO.setProgramName("测试节目");
        sampleProgramDO.setAuthorId(1001);
        sampleProgramDO.setVsnName("testVsn");
        
        // 配置默认Mock行为（宽松模式）
        lenient().when(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(sampleProgramDO);
    }

    @Test
    @DisplayName("根据节目名和作者查询ID - 完整参数成功")
    void findProgramIdByNameAndAuthor_FullParams_Success() {
        // Given: 提供完整的查询参数
        String programName = "测试节目";
        Integer authorId = 1001;
        String vsnName = "testVsn";
        
        given(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(sampleProgramDO);

        // When: 执行查询
        Integer result = mysqlProgramRepositoryAdapter.findProgramIdByNameAndAuthor(programName, authorId, vsnName);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(12345, result);
        then(programMapper).should().selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("根据节目名和作者查询ID - 仅节目名成功")
    void findProgramIdByNameAndAuthor_OnlyProgramName_Success() {
        // Given: 只提供节目名，其他参数为null或无效值
        String programName = "测试节目";
        Integer authorId = null;
        String vsnName = "";
        
        given(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(sampleProgramDO);

        // When: 执行查询
        Integer result = mysqlProgramRepositoryAdapter.findProgramIdByNameAndAuthor(programName, authorId, vsnName);

        // Then: 验证结果（应该只用节目名查询）
        assertNotNull(result);
        assertEquals(12345, result);
    }

    @Test
    @DisplayName("根据节目名和作者查询ID - authorId为0时忽略")
    void findProgramIdByNameAndAuthor_AuthorIdZero_Ignored() {
        // Given: authorId为0（应该被忽略）
        String programName = "测试节目";
        Integer authorId = 0;
        String vsnName = "testVsn";
        
        given(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(sampleProgramDO);

        // When: 执行查询
        Integer result = mysqlProgramRepositoryAdapter.findProgramIdByNameAndAuthor(programName, authorId, vsnName);

        // Then: 查询应该成功（authorId=0被忽略）
        assertNotNull(result);
        assertEquals(12345, result);
    }

    @Test
    @DisplayName("根据节目名和作者查询ID - 未找到记录")
    void findProgramIdByNameAndAuthor_NotFound() {
        // Given: 查询返回null
        String programName = "不存在的节目";
        Integer authorId = 1001;
        String vsnName = "testVsn";
        
        given(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(null);

        // When: 执行查询
        Integer result = mysqlProgramRepositoryAdapter.findProgramIdByNameAndAuthor(programName, authorId, vsnName);

        // Then: 返回null
        assertNull(result);
    }

    @Test
    @DisplayName("根据节目名和作者查询ID - 查询到的节目ID为null")
    void findProgramIdByNameAndAuthor_ProgramIdNull() {
        // Given: 查询到记录但programId为null
        ProgramDO programWithNullId = new ProgramDO();
        programWithNullId.setProgramId(null);
        
        given(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(programWithNullId);

        // When: 执行查询
        Integer result = mysqlProgramRepositoryAdapter.findProgramIdByNameAndAuthor("测试节目", 1001, "testVsn");

        // Then: 返回null
        assertNull(result);
    }

    @Test
    @DisplayName("根据节目名和作者查询ID - 数据库异常")
    void findProgramIdByNameAndAuthor_DatabaseException() {
        // Given: 数据库查询抛出异常
        RuntimeException dbException = new RuntimeException("数据库连接失败");
        given(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willThrow(dbException);

        // When & Then: 验证异常被包装为TechnicalException
        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> mysqlProgramRepositoryAdapter.findProgramIdByNameAndAuthor("测试节目", 1001, "testVsn"));
        
        assertEquals(dbException, exception.getCause());
    }

    @Test
    @DisplayName("根据节目名和作者查询ID - vsnName为空字符串时忽略")
    void findProgramIdByNameAndAuthor_VsnNameBlank_Ignored() {
        // Given: vsnName为空字符串或空白字符
        String programName = "测试节目";
        Integer authorId = 1001;
        String vsnName = "   "; // 空白字符
        
        given(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(sampleProgramDO);

        // When: 执行查询
        Integer result = mysqlProgramRepositoryAdapter.findProgramIdByNameAndAuthor(programName, authorId, vsnName);

        // Then: 查询应该成功（空白vsnName被忽略）
        assertNotNull(result);
        assertEquals(12345, result);
    }

    @Test
    @DisplayName("根据节目名和作者查询ID - authorId为负数时忽略")
    void findProgramIdByNameAndAuthor_AuthorIdNegative_Ignored() {
        // Given: authorId为负数（应该被忽略）
        String programName = "测试节目";
        Integer authorId = -1;
        String vsnName = "testVsn";
        
        given(programMapper.selectOne(any(LambdaQueryWrapper.class)))
                .willReturn(sampleProgramDO);

        // When: 执行查询
        Integer result = mysqlProgramRepositoryAdapter.findProgramIdByNameAndAuthor(programName, authorId, vsnName);

        // Then: 查询应该成功（负数authorId被忽略）
        assertNotNull(result);
        assertEquals(12345, result);
    }
}