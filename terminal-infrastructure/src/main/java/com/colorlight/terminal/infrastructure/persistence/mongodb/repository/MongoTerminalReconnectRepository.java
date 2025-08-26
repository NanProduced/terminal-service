package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.dto.record.TerminalReconnectRecord;
import com.colorlight.terminal.application.port.outbound.repository.TerminalReconnectRepository;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.TerminalRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalAbnormalReconnectDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MongoTerminalReconnectRepository implements TerminalReconnectRepository {

    private final MongoTemplate mongoTemplate;
    private final TerminalRecordConverter terminalRecordConverter;

    @Override
    public void saveReconnectRecord(TerminalReconnectRecord record) {
        try {
            TerminalAbnormalReconnectDocument terminalAbnormalReconnectDocument = terminalRecordConverter.convertToTerminalReconnectDocument(record);
            mongoTemplate.save(terminalAbnormalReconnectDocument);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.MONGO_DB_ERROR, e);
        }
    }
}
