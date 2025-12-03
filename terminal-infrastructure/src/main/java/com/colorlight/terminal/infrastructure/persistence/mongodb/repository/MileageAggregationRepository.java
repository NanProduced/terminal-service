package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.infrastructure.persistence.mongodb.document.MileageAggDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 里程聚合数据Repository
 *
 * @author Claude
 */
@Repository
@RequiredArgsConstructor
public class MileageAggregationRepository {

    private final MongoTemplate mongoTemplate;

    public void batchSave(List<MileageAggDocument> documents) {
        documents.forEach(mongoTemplate::save);
    }

    /**
     * 批量查询多个时间窗口的聚合文档
     *
     * @param deviceId     设备ID
     * @param windowStarts 窗口起始时间集合
     * @return 聚合文档列表
     */
    public List<MileageAggDocument> findByDeviceIdAndWindowStartTimeIn(Long deviceId, Collection<LocalDateTime> windowStarts) {
        if (windowStarts == null || windowStarts.isEmpty()) {
            return Collections.emptyList();
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("deviceId").is(deviceId)
                .and("windowStartTime").in(windowStarts));

        return mongoTemplate.find(query, MileageAggDocument.class);
    }
}
