package com.colorlight.terminal.infrastructure.async;

import com.colorlight.terminal.application.dto.record.LoginUpdateRecord;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import com.colorlight.terminal.application.port.outbound.status.AsyncTerminalLoginUpdatePort;
import com.colorlight.terminal.infrastructure.event.AsyncBufferFlushEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步终端登录时间更新服务
 * <p>
 * 基于缓冲池机制，提供高性能的登录时间批量更新
 * 自动去重：每设备仅保留最新的登录记录
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTerminalLoginUpdateService implements AsyncTerminalLoginUpdatePort {
    
    private final TerminalAccountRepository terminalAccountRepository;
    private final DeviceConfigPort deviceConfigPort;
    private final ApplicationEventPublisher eventPublisher;
    
    // 去重缓冲池 - 使用ConcurrentHashMap自动去重，每设备仅保留最新记录
    private final ConcurrentHashMap<Long, LoginUpdateRecord> bufferPool = new ConcurrentHashMap<>();
    
    // 刷新锁 - 防止并发刷新
    private final ReentrantLock flushLock = new ReentrantLock();
    
    // 服务状态
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 统计指标
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFlushed = new AtomicLong(0);
    private volatile long lastFlushTime = 0;
    
    @PostConstruct
    public void init() {
        isRunning.set(true);
        lastFlushTime = System.currentTimeMillis();
        
        log.info("AsyncTerminalLoginUpdate - 异步登录时间更新服务启动: windowMs={}, maxSize={}, batchSize={}",
                deviceConfigPort.getBufferPoolWindowMs(),
                deviceConfigPort.getBufferPoolMaxSize(),
                deviceConfigPort.getBufferPoolBatchSize());
    }
    
    @PreDestroy
    public void destroy() {
        isRunning.set(false);
        
        // 服务关闭时强制刷新缓冲池
        log.info("AsyncTerminalLoginUpdate - 服务关闭，强制刷新缓冲池: bufferSize={}", bufferPool.size());
        flushBuffer();
        
        log.info("AsyncTerminalLoginUpdate - 异步登录时间更新服务关闭: totalProcessed={}, totalFlushed={}",
                totalProcessed.get(), totalFlushed.get());
    }
    
    @Override
    public void submitLoginUpdate(Long deviceId, String clientIp) {
        submitLoginUpdate(deviceId, clientIp, LocalDateTime.now());
    }
    
    @Override
    public void submitLoginUpdate(Long deviceId, String clientIp, LocalDateTime updateTime) {
        if (!isRunning.get() || deviceId == null) {
            return;
        }
        
        try {
            // 创建登录更新记录
            LoginUpdateRecord loginUpdateRecord = LoginUpdateRecord.create(deviceId, clientIp, updateTime);
            
            // 添加到缓冲池（自动去重，相同deviceId会被覆盖）
            bufferPool.put(deviceId, loginUpdateRecord);
            totalProcessed.incrementAndGet();
            
            // 检查是否需要紧急刷新
            // checkEmergencyFlush(); // 暂时应该用不上
            
        } catch (Exception e) {
            log.error("AsyncTerminalLoginUpdate - 提交登录更新失败: deviceId={}", deviceId, e);
        }
    }
    
    @Override
    public void submitBatchLoginUpdate(List<LoginUpdateRecord> records) {
        if (!isRunning.get() || records == null || records.isEmpty()) {
            return;
        }
        
        try {
            // 批量添加到缓冲池（自动去重）
            for (LoginUpdateRecord loginUpdateRecord : records) {
                if (loginUpdateRecord != null && loginUpdateRecord.getDeviceId() != null) {
                    bufferPool.put(loginUpdateRecord.getDeviceId(), loginUpdateRecord);
                    totalProcessed.incrementAndGet();
                }
            }

            // 检查是否需要紧急刷新
            // checkEmergencyFlush(); // 暂时应该用不上
            
        } catch (Exception e) {
            log.error("AsyncTerminalLoginUpdate - 批量提交登录更新失败: count={}", records.size(), e);
        }
    }
    
    @Override
    public void flushBuffer() {
        if (!flushLock.tryLock()) {
            return;
        }
        
        try {
            int bufferSize = bufferPool.size();
            if (bufferSize == 0) {
                return;
            }
            
            long startTime = System.currentTimeMillis();
            int batchSize = deviceConfigPort.getBufferPoolBatchSize();
            int processedCount = 0;

            // 分批处理缓冲池中的登录更新
            List<LoginUpdateRecord> batch = new ArrayList<>(batchSize);
            
            // 从ConcurrentHashMap中批量取出记录
            var iterator = bufferPool.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                batch.add(entry.getValue());
                iterator.remove(); // 从缓冲池中移除
                
                // 达到批处理大小或已处理完所有记录，执行批量更新
                if (batch.size() >= batchSize || !iterator.hasNext()) {
                    processBatch(new ArrayList<>(batch));
                    processedCount += batch.size();
                    batch.clear();
                }
            }
            
            lastFlushTime = System.currentTimeMillis();
            totalFlushed.addAndGet(processedCount);
            
            log.info("AsyncTerminalLoginUpdate - 缓冲池刷新完成: processed={}, duration={}ms, remainingBuffer={}", 
                    processedCount, lastFlushTime - startTime, bufferPool.size());
            
        } catch (Exception e) {
            log.error("AsyncTerminalLoginUpdate - 刷新缓冲池失败", e);
        } finally {
            flushLock.unlock();
        }
    }
    
    /**
     * 定时刷新缓冲池
     * 根据配置的窗口时间定期刷新
     * 配置延迟启动，避免系统启动时资源竞争
     */
    @Override
    @Scheduled(fixedDelayString = "#{@deviceConfigPort.getBufferPoolWindowMs()}",
               initialDelayString = "#{@deviceConfigPort.getTaskBufferPoolDelayMs()}")
    public void scheduledFlush() {
        if (!isRunning.get()) {
            return;
        }

        try {
            if (!bufferPool.isEmpty()) {
                // 发布异步刷新事件
                eventPublisher.publishEvent(AsyncBufferFlushEvent.createLoginUpdateFlushEvent(this, bufferPool.size()));
            }
        } catch (Exception e) {
            log.error("AsyncTerminalLoginUpdate - 定时刷新失败", e);
        }
    }
    
    
    /**
     * 检查是否需要紧急刷新
     */
    private void checkEmergencyFlush() {
        if (!deviceConfigPort.isEmergencyFlushEnabled()) {
            return;
        }
        
        int currentSize = bufferPool.size();
        int maxSize = deviceConfigPort.getBufferPoolMaxSize();
        double threshold = deviceConfigPort.getEmergencyFlushThreshold();
        
        if (currentSize > 0 && maxSize > 0) {
            double utilizationRate = (double) currentSize / maxSize;
            
            if (utilizationRate >= threshold) {
                log.warn("AsyncTerminalLoginUpdate - 缓冲池使用率达到紧急阈值，触发紧急刷新: " +
                        "currentSize={}, maxSize={}, utilizationRate={}%, threshold={}%",
                        currentSize, maxSize, (int)(utilizationRate * 100), (int)(threshold * 100));
                
                // 发布紧急刷新事件
                eventPublisher.publishEvent(AsyncBufferFlushEvent.createLoginUpdateFlushEvent(this, currentSize));
            }
        }
    }
    
    /**
     * 处理一批登录时间更新
     */
    private void processBatch(List<LoginUpdateRecord> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        int successCount = 0;
        int failureCount = 0;
        
        // 逐个处理记录，单个失败不影响其他记录
        for (LoginUpdateRecord loginUpdateRecord : batch) {
            try {
                terminalAccountRepository.updateLoginTime(
                        loginUpdateRecord.getDeviceId(),
                        loginUpdateRecord.getClientIp(),
                        loginUpdateRecord.getUpdateTime()
                );
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("AsyncTerminalLoginUpdate - 单个记录处理失败: deviceId={}, clientIp={}",
                        loginUpdateRecord.getDeviceId(), loginUpdateRecord.getClientIp(), e);
            }
        }
    }

}