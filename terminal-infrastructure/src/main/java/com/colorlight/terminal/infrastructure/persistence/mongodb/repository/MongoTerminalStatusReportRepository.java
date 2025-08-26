package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.commons.utils.BeanUtils;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalStatusReportDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 终端上报数据的MongoDB存储实现类
 *
 * @author Demon
 */
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
    public Optional<TerminalStatusReport> findByDeviceId(Long deviceId) {
        Query query = Query.query(Criteria.where("deviceId").is(deviceId));
        TerminalStatusReportDocument document = mongoTemplate.findOne(query, TerminalStatusReportDocument.class);
        return Optional.ofNullable(document)
                .map(TerminalStatusReportDocument::getTerminalStatusReport);
    }

    @Override
    public Map<Long, TerminalStatusReport> findByDeviceIds(List<Long> deviceIds) {
        Query query = Query.query(Criteria.where("deviceId").in(deviceIds));
        List<TerminalStatusReportDocument> documents = mongoTemplate.find(query, TerminalStatusReportDocument.class);

        return documents.stream()
                .filter(doc -> doc.getDeviceId() != null && doc.getTerminalStatusReport() != null)
                .collect(Collectors.toMap(
                        TerminalStatusReportDocument::getDeviceId,
                        TerminalStatusReportDocument::getTerminalStatusReport,
                        (existing, replacement) -> replacement // 如果有重复key，使用新值
                ));
    }
}
