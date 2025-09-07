package com.colorlight.terminal.infrastructure.cleanup.cleaner;

import com.colorlight.terminal.rpc.dto.enums.DataType;

import java.util.Set;

/**
 * 数据存储清理器接口
 * 不同存储类型的清理器实现此接口
 * 
 * @author Nan
 */
public interface DataStoreCleaner {
    
    /**
     * 获取存储类型标识
     * @return 存储类型 (如: MySQL, MongoDB, Redis)
     */
    String getStorageType();
    
    /**
     * 清理指定设备的数据
     * @param deviceId 设备ID
     * @param dataTypes 需要清理的数据类型
     * @return 删除的记录总数
     */
    int cleanup(Long deviceId, Set<DataType> dataTypes);
    
    /**
     * 检查是否支持指定的数据类型
     * @param dataType 数据类型
     * @return true表示支持
     */
    boolean supports(DataType dataType);
}