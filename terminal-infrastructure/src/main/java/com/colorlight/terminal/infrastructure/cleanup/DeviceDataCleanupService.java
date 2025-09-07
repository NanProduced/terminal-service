package com.colorlight.terminal.infrastructure.cleanup;

import com.colorlight.terminal.infrastructure.config.properties.DeviceConfigProperties;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceDeletionRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceDeletionRecordMapper;
import com.colorlight.terminal.infrastructure.cleanup.cleaner.DataStoreCleaner;
import com.colorlight.terminal.rpc.dto.config.DataCleanupConfigDTO;
import com.colorlight.terminal.rpc.dto.enums.CleanupMode;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    
    /**
     * 异步清理单个设备数据
     * @param deviceId 设备ID
     * @param customConfig 自定义配置 (可为null)
     * @return CompletableFuture
     */
    @Async("defaultAsyncExecutor")
    public CompletableFuture<Void> cleanupDeviceDataAsync(Long deviceId, DataCleanupConfigDTO customConfig) {
        log.info("开始清理设备数据: deviceId={}", deviceId);
        
        // 创建删除记录
        DeviceDeletionRecordDO record = createDeletionRecord(deviceId, customConfig);
        deletionRecordMapper.insert(record);
        
        try {
            // 更新状态为运行中
            updateRecordStatus(record.getId(), "RUNNING", null, null, LocalDateTime.now());
            
            // 解析需要清理的数据类型
            Set<DataType> dataTypesToCleanup = resolveDataTypesToCleanup(customConfig);
            log.info("设备 {} 需要清理的数据类型: {}", deviceId, dataTypesToCleanup);
            
            // 执行清理操作
            Map<String, Integer> deletedCounts = performCleanup(deviceId, dataTypesToCleanup);
            
            // 更新为成功状态
            updateRecordStatus(record.getId(), "SUCCESS", deletedCounts, null, LocalDateTime.now());
            
            log.info("设备数据清理完成: deviceId={}, 清理统计={}", deviceId, deletedCounts);
            
        } catch (Exception e) {
            log.error("设备数据清理失败: deviceId={}", deviceId, e);
            updateRecordStatus(record.getId(), "FAILED", null, e.getMessage(), LocalDateTime.now());
            throw e;
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 异步批量清理设备数据
     * @param deviceIds 设备ID列表
     * @param customConfig 自定义配置 (可为null)
     * @return CompletableFuture
     */
    @Async("defaultAsyncExecutor")
    public CompletableFuture<Void> batchCleanupDeviceDataAsync(List<Long> deviceIds, DataCleanupConfigDTO customConfig) {
        log.info("开始批量清理设备数据: count={}", deviceIds.size());
        
        // 并行处理每个设备
        List<CompletableFuture<Void>> futures = deviceIds.stream()
                .map(deviceId -> cleanupDeviceDataAsync(deviceId, customConfig))
                .toList();
                
        // 等待所有任务完成
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        
        return allOf.thenRun(() -> {
            log.info("批量设备数据清理完成: count={}", deviceIds.size());
        }).exceptionally(throwable -> {
            log.error("批量设备数据清理失败: count={}", deviceIds.size(), throwable);
            throw new RuntimeException(throwable);
        });
    }
    
    /**
     * 创建删除记录
     */
    private DeviceDeletionRecordDO createDeletionRecord(Long deviceId, DataCleanupConfigDTO customConfig) {
        DeviceDeletionRecordDO record = new DeviceDeletionRecordDO();
        record.setDeviceId(deviceId);
        record.setStatus("PENDING");
        record.setCreateTime(LocalDateTime.now());
        
        if (customConfig != null) {
            record.setCleanupMode(customConfig.getMode().name());
            record.setDataTypes(convertDataTypesToJson(customConfig.getDataTypes()));
        } else {
            var defaultConfig = deviceConfig.getCleanup();
            record.setCleanupMode(defaultConfig.getMode().name());
            record.setDataTypes(convertDataTypesToJson(defaultConfig.getDataTypes()));
        }
        
        return record;
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
        DeviceDeletionRecordDO record = new DeviceDeletionRecordDO();
        record.setId(recordId);
        record.setStatus(status);
        record.setErrorMessage(errorMessage);
        
        if ("RUNNING".equals(status)) {
            record.setStartTime(endTime);
        } else if (Arrays.asList("SUCCESS", "FAILED", "PARTIAL").contains(status)) {
            record.setEndTime(endTime);
            if (deletedCounts != null) {
                record.setDeletedCounts(convertDeletedCountsToJson(deletedCounts));
            }
        }
        
        deletionRecordMapper.updateById(record);
    }
    
    /**
     * 转换数据类型为JSON
     */
    private String convertDataTypesToJson(Set<DataType> dataTypes) {
        if (dataTypes == null || dataTypes.isEmpty()) {
            return null;
        }
        
        try {
            List<String> typeNames = dataTypes.stream()
                    .map(DataType::name)
                    .sorted()
                    .toList();
            return objectMapper.writeValueAsString(typeNames);
        } catch (Exception e) {
            log.error("转换DataTypes到JSON失败", e);
            return null;
        }
    }
    
    /**
     * 转换删除统计为JSON
     */
    private String convertDeletedCountsToJson(Map<String, Integer> deletedCounts) {
        if (deletedCounts == null || deletedCounts.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(deletedCounts);
        } catch (Exception e) {
            log.error("转换DeletedCounts到JSON失败", e);
            return null;
        }
    }
}