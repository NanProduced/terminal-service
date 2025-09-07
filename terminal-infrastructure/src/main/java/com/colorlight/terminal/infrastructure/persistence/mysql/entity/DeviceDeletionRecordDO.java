package com.colorlight.terminal.infrastructure.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备删除记录表
 * 用于记录设备数据清理操作，供运维查看
 * 
 * @author Nan
 */
@Data
@TableName("device_deletion_record")
public class DeviceDeletionRecordDO {
    
    /**
     * 记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 设备ID
     */
    @TableField("device_id")
    private Long deviceId;
    
    /**
     * 清理模式: ALL/INCLUDE/EXCLUDE
     */
    @TableField("cleanup_mode")
    private String cleanupMode;
    
    /**
     * 数据类型配置 (JSON格式存储)
     * 例如: ["TERMINAL_LOG", "GPS_RECORD"] 
     */
    @TableField("data_types")
    private String dataTypes;
    
    /**
     * 清理状态: PENDING/RUNNING/SUCCESS/FAILED/PARTIAL
     */
    @TableField("status")
    private String status;
    
    /**
     * 清理开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;
    
    /**
     * 清理结束时间
     */
    @TableField("end_time") 
    private LocalDateTime endTime;
    
    /**
     * 清理结果统计 (JSON格式)
     * 例如: {"mysql": 100, "mongodb": 500, "redis": 10}
     */
    @TableField("deleted_counts")
    private String deletedCounts;
    
    /**
     * 失败原因 (状态为FAILED/PARTIAL时记录)
     */
    @TableField("error_message")
    private String errorMessage;
    
    /**
     * 执行时长(毫秒)
     */
    @TableField("execution_time_ms")
    private Long executionTimeMs;
    
    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}