package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.domain.command.TerminalCommand;
import com.colorlight.terminal.application.dto.result.CommandFetchResult;
import com.colorlight.terminal.application.port.outbound.command.CommandCachePort;
import com.colorlight.terminal.application.port.outbound.config.CommandConfigPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        String detailKey = String.format(COMMAND_DETAIL_KEY, command.getDeviceId(), command.getCommandId());

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
                public Object doInRedis(@NotNull RedisConnection connection) throws DataAccessException {
                    // 第一步：获取指令ID队列
                    connection.listCommands().lRange(queueKey.getBytes(StandardCharsets.UTF_8), 0, -1);
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
                .map(id -> String.format(COMMAND_DETAIL_KEY, deviceId, Integer.valueOf(id.toString())))
                .toList();
        
        List<Object> detailResults = redisTemplate.executePipelined(
            new RedisCallback<Object>() {
                @Override
                public Object doInRedis(@NotNull RedisConnection connection) throws DataAccessException {
                    // 批量获取所有指令详情
                    for (String detailKey : detailKeys) {
                        connection.stringCommands().get(detailKey.getBytes(StandardCharsets.UTF_8));
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
                    .map(id -> String.format(COMMAND_DETAIL_KEY, deviceId, Integer.valueOf(id.toString())))
                    .toList();
            
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
                    if (commandData instanceof byte[] byteArray) {
                        // Pipeline返回的是byte[]格式
                        commandJson = new String(byteArray, StandardCharsets.UTF_8);
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
    public Optional<TerminalCommand> getCommand(Long deviceId, Integer commandId) {
        try {
            String detailKey = String.format(COMMAND_DETAIL_KEY, deviceId, commandId);
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
            String detailKey = String.format(COMMAND_DETAIL_KEY, deviceId, commandId);
            
            // 1. 获取指令详情以确定authorUrl
            Optional<TerminalCommand> commandOpt = getCommand(deviceId, commandId);
            
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
        log.debug("TerminalCommandCache - 批量清理过期指令, deviceId: {}", deviceId);
        long startTime = System.currentTimeMillis();

        try {
            // 优化策略：批量获取 -> 本地过滤 -> Pipeline删除
            return cleanExpiredCommandsWithBatch(deviceId, startTime);

        } catch (Exception e) {
            log.warn("TerminalCommandCache - 批量清理失败，降级到逐个清理, deviceId: {}", deviceId);
            // 降级到原始逐个清理方案
            return cleanExpiredCommandsFallback(deviceId);
        }
    }

    /**
     * 批量清理过期指令的优化实现
     * 策略：multiGet批量获取 -> 本地过滤 -> Pipeline批量删除
     */
    private int cleanExpiredCommandsWithBatch(Long deviceId, long startTime) {
        String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
        String indexKey = String.format(COMMAND_INDEX_KEY, deviceId);

        // 1. 获取所有指令ID
        List<Object> commandIds = redisTemplate.opsForList().range(queueKey, 0, -1);
        if (commandIds == null || commandIds.isEmpty()) {
            return 0;
        }

        // 2. 批量获取所有指令详情
        List<String> detailKeys = commandIds.stream()
                .map(id -> String.format(COMMAND_DETAIL_KEY, deviceId, Integer.valueOf(id.toString())))
                .toList();

        List<Object> commandJsons = redisTemplate.opsForValue().multiGet(detailKeys);

        // 3. 本地过滤出过期的指令
        ExpiredCommandFilterResult filterResult = filterExpiredCommands(deviceId, commandIds, commandJsons);

        if (filterResult.expiredCommandIds.isEmpty()) {
            log.debug("TerminalCommandCache - 无过期指令需清理, deviceId: {}", deviceId);
            return 0;
        }

        // 4. Pipeline批量删除过期指令
        int cleanedCount = batchRemoveExpiredCommands(deviceId, queueKey, indexKey, 
                                                     filterResult.expiredCommandIds, filterResult.expiredAuthorUrls);

        long duration = System.currentTimeMillis() - startTime;
        log.info("TerminalCommandCache - 批量清理过期指令完成: deviceId={}, 候选数={}, 清理数={}, 耗时={}ms",
                deviceId, commandIds.size(), cleanedCount, duration);

        return cleanedCount;
    }

    /**
     * 过滤出过期的指令
     */
    private ExpiredCommandFilterResult filterExpiredCommands(Long deviceId, List<Object> commandIds, List<Object> commandJsons) {
        List<Integer> expiredCommandIds = new ArrayList<>();
        List<String> expiredAuthorUrls = new ArrayList<>();

        for (int i = 0; i < commandIds.size(); i++) {
            Integer commandId = Integer.valueOf(commandIds.get(i).toString());
            Object commandJson = (commandJsons != null && i < commandJsons.size()) ? commandJsons.get(i) : null;

            if (commandJson == null) {
                // 指令详情不存在，标记为需清理
                expiredCommandIds.add(commandId);
                log.debug("TerminalCommandCache - 指令详情缺失: deviceId={}, commandId={}", deviceId, commandId);

            } else {
                try {
                    TerminalCommand command = JsonUtils.fromJson(commandJson.toString(), TerminalCommand.class);
                    if (command.isExpired()) {
                        // 指令已过期，标记为需清理
                        expiredCommandIds.add(commandId);
                        expiredAuthorUrls.add(command.getAuthorUrl());
                        log.debug("TerminalCommandCache - 指令已过期: deviceId={}, commandId={}, authorUrl={}",
                                deviceId, commandId, command.getAuthorUrl());
                    }
                } catch (Exception e) {
                    log.warn("TerminalCommandCache - 解析指令数据失败: deviceId={}, commandId={}", deviceId, commandId, e);
                    // 解析失败的也视为需清理
                    expiredCommandIds.add(commandId);
                }
            }
        }
        
        return new ExpiredCommandFilterResult(expiredCommandIds, expiredAuthorUrls);
    }

    /**
     * 过期指令过滤结果类
     */
    private record ExpiredCommandFilterResult(List<Integer> expiredCommandIds, List<String> expiredAuthorUrls) {
    }

    /**
     * Pipeline批量删除过期指令
     */
    private int batchRemoveExpiredCommands(Long deviceId, String queueKey, String indexKey,
                                         List<Integer> expiredCommandIds, List<String> expiredAuthorUrls) {

        try {
            // 使用Pipeline批量执行删除操作
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                StringRedisConnection stringConnection = (StringRedisConnection) connection;

                for (int i = 0; i < expiredCommandIds.size(); i++) {
                    Integer commandId = expiredCommandIds.get(i);
                    String detailKey = String.format(COMMAND_DETAIL_KEY, deviceId, commandId);

                    // 从队列中移除指令ID
                    stringConnection.lRem(queueKey, 1, commandId.toString());

                    // 删除指令详情
                    stringConnection.del(detailKey);

                    // 从索引中移除(如果有authorUrl)
                    if (i < expiredAuthorUrls.size() && expiredAuthorUrls.get(i) != null) {
                        stringConnection.hDel(indexKey, expiredAuthorUrls.get(i));
                    }
                }

                return null;
            });

            log.debug("TerminalCommandCache - Pipeline批量删除完成: deviceId={}, 删除数量={}", deviceId, expiredCommandIds.size());
            return expiredCommandIds.size();

        } catch (Exception e) {
            log.error("TerminalCommandCache - Pipeline批量删除失败: deviceId={}, 指令数={}", deviceId, expiredCommandIds.size(), e);
            // Pipeline失败时，尝试逐个删除关键数据
            return fallbackRemoveExpired(deviceId, queueKey, expiredCommandIds);
        }
    }

    /**
     * Pipeline失败时的降级删除策略
     */
    private int fallbackRemoveExpired(Long deviceId, String queueKey, List<Integer> expiredCommandIds) {
        int cleanedCount = 0;

        for (Integer commandId : expiredCommandIds) {
            try {
                // 至少从队列中移除，避免数据不一致
                Long removedCount = redisTemplate.opsForList().remove(queueKey, 1, commandId);
                if (removedCount != null && removedCount > 0) {
                    cleanedCount++;
                }

                // 尝试删除详情
                String detailKey = String.format(COMMAND_DETAIL_KEY, deviceId, commandId);
                redisTemplate.delete(detailKey);

            } catch (Exception e) {
                log.warn("TerminalCommandCache - 降级删除单个指令失败: deviceId={}, commandId={}", deviceId, commandId, e);
            }
        }

        log.debug("TerminalCommandCache - 降级删除完成: deviceId={}, 清理数量={}", deviceId, cleanedCount);
        return cleanedCount;
    }

    /**
     * 原始逐个清理的降级方案
     */
    private int cleanExpiredCommandsFallback(Long deviceId) {
        log.debug("TerminalCommandCache - 降级清理过期指令, deviceId: {}", deviceId);

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
                Optional<TerminalCommand> commandOpt = getCommand(deviceId, commandId);

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
                log.info("TerminalCommandCache - 降级清理过期指令完成, deviceId: {}, 清理数量: {}", deviceId, cleanedCount);
            }

            return cleanedCount;

        } catch (Exception e) {
            log.error("TerminalCommandCache - 降级清理过期指令异常, deviceId: {}", deviceId, e);
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
            String detailKey = String.format(COMMAND_DETAIL_KEY, deviceId, oldCommandId);

            // 从队列移除
            redisTemplate.opsForList().remove(queueKey, 1, oldCommandId);

            // 删除详情
            redisTemplate.delete(detailKey);

            log.debug("TerminalCommandCache - 旧指令移除成功, deviceId: {}, oldCommandId: {}", deviceId, oldCommandId);

        } catch (Exception e) {
            log.warn("TerminalCommandCache - 移除旧指令异常, deviceId: {}, oldCommandId: {}", deviceId, oldCommandId, e);
        }
    }

    @Override
    public CommandFetchResult getPendingCommandsWithCleanup(Long deviceId) {
        log.debug("TerminalCommandCache - 整合获取指令并清理过期, deviceId: {}", deviceId);
        long startTime = System.currentTimeMillis();

        try {
            // 优化策略：一次查询，本地分离，异步删除
            return getPendingCommandsWithCleanupOptimized(deviceId, startTime);

        } catch (Exception e) {
            log.warn("TerminalCommandCache - 整合方案失败，降级到分步处理, deviceId: {}", deviceId, e);
            // 降级到原有的两步调用
            return getPendingCommandsWithCleanupFallback(deviceId, startTime);
        }
    }

    /**
     * 整合获取和清理的优化实现
     * 策略：一次查询 → 本地分离有效/过期 → 异步删除过期 + 返回有效
     */
    private CommandFetchResult getPendingCommandsWithCleanupOptimized(Long deviceId, long startTime) {
        String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
        String indexKey = String.format(COMMAND_INDEX_KEY, deviceId);

        // 1. 获取所有指令ID
        List<Object> commandIds = redisTemplate.opsForList().range(queueKey, 0, -1);
        if (commandIds == null || commandIds.isEmpty()) {
            long duration = System.currentTimeMillis() - startTime;
            return CommandFetchResult.success(List.of(), 0, 0, duration, true);
        }

        // 2. 批量获取所有指令详情
        List<String> detailKeys = commandIds.stream()
                .map(id -> String.format(COMMAND_DETAIL_KEY, deviceId, Integer.valueOf(id.toString())))
                .toList();

        List<Object> commandJsons = redisTemplate.opsForValue().multiGet(detailKeys);

        // 3. 本地分离有效和过期指令
        CommandSeparationResult separationResult = separateValidAndExpiredCommands(deviceId, commandIds, commandJsons);

        // 4. 异步删除过期指令（不阻塞返回）
        asyncRemoveExpiredCommands(deviceId, queueKey, indexKey, separationResult);

        long duration = System.currentTimeMillis() - startTime;
        log.info("TerminalCommandCache - 整合获取完成: deviceId={}, 总数={}, 有效={}, 清理={}, 耗时={}ms",
                deviceId, commandIds.size(), separationResult.validCommands.size(), separationResult.expiredCommandIds.size(), duration);

        return CommandFetchResult.success(
                separationResult.validCommands,
                separationResult.expiredCommandIds.size(),
                commandIds.size(),
                duration,
                true
        );
    }

    /**
     * 分离有效和过期的指令
     */
    private CommandSeparationResult separateValidAndExpiredCommands(Long deviceId, List<Object> commandIds, List<Object> commandJsons) {
        List<TerminalCommand> validCommands = new ArrayList<>();
        List<Integer> expiredCommandIds = new ArrayList<>();
        List<String> expiredAuthorUrls = new ArrayList<>();

        for (int i = 0; i < commandIds.size(); i++) {
            Integer commandId = Integer.valueOf(commandIds.get(i).toString());
            Object commandJson = (commandJsons != null && i < commandJsons.size()) ? commandJsons.get(i) : null;

            if (commandJson == null) {
                // 指令详情不存在，标记为需清理
                expiredCommandIds.add(commandId);
            } 
            else {
                processCommandJson(deviceId, commandId, commandJson, validCommands, expiredCommandIds, expiredAuthorUrls);
            }
        }
        
        return new CommandSeparationResult(validCommands, expiredCommandIds, expiredAuthorUrls);
    }

    /**
     * 处理单个指令JSON
     */
    private void processCommandJson(Long deviceId, Integer commandId, Object commandJson, 
                                  List<TerminalCommand> validCommands, List<Integer> expiredCommandIds, List<String> expiredAuthorUrls) {
        try {
            TerminalCommand command = JsonUtils.fromJson(commandJson.toString(), TerminalCommand.class);
            if (command.isExpired()) {
                // 指令已过期，标记为需清理
                expiredCommandIds.add(commandId);
                expiredAuthorUrls.add(command.getAuthorUrl());
                log.debug("TerminalCommandCache - 指令已过期: deviceId={}, commandId={}", deviceId, commandId);
            } else {
                // 指令有效，添加到结果列表
                validCommands.add(command);
            }
        } catch (Exception e) {
            log.warn("TerminalCommandCache - 解析指令数据失败: deviceId={}, commandId={}", deviceId, commandId, e);
            // 解析失败的也视为需清理
            expiredCommandIds.add(commandId);
        }
    }

    /**
     * 异步删除过期指令
     */
    private void asyncRemoveExpiredCommands(Long deviceId, String queueKey, String indexKey, CommandSeparationResult separationResult) {
        if (!separationResult.expiredCommandIds.isEmpty()) {
            try {
                // 使用现有的批量删除方法
                batchRemoveExpiredCommands(deviceId, queueKey, indexKey, separationResult.expiredCommandIds, separationResult.expiredAuthorUrls);
                log.debug("TerminalCommandCache - 异步删除过期指令: deviceId={}, 删除数量={}", deviceId, separationResult.expiredCommandIds.size());
            } catch (Exception e) {
                log.warn("TerminalCommandCache - 异步删除过期指令失败: deviceId={}, 过期数量={}", deviceId, separationResult.expiredCommandIds.size(), e);
                // 删除失败不影响返回结果
            }
        }
    }

    /**
     * 指令分离结果类
     */
    private record CommandSeparationResult(List<TerminalCommand> validCommands, List<Integer> expiredCommandIds,
                                               List<String> expiredAuthorUrls) {
    }

    /**
     * 降级到原有的分步处理方案
     */
    private CommandFetchResult getPendingCommandsWithCleanupFallback(Long deviceId, long startTime) {
        log.debug("TerminalCommandCache - 降级到分步处理, deviceId: {}", deviceId);

        try {
            // 步骤1：清理过期指令
            int cleanedCount = cleanExpiredCommands(deviceId);

            // 步骤2：获取有效指令
            List<TerminalCommand> validCommands = getPendingCommands(deviceId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("TerminalCommandCache - 降级分步处理完成: deviceId={}, 有效={}, 清理={}, 耗时={}ms",
                    deviceId, validCommands.size(), cleanedCount, duration);

            return CommandFetchResult.success(
                    validCommands,
                    cleanedCount,
                    validCommands.size() + cleanedCount, // 近似值
                    duration,
                    false
            );

        } catch (Exception e) {
            log.error("TerminalCommandCache - 降级分步处理失败: deviceId={}", deviceId, e);
            long duration = System.currentTimeMillis() - startTime;
            return CommandFetchResult.success(List.of(), 0, 0, duration, false);
        }
    }
}