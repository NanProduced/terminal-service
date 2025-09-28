package com.colorlight.terminal.infrastructure.testutil;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis测试工具类
 * 提供Redis Template的Mock配置和常用测试场景支持
 * 
 * @author Nan
 */
public class RedisTestUtils {
    
    /**
     * 创建完整配置的RedisTemplate Mock对象
     */
    @SuppressWarnings("unchecked")
    public static RedisTemplate<String, Object> createMockRedisTemplate() {
        RedisTemplate<String, Object> mockRedisTemplate = mock(RedisTemplate.class);
        
        // Mock各种操作类 - 只创建实际使用的操作类
        ValueOperations<String, Object> mockValueOps = mock(ValueOperations.class);
        HashOperations<String, Object, Object> mockHashOps = mock(HashOperations.class);
        SetOperations<String, Object> mockSetOps = mock(SetOperations.class);
        
        // 配置操作类返回 - 使用lenient避免UnnecessaryStubbingException
        lenient().when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        lenient().when(mockRedisTemplate.opsForHash()).thenReturn(mockHashOps);
        lenient().when(mockRedisTemplate.opsForSet()).thenReturn(mockSetOps);
        
        // 配置基本操作
        setupBasicOperations(mockRedisTemplate, mockValueOps, mockHashOps, mockSetOps);
        
        return mockRedisTemplate;
    }

