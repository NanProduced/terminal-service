package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.port.outbound.config.CommandConfigPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TerminalCommandRedisService 单元测试
 * 测试终端指令Redis缓存服务的核心业务逻辑
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("终端指令Redis服务测试")
class TerminalCommandRedisServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private CommandConfigPort commandConfigPort;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    @Mock
    private ListOperations<String, Object> listOperations;
    
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    
    private TerminalCommandRedisService service;
    
    // 测试数据
    private static final Long TEST_DEVICE_ID = 12345L;
    private static final Integer TEST_COMMAND_ID = 1001;
    private static final String TEST_AUTHOR_URL = "test_command_type";
    private static final Long TEST_TTL_HOURS = 24L;
    
    @BeforeEach
    void setUp() {
        service = new TerminalCommandRedisService(redisTemplate, commandConfigPort);
        
        // 设置默认的Mock行为
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(commandConfigPort.getCacheTtlHours()).thenReturn(TEST_TTL_HOURS);
    }
    
    @Nested
    @DisplayName("指令保存测试")
    class CommandSaveTest {
        
        @Test
        @DisplayName("应该成功保存新指令到Redis")
        void should_save_new_command_to_redis_successfully() {
            // Given - 准备新指令，没有重复
            TerminalCommand command = createTestCommand();
            lenient().when(hashOperations.get(anyString(), eq(TEST_AUTHOR_URL))).thenReturn(null); // 没有重复指令
            
            // When - 保存指令
            boolean result = service.saveCommand(command);
            
            // Then - 应该保存成功
            assertThat(result).isTrue();
            
            // 验证Redis操作
            verify(valueOperations).set(anyString(), anyString(), eq(TEST_TTL_HOURS * 3600), eq(TimeUnit.SECONDS));
            verify(listOperations).rightPush(anyString(), eq(TEST_COMMAND_ID));
            verify(hashOperations).put(anyString(), eq(TEST_AUTHOR_URL), eq(TEST_COMMAND_ID));
            verify(redisTemplate, times(2)).expire(anyString(), eq(Duration.ofHours(TEST_TTL_HOURS)));
        }
        
        @Test
        @DisplayName("应该处理重复指令类型的覆盖操作")
        void should_handle_duplicate_command_type_override() {
            // Given - 存在相同类型的旧指令
            TerminalCommand command = createTestCommand();
            Integer oldCommandId = 999;
            lenient().when(hashOperations.get(anyString(), eq(TEST_AUTHOR_URL))).thenReturn(oldCommandId);
            
            // Mock获取旧指令详情（用于删除）
            TerminalCommand oldCommand = createTestCommand();
            oldCommand.setCommandId(oldCommandId);
            lenient().when(valueOperations.get(anyString())).thenReturn("{\"commandId\":" + oldCommandId + "}");
            
            // When - 保存指令
            boolean result = service.saveCommand(command);
            
            // Then - 应该先删除旧指令再保存新指令
            assertThat(result).isTrue();
            
            // 验证删除旧指令的操作
            verify(listOperations).remove(anyString(), eq(1L), eq(oldCommandId));
            verify(redisTemplate).delete(anyString());
            
            // 验证保存新指令的操作
            verify(valueOperations).set(anyString(), anyString(), eq(TEST_TTL_HOURS * 3600), eq(TimeUnit.SECONDS));
            verify(listOperations).rightPush(anyString(), eq(TEST_COMMAND_ID));
        }
        
        @Test
        @DisplayName("应该正确设置指令的TTL")
        void should_set_correct_ttl_for_command() {
            // Given - 准备新指令
            TerminalCommand command = createTestCommand();
            lenient().when(hashOperations.get(anyString(), eq(TEST_AUTHOR_URL))).thenReturn(null);
            
            // When - 保存指令
            service.saveCommand(command);
            
            // Then - 验证TTL设置
            verify(valueOperations).set(anyString(), anyString(), eq(TEST_TTL_HOURS * 3600), eq(TimeUnit.SECONDS));
            verify(redisTemplate, times(2)).expire(anyString(), eq(Duration.ofHours(TEST_TTL_HOURS)));
        }
    }
    
    @Nested
    @DisplayName("待执行指令获取测试")
    class PendingCommandsRetrievalTest {
        
        @Test
        @DisplayName("应该使用Pipeline优化方案获取待执行指令")
        void should_use_pipeline_optimization_to_get_pending_commands() {
            // Given - Mock Pipeline执行结果
            List<Object> commandIds = List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1);
            List<Object> pipelineResults = List.of(commandIds);
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(pipelineResults);
            
            // Mock指令详情获取
            String commandJson = "{\"commandId\":" + TEST_COMMAND_ID + ",\"expired\":false}";
            List<Object> detailResults = List.of(commandJson.getBytes(), commandJson.getBytes());
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class)))
                    .thenReturn(pipelineResults)  // 第一次调用返回ID列表
                    .thenReturn(detailResults);   // 第二次调用返回详情列表
            
            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);
            
            // Then - 应该使用Pipeline优化
            assertThat(result).isNotNull();
            verify(redisTemplate, times(2)).executePipelined(any(RedisCallback.class));
        }
        
        @Test
        @DisplayName("当Pipeline失败时应该降级到原始方案")
        void should_fallback_to_original_method_when_pipeline_fails() {
            // Given - Pipeline执行失败
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class)))
                    .thenThrow(new RuntimeException("Pipeline失败"));
            
            // Mock降级方案的操作
            List<Object> commandIds = List.of(TEST_COMMAND_ID);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(commandIds);
            
            // List<String> detailKeys = List.of("detail_key"); // 未使用的变量
            String commandJson = "{\"commandId\":" + TEST_COMMAND_ID + ",\"expired\":false}";
            lenient().when(valueOperations.multiGet(anyList())).thenReturn(List.of(commandJson));
            
            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);
            
            // Then - 应该降级到原始方案
            assertThat(result).isNotNull();
            verify(listOperations).range(anyString(), eq(0L), eq(-1L));
            verify(valueOperations).multiGet(anyList());
        }
        
        @Test
        @DisplayName("应该过滤掉过期的指令")
        void should_filter_out_expired_commands() {
            // Given - 包含过期指令的结果
            List<Object> commandIds = List.of(TEST_COMMAND_ID);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(commandIds);
            
            // Mock过期指令
            String expiredCommandJson = "{\"commandId\":" + TEST_COMMAND_ID + ",\"expired\":true}";
            lenient().when(valueOperations.multiGet(anyList())).thenReturn(List.of(expiredCommandJson));
            
            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);
            
            // Then - 过期指令应该被过滤掉
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("当没有待执行指令时应该返回空列表")
        void should_return_empty_list_when_no_pending_commands() {
            // Given - 没有待执行指令
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of());
            
            // When - 获取待执行指令
            List<TerminalCommand> result = service.getPendingCommands(TEST_DEVICE_ID);
            
            // Then - 应该返回空列表
            assertThat(result).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("单个指令获取测试")
    class SingleCommandRetrievalTest {
        
        @Test
        @DisplayName("应该成功获取指定的指令详情")
        void should_get_specific_command_details_successfully() {
            // Given - Redis中存在指令详情
            String commandJson = "{\"commandId\":" + TEST_COMMAND_ID + "}";
            lenient().when(valueOperations.get(anyString())).thenReturn(commandJson);
            
            // When - 获取指令详情
            Optional<TerminalCommand> result = service.getCommand(TEST_DEVICE_ID, TEST_COMMAND_ID);
            
            // Then - 应该返回指令详情
            assertThat(result).isPresent();
            assertThat(result.get().getCommandId()).isEqualTo(TEST_COMMAND_ID);
        }
        
        @Test
        @DisplayName("当指令不存在时应该返回空Optional")
        void should_return_empty_optional_when_command_not_exists() {
            // Given - Redis中不存在指令详情
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            
            // When - 获取指令详情
            Optional<TerminalCommand> result = service.getCommand(TEST_DEVICE_ID, TEST_COMMAND_ID);
            
            // Then - 应该返回空Optional
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("当JSON解析失败时应该返回空Optional")
        void should_return_empty_optional_when_json_parsing_fails() {
            // Given - Redis中存在无效的JSON
            lenient().when(valueOperations.get(anyString())).thenReturn("invalid_json");
            
            // When - 获取指令详情
            Optional<TerminalCommand> result = service.getCommand(TEST_DEVICE_ID, TEST_COMMAND_ID);
            
            // Then - 应该返回空Optional
            assertThat(result).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("指令移除测试")
    class CommandRemovalTest {
        
        @Test
        @DisplayName("应该成功移除指定的指令")
        void should_remove_specific_command_successfully() {
            // Given - 指令存在且移除成功
            String commandJson = "{\"commandId\":" + TEST_COMMAND_ID + ",\"authorUrl\":\"" + TEST_AUTHOR_URL + "\"}";
            lenient().when(valueOperations.get(anyString())).thenReturn(commandJson);
            lenient().when(listOperations.remove(anyString(), eq(1L), eq(TEST_COMMAND_ID))).thenReturn(1L);
            
            // When - 移除指令
            boolean result = service.removeCommand(TEST_DEVICE_ID, TEST_COMMAND_ID);
            
            // Then - 应该移除成功
            assertThat(result).isTrue();
            verify(listOperations).remove(anyString(), eq(1L), eq(TEST_COMMAND_ID));
            verify(hashOperations).delete(anyString(), eq(TEST_AUTHOR_URL));
            verify(redisTemplate).delete(anyString());
        }
        
        @Test
        @DisplayName("当指令不存在时移除操作应该返回false")
        void should_return_false_when_removing_non_existent_command() {
            // Given - 指令不存在
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            lenient().when(listOperations.remove(anyString(), eq(1L), eq(TEST_COMMAND_ID))).thenReturn(0L);
            
            // When - 移除指令
            boolean result = service.removeCommand(TEST_DEVICE_ID, TEST_COMMAND_ID);
            
            // Then - 应该返回false
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("当移除操作发生异常时应该返回false")
        void should_return_false_when_removal_operation_throws_exception() {
            // Given - 移除操作抛出异常
            lenient().when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis连接失败"));
            
            // When - 移除指令
            boolean result = service.removeCommand(TEST_DEVICE_ID, TEST_COMMAND_ID);
            
            // Then - 应该返回false
            assertThat(result).isFalse();
        }
    }
    
    @Nested
    @DisplayName("过期指令清理测试")
    class ExpiredCommandCleanupTest {
        
        @Test
        @DisplayName("应该成功清理过期的指令")
        void should_clean_expired_commands_successfully() {
            // Given - 存在过期指令
            List<Object> commandIds = List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(commandIds);
            
            // Mock一个过期指令和一个有效指令
            TerminalCommand validCommand = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID + 1)
                    .expireTime(LocalDateTime.now().plusDays(1))
                    .build();
            TerminalCommand expiredCommand = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID)
                    .expireTime(LocalDateTime.now().minusDays(1))
                    .build();
            lenient().when(valueOperations.get(anyString()))
                    .thenReturn(JsonUtils.toJson(expiredCommand))
                    .thenReturn(JsonUtils.toJson(validCommand));
            
            // Mock removeCommand方法中需要的getCommand调用
            String detailKeyForRemove = String.format("terminal:command:detail:%d:%d", TEST_DEVICE_ID, TEST_COMMAND_ID);
            lenient().when(redisTemplate.delete(eq(detailKeyForRemove))).thenReturn(true);
            lenient().when(listOperations.remove(anyString(), eq(1L), eq(TEST_COMMAND_ID))).thenReturn(1L);
            lenient().when(hashOperations.delete(anyString(), anyString())).thenReturn(1L);
            
            // When - 清理过期指令
            int cleanedCount = service.cleanExpiredCommands(TEST_DEVICE_ID);
            
            // Then - 应该清理了1个过期指令
            assertThat(cleanedCount).isEqualTo(1);
        }
        
        @Test
        @DisplayName("当没有过期指令时清理数量应该为0")
        void should_return_zero_when_no_expired_commands() {
            // Given - 没有指令或都是有效指令
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of());
            
            // When - 清理过期指令
            int cleanedCount = service.cleanExpiredCommands(TEST_DEVICE_ID);
            
            // Then - 清理数量应该为0
            assertThat(cleanedCount).isZero();
        }
        
        @Test
        @DisplayName("应该清理详情不存在的指令")
        void should_clean_commands_with_missing_details() {
            // Given - 指令ID存在但详情不存在
            List<Object> commandIds = List.of(TEST_COMMAND_ID);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(commandIds);
            lenient().when(valueOperations.get(anyString())).thenReturn(null); // 详情不存在
            
            // When - 清理过期指令
            int cleanedCount = service.cleanExpiredCommands(TEST_DEVICE_ID);
            
            // Then - 应该清理了1个指令
            assertThat(cleanedCount).isEqualTo(1);
            verify(listOperations).remove(anyString(), eq(1L), eq(TEST_COMMAND_ID));
        }
    }
    
    @Nested
    @DisplayName("待执行指令检查测试")
    class PendingCommandCheckTest {
        
        @Test
        @DisplayName("当存在待执行指令时应该返回true")
        void should_return_true_when_pending_commands_exist() {
            // Given - 存在待执行指令
            lenient().when(listOperations.size(anyString())).thenReturn(3L);
            
            // When - 检查是否有待执行指令
            boolean result = service.hasPendingCommands(TEST_DEVICE_ID);
            
            // Then - 应该返回true
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("当没有待执行指令时应该返回false")
        void should_return_false_when_no_pending_commands() {
            // Given - 没有待执行指令
            lenient().when(listOperations.size(anyString())).thenReturn(0L);
            
            // When - 检查是否有待执行指令
            boolean result = service.hasPendingCommands(TEST_DEVICE_ID);
            
            // Then - 应该返回false
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("当Redis操作失败时应该返回false")
        void should_return_false_when_redis_operation_fails() {
            // Given - Redis操作失败
            lenient().when(listOperations.size(anyString())).thenThrow(new RuntimeException("Redis连接失败"));
            
            // When - 检查是否有待执行指令
            boolean result = service.hasPendingCommands(TEST_DEVICE_ID);
            
            // Then - 应该返回false
            assertThat(result).isFalse();
        }
    }
    
    // ===================== 测试数据构建辅助方法 =====================
    
    /**
     * 创建测试用的终端指令
     */
    private TerminalCommand createTestCommand() {
        TerminalCommand command = new TerminalCommand();
        command.setDeviceId(TEST_DEVICE_ID);
        command.setCommandId(TEST_COMMAND_ID);
        command.setAuthorUrl(TEST_AUTHOR_URL);
        command.setCreateTime(LocalDateTime.now());
        command.setExpireTime(LocalDateTime.now().plusHours(1)); // 1小时后过期
        return command;
    }
}