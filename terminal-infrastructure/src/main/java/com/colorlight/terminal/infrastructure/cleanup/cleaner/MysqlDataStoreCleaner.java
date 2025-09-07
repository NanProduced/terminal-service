package com.colorlight.terminal.infrastructure.cleanup.cleaner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceScreenshotRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceScreenshotRecordMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.TerminalAccountMapper;
import com.colorlight.terminal.infrastructure.storage.minio.service.MinioScreenshotStorageService;
import com.colorlight.terminal.rpc.dto.enums.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * MySQL数据存储清理器
 * 清理MySQL中的设备相关数据
 * 
 * @author Nan
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MysqlDataStoreCleaner implements DataStoreCleaner {
    
    private final DeviceScreenshotRecordMapper screenshotRecordMapper;
    private final TerminalAccountMapper terminalAccountMapper;
    private final MinioScreenshotStorageService minioService;
    
    @Override
    public String getStorageType() {
        return "MySQL";
    }
    
    @Override
    public int cleanup(Long deviceId, Set<DataType> dataTypes) {
        int totalDeleted = 0;
        
        // 清理设备账号信息 (必须删除，不受配置影响)
        totalDeleted += cleanupDeviceAccount(deviceId);
        
        // 清理截图记录 (同时删除MinIO文件)
        if (dataTypes.contains(DataType.SCREENSHOT_RECORD)) {
            totalDeleted += cleanupScreenshotRecords(deviceId);
        }
        
        // 清理开机记录 (如果有对应的Mapper)
        if (dataTypes.contains(DataType.SWITCH_RECORD)) {
            totalDeleted += cleanupSwitchRecords(deviceId);
        }
        
        return totalDeleted;
    }
    
    @Override
    public boolean supports(DataType dataType) {
        return dataType.isMysqlType();
    }
    
    /**
     * 清理截图记录
     * 同时删除MinIO中的截图文件
     */
    private int cleanupScreenshotRecords(Long deviceId) {
        try {
            // 查询需要删除的截图记录
            List<DeviceScreenshotRecordDO> records = screenshotRecordMapper.selectList(
                new LambdaQueryWrapper<DeviceScreenshotRecordDO>()
                    .eq(DeviceScreenshotRecordDO::getDeviceId, deviceId)
            );
            
            if (records.isEmpty()) {
                log.debug("设备 {} 无截图记录需要清理", deviceId);
                return 0;
            }
            
            // 删除MinIO中的文件
            int fileDeletedCount = 0;
            for (DeviceScreenshotRecordDO record : records) {
                String objectKey = record.getObjectKey();
                if (objectKey != null && !objectKey.trim().isEmpty()) {
                    try {
                        minioService.deleteObject(objectKey);
                        fileDeletedCount++;
                        log.debug("删除MinIO文件: {}", objectKey);
                    } catch (Exception e) {
                        log.warn("删除MinIO文件失败: objectKey={}, deviceId={}", objectKey, deviceId, e);
                        // 继续删除其他文件，不影响整体流程
                    }
                }
            }
            
            // 删除数据库记录
            int dbDeletedCount = screenshotRecordMapper.delete(
                new LambdaQueryWrapper<DeviceScreenshotRecordDO>()
                    .eq(DeviceScreenshotRecordDO::getDeviceId, deviceId)
            );
            
            log.info("设备 {} 截图记录清理完成: DB删除={}条, MinIO删除={}个文件", 
                deviceId, dbDeletedCount, fileDeletedCount);
            
            return dbDeletedCount;
            
        } catch (Exception e) {
            log.error("设备 {} 截图记录清理失败", deviceId, e);
            throw new RuntimeException("截图记录清理失败", e);
        }
    }
    
    /**
     * 清理设备账号信息
     * 设备删除时必须清理账号信息
     */
    private int cleanupDeviceAccount(Long deviceId) {
        try {
            // 检查账号是否存在
            TerminalAccountDO account = terminalAccountMapper.selectById(deviceId);
            if (account == null) {
                log.debug("设备 {} 无账号信息需要清理", deviceId);
                return 0;
            }
            
            // 删除账号信息
            int deleted = terminalAccountMapper.deleteById(deviceId);
            
            if (deleted > 0) {
                log.info("设备 {} 账号信息清理完成: account={}", deviceId, account.getAccount());
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("设备 {} 账号信息清理失败", deviceId, e);
            throw new RuntimeException("设备账号信息清理失败", e);
        }
    }
    
    /**
     * 清理开机记录
     * 注意: 这里假设有DeviceSwitchOnRecordMapper，需要根据实际情况调整
     */
    private int cleanupSwitchRecords(Long deviceId) {
        try {
            // TODO: 根据实际的DeviceSwitchOnRecordMapper实现
            // 目前先返回0，实际项目中需要实现对应的清理逻辑
            log.debug("设备 {} 开机记录清理 (待实现)", deviceId);
            return 0;
        } catch (Exception e) {
            log.error("设备 {} 开机记录清理失败", deviceId, e);
            throw new RuntimeException("开机记录清理失败", e);
        }
    }
}