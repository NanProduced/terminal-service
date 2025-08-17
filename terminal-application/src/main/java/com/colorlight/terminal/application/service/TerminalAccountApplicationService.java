package com.colorlight.terminal.application.service;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.dto.request.CreateTerminalAccountRequest;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.application.port.inbound.account.TerminalAccountUseCase;
import com.colorlight.terminal.application.port.outbound.auth.EncoderPort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 终端账号应用服务
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalAccountApplicationService implements TerminalAccountUseCase {
    
    private final TerminalAccountRepository terminalAccountRepository;
    private final EncoderPort encoderPort;
    
    @Override
    @Transactional
    public TerminalAccount createTerminalAccount(CreateTerminalAccountRequest request) {
        log.debug("ApplicationService - 开始创建终端账号, accountName: {}", request.getAccountName());
        
        // 1. 检查账号名是否重复
        boolean exist = terminalAccountRepository.ifExistTerminalAccount(request.getAccountName());
        if (exist) {
            throw new BusinessException(CommonErrorCode.TERMINAL_ACCOUNT_EXIST);
        }
        
        // 2. 密码加密
        String encodedPassword = encoderPort.encodeByPasswordEncoder(request.getRawPassword());
        
        // 3. 创建域对象
        TerminalAccount terminalAccount = TerminalAccount.builder()
                .accountName(request.getAccountName())
                .passwordHash(encodedPassword)
                .status(TerminalAccountStatus.ENABLE)
                .build();
        
        // 4. 保存到数据库
        TerminalAccount savedAccount = terminalAccountRepository.save(terminalAccount);
        
        log.info("ApplicationService - 终端账号创建成功, deviceId: {}, accountName: {}",
                savedAccount.getDeviceId(), savedAccount.getAccountName());
        
        return savedAccount;
    }

}