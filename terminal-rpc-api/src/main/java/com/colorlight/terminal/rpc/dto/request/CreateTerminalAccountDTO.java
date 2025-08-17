package com.colorlight.terminal.rpc.dto.request;

import java.io.Serializable;

/**
 * 创建终端账号请求
 * 
 * @author Nan
 */
public class CreateTerminalAccountDTO implements Serializable {


    private static final long serialVersionUID = 3204054937916936322L;
    /**
     * 账号名称
     */
    private String accountName;
    
    /**
     * 原始密码
     */
    private String rawPassword;

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getRawPassword() {
        return rawPassword;
    }

    public void setRawPassword(String rawPassword) {
        this.rawPassword = rawPassword;
    }

    public CreateTerminalAccountDTO(String accountName, String rawPassword) {
        this.accountName = accountName;
        this.rawPassword = rawPassword;
    }
}