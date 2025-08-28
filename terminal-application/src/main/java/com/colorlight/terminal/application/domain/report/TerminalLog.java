package com.colorlight.terminal.application.domain.report;

import com.colorlight.terminal.application.enums.TerminalLogType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TerminalLog {

    private Long deviceId;

    /**
     * 业务上重新分类的操作类型
     */
    private TerminalLogType operation;

    /**
     * 一级分类
     */
    private String logSubtype1;

    /**
     * 二级分类
     */
    private String logSubtype2;

    /**
     * 三级分类
     */
    private String logSubtype3;

    /**
     * 日志类型
     */
    private String logType;

    /**
     * 参数1
     */
    private String logArg1;

    /**
     * 参数2
     */
    private String logArg2;

    /**
     * 参数3
     */
    private String logArg3;

    /**
     * 参数4
     */
    private String logArg4;

    /**
     * 参数5
     */
    private String logArg5;

    /**
     * 参数6
     */
    private String logArg6;

    /**
     * 设备时间
     */
    private String deviceTime;

    /**
     * 服务器时间
     */
    private LocalDateTime serverTime;
}
