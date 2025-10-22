package com.colorlight.terminal.infrastructure.persistence.mongodb.repository;

import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.port.outbound.repository.TerminalOnlineStatusRepository;
import com.colorlight.terminal.infrastructure.persistence.mongodb.document.TerminalOnlineStatusDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

import static com.colorlight.terminal.application.domain.CommonConstant.Device.CREATED_AT;
import static com.colorlight.terminal.application.domain.CommonConstant.Device.DEVICE_ID;
import static com.colorlight.terminal.application.domain.CommonConstant.Device.ONLINE_START_TIME;
import static com.colorlight.terminal.application.domain.CommonConstant.Device.STATUS;
import static com.colorlight.terminal.application.domain.CommonConstant.Device.TOTAL_ONLINE_TIME;
import static com.colorlight.terminal.application.domain.CommonConstant.Device.UPDATED_AT;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoTerminalOnlineStatusRepository implements TerminalOnlineStatusRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void upsertOnlineState(Long deviceId, OnlineStatus status, LocalDateTime onlineStartTime) {
        if (deviceId == null || status == null) {
            log.warn("TerminalOnlineStatus - 跳过处理，无效参数: deviceId={}, status={}", deviceId, status);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Query query = Query.query(Criteria.where(DEVICE_ID).is(deviceId));
        Update update = new Update()
                .set(STATUS, normalizeStatus(status))
                .set(ONLINE_START_TIME, onlineStartTime)
                .set(UPDATED_AT, now)
                .setOnInsert(DEVICE_ID, deviceId)
                .setOnInsert(TOTAL_ONLINE_TIME, 0L)
                .setOnInsert(CREATED_AT, now);

        try {
            mongoTemplate.upsert(query, update, TerminalOnlineStatusDocument.class);
            log.debug("TerminalOnlineStatus - upsert状态成功: deviceId={}, status={}", deviceId, normalizeStatus(status));
        } catch (Exception e) {
            log.error("TerminalOnlineStatus - upsert状态失败: deviceId={}, status={}", deviceId, normalizeStatus(status), e);
        }
    }

    @Override
    public void finalizeOnlineSession(Long deviceId, LocalDateTime onlineStartTime, long sessionDurationSecs) {
        if (deviceId == null || sessionDurationSecs <= 0) {
            log.warn("TerminalOnlineStatus - 跳过在线时长累计处理, 无效的参数: deviceId={}, duration={}", deviceId, sessionDurationSecs);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Query query = Query.query(Criteria.where(DEVICE_ID).is(deviceId));
        Update update = new Update()
                .inc(TOTAL_ONLINE_TIME, sessionDurationSecs)
                .set(STATUS, OnlineStatus.OFFLINE.name())
                .set(ONLINE_START_TIME, onlineStartTime)
                .set(UPDATED_AT, now)
                .setOnInsert(DEVICE_ID, deviceId)
                .setOnInsert(CREATED_AT, now);

        try {
            mongoTemplate.upsert(query, update, TerminalOnlineStatusDocument.class);
            log.debug("TerminalOnlineStatus - 在线时长累计成功: deviceId={}, addedDuration={}s", deviceId, sessionDurationSecs);
        } catch (Exception e) {
            log.error("TerminalOnlineStatus - 在线时长累计失败: deviceId={}, addedDuration={}s", deviceId, sessionDurationSecs, e);
        }
    }

    private String normalizeStatus(OnlineStatus status) {
        return status == OnlineStatus.OFFLINE ? OnlineStatus.OFFLINE.name() : OnlineStatus.ONLINE.name();
    }
}
