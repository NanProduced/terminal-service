package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.dto.record.TerminalOnlineTimeRecord;
import com.colorlight.terminal.application.port.outbound.repository.TerminalOnlineTimeRepository;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.TerminalOnlineTimeConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalOnlineTimeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;


@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoTerminalOnlineTimeRepository implements TerminalOnlineTimeRepository {

    private final TerminalOnlineTimeConverter terminalOnlineTimeConverter;
    private final MongoTemplate mongoTemplate;

    @Override
    public void saveTerminalOnlineTime(TerminalOnlineTimeRecord record) {
        try {
            TerminalOnlineTimeDocument document = terminalOnlineTimeConverter.convertToTerminalOnlineTimeDocument(record);
            mongoTemplate.save(document);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.MONGO_DB_ERROR, e);
        }
    }
}
