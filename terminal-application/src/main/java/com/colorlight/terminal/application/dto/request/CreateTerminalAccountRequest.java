package com.colorlight.terminal.application.dto.request;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 创建终端账号请求 (Application层)
 * 
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTerminalAccountRequest {
    
    /**
     * 账号名称
     */
    private String accountName;
    
    /**
     * 原始密码
     */
    private String rawPassword;

    /**
     * 创建来源
     */
    private Source source;


    public enum Source {

        CLOUD;


    }
}