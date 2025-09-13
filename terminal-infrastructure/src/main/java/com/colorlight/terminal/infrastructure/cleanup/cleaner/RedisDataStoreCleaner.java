package com.colorlight.terminal.infrastructure.cleanup.cleaner;

import com.colorlight.terminal.rpc.dto.enums.DataType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Redis数据存储清理器
 * 注意：不清理缓存数据，避免计数错误，使用Redis默认过期策略
 * 
 * @author Nan
 */
@Component
@Slf4j
public class RedisDataStoreCleaner implements DataStoreCleaner {
    
    @Override
    public String getStorageType() {
        return "Redis";
    }
    
    @Override
    public int cleanup(Long deviceId, Set<DataType> dataTypes) {
        if (!dataTypes.contains(DataType.REDIS_CACHE)) {
            return 0;
        }
        
        // Redis缓存数据不主动删除，使用默认过期策略
        // 避免主动删除导致计数不正确或影响正常业务
        log.info("设备 {} Redis缓存使用默认过期策略，无需主动清理", deviceId);
        return 0;
    }
    
    @Override
    public boolean supports(DataType dataType) {
        return dataType.isRedisType();
    }
}