package com.colorlight.terminal.infrastructure.cleanup;

import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.infrastructure.cleanup.cleaner.DataStoreCleaner;
import com.colorlight.terminal.infrastructure.config.properties.DeviceConfigProperties;
import com.colorlight.terminal.infrastructure.event.AsyncBufferFlushEvent;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceDeletionRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceDeletionRecordMapper;
import com.colorlight.terminal.rpc.dto.config.DataCleanupConfigDTO;
import com.colorlight.terminal.rpc.dto.enums.CleanupMode;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.TerminalAccountMapper;
import com.colorlight.terminal.application.port.outbound.cache.TerminalAuthCachePort;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 设备数据清理服务
 * Infrastructure层实现，使用@Async进行异步处理
 * 
 * @author Nan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceDataCleanupService {
    
    private final DeviceConfigProperties deviceConfig;
    private final DeviceDeletionRecordMapper deletionRecordMapper;
    private final List<DataStoreCleaner> cleaners;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TerminalAuthCachePort terminalAuthCachePort;
    private final TerminalAccountMapper terminalAccountMapper;
    
    /**
     * 异步清理单个设备数据
     *
     * @param deviceId     设备ID
     * @param customConfig 自定义配置 (可为null)
     */
    @Async("defaultAsyncExecutor")
    public void cleanupDeviceDataAsync(Long deviceId, DataCleanupConfigDTO customConfig) {
        log.info("开始清理设备数据: deviceId={}", deviceId);

        // 创建删除记录
        DeviceDeletionRecordDO deletionRecord = createDeletionRecord(deviceId, customConfig);
        deletionRecordMapper.insert(deletionRecord);

        // 查询设备账户信息（用于后续缓存清理）
        TerminalAccountDO account = null;
        try {
            account = terminalAccountMapper.selectById(deviceId);
        } catch (Exception e) {
            log.warn("设备数据清理 - 查询设备账户信息失败: deviceId={}", deviceId, e);
        }

        try {
            // 更新状态为运行中
            updateRecordStatus(deletionRecord.getId(), "RUNNING", null, null, LocalDateTime.now());

            // 解析需要清理的数据类型
            Set<DataType> dataTypesToCleanup = resolveDataTypesToCleanup(customConfig);
            log.info("设备 {} 需要清理的数据类型: {}", deviceId, dataTypesToCleanup);

            // 执行清理操作
            Map<String, Integer> deletedCounts = performCleanup(deviceId, dataTypesToCleanup);

            // 清理Caffeine缓存中的设备账户信息
            cleanupTerminalAuthCache(account);

            // 更新为成功状态
            updateRecordStatus(deletionRecord.getId(), "SUCCESS", deletedCounts, null, LocalDateTime.now());

            log.info("设备数据清理完成: deviceId={}, 清理统计={}", deviceId, deletedCounts);

        } catch (Exception e) {
            log.error("设备数据清理失败: deviceId={}", deviceId, e);
            updateRecordStatus(deletionRecord.getId(), "FAILED", null, e.getMessage(), LocalDateTime.now());
            throw e;
        }

        CompletableFuture.completedFuture(null);
    }
    
    /**
     * 异步批量清理设备数据
     * 使用事件发布机制解决@Async自调用代理失效问题
     *
     * @param deviceIds    设备ID列表
     * @param customConfig 自定义配置 (可为null)
     */
    @Async("defaultAsyncExecutor")
    public void batchCleanupDeviceDataAsync(List<Long> deviceIds, DataCleanupConfigDTO customConfig) {
        log.info("开始批量清理设备数据: count={}", deviceIds.size());
        
        try {
            // 通过事件发布机制触发每个设备的清理任务
            for (Long deviceId : deviceIds) {
                AsyncBufferFlushEvent event = AsyncBufferFlushEvent.createDeviceCleanupFlushEvent(
                    this, deviceId, customConfig);
                eventPublisher.publishEvent(event);
                
                log.debug("DeviceDataCleanup - 发布设备清理事件: deviceId={}", deviceId);
            }
            
            log.info("批量设备数据清理事件发布完成: count={}", deviceIds.size());
            
        } catch (Exception e) {
            log.error("批量设备数据清理事件发布失败: count={}", deviceIds.size(), e);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR, e);
        }
    }
    
    /**
     * 创建删除记录
     */
    private DeviceDeletionRecordDO createDeletionRecord(Long deviceId, DataCleanupConfigDTO customConfig) {
        DeviceDeletionRecordDO deletionRecordDO = new DeviceDeletionRecordDO();
        deletionRecordDO.setDeviceId(deviceId);
        deletionRecordDO.setStatus("PENDING");
        deletionRecordDO.setCreateTime(LocalDateTime.now());
        
        if (customConfig != null) {
            deletionRecordDO.setCleanupMode(customConfig.getMode().name());
            deletionRecordDO.setDataTypes(convertDataTypesToJson(customConfig.getDataTypes()));
        } else {
            var defaultConfig = deviceConfig.getCleanup();
            deletionRecordDO.setCleanupMode(defaultConfig.getMode().name());
            deletionRecordDO.setDataTypes(convertDataTypesToJson(defaultConfig.getDataTypes()));
        }
        
        return deletionRecordDO;
    }
    
    /**
     * 解析需要清理的数据类型
     */
    private Set<DataType> resolveDataTypesToCleanup(DataCleanupConfigDTO customConfig) {
        CleanupMode mode;
        Set<DataType> configuredTypes;
        
        if (customConfig != null) {
            mode = customConfig.getMode();
            configuredTypes = customConfig.getDataTypes() != null ? 
                customConfig.getDataTypes() : new HashSet<>();
        } else {
            var defaultConfig = deviceConfig.getCleanup();
            mode = defaultConfig.getMode();
            configuredTypes = defaultConfig.getDataTypes();
        }
        
        Set<DataType> result = switch (mode) {
            case ALL -> EnumSet.allOf(DataType.class);
            case INCLUDE -> configuredTypes != null ? 
                EnumSet.copyOf(configuredTypes) : EnumSet.noneOf(DataType.class);
            case EXCLUDE -> {
                Set<DataType> excludeResult = EnumSet.allOf(DataType.class);
                if (configuredTypes != null && !configuredTypes.isEmpty()) {
                    excludeResult.removeAll(configuredTypes);
                }
                yield excludeResult;
            }
        };
        
        // 确保设备账号信息始终被删除 (安全要求)
        result.add(DataType.DEVICE_ACCOUNT);
        
        return result;
    }
    
    /**
     * 执行清理操作
     */
    private Map<String, Integer> performCleanup(Long deviceId, Set<DataType> dataTypes) {
        Map<String, Integer> deletedCounts = new HashMap<>();
        
        for (DataStoreCleaner cleaner : cleaners) {
            try {
                int deleted = cleaner.cleanup(deviceId, dataTypes);
                if (deleted > 0) {
                    deletedCounts.put(cleaner.getStorageType(), deleted);
                    log.info("设备 {} {} 清理完成: 删除 {} 条记录", deviceId, cleaner.getStorageType(), deleted);
                }
            } catch (Exception e) {
                log.error("设备 {} {} 清理失败", deviceId, cleaner.getStorageType(), e);
                // 继续执行其他清理器，不中断整个流程
            }
        }
        
        return deletedCounts;
    }
    
    /**
     * 更新删除记录状态
     */
    private void updateRecordStatus(Long recordId, String status, Map<String, Integer> deletedCounts, 
                                   String errorMessage, LocalDateTime endTime) {
        DeviceDeletionRecordDO deletionRecordDO = new DeviceDeletionRecordDO();
        deletionRecordDO.setId(recordId);
        deletionRecordDO.setStatus(status);
        deletionRecordDO.setErrorMessage(errorMessage);
        
        if ("RUNNING".equals(status)) {
            deletionRecordDO.setStartTime(endTime);
        } else if (Arrays.asList("SUCCESS", "FAILED", "PARTIAL").contains(status)) {
            deletionRecordDO.setEndTime(endTime);
            if (deletedCounts != null) {
                deletionRecordDO.setDeletedCounts(convertDeletedCountsToJson(deletedCounts));
            }
        }
        
        deletionRecordMapper.updateById(deletionRecordDO);
    }
    
    /**
     * 清理终端认证缓存
     *
     * @param account  终端账户信息
     */
    private void cleanupTerminalAuthCache(TerminalAccountDO account) {
        if (account != null && account.getAccount() != null) {
            try {
                terminalAuthCachePort.remove(account.getAccount());
                log.info("设备数据清理 - Caffeine缓存已清理: deviceId={}, account={}", account.getDeviceId(), account.getAccount());
            } catch (Exception e) {
                log.error("设备数据清理 - Caffeine缓存清理失败: deviceId={}, account={}", account.getDeviceId(), account.getAccount(), e);
                // 缓存清理失败不中断主流程
            }
        }
    }

    /**
     * 转换数据类型为JSON
     */
    private String convertDataTypesToJson(Set<DataType> dataTypes) {
        try {
            List<String> typeNames = dataTypes == null || dataTypes.isEmpty() ?
                Collections.emptyList() :
                dataTypes.stream()
                    .map(DataType::name)
                    .sorted()
                    .toList();
            return objectMapper.writeValueAsString(typeNames);
        } catch (Exception e) {
            log.error("转换DataTypes到JSON失败", e);
            return "[]";  // 返回空数组JSON而不是null，确保数据库字段有值
        }
    }
    
    /**
     * 转换删除统计为JSON
     */
    private String convertDeletedCountsToJson(Map<String, Integer> deletedCounts) {
        try {
            if (deletedCounts == null || deletedCounts.isEmpty()) {
                return "{}";  // 返回空对象JSON而不是null，确保数据库字段有值
            }
            return objectMapper.writeValueAsString(deletedCounts);
        } catch (Exception e) {
            log.error("转换DeletedCounts到JSON失败", e);
            return "{}";  // 返回空对象JSON而不是null
        }
    }
}