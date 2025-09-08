package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.DownloadingReport;
import com.colorlight.terminal.application.domain.report.ProgramDownloadingReport;
import com.colorlight.terminal.application.domain.report.UpgradePackageDownloadingReport;
import com.colorlight.terminal.application.port.outbound.repository.DownloadingRepository;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalDownloadingDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoDownloadingRepository implements DownloadingRepository {

    private final MongoTemplate mongoTemplate;

    /**
     * 保存设备的下载状态到数据库。
     *
     * @param deviceId 设备ID
     * @param report 下载进度报告，可以是节目下载进度或升级包下载进度
     */
    @Override
    public void saveDeviceDownloadingStatus(Long deviceId, DownloadingReport report) {
        final LocalDateTime now = LocalDateTime.now();
        Query query = new Query(Criteria.where("deviceId").is(deviceId));
        TerminalDownloadingDocument exist = mongoTemplate.findOne(query, TerminalDownloadingDocument.class);
        if (report instanceof ProgramDownloadingReport) {
            saveOrUpdateProgramStatus(deviceId, (ProgramDownloadingReport) report, exist, now);
            log.debug("DownloadingRepository - 保存节目下载进度成功");
        }
        else if (report instanceof UpgradePackageDownloadingReport) {
            saveOrUpdateUpgradeStatus(deviceId, (UpgradePackageDownloadingReport) report, exist, now);
            log.debug("DownloadingRepository - 保存升级包下载进度成功");
        }
        else {
            log.error("DownloadingRepository - DownloadingReport转换失败");
        }
    }

    /**
     * 保存或更新设备的节目下载状态。
     *
     * @param deviceId 设备ID
     * @param report 节目下载进度报告
     * @param exist 已存在的终端下载文档，如果不存在则为null
     * @param updateTime 更新时间
     */
    private void saveOrUpdateProgramStatus(Long deviceId, ProgramDownloadingReport report, TerminalDownloadingDocument exist, LocalDateTime updateTime) {
        if (Objects.nonNull(exist)) {
            exist.setProgramStatus(report);
            exist.setProgramUpdateTime(updateTime);
            exist.setUpdateAt(updateTime);
            mongoTemplate.save(exist);
        }
        else {
            TerminalDownloadingDocument document = new TerminalDownloadingDocument();
            document.setDeviceId(deviceId);
            document.setProgramStatus(report);
            document.setProgramUpdateTime(updateTime);
            document.setUpdateAt(updateTime);
            mongoTemplate.insert(document);
        }
    }

    /**
     * 保存或更新设备的升级包下载状态。
     *
     * @param deviceId 设备ID
     * @param report 升级包下载进度报告
     * @param exist 已存在的终端下载文档，如果不存在则为null
     * @param updateTime 更新时间
     */
    private void saveOrUpdateUpgradeStatus(Long deviceId, UpgradePackageDownloadingReport report, TerminalDownloadingDocument exist, LocalDateTime updateTime) {
        if (Objects.nonNull(exist)) {
            exist.setUpgradeStatus(report);
            exist.setUpgradeUpdateTime(updateTime);
            exist.setUpdateAt(updateTime);
            mongoTemplate.save(exist);
        }
        else {
            TerminalDownloadingDocument document = new TerminalDownloadingDocument();
            document.setDeviceId(deviceId);
            document.setUpgradeStatus(report);
            document.setUpgradeUpdateTime(updateTime);
            document.setUpdateAt(updateTime);
            mongoTemplate.insert(document);
        }
    }
}
