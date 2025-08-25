package com.colorlight.terminal.application.port.outbound.status;

import com.colorlight.terminal.application.dto.cache.LoginUpdateRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步终端登录时间更新端口接口
 * 
 * @author Nan
 */
public interface AsyncTerminalLoginUpdatePort {
    
    /**
     * 提交单个登录时间更新到缓冲池
     * 
     * @param deviceId 设备ID
     * @param clientIp 客户端IP
     */
    void submitLoginUpdate(Long deviceId, String clientIp);
    
    /**
     * 提交单个登录时间更新到缓冲池（指定更新时间）
     * 
     * @param deviceId 设备ID
     * @param clientIp 客户端IP  
     * @param updateTime 更新时间
     */
    void submitLoginUpdate(Long deviceId, String clientIp, LocalDateTime updateTime);
    
    /**
     * 批量提交登录时间更新到缓冲池
     * 
     * @param records 更新记录列表
     */
    void submitBatchLoginUpdate(List<LoginUpdateRecord> records);
    
    /**
     * 立即刷新缓冲池
     * 强制执行所有待处理的更新
     */
    void flushBuffer();

    /**
     * 定时刷新设备最后登录信息缓冲池。
     * <p>
     * 该方法由 Spring {@code @Scheduled} 注解自动触发执行。
     * <b>请勿在业务代码中手动调用此方法，因为它专为后台定时任务设计。</b>
     *
     */
    void scheduledFlush();
    

}