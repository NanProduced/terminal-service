package com.colorlight.terminal.infrastructure.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备账号实体
 * 终端服务认证使用
 *
 * @author Nan
 */
@Data
@TableName("device_terminal_account")
public class TerminalAccountDO {

    /**
     * 主键ID - 雪花算法生成
     */
    @TableId(value = "device_id", type = IdType.ASSIGN_ID)
    private Long deviceId;

    /**
     * 设备名称
     */
    @TableField("account")
    private String account;

    /**
     * 设备密码 - BCrypt加密
     */
    @TableField("password")
    private String password;

    /**
     * 设备状态：
     */
    @TableField("account_status")
    private Byte accountStatus;

    /**
     * 首次登录时间 - 上云时间
     */
    @TableField(value = "first_login_time", updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime firstLoginTime;

    /**
     * 最后登录时间
     */
    @TableField(value = "last_login_time", updateStrategy = FieldStrategy.NOT_NULL)
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    @TableField(value = "last_login_ip", updateStrategy = FieldStrategy.NOT_NULL)
    private String lastLoginIp;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 版本号 - 乐观锁
     */
    @Version
    @TableField("version")
    private Integer version;
}
