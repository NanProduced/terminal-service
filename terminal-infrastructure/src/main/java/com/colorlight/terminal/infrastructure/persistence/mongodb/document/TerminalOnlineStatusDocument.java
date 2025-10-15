package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("terminal_online_status")
public class TerminalOnlineStatusDocument {

    @Id
    private String objectId;

    @Indexed(unique = true)
    private Long deviceId;

    /**
     * 累计在线时长（秒）
     */
    private Long totalOnlineTime;

    /**
     * 本轮在线起始时间
     */
    private LocalDateTime onlineStartTime;

    /**
     * 当前在线状态字符串表示
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
