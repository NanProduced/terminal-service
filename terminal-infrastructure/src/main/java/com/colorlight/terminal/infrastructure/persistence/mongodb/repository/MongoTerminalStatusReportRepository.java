package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.commons.utils.BeanUtils;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalStatusReportDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 终端上报数据的MongoDB存储实现类
 *
 * @author Nan
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoTerminalStatusReportRepository implements TerminalStatusReportRepository {

    private final MongoTemplate mongoTemplate;

    /**
     * 保存或更新led_status上报数据
     * @param deviceId 设备ID
     * @param report 上报数据
     */
    @Override
    public void saveTerminalStatusReport(Long deviceId, TerminalStatusReport report) {
        final LocalDateTime now = LocalDateTime.now();
        Query query = Query.query(Criteria.where("deviceId").is(deviceId));
        TerminalStatusReportDocument existDoc = mongoTemplate.findOne(query, TerminalStatusReportDocument.class);
        if (Objects.nonNull(existDoc)) {
            BeanUtils.copyNonNullProperties(report, existDoc.getTerminalStatusReport());
            existDoc.setUpdateTime(now);
            mongoTemplate.save(existDoc);
        }
        else {
            TerminalStatusReportDocument newDoc = new TerminalStatusReportDocument();
            newDoc.setDeviceId(deviceId);
            newDoc.setTerminalStatusReport(report);
            newDoc.setUpdateTime(now);
            mongoTemplate.save(newDoc);
        }
    }

    @Override
    public Optional<TerminalStatusReport> getReportData(Long deviceId) {
        Query query = Query.query(Criteria.where("deviceId").is(deviceId));
        try {
            return Optional.ofNullable(mongoTemplate.findOne(query, TerminalStatusReportDocument.class))
                    .map(TerminalStatusReportDocument::getTerminalStatusReport);
        } catch (Exception e) {
            log.error("ReportRepository - 获取终端上报数据失败: deviceId={}", deviceId, e);
            return Optional.empty();
        }
    }
}
