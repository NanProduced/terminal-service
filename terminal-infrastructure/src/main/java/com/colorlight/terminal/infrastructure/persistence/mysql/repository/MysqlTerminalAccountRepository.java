package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.infrastructure.persistence.mysql.converter.TerminalAccountConverter;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.TerminalAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MysqlTerminalAccountRepository implements TerminalAccountRepository {

    private final TerminalAccountMapper terminalAccountMapper;
    private final TerminalAccountConverter terminalAccountConverter;

    @Override
    public TerminalAccount findTerminalAccountByName(String accountName) {
        TerminalAccountDO terminalAccountDO = terminalAccountMapper.selectOne(new LambdaQueryWrapper<TerminalAccountDO>()
                .eq(TerminalAccountDO::getAccount, accountName));
        return terminalAccountConverter.toDomain(terminalAccountDO);
    }

    @Override
    public TerminalAccount findTerminalAccountById(Long deviceId) {
        return terminalAccountConverter.toDomain(terminalAccountMapper.selectById(deviceId));
    }

    @Override
    public boolean ifExistTerminalAccount(String accountName) {
        return terminalAccountMapper.exists(new LambdaQueryWrapper<TerminalAccountDO>()
                .eq(TerminalAccountDO::getAccount, accountName));
    }

    @Override
    public TerminalAccount save(TerminalAccount terminalAccount) {
        LocalDateTime now = LocalDateTime.now();
        TerminalAccountDO terminalAccountDO = terminalAccountConverter.toDO(terminalAccount);

        if (terminalAccountDO.getDeviceId() == null) {
            // 新增
            terminalAccountDO.setCreateTime(now);
            terminalAccountDO.setUpdateTime(now);
            terminalAccountMapper.insert(terminalAccountDO);
        } else {
            // 更新
            terminalAccountDO.setUpdateTime(now);
            terminalAccountMapper.updateById(terminalAccountDO);
        }
        
        return terminalAccountConverter.toDomain(terminalAccountDO);
    }

    @Override
    public int updateLoginTimeImmediate(Long deviceId, String clientIp, LocalDateTime loginTime) {
        try {
            int rows = terminalAccountMapper.updateLoginTimeImmediate(deviceId, clientIp, loginTime);
            if (rows > 0) {
                log.debug("立即更新登录时间成功: deviceId={}, clientIp={}, loginTime={}", deviceId, clientIp, loginTime);
            } else {
                log.warn("立即更新登录时间无影响: deviceId={}, 设备可能不存在", deviceId);
            }
            return rows;
        } catch (Exception e) {
            log.error("立即更新登录时间失败: deviceId={}, clientIp={}", deviceId, clientIp, e);
            throw e;
        }
    }

    @Override
    public int updateLoginTime(Long deviceId, String clientIp, LocalDateTime loginTime) {
        try {
            int rows = terminalAccountMapper.updateLoginTime(deviceId, clientIp, loginTime);
            if (rows > 0) {
                log.debug("批量更新登录时间成功: deviceId={}, clientIp={}, loginTime={}", deviceId, clientIp, loginTime);
            } else {
                log.debug("批量更新登录时间无影响: deviceId={}, 设备可能不存在", deviceId);
            }
            return rows;
        } catch (Exception e) {
            log.error("批量更新登录时间失败: deviceId={}, clientIp={}", deviceId, clientIp, e);
            throw e;
        }
    }
}
