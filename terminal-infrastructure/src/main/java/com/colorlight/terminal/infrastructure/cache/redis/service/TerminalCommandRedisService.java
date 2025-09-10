package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.port.outbound.command.CommandCachePort;
import com.colorlight.terminal.application.port.outbound.config.CommandConfigPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;

import static com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant.*;

/**
 * 终端指令Redis缓存服务
 * 高性能实现：支持指令去重、TTL管理、批量操作
 * 
 * @author Nan
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalCommandRedisService implements CommandCachePort {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final CommandConfigPort commandConfigPort;

    @Override
    public boolean saveCommand(TerminalCommand command) {
        log.debug("TerminalCommandCache - 保存指令到Redis, deviceId: {}, commandId: {}, authorUrl: {}",
                command.getDeviceId(), command.getCommandId(), command.getAuthorUrl());

        String queueKey = String.format(COMMAND_QUEUE_KEY, command.getDeviceId());
        String indexKey = String.format(COMMAND_INDEX_KEY, command.getDeviceId());
        String detailKey = String.format(COMMAND_DETAIL_KEY, command.getCommandId());

        // 1. 检查是否存在相同类型的指令(去重逻辑)
        Object existingCommandIdObj = redisTemplate.opsForHash().get(indexKey, command.getAuthorUrl());
        if (existingCommandIdObj != null) {
            Integer existingCommandId = Integer.valueOf(existingCommandIdObj.toString());
            log.debug("TerminalCommandCache - 发现重复指令类型，执行覆盖操作, authorUrl: {}, oldCommandId: {}",
                    command.getAuthorUrl(), existingCommandId);

            // 删除旧指令
            removeOldCommand(command.getDeviceId(), existingCommandId);
        }

        Long ttlHours = commandConfigPort.getCacheTtlHours();
        long ttlSeconds = ttlHours * 3600;
        
        // 2. 保存新指令详情
        String commandJson = JsonUtils.toJson(command);
        redisTemplate.opsForValue().set(detailKey, commandJson, ttlSeconds, TimeUnit.SECONDS);

        // 3. 添加到指令队列 (右推入，保持时间顺序)
        redisTemplate.opsForList().rightPush(queueKey, command.getCommandId());
        redisTemplate.expire(queueKey, Duration.ofHours(ttlHours));

        // 4. 更新去重索引
        redisTemplate.opsForHash().put(indexKey, command.getAuthorUrl(), command.getCommandId());
        redisTemplate.expire(indexKey, Duration.ofHours(ttlHours));

        log.info("TerminalCommandCache - 指令保存成功, deviceId: {}, commandId: {}, authorUrl: {}",
                command.getDeviceId(), command.getCommandId(), command.getAuthorUrl());
        return true;
    }
    
    @Override
    public List<TerminalCommand> getPendingCommands(Long deviceId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 尝试Pipeline优化方案
            List<TerminalCommand> result = getPendingCommandsWithPipeline(deviceId);
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Pipeline获取指令完成, deviceId: {}, count: {}, duration: {}ms", 
                     deviceId, result.size(), duration);
            return result;
            
        } catch (Exception e) {
            log.warn("Pipeline获取指令失败，降级到原始方案, deviceId: {}, error: {}", deviceId, e.getMessage());
            // 降级到原始实现
            return getPendingCommandsFallback(deviceId);
        }
    }
    
    /**
     * Pipeline优化版本 - 减少网络往返次数
     */
    private List<TerminalCommand> getPendingCommandsWithPipeline(Long deviceId) {
        log.debug("TerminalCommandCache - Pipeline获取设备待执行指令, deviceId: {}", deviceId);
        
        String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
        
        // 使用Pipeline执行两步操作：1)获取ID队列 2)批量获取详情
        List<Object> pipelineResults = redisTemplate.executePipelined(
            new RedisCallback<Object>() {
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    // 第一步：获取指令ID队列
                    connection.lRange(queueKey.getBytes(StandardCharsets.UTF_8), 0, -1);
                    return null;
                }
            }
        );
        
        if (pipelineResults.isEmpty()) {
            return List.of();
        }
        
        @SuppressWarnings("unchecked")
        List<Object> commandIds = (List<Object>) pipelineResults.get(0);
        if (commandIds == null || commandIds.isEmpty()) {
            return List.of();
        }
        
        // 第二个Pipeline：批量获取指令详情
        List<String> detailKeys = commandIds.stream()
                .map(id -> String.format(COMMAND_DETAIL_KEY, Integer.valueOf(id.toString())))
                .collect(Collectors.toList());
        
        List<Object> detailResults = redisTemplate.executePipelined(
            new RedisCallback<Object>() {
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    // 批量获取所有指令详情
                    for (String detailKey : detailKeys) {
                        connection.get(detailKey.getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                }
            }
        );
        
        return parseCommandResults(detailResults, deviceId);
    }
    
    /**
     * 原始实现作为降级方案
     */
    private List<TerminalCommand> getPendingCommandsFallback(Long deviceId) {
        log.debug("TerminalCommandCache - 降级获取设备待执行指令, deviceId: {}", deviceId);
        
        try {
            String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
            
            // 获取所有指令ID (左到右，按时间顺序)
            List<Object> commandIds = redisTemplate.opsForList().range(queueKey, 0, -1);
            if (commandIds == null || commandIds.isEmpty()) {
                return List.of();
            }
            
            // 批量获取指令详情
            List<String> detailKeys = commandIds.stream()
                    .map(id -> String.format(COMMAND_DETAIL_KEY, Integer.valueOf(id.toString())))
                    .collect(Collectors.toList());
            
            List<Object> commandJsons = redisTemplate.opsForValue().multiGet(detailKeys);
            if (CollectionUtils.isEmpty(commandJsons)) return Collections.emptyList();
            
            return parseCommandResults(commandJsons, deviceId);
            
        } catch (Exception e) {
            log.error("降级获取待执行指令异常, deviceId: {}", deviceId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 解析指令结果的通用方法
     */
    private List<TerminalCommand> parseCommandResults(List<Object> commandResults, Long deviceId) {
        List<TerminalCommand> commands = new ArrayList<>();
        
        for (Object commandData : commandResults) {
            if (commandData != null) {
                try {
                    String commandJson;
                    if (commandData instanceof byte[]) {
                        // Pipeline返回的是byte[]格式
                        commandJson = new String((byte[]) commandData, StandardCharsets.UTF_8);
                    } else {
                        // multiGet返回的是String格式
                        commandJson = commandData.toString();
                    }
                    
                    TerminalCommand command = JsonUtils.fromJson(commandJson, TerminalCommand.class);
                    
                    // 检查指令是否过期
                    if (!command.isExpired()) {
                        commands.add(command);
                    }
                } catch (Exception e) {
                    log.warn("解析指令数据失败, deviceId: {}, data: {}", deviceId, commandData, e);
                }
            }
        }
        
        log.debug("获取到 {} 条有效指令, deviceId: {}", commands.size(), deviceId);
        return commands;
    }
    
    @Override
    public Optional<TerminalCommand> getCommand(Integer commandId) {
        try {
            String detailKey = String.format(COMMAND_DETAIL_KEY, commandId);
            Object commandJson = redisTemplate.opsForValue().get(detailKey);
            
            if (commandJson != null) {
                TerminalCommand command =JsonUtils.fromJson(
                        commandJson.toString(), TerminalCommand.class);
                return Optional.of(command);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("TerminalCommandCache - 获取指令详情异常, commandId: {}", commandId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public boolean removeCommand(Long deviceId, Integer commandId) {
        log.debug("TerminalCommandCache - 移除指令, deviceId: {}, commandId: {}", deviceId, commandId);
        
        try {
            String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
            String indexKey = String.format(COMMAND_INDEX_KEY, deviceId);
            String detailKey = String.format(COMMAND_DETAIL_KEY, commandId);
            
            // 1. 获取指令详情以确定authorUrl
            Optional<TerminalCommand> commandOpt = getCommand(commandId);
            
            // 2. 从队列中移除
            Long removedCount = redisTemplate.opsForList().remove(queueKey, 1, commandId);
            
            // 3. 从索引中移除
            commandOpt.ifPresent(terminalCommand -> redisTemplate.opsForHash().delete(indexKey, terminalCommand.getAuthorUrl()));
            
            // 4. 删除详情
            redisTemplate.delete(detailKey);
            
            boolean success = removedCount != null && removedCount > 0;
            if (success) {
                log.info("TerminalCommandCache - 指令移除成功, deviceId: {}, commandId: {}", deviceId, commandId);
            } else {
                log.warn("TerminalCommandCache - 指令移除失败，可能不存在, deviceId: {}, commandId: {}", deviceId, commandId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("TerminalCommandCache - 移除指令异常, deviceId: {}, commandId: {}", deviceId, commandId, e);
            return false;
        }
    }
    
    @Override
    public int cleanExpiredCommands(Long deviceId) {
        log.debug("TerminalCommandCache - 清理过期指令, deviceId: {}", deviceId);
        
        try {
            String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
            
            // 获取所有指令ID
            List<Object> commandIds = redisTemplate.opsForList().range(queueKey, 0, -1);
            if (commandIds == null || commandIds.isEmpty()) {
                return 0;
            }
            
            int cleanedCount = 0;
            
            for (Object commandIdObj : commandIds) {
                Integer commandId = Integer.valueOf(commandIdObj.toString());
                Optional<TerminalCommand> commandOpt = getCommand(commandId);
                
                if (commandOpt.isPresent() && commandOpt.get().isExpired()) {
                    if (removeCommand(deviceId, commandId)) {
                        cleanedCount++;
                    }
                } else if (commandOpt.isEmpty()) {
                    // 指令详情不存在，也从队列中移除
                    redisTemplate.opsForList().remove(queueKey, 1, commandId);
                    cleanedCount++;
                }
            }
            
            if (cleanedCount > 0) {
                log.info("TerminalCommandCache - 清理过期指令完成, deviceId: {}, 清理数量: {}", deviceId, cleanedCount);
            }
            
            return cleanedCount;
            
        } catch (Exception e) {
            log.error("TerminalCommandCache - 清理过期指令异常, deviceId: {}", deviceId, e);
            return 0;
        }
    }
    
    @Override
    public boolean hasPendingCommands(Long deviceId) {
        try {
            String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
            Long count = redisTemplate.opsForList().size(queueKey);
            return count != null && count > 0;
            
        } catch (Exception e) {
            log.error("TerminalCommandCache - 检查待执行指令异常, deviceId: {}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 移除旧指令 (去重时使用)
     */
    private void removeOldCommand(Long deviceId, Integer oldCommandId) {
        try {
            String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
            String detailKey = String.format(COMMAND_DETAIL_KEY, oldCommandId);
            
            // 从队列移除
            redisTemplate.opsForList().remove(queueKey, 1, oldCommandId);
            
            // 删除详情
            redisTemplate.delete(detailKey);
            
            log.debug("TerminalCommandCache - 旧指令移除成功, deviceId: {}, oldCommandId: {}", deviceId, oldCommandId);
            
        } catch (Exception e) {
            log.warn("TerminalCommandCache - 移除旧指令异常, deviceId: {}, oldCommandId: {}", deviceId, oldCommandId, e);
        }
    }
}