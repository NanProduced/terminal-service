package com.colorlight.terminal.infrastructure.generator;

import com.colorlight.terminal.application.port.outbound.generator.CommandIdGeneratorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 递增ID生成器实现
 * 线程安全的递增ID生成，从1开始到Integer.MAX_VALUE后重新从1开始
 * 
 * @author Nan
 * @version 1.0.0
 */
@Slf4j
@Component
public class IncrementalCommandIdGenerator implements CommandIdGeneratorPort {
    
    /**
     * 原子递增计数器
     * 初始值为0，第一次调用返回1
     */
    private final AtomicInteger currentId = new AtomicInteger(0);
    
    /**
     * 重置阈值：当ID达到这个值时重置为0
     * 使用Integer.MAX_VALUE - 1000 留出安全边界
     */
    private static final int RESET_THRESHOLD = Integer.MAX_VALUE - 1000;
    
    @Override
    public Integer generateCommandId() {
        int nextId = currentId.incrementAndGet();
        
        // 检查是否需要重置
        if (nextId >= RESET_THRESHOLD) {
            // 使用CAS操作确保线程安全的重置
            if (currentId.compareAndSet(nextId, 1)) {
                log.info("CommandIdGenerator - 指令ID生成器已重置，从1重新开始。上一个ID: {}", nextId);
                return 1;
            } else {
                // 如果CAS失败，说明其他线程已经重置，重新获取
                return currentId.incrementAndGet();
            }
        }
        
        return nextId;
    }
    
    @Override
    public Integer getCurrentId() {
        return currentId.get();
    }
    
    @Override
    public void reset() {
        int oldValue = currentId.getAndSet(0);
        log.warn("CommandIdGenerator - ID生成器手动重置。原值: {}", oldValue);
    }
    
    /**
     * 获取生成器统计信息
     * 用于监控和调试
     */
    public GeneratorStats getStats() {
        int current = currentId.get();
        return new GeneratorStats(
            current,
            RESET_THRESHOLD,
            (double) current / RESET_THRESHOLD * 100
        );
    }

    /**
     * 生成器统计信息
     */
    public record GeneratorStats(int currentId, int maxId, double usagePercentage) {

        @Override
        public String toString() {
            return String.format("CommandIdGenerator - GeneratorStats{currentId=%d, maxId=%d, usage=%.2f%%}",
                    currentId, maxId, usagePercentage);
        }
    }
}