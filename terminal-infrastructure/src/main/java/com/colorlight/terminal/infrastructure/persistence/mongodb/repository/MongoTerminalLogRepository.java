package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.HistoryLogFileList;
import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.port.outbound.repository.TerminalLogRepository;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.TerminalLogDocConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.DeviceLocalLogDocument;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalLogDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.colorlight.terminal.application.domain.CommonConstant.Device.DEVICE_ID;

@Repository
@RequiredArgsConstructor
public class MongoTerminalLogRepository implements TerminalLogRepository {

    private final MongoTemplate mongoTemplate;
    private final TerminalLogDocConverter terminalLogDocConverter;

    /**
     * 保存终端日志
     * @param terminalLog 终端日志domain
     */
    @Override
    public void saveTerminalLog(TerminalLog terminalLog) {
        TerminalLogDocument terminalLogDocument = terminalLogDocConverter.convertToTerminalLogDocument(terminalLog);
        mongoTemplate.save(terminalLogDocument);
    }

    @Override
    public void batchSaveTerminalLog(List<TerminalLog> terminalLogs) {
        List<TerminalLogDocument> terminalLogDocuments = terminalLogDocConverter.convertToTerminalLogDocumentList(terminalLogs);
        mongoTemplate.insertAll(terminalLogDocuments);
    }

    @Override
    public void saveHistoryLogFileList(Long deviceId, List<HistoryLogFileList.HistoryLogFile> files) {
        final List<DeviceLocalLogDocument.Log> logs = files.stream().map(e -> new DeviceLocalLogDocument.Log(e.getName(), e.getSize(), e.getLastModify())).toList();
        DeviceLocalLogDocument document = DeviceLocalLogDocument.builder()
                .deviceId(deviceId)
                .logs(logs)
                .updateTime(LocalDateTime.now())
                .build();
        Query query = Query.query(Criteria.where(DEVICE_ID).is(document.getDeviceId()));
        Update update = new Update()
                .set("logs", document.getLogs())
                .set("updateTime", document.getUpdateTime());
        mongoTemplate.upsert(query, update, DeviceLocalLogDocument.class);
    }
}
