package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.infrastructure.persistence.mysql.converter.TerminalAccountConverter;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.TerminalAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

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
}
