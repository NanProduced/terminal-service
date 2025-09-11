package com.colorlight.terminal.infrastructure.cleanup.cleaner;

import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.*;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * MongoDB数据存储清理器
 * 清理MongoDB中的设备相关数据
 * 
 * @author Nan
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MongoDataStoreCleaner implements DataStoreCleaner {
    
    private final MongoTemplate mongoTemplate;
    
    // 数据类型到文档类的映射
    private static final Map<DataType, Class<?>> DATA_TYPE_CLASS_MAP = Map.of(
        DataType.GPS_RECORD, GpsRecordDocument.class,
        DataType.STATUS_REPORT, TerminalStatusReportDocument.class,
        DataType.TERMINAL_LOG, TerminalLogDocument.class,
        DataType.MEDIA_PLAY_RECORD, MediaPlayRecordDocument.class,
        DataType.PROGRAM_PLAY_RECORD, ProgramPlayRecordDocument.class,
        DataType.ONLINE_TIME, TerminalOnlineTimeDocument.class,
        DataType.ABNORMAL_RECONNECT, TerminalAbnormalReconnectDocument.class
    );
    
    @Override
    public String getStorageType() {
        return "MongoDB";
    }
    
    @Override
    public int cleanup(Long deviceId, Set<DataType> dataTypes) {
        int totalDeleted = 0;
        Map<String, Integer> collectionCounts = new HashMap<>();
        
        for (DataType dataType : dataTypes) {
            if (!supports(dataType)) {
                continue;
            }
            
            try {
                int deleted = cleanupCollection(deviceId, dataType);
                if (deleted > 0) {
                    totalDeleted += deleted;
                    collectionCounts.put(dataType.name(), deleted);
                }
            } catch (Exception e) {
                log.error("设备 {} MongoDB集合 {} 清理失败", deviceId, dataType.name(), e);
                // 继续清理其他集合，不中断整个流程
            }
        }
        
        if (!collectionCounts.isEmpty()) {
            log.info("设备 {} MongoDB清理完成: 总删除={}条, 详情={}", deviceId, totalDeleted, collectionCounts);
        }
        
        return totalDeleted;
    }
    
    @Override
    public boolean supports(DataType dataType) {
        return dataType.isMongodbType() && DATA_TYPE_CLASS_MAP.containsKey(dataType);
    }
    
    /**
     * 清理指定集合中的设备数据
     */
    private int cleanupCollection(Long deviceId, DataType dataType) {
        Class<?> documentClass = DATA_TYPE_CLASS_MAP.get(dataType);
        if (documentClass == null) {
            log.warn("未找到数据类型 {} 对应的文档类", dataType);
            return 0;
        }
        
        try {
            // 构建查询条件
            Query query = new Query(Criteria.where("deviceId").is(deviceId));
            
            // 执行删除操作
            var deleteResult = mongoTemplate.remove(query, documentClass);
            long deletedCount = deleteResult.getDeletedCount();
            
            if (deletedCount > 0) {
                log.debug("设备 {} 集合 {} 删除 {} 条记录", 
                    deviceId, dataType.getTarget(), deletedCount);
            }
            
            return (int) deletedCount;
            
        } catch (Exception e) {
            log.error("设备 {} 清理MongoDB集合 {} 失败", deviceId, dataType.name(), e);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR, e);
        }
    }
}