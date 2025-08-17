package com.colorlight.terminal.rpc.dto.result;

import java.io.Serializable;

/**
 * 终端账号结果
 * 
 * @author Nan
 */
public class TerminalAccountResultDTO implements Serializable {


    private static final long serialVersionUID = -7456020028649378245L;
    /**
     * 设备ID
     */
    private Long deviceId;
    
    /**
     * 账号名称
     */
    private String accountName;
    
    /**
     * 账号状态
     */
    private String status;

    public TerminalAccountResultDTO() {}

    public TerminalAccountResultDTO(Long deviceId, String accountName, String status) {
        this.deviceId = deviceId;
        this.accountName = accountName;
        this.status = status;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}