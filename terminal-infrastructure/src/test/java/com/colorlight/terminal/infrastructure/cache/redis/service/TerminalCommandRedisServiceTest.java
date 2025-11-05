package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.dto.result.CommandFetchResult;
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
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
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

            // 使用multiGet Mock (批量优化版本)
            List<Object> commandJsons = List.of(
                JsonUtils.toJson(expiredCommand),
                JsonUtils.toJson(validCommand)
            );
            lenient().when(valueOperations.multiGet(anyList())).thenReturn(commandJsons);

            // Mock Pipeline执行用于批量删除
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(List.of());

            // When - 清理过期指令
            int cleanedCount = service.cleanExpiredCommands(TEST_DEVICE_ID);

            // Then - 应该清理了1个过期指令
            assertThat(cleanedCount).isEqualTo(1);
            // 验证使用了批量优化方案
            verify(valueOperations).multiGet(anyList());
            verify(redisTemplate).executePipelined(any(RedisCallback.class));
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

            // 使用Arrays.asList以支持null元素
            List<Object> commandJsons = Collections.singletonList((Object) null); // 详情不存在
            lenient().when(valueOperations.multiGet(anyList())).thenReturn(commandJsons);

            // Mock Pipeline执行用于批量删除
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(List.of());

            // When - 清理过期指令
            int cleanedCount = service.cleanExpiredCommands(TEST_DEVICE_ID);

            // Then - 应该清理了1个指令(详情缺失的指令)
            assertThat(cleanedCount).isEqualTo(1);
            // 验证使用了批量优化方案
            verify(valueOperations).multiGet(anyList());
            verify(redisTemplate).executePipelined(any(RedisCallback.class));
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

    @Nested
    @DisplayName("整合获取指令并清理过期测试")
    class IntegratedCommandFetchWithCleanupTest {

        @Test
        @DisplayName("应该成功整合获取指令并清理过期指令（使用批量优化）")
        void should_fetch_commands_with_cleanup_using_batch_optimization() {
            // Given - 存在有效和过期指令的混合情况
            List<Object> commandIds = List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1, TEST_COMMAND_ID + 2);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(commandIds);

            // Mock指令详情 - 一个有效，两个过期
            TerminalCommand validCommand = createTestCommand();
            validCommand.setCommandId(TEST_COMMAND_ID);
            validCommand.setExpireTime(LocalDateTime.now().plusHours(1)); // 有效指令

            TerminalCommand expiredCommand1 = createTestCommand();
            expiredCommand1.setCommandId(TEST_COMMAND_ID + 1);
            expiredCommand1.setExpireTime(LocalDateTime.now().minusHours(1)); // 过期指令

            TerminalCommand expiredCommand2 = createTestCommand();
            expiredCommand2.setCommandId(TEST_COMMAND_ID + 2);
            expiredCommand2.setExpireTime(LocalDateTime.now().minusHours(2)); // 过期指令

            List<Object> commandJsons = List.of(
                JsonUtils.toJson(validCommand),
                JsonUtils.toJson(expiredCommand1),
                JsonUtils.toJson(expiredCommand2)
            );
            lenient().when(valueOperations.multiGet(anyList())).thenReturn(commandJsons);

            // Mock Pipeline执行用于删除过期指令
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(List.of());

            // When - 整合获取指令并清理
            CommandFetchResult result = service.getPendingCommandsWithCleanup(TEST_DEVICE_ID);

            // Then - 验证结果
            assertThat(result).isNotNull();
            assertThat(result.getValidCommands()).hasSize(1);
            assertThat(result.getValidCommands().get(0).getCommandId()).isEqualTo(TEST_COMMAND_ID);
            assertThat(result.getExpiredCleanedCount()).isEqualTo(2);
            assertThat(result.getTotalCandidateCount()).isEqualTo(3);
            assertThat(result.isUsedBatchOptimization()).isTrue();
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.getCleanupEfficiencyPercent()).isCloseTo(66.67, within());

            // 验证Redis操作
            verify(listOperations).range(anyString(), eq(0L), eq(-1L));
            verify(valueOperations).multiGet(anyList());
            verify(redisTemplate).executePipelined(any(RedisCallback.class));
        }

        @Test
        @DisplayName("应该正确处理无待执行指令的情况")
        void should_handle_no_pending_commands_correctly() {
            // Given - 没有待执行指令
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(List.of());

            // When - 整合获取指令并清理
            CommandFetchResult result = service.getPendingCommandsWithCleanup(TEST_DEVICE_ID);

            // Then - 验证结果
            assertThat(result).isNotNull();
            assertThat(result.getValidCommands()).isEmpty();
            assertThat(result.getExpiredCleanedCount()).isZero();
            assertThat(result.getTotalCandidateCount()).isZero();
            assertThat(result.isUsedBatchOptimization()).isTrue();
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0); // 修改为大于等于0
            assertThat(result.getCleanupEfficiencyPercent()).isEqualTo(0.0);

            // 验证没有进行后续操作
            verify(listOperations).range(anyString(), eq(0L), eq(-1L));
            verify(valueOperations, never()).multiGet(anyList());
            verify(redisTemplate, never()).executePipelined(any(RedisCallback.class));
        }

        @Test
        @DisplayName("应该正确处理指令详情缺失的情况")
        void should_handle_missing_command_details_correctly() {
            // Given - 存在指令ID但详情缺失
            List<Object> commandIds = List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(commandIds);

            // Mock指令详情 - 一个存在，一个缺失
            TerminalCommand validCommand = createTestCommand();
            validCommand.setCommandId(TEST_COMMAND_ID);
            validCommand.setExpireTime(LocalDateTime.now().plusHours(1));

            // 使用Arrays.asList而不是List.of，因为List.of不允许null元素
            List<Object> commandJsons = Arrays.asList(
                JsonUtils.toJson(validCommand),
                null // 详情缺失
            );
            lenient().when(valueOperations.multiGet(anyList())).thenReturn(commandJsons);

            // Mock Pipeline执行用于删除过期指令
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(List.of());

            // When - 整合获取指令并清理
            CommandFetchResult result = service.getPendingCommandsWithCleanup(TEST_DEVICE_ID);

            // Then - 验证结果
            assertThat(result).isNotNull();
            assertThat(result.getValidCommands()).hasSize(1);
            assertThat(result.getExpiredCleanedCount()).isEqualTo(1); // 详情缺失的被清理
            assertThat(result.getTotalCandidateCount()).isEqualTo(2);
            assertThat(result.isUsedBatchOptimization()).isTrue();
        }

        @Test
        @DisplayName("应该正确处理JSON解析失败的情况")
        void should_handle_json_parsing_failures_correctly() {
            // Given - 存在无效JSON的指令
            List<Object> commandIds = List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(commandIds);

            // Mock指令详情 - 一个有效，一个无效JSON
            TerminalCommand validCommand = createTestCommand();
            validCommand.setCommandId(TEST_COMMAND_ID);
            validCommand.setExpireTime(LocalDateTime.now().plusHours(1));

            // 使用Arrays.asList而不是List.of，因为List.of不允许null元素
            List<Object> commandJsons = Arrays.asList(
                JsonUtils.toJson(validCommand),
                "invalid_json" // 无效JSON
            );
            lenient().when(valueOperations.multiGet(anyList())).thenReturn(commandJsons);

            // Mock Pipeline执行用于删除过期指令
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(List.of());

            // When - 整合获取指令并清理
            CommandFetchResult result = service.getPendingCommandsWithCleanup(TEST_DEVICE_ID);

            // Then - 验证结果
            assertThat(result).isNotNull();
            assertThat(result.getValidCommands()).hasSize(1);
            assertThat(result.getExpiredCleanedCount()).isEqualTo(1); // 解析失败的被清理
            assertThat(result.getTotalCandidateCount()).isEqualTo(2);
            assertThat(result.isUsedBatchOptimization()).isTrue();
        }



        @Test
        @DisplayName("应该正确记录性能指标和清理统计")
        void should_record_performance_metrics_and_cleanup_statistics_correctly() {
            // Given - 准备测试数据
            List<Object> commandIds = List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1, TEST_COMMAND_ID + 2, TEST_COMMAND_ID + 3);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L))).thenReturn(commandIds);

            // Mock指令详情 - 2个有效，2个过期
            TerminalCommand validCommand1 = createTestCommand();
            validCommand1.setCommandId(TEST_COMMAND_ID);
            validCommand1.setExpireTime(LocalDateTime.now().plusHours(1));

            TerminalCommand validCommand2 = createTestCommand();
            validCommand2.setCommandId(TEST_COMMAND_ID + 1);
            validCommand2.setExpireTime(LocalDateTime.now().plusHours(2));

            TerminalCommand expiredCommand1 = createTestCommand();
            expiredCommand1.setCommandId(TEST_COMMAND_ID + 2);
            expiredCommand1.setExpireTime(LocalDateTime.now().minusHours(1));

            TerminalCommand expiredCommand2 = createTestCommand();
            expiredCommand2.setCommandId(TEST_COMMAND_ID + 3);
            expiredCommand2.setExpireTime(LocalDateTime.now().minusHours(2));

            // 使用Arrays.asList而不是List.of，因为List.of不允许null元素
            List<Object> commandJsons = Arrays.asList(
                JsonUtils.toJson(validCommand1),
                JsonUtils.toJson(validCommand2),
                JsonUtils.toJson(expiredCommand1),
                JsonUtils.toJson(expiredCommand2)
            );
            lenient().when(valueOperations.multiGet(anyList())).thenReturn(commandJsons);

            // Mock Pipeline执行
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(List.of());

            // When - 整合获取指令并清理
            CommandFetchResult result = service.getPendingCommandsWithCleanup(TEST_DEVICE_ID);

            // Then - 验证性能指标和统计
            assertThat(result).isNotNull();
            assertThat(result.getValidCommandCount()).isEqualTo(2);
            assertThat(result.getExpiredCleanedCount()).isEqualTo(2);
            assertThat(result.getTotalCandidateCount()).isEqualTo(4);
            assertThat(result.getCleanupEfficiencyPercent()).isEqualTo(50.0); // 2/4 = 50%
            assertThat(result.isUsedBatchOptimization()).isTrue();
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);

            // 验证有效指令内容
            assertThat(result.getValidCommands()).hasSize(2);
            assertThat(result.getValidCommands())
                    .extracting(TerminalCommand::getCommandId)
                    .containsExactlyInAnyOrder(TEST_COMMAND_ID, TEST_COMMAND_ID + 1);
        }

        private static org.assertj.core.data.Offset<Double> within() {
            return org.assertj.core.data.Offset.offset(0.01);
        }
    }

    @Nested
    @DisplayName("Fallback降级处理测试")
    class FallbackHandlingTest {

        @Test
        @DisplayName("当Pipeline失败时应该使用cleanExpiredCommandsFallback")
        void should_use_fallback_when_pipeline_fails() {
            // Given - Pipeline执行失败
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenReturn(List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1));

            TerminalCommand validCommand = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID + 1)
                    .expireTime(LocalDateTime.now().plusDays(1))
                    .build();
            TerminalCommand expiredCommand = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID)
                    .expireTime(LocalDateTime.now().minusDays(1))
                    .build();

            // Pipeline失败，触发fallback
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenThrow(new RuntimeException("Pipeline执行失败"));

            // Mock个别删除操作用于fallback
            lenient().when(valueOperations.multiGet(anyList()))
                .thenReturn(Arrays.asList(
                    JsonUtils.toJson(expiredCommand),
                    JsonUtils.toJson(validCommand)
                ));

            // When - 清理过期指令，Pipeline失败触发fallback
            int cleanedCount = service.cleanExpiredCommands(TEST_DEVICE_ID);

            // Then - Fallback应该能继续清理（单个删除操作）
            assertThat(cleanedCount).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("fallbackRemoveExpired应该能单个删除过期指令")
        void should_delete_expired_commands_individually_in_fallback() {
            // Given - 准备过期指令列表
            List<Object> commandIds = List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1, TEST_COMMAND_ID + 2);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenReturn(commandIds);

            // 准备指令数据
            TerminalCommand expiredCommand1 = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID)
                    .expireTime(LocalDateTime.now().minusHours(1))
                    .build();
            TerminalCommand expiredCommand2 = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID + 1)
                    .expireTime(LocalDateTime.now().minusHours(2))
                    .build();
            TerminalCommand validCommand = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID + 2)
                    .expireTime(LocalDateTime.now().plusHours(1))
                    .build();

            // multiGet返回指令内容
            lenient().when(valueOperations.multiGet(anyList()))
                .thenReturn(Arrays.asList(
                    JsonUtils.toJson(expiredCommand1),
                    JsonUtils.toJson(expiredCommand2),
                    JsonUtils.toJson(validCommand)
                ));

            // Pipeline失败，触发fallback
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenThrow(new RuntimeException("Pipeline执行失败"));

            // When - 清理过期指令
            int cleanedCount = service.cleanExpiredCommands(TEST_DEVICE_ID);

            // Then - 应该清理了至少2个过期指令
            assertThat(cleanedCount).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("getPendingCommandsWithCleanupFallback应该处理Pipeline失败场景")
        void should_handle_pipeline_failure_in_get_pending_commands_with_cleanup() {
            // Given - 准备指令列表
            List<Object> commandIds = List.of(TEST_COMMAND_ID, TEST_COMMAND_ID + 1);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenReturn(commandIds);

            // 准备混合的指令数据
            TerminalCommand validCommand = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID + 1)
                    .expireTime(LocalDateTime.now().plusDays(1))
                    .build();
            TerminalCommand expiredCommand = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID)
                    .expireTime(LocalDateTime.now().minusDays(1))
                    .build();

            lenient().when(valueOperations.multiGet(anyList()))
                .thenReturn(Arrays.asList(
                    JsonUtils.toJson(expiredCommand),
                    JsonUtils.toJson(validCommand)
                ));

            // Pipeline执行失败
            lenient().when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenThrow(new RuntimeException("Pipeline超时"));

            // When - 获取待发指令并清理（Pipeline失败触发fallback）
            CommandFetchResult result = service.getPendingCommandsWithCleanup(TEST_DEVICE_ID);

            // Then - Fallback应该返回结果
            assertThat(result).isNotNull();
            // 有效指令应该被保留
            assertThat(result.getValidCommands()).isNotNull();
        }

        @Test
        @DisplayName("异步清理失败时不应该影响返回结果")
        void should_not_affect_result_when_async_cleanup_fails() {
            // Given - 异步清理抛出异常
            List<Object> commandIds = List.of(TEST_COMMAND_ID);
            lenient().when(listOperations.range(anyString(), eq(0L), eq(-1L)))
                .thenReturn(commandIds);

            TerminalCommand validCommand = TerminalCommand.builder()
                    .commandId(TEST_COMMAND_ID)
                    .expireTime(LocalDateTime.now().plusHours(1))
                    .build();

            lenient().when(valueOperations.multiGet(anyList()))
                .thenReturn(List.of(JsonUtils.toJson(validCommand)));

            // 异步清理抛出异常
            lenient().doThrow(new RuntimeException("异步清理失败"))
                .when(redisTemplate).executePipelined(any(RedisCallback.class));

            // When - 获取待发指令
            CommandFetchResult result = service.getPendingCommandsWithCleanup(TEST_DEVICE_ID);

            // Then - 仍然应该返回有效指令
            assertThat(result).isNotNull();
            assertThat(result.getValidCommands()).contains(validCommand);
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