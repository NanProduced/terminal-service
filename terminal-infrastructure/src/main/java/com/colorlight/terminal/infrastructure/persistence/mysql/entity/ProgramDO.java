package com.colorlight.terminal.infrastructure.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 节目表DO
 * <p><b>这张表在主服务维护，设备服务仅查询</b></p>
 * <p>仅包含设备服务所需字段</p>
 *
 * @author Nan
 */
@Data
@TableName("program")
public class ProgramDO {

    @TableId("id")
    private Integer programId;

    @TableField("author_id")
    private Integer authorId;

    @TableField("title")
    private String programName;

    @TableField("name")
    private String vsnName;

    @TableField("status")
    private String status;

    @TableField("is_delete_flag")
    private Integer isDelete;






}
