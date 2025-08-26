package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 专门记录终端短时间内掉线并重连的记录
 * <p>额外的记录/用于异常排查</p>
 *
 * @author Nan
 */
@Data
@Builder
@Document("terminal_abnormal_reconnect")
public class TerminalAbnormalReconnectDocument {

    private String objectId;

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
