package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.outbound.repository.TerminalStatusReportRepository;
import com.colorlight.terminal.commons.utils.BeanUtils;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalStatusReportDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import static com.colorlight.terminal.application.domain.CommonConstant.Device.DEVICE_ID;

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
     * @param rawReportJson 原始上报JSON字符串
     */
    @Override
    public void saveTerminalStatusReport(Long deviceId, TerminalStatusReport report, String rawReportJson) {
        final LocalDateTime now = LocalDateTime.now();
        if (report == null || StringUtils.isBlank(rawReportJson)) {
            legacySave(deviceId, report, now);
            return;
        }

        JsonNode rawNode;
        try {
            rawNode = JsonUtils.fromJson(rawReportJson);
        } catch (Exception e) {
            log.warn("ReportRepository - 解析原始上报JSON失败，回退到兼容保存逻辑: deviceId={}", deviceId, e);
            legacySave(deviceId, report, now);
            return;
        }

        if (rawNode == null || !rawNode.isObject()) {
            legacySave(deviceId, report, now);
            return;
        }

        Query query = Query.query(Criteria.where(DEVICE_ID).is(deviceId));
        Update update = buildUpdateFromRawJson(report, rawNode, now, deviceId);
        mongoTemplate.upsert(query, update, TerminalStatusReportDocument.class);
    }

    @Override
    public Optional<TerminalStatusReport> getReportData(Long deviceId) {
        Query query = Query.query(Criteria.where(DEVICE_ID).is(deviceId));
        try {
            return Optional.ofNullable(mongoTemplate.findOne(query, TerminalStatusReportDocument.class))
                    .map(TerminalStatusReportDocument::getTerminalStatusReport);
        } catch (Exception e) {
            log.error("ReportRepository - 获取终端上报数据失败: deviceId={}", deviceId, e);
            return Optional.empty();
        }
    }

    /**
     * 兼容保存逻辑（兜底方法，设备一般不会上报空数据）
     * @param deviceId 设备ID
     * @param report 上报数据
     * @param now 当前时间
     */
    private void legacySave(Long deviceId, TerminalStatusReport report, LocalDateTime now) {
        Query query = Query.query(Criteria.where(DEVICE_ID).is(deviceId));
        TerminalStatusReportDocument existDoc = mongoTemplate.findOne(query, TerminalStatusReportDocument.class);
        if (Objects.nonNull(existDoc)) {
            if (report != null) {
                TerminalStatusReport existingReport = existDoc.getTerminalStatusReport();
                if (existingReport != null) {
                    BeanUtils.copyNonNullProperties(report, existingReport);
                } else {
                    existDoc.setTerminalStatusReport(report);
                }
            }
            existDoc.setUpdateTime(now);
            mongoTemplate.save(existDoc);
        } else {
            TerminalStatusReportDocument newDoc = new TerminalStatusReportDocument();
            newDoc.setDeviceId(deviceId);
            newDoc.setTerminalStatusReport(report);
            newDoc.setUpdateTime(now);
            mongoTemplate.save(newDoc);
        }
    }

    private Update buildUpdateFromRawJson(TerminalStatusReport report, JsonNode rawNode, LocalDateTime now, Long deviceId) {
        ObjectMapper mapper = JsonUtils.getDefaultObjectMapper();
        JsonNode reportNode = mapper.valueToTree(report);
        JsonUtils.JsonPathSummary pathSummary = JsonUtils.collectPaths(rawNode);

        Update update = new Update();
        for (String path : pathSummary.getLeafPaths()) {
            JsonNode valueNode = JsonUtils.getNodeByPath(reportNode, path);
            if (valueNode == null || valueNode.isMissingNode()) {
                continue;
            }
            update.set("terminalStatusReport." + path, mapper.convertValue(valueNode, Object.class));
        }

        for (String objectPath : pathSummary.getObjectPaths()) {
            String reportTimePath = objectPath + "._report_time";
            JsonNode reportTimeNode = JsonUtils.getNodeByPath(reportNode, reportTimePath);
            if (reportTimeNode != null && !reportTimeNode.isMissingNode()) {
                update.set("terminalStatusReport." + reportTimePath, mapper.convertValue(reportTimeNode, Object.class));
            }
        }

        if (report.getClientIp() != null) {
            update.set("terminalStatusReport.clientIp", report.getClientIp());
        }
        update.set("updateTime", now);
        update.setOnInsert(DEVICE_ID, deviceId);
        return update;
    }

}
