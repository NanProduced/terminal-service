package com.colorlight.terminal.infrastructure.persistence.mongodb.document;

import com.colorlight.terminal.application.domain.report.ProgramDownloadingReport;
import com.colorlight.terminal.application.domain.report.UpgradePackageDownloadingReport;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("terminal_downloading_status")
public class TerminalDownloadingDocument {

    @Id
    private String objectId;

    /**
     * 设备Id
     */
    private Long deviceId;

    /**
     * 节目下载状态（最新）
     */
    private ProgramDownloadingReport programStatus;

    /**
     * 节目下载状态更新时间
     */
    private LocalDateTime programUpdateTime;

    /**
     * 升级包下载状态（最新）
     */
    private UpgradePackageDownloadingReport upgradeStatus;

    /**
     * 升级包下载状态更新时间
     */
    private LocalDateTime upgradeUpdateTime;

    /**
     * 文档更新时间
     */
    private LocalDateTime updateAt;
}
