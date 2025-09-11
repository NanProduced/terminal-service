package com.colorlight.terminal.infrastructure.event;

import com.colorlight.terminal.infrastructure.async.AsyncDeviceStatusUpdateService;
import com.colorlight.terminal.infrastructure.async.AsyncGpsRecordService;
import com.colorlight.terminal.infrastructure.async.AsyncTerminalLoginUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步缓冲区刷新事件监听器
 * 处理各种异步服务的缓冲池刷新事件，解决@Async自调用代理失效问题
 * 
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncBufferFlushEventListener {
    
    /**
     * 处理设备状态更新缓冲池刷新事件
     * 使用设备状态专用线程池执行异步刷新
     */
    @EventListener
    @Async("deviceStatusExecutor")
    public void handleDeviceStatusBufferFlush(AsyncBufferFlushEvent event) {
        if (event.getBufferType() != AsyncBufferFlushEvent.BufferType.DEVICE_STATUS) {
            return;
        }
        
        try {
            AsyncDeviceStatusUpdateService service = (AsyncDeviceStatusUpdateService) event.getServiceInstance();
            
            log.debug("AsyncBufferFlush - 开始执行设备状态缓冲池异步刷新: bufferSize={}, eventTime={}", 
                    event.getBufferSize(), event.getEventTime());
            
            service.flushBuffer();
            
            log.debug("AsyncBufferFlush - 设备状态缓冲池异步刷新完成");
            
        } catch (Exception e) {
            log.error("AsyncBufferFlush - 设备状态缓冲池异步刷新失败: bufferSize={}", 
                    event.getBufferSize(), e);
        }
    }
    
    /**
     * 处理GPS记录缓冲池刷新事件
     * 使用统计报告专用线程池执行异步刷新
     */
    @EventListener
    @Async("statisticsReportExecutor")
    public void handleGpsRecordBufferFlush(AsyncBufferFlushEvent event) {
        if (event.getBufferType() != AsyncBufferFlushEvent.BufferType.GPS_RECORD) {
            return;
        }
        
        try {
            AsyncGpsRecordService service = (AsyncGpsRecordService) event.getServiceInstance();
            
            log.debug("AsyncBufferFlush - 开始执行GPS记录缓冲池异步刷新: bufferSize={}, eventTime={}", 
                    event.getBufferSize(), event.getEventTime());
            
            service.flushBuffer();
            
            log.debug("AsyncBufferFlush - GPS记录缓冲池异步刷新完成");
            
        } catch (Exception e) {
            log.error("AsyncBufferFlush - GPS记录缓冲池异步刷新失败: bufferSize={}", 
                    event.getBufferSize(), e);
        }
    }
    
    /**
     * 处理终端登录更新缓冲池刷新事件
     * 使用设备状态专用线程池执行异步刷新
     */
    @EventListener
    @Async("deviceStatusExecutor")
    public void handleLoginUpdateBufferFlush(AsyncBufferFlushEvent event) {
        if (event.getBufferType() != AsyncBufferFlushEvent.BufferType.LOGIN_UPDATE) {
            return;
        }
        
        try {
            AsyncTerminalLoginUpdateService service = (AsyncTerminalLoginUpdateService) event.getServiceInstance();
            
            log.debug("AsyncBufferFlush - 开始执行终端登录缓冲池异步刷新: bufferSize={}, eventTime={}", 
                    event.getBufferSize(), event.getEventTime());
            
            service.flushBuffer();
            
            log.debug("AsyncBufferFlush - 终端登录缓冲池异步刷新完成");
            
        } catch (Exception e) {
            log.error("AsyncBufferFlush - 终端登录缓冲池异步刷新失败: bufferSize={}", 
                    event.getBufferSize(), e);
        }
    }
    
    /**
     * 统一处理所有缓冲池刷新事件（用于监控和统计）
     * 记录事件处理情况，便于问题排查和性能监控
     */
    @EventListener
    public void handleAllBufferFlushEvents(AsyncBufferFlushEvent event) {
        log.debug("AsyncBufferFlush - 缓冲池刷新事件: type={}, bufferSize={}, eventTime={}", 
                event.getBufferType().getDescription(), 
                event.getBufferSize(), 
                event.getEventTime());
    }
}