package com.colorlight.terminal.application.dto.record;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备重连记录
 *
 * @author Nan
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TerminalReconnectRecord {

    private Long deviceId;

    /**
     * 开始在线的时间
     */
    private LocalDateTime startOnlineTime;

    /**
     * 掉线前最后上报时间
     */
    private LocalDateTime lastReportTime;

    /**
     * 重连时间
     */
    private LocalDateTime reconnectTime;

    /**
     * 重连IP
     */
    private String reconnectIp;

    /**
     * 重连方式
     */
    private String reconnectSource;
}
