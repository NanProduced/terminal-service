package com.colorlight.terminal.rpc.dto.result;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 终端列表项DTO - 用于终端主列表展示
 * 包含终端主列表需要的关键信息
 *
 * @author Demon
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 终端名称
     */
    private String terminalName;

    /**
     * 亮度
     */
    private Integer brightness;

    /**
     * 音量
     */
    private Integer volume;

    /**
     * 当前节目名称
     */
    private String currentProgramName;

    /**
     * 设备版本号
     */
    private String deviceVersion;

    /**
     * 分辨率
     */
    private String resolution;

    /**
     * 存储空间使用百分比
     */
    private Double storageUsagePercent;

    /**
     * 网络类型
     */
    private String networkType;

    /**
     * 运行时间（秒）
     */
    private Long uptime;

    /**
     * 设备型号
     */
    private String deviceModel;

    /**
     * 序列号
     */
    private String serialNumber;
}
