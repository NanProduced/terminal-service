package com.colorlight.terminal.infrastructure.generator;

import com.colorlight.terminal.application.port.outbound.generator.CommandIdGeneratorPort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于Redis的指令ID生成器实现
 * <p>
 * 根据需求修改为和LedMaster一样的生成策略
 * <p>
 *    存在预期内的问题：当缓存失效，会导致ID重置为1，导致ID重复
 * </p>
 *
 * @author Nan
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class RedisCommandIdGenerator implements CommandIdGeneratorPort {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 重置阈值：当ID达到这个值时重置为1
     * 使用Integer.MAX_VALUE - 100 留出安全边界
     */
    private static final int RESET_THRESHOLD = Integer.MAX_VALUE - 100;

    @Override
    public Integer generateCommandId() {
        try {
            // 使用Redis INCR命令原子性递增
            // 如果key不存在，INCR会自动创建并从0开始递增，返回1
            Long nextId = redisTemplate.opsForValue().increment(RedisKeyConstant.COMMAND_ID_SEQ_KEY);

            if (nextId == null) {
                log.warn("CommandIdGenerator - Redis INCR返回null，使用默认值1");
                return 1;
            }

            // 检查是否需要重置
            if (nextId >= RESET_THRESHOLD) {
                // 重置为1并返回
                redisTemplate.opsForValue().set(RedisKeyConstant.COMMAND_ID_SEQ_KEY, 1);
                log.info("CommandIdGenerator - Redis指令ID生成器已重置，从1重新开始。上一个ID: {}", nextId);
                return 1;
            }

            return nextId.intValue();

        } catch (Exception e) {
            log.error("CommandIdGenerator - Redis生成指令ID异常，降级使用时间戳", e);
            return 1;
        }
    }

    @Override
    public Integer getCurrentId() {
        try {
            Integer currentId = (Integer) redisTemplate.opsForValue().get(RedisKeyConstant.COMMAND_ID_SEQ_KEY);
            return currentId != null ? currentId : 0;
        } catch (Exception e) {
            log.error("CommandIdGenerator - 获取当前Redis指令ID异常", e);
            return 0;
        }
    }

    @Override
    public void reset() {
        try {
            Integer oldValue = getCurrentId();
            redisTemplate.opsForValue().set(RedisKeyConstant.COMMAND_ID_SEQ_KEY, 0);
            log.warn("CommandIdGenerator - Redis ID生成器手动重置。原值: {}", oldValue);
        } catch (Exception e) {
            log.error("CommandIdGenerator - 重置Redis指令ID异常", e);
        }
    }
}