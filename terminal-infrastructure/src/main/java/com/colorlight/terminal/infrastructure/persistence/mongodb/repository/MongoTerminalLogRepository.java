package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.TerminalLog;
import com.colorlight.terminal.application.port.outbound.repository.TerminalLogRepository;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.TerminalLogDocConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalLogDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
