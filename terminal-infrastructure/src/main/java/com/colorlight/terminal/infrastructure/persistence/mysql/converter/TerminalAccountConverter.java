package com.colorlight.terminal.infrastructure.persistence.mysql.converter;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

/**
 * 设备账号领域对象与数据对象转换器
 * 
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface TerminalAccountConverter {

    /**
     * DO转换为领域对象
     * 
     * @param terminalAccountDO 数据对象
     * @return 领域对象
     */
    @Mapping(source = "account", target = "accountName")
    @Mapping(source = "accountStatus", target = "status", qualifiedByName = "byteToStatus")
    @Mapping(source = "password", target = "passwordHash")
    TerminalAccount toDomain(TerminalAccountDO terminalAccountDO);

    /**
     * 领域对象转换为DO
     * 
     * @param terminalAccount 领域对象
     * @return 数据对象
     */
    @Mapping(source = "accountName", target = "account")
    @Mapping(source = "status", target = "accountStatus", qualifiedByName = "statusToByte")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "version", ignore = true)
    TerminalAccountDO toDO(TerminalAccount terminalAccount);

    /**
     * Byte转换为状态枚举
     * 
     * @param status 状态值
     * @return 状态枚举
     */
    @Named("byteToStatus")
    default TerminalAccountStatus byteToStatus(Byte status) {
        if (status == null) {
            return null;
        }
        
        for (TerminalAccountStatus accountStatus : TerminalAccountStatus.values()) {
            if (accountStatus.getStatus().equals(status.intValue())) {
                return accountStatus;
            }
        }
        
        throw new IllegalArgumentException("未知的账号状态: " + status);
    }

    /**
     * 状态枚举转换为Byte
     * 
     * @param status 状态枚举
     * @return 状态值
     */
    @Named("statusToByte")
    default Byte statusToByte(TerminalAccountStatus status) {
        return status == null ? null : status.getStatus().byteValue();
    }
}
