package com.colorlight.terminal.application.port.outbound.cache;

import com.colorlight.terminal.application.dto.cache.TerminalAuthCache;

import java.util.Optional;

/**
 * 终端认证缓存端口接口
 * 定义终端认证信息缓存的标准操作
 * 
 * @author Nan
 * @version 1.0.0
 * @since 2024-12-15
 */
public interface TerminalAuthCachePort {

    /**
     * 缓存终端认证信息
     * 
     * @param accountName 账户名称
     * @param terminalAuthCache 认证缓存对象
     */
    void cache(String accountName, TerminalAuthCache terminalAuthCache);

    /**
     * 安全获取终端认证缓存信息
     * 
     * @param accountName 账户名称
     * @return Optional包装的认证缓存对象
     */
    Optional<TerminalAuthCache> get(String accountName);

    /**
     * 移除终端认证缓存
     * 
     * @param accountName 账户名称
     */
    void remove(String accountName);


    /**
     * 清空所有认证缓存
     */
    void clearAll();

}