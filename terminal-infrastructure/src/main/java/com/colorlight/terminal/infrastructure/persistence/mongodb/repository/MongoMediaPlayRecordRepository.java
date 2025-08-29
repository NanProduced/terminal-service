package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.report.MediaPlayRecordReport;
import com.colorlight.terminal.application.port.outbound.repository.MediaPlayRecordRepository;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.MediaPlayRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.MediaPlayRecordDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 素材统计MongoDB存储实现
 *
 * @author Nan
 */
@Repository
@RequiredArgsConstructor
public class MongoMediaPlayRecordRepository implements MediaPlayRecordRepository {

    private final MongoTemplate mongoTemplate;
    private final MediaPlayRecordConverter mediaPlayRecordConverter;

    /**
     * 保存素材播放记录
     * @param deviceId 设备Id
     * @param reports 上报记录
     */
    @Override
    public void saveMediaPlayRecords(Long deviceId, List<MediaPlayRecordReport> reports) {
        List<MediaPlayRecordDocument> mediaPlayRecordDocuments = mediaPlayRecordConverter.convertToMediaPlayRecordDocumentList(deviceId, reports);
        try {
            mongoTemplate.insertAll(mediaPlayRecordDocuments);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.MYSQL_ERROR, e);
        }
    }
}
