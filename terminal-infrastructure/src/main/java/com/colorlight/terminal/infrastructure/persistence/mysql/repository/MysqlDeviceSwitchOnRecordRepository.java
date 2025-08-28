package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.colorlight.terminal.application.port.outbound.repository.TerminalSwitchOnRecordRepository;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceSwitchOnRecordDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.DeviceSwitchOnRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MysqlDeviceSwitchOnRecordRepository implements TerminalSwitchOnRecordRepository {

    private final DeviceSwitchOnRecordMapper deviceSwitchOnRecordMapper;

    @Override
    public void saveSwitchOnRecord(Long deviceId, Long timestamp) {
        try {
            DeviceSwitchOnRecordDO deviceSwitchOnRecordDO = new DeviceSwitchOnRecordDO(deviceId, timestamp);
            deviceSwitchOnRecordMapper.insert(deviceSwitchOnRecordDO);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.MYSQL_ERROR, e);
        }
    }
}
