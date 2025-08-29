package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.ProgramPlayRecordReport;
import com.colorlight.terminal.application.port.outbound.repository.ProgramPlayRecordRepository;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.ProgramPlayRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.ProgramPlayRecordDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 节目播放上报统计MongoDB存储实现
 *
 * @author Nan
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoProgramPlayRecordRepository implements ProgramPlayRecordRepository {

    private final MongoTemplate mongoTemplate;
    private final ProgramPlayRecordConverter programPlayRecordConverter;

    @Override
    public void saveProgramPlayRecords(List<ProgramPlayRecordReport> reports) {

        List<ProgramPlayRecordDocument> documents = programPlayRecordConverter.convertToProgramRecordDocumentList(reports);
        try {
            mongoTemplate.insertAll(documents);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.MONGO_DB_ERROR, e);
        }

    }
}


