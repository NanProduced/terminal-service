package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.application.port.outbound.repository.GpsRecordRepository;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import com.colorlight.terminal.infrastructure.generator.GpsIndexesGenerator;
import com.colorlight.terminal.infrastructure.persistence.mongodb.converter.GpsRecordConverter;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.GpsRecordDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoGpsRecordRepository类实现了GpsRecordRepository接口，提供了基于MongoDB的GPS记录存储功能。
 * 该类使用MongoTemplate进行数据库操作，并通过批处理方式高效地保存GPS记录。
 *
 * @author Nan
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoGpsRecordRepository implements GpsRecordRepository {

    private final MongoTemplate mongoTemplate;
    private final GpsIndexesGenerator gpsIndexesGenerator;
    private final GpsRecordConverter gpsRecordConverter;

    /**
     * 批量插入gps记录
     * @param reports 上报数据
     */
    @Override
    public void batchSaveGpsRecord(List<GpsReport> reports) {
        if (reports == null || reports.isEmpty()) {
            log.debug("GpsRepository - 空的GPS记录列表，跳过批量保存");
            return;
        }

        try {
            // 数据转换 索引值添加
            List<GpsRecordDocument> documents = gpsRecordConverter.convertToGpsDocumentList(reports, gpsIndexesGenerator);
            if (documents.isEmpty()) {
                log.warn("GpsRepository - 转换后的文档列表为空，原始记录数: {}", reports.size());
                return;
            }
            
            // unordered模式，不保证执行顺序，最大化性能
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, GpsRecordDocument.class);
            bulkOps.insert(documents);
            
            // 执行批量操作
            var result = bulkOps.execute();
            
            // 记录操作结果
            int insertedCount = result.getInsertedCount();
            if (insertedCount != documents.size()) {
                log.warn("GpsRepository - 部分GPS记录保存失败: 期望={}, 实际={}", documents.size(), insertedCount);
            } else {
                log.info("GpsRepository - GPS数据批量入库完成: size={}", insertedCount);
            }
            
        } catch (Exception e) {
            log.error("GpsRepository - GPS数据批量入库失败: size={}", reports.size(), e);
            // 重新抛出异常，让上层处理重试逻辑
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR, "GPS数据批量保存失败", e);
        }
    }
}
