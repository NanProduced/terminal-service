package com.colorlight.terminal.infrastructure.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备开关机记录entity
 * <P>和原逻辑保持一致，仅取消分表</P>
 *
 * @author Nan
 */
@Data
@TableName("device_switch_on_record")
public class DeviceSwitchOnRecordDO {

    /**
     * 主键Id - 自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private Long deviceId;

    /**
     * 开机时间戳
     */
    @TableField("switch_on_utc")
    private Long switchOnUtc;

    @TableField("create_time")
    private LocalDateTime createTime;

    public DeviceSwitchOnRecordDO(Long deviceId, Long switchOnUtc) {
        this.deviceId = deviceId;
        this.switchOnUtc = switchOnUtc;
        this.createTime = LocalDateTime.now();
    }
}
