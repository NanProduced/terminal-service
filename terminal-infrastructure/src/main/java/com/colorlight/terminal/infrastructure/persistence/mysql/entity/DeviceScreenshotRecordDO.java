package com.colorlight.terminal.infrastructure.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_screenshot_record")
public class DeviceScreenshotRecordDO {

    /**
     * 设备Id
     */
    @TableId(value = "device_id", type = IdType.ASSIGN_ID)
    private Long deviceId;

    /**
     * 上传时间
     */
    @TableField("upload_time")
    private LocalDateTime uploadTime;

    /**
     * 对象key（MinIo）
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;



}
