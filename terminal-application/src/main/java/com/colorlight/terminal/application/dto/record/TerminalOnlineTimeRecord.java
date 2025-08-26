package com.colorlight.terminal.application.dto.record;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 终端在线时长记录
 *
 * @author Nan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TerminalOnlineTimeRecord {

    private Long deviceId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