    /**
     * 创建完整配置的StringRedisTemplate Mock对象
     */
    @SuppressWarnings("unchecked")
    public static StringRedisTemplate createMockStringRedisTemplate() {
        StringRedisTemplate mockStringRedisTemplate = mock(StringRedisTemplate.class);

        // Mock各种操作类
        ValueOperations<String, String> mockValueOps = mock(ValueOperations.class);
        HashOperations<String, Object, Object> mockHashOps = mock(HashOperations.class);
        SetOperations<String, String> mockSetOps = mock(SetOperations.class);

        // 配置操作类返回
        lenient().when(mockStringRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        lenient().when(mockStringRedisTemplate.opsForHash()).thenReturn(mockHashOps);
        lenient().when(mockStringRedisTemplate.opsForSet()).thenReturn(mockSetOps);

        // 配置基本操作
        setupStringBasicOperations(mockStringRedisTemplate, mockValueOps, mockHashOps, mockSetOps);

        return mockStringRedisTemplate;
    }

    /**
     * 配置基本的Redis操作Mock行为
     */
    @SuppressWarnings("unchecked")
    private static void setupBasicOperations(
            RedisTemplate<String, Object> mockRedisTemplate,
            ValueOperations<String, Object> mockValueOps,
            HashOperations<String, Object, Object> mockHashOps,
            SetOperations<String, Object> mockSetOps) {
        
        // 内存存储模拟Redis数据
        Map<String, Object> redisMemory = new HashMap<>();
        Map<String, Map<Object, Object>> redisHashMemory = new HashMap<>();
        Map<String, Set<Object>> redisSetMemory = new HashMap<>();
        
        // === Value Operations ===
        lenient().when(mockValueOps.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisMemory.get(key);
        });
        
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            redisMemory.put(key, value);
            return null;
        }).when(mockValueOps).set(anyString(), any());
        
        lenient().when(mockValueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            if (!redisMemory.containsKey(key)) {
                redisMemory.put(key, value);
                return true;
            }
            return false;
        });
        
        lenient().when(mockValueOps.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Integer current = (Integer) redisMemory.get(key);
            if (current == null) current = 0;
            Integer newValue = current + 1;
            redisMemory.put(key, newValue);
            return newValue.longValue();
        });
        
        lenient().when(mockValueOps.decrement(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Integer current = (Integer) redisMemory.get(key);
            if (current == null) current = 0;
            Integer newValue = current - 1;
            redisMemory.put(key, newValue);
            return newValue.longValue();
        });
        
        // === Hash Operations ===
        lenient().when(mockHashOps.entries(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisHashMemory.getOrDefault(key, new HashMap<>());
        });
        
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Map<Object, Object> hash = invocation.getArgument(1);
            redisHashMemory.put(key, new HashMap<>(hash));
            return null;
        }).when(mockHashOps).putAll(anyString(), any(Map.class));
        
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object field = invocation.getArgument(1);
            Object value = invocation.getArgument(2);
            redisHashMemory.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
            return null;
        }).when(mockHashOps).put(anyString(), any(), any());
        
        // === Set Operations ===
        lenient().when(mockSetOps.members(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisSetMemory.getOrDefault(key, new HashSet<>());
        });
        
        lenient().when(mockSetOps.add(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object[] values = Arrays.copyOfRange(invocation.getArguments(), 1, invocation.getArguments().length);
            Set<Object> set = redisSetMemory.computeIfAbsent(key, k -> new HashSet<>());
            long added = 0;
            for (Object value : values) {
                if (set.add(value)) {
                    added++;
                }
            }
            return added;
        });
        
        lenient().when(mockSetOps.remove(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object[] values = Arrays.copyOfRange(invocation.getArguments(), 1, invocation.getArguments().length);
            Set<Object> set = redisSetMemory.get(key);
            if (set == null) return 0L;
            long removed = 0;
            for (Object value : values) {
                if (set.remove(value)) {
                    removed++;
                }
            }
            return removed;
        });
        
        // === 基本操作 ===
        lenient().when(mockRedisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            boolean existed = redisMemory.containsKey(key) || redisHashMemory.containsKey(key) || redisSetMemory.containsKey(key);
            redisMemory.remove(key);
            redisHashMemory.remove(key);
            redisSetMemory.remove(key);
            return existed;
        });
        
        lenient().when(mockRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(mockRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        // === 事务操作 ===
        lenient().when(mockRedisTemplate.execute(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            RedisOperations<String, Object> mockOps = mock(RedisOperations.class);
            
            // Mock事务操作
            lenient().when(mockOps.exec()).thenReturn(Arrays.asList("OK", "OK", "OK"));
            lenient().when(mockOps.opsForValue()).thenReturn(mockValueOps);
            lenient().when(mockOps.opsForHash()).thenReturn(mockHashOps);
            lenient().when(mockOps.opsForSet()).thenReturn(mockSetOps);
            lenient().when(mockOps.delete(anyString())).thenReturn(true);
            lenient().when(mockOps.expire(anyString(), any(Duration.class))).thenReturn(true);
            lenient().doNothing().when(mockOps).multi();
            
            return callback.execute(mockOps);
        });
        
        // === Pipeline操作 ===
        lenient().when(mockRedisTemplate.executePipelined(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            RedisOperations<String, Object> mockOps = mock(RedisOperations.class);
            lenient().when(mockOps.opsForHash()).thenReturn(mockHashOps);
            
            // 执行pipeline操作
            callback.execute(mockOps);
            
            // 返回模拟的批量结果
            return Arrays.asList(new HashMap<>(), new HashMap<>(), new HashMap<>());
        });
    }

    /**
     * 配置基本的StringRedisTemplate操作Mock行为
     */
    @SuppressWarnings("unchecked")
    private static void setupStringBasicOperations(
            StringRedisTemplate mockStringRedisTemplate,
            ValueOperations<String, String> mockValueOps,
            HashOperations<String, Object, Object> mockHashOps,
            SetOperations<String, String> mockSetOps) {

        // 内存存储模拟Redis数据
        Map<String, String> redisMemory = new HashMap<>();
        Map<String, Map<String, String>> redisHashMemory = new HashMap<>();
        Map<String, Set<String>> redisSetMemory = new HashMap<>();

        // === Value Operations ===
        lenient().when(mockValueOps.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisMemory.get(key);
        });

        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redisMemory.put(key, value);
            return null;
        }).when(mockValueOps).set(anyString(), anyString());

        lenient().when(mockValueOps.decrement(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String current = redisMemory.get(key);
            long currentVal = current != null ? Long.parseLong(current) : 0L;
            long newValue = currentVal - 1;
            redisMemory.put(key, String.valueOf(newValue));
            return newValue;
        });

        // === Hash Operations ===
        lenient().when(mockHashOps.entries(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Map<String, String> result = redisHashMemory.getOrDefault(key, new HashMap<>());
            Map<Object, Object> objectMap = new HashMap<>();
            result.forEach(objectMap::put);
            return objectMap;
        });

        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String field = invocation.getArgument(1);
            String value = invocation.getArgument(2);
            redisHashMemory.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
            return null;
        }).when(mockHashOps).put(anyString(), anyString(), anyString());

        // === Set Operations ===
        lenient().when(mockSetOps.remove(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String[] values = Arrays.copyOfRange(invocation.getArguments(), 1, invocation.getArguments().length, String[].class);
            Set<String> set = redisSetMemory.get(key);
            if (set == null) return 0L;
            long removed = 0;
            for (String value : values) {
                if (set.remove(value)) {
                    removed++;
                }
            }
            return removed;
        });

        // === 基本操作 ===
        lenient().when(mockStringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        // === Pipeline操作 ===
        lenient().when(mockStringRedisTemplate.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);

            // Mock StringRedisConnection
            StringRedisConnection mockConnection = mock(StringRedisConnection.class);

            // Mock hSet操作
            lenient().doAnswer(setInvocation -> {
                String key = setInvocation.getArgument(0);
                String field = setInvocation.getArgument(1);
                String value = setInvocation.getArgument(2);
                redisHashMemory.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
                return true;
            }).when(mockConnection).hSet(anyString(), anyString(), anyString());

            // Mock expire操作
            lenient().when(mockConnection.expire(anyString(), anyLong())).thenReturn(true);

            // Mock sRem操作
            lenient().when(mockConnection.sRem(anyString(), anyString())).thenReturn(1L);

            // Mock decrBy和decr操作
            lenient().when(mockConnection.decrBy(anyString(), anyLong())).thenAnswer(decrInvocation -> {
                String key = decrInvocation.getArgument(0);
                Long decrement = decrInvocation.getArgument(1);
                String current = redisMemory.get(key);
                long currentVal = current != null ? Long.parseLong(current) : 0L;
                long newValue = currentVal - decrement;
                redisMemory.put(key, String.valueOf(newValue));
                return newValue;
            });

            lenient().when(mockConnection.decr(anyString())).thenAnswer(decrInvocation -> {
                String key = decrInvocation.getArgument(0);
                String current = redisMemory.get(key);
                long currentVal = current != null ? Long.parseLong(current) : 0L;
                long newValue = currentVal - 1;
                redisMemory.put(key, String.valueOf(newValue));
                return newValue;
            });

            // 执行callback
            callback.doInRedis(mockConnection);

            // 返回模拟的批量结果
            return Arrays.asList("OK", "OK", "OK");
        });
    }

    /**
     * 创建Mock的Cursor对象用于Scan操作
     */
    @SuppressWarnings("unchecked")
    public static Cursor<Object> createMockCursor(Collection<Object> data) {
        Cursor<Object> mockCursor = mock(Cursor.class);
        Iterator<Object> iterator = data.iterator();
        
        when(mockCursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(mockCursor.next()).thenAnswer(invocation -> iterator.next());
        
        doNothing().when(mockCursor).close();
        
        return mockCursor;
    }
    
    /**
     * 验证Redis操作的参数捕获器
     */
    public static class RedisArgumentCaptor {
        public final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        public final ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        public final ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        public final ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
    }
    
    /**
     * 验证Redis Key的格式
     */
    public static boolean isValidRedisKey(String key, String pattern) {
        return key != null && key.matches(pattern);
    }
    
    /**
     * 模拟Redis连接异常
     */
    @SuppressWarnings("unchecked")
    public static void simulateRedisConnectionError(RedisTemplate<String, Object> mockRedisTemplate) {
        reset(mockRedisTemplate);
        when(mockRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection failed"));
        when(mockRedisTemplate.opsForHash()).thenThrow(new RuntimeException("Redis connection failed"));
        when(mockRedisTemplate.opsForSet()).thenThrow(new RuntimeException("Redis connection failed"));
    }
    
    /**
     * 重置Redis Template Mock状态
     */
    public static void resetRedisTemplate(RedisTemplate<String, Object> mockRedisTemplate) {
        Mockito.reset(mockRedisTemplate);
    }
}