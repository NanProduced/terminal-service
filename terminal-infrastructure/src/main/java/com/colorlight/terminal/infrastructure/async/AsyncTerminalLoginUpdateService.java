package com.colorlight.terminal.infrastructure.async;

import com.colorlight.terminal.application.dto.record.LoginUpdateRecord;
import com.colorlight.terminal.application.port.outbound.status.AsyncTerminalLoginUpdatePort;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.repository.TerminalAccountRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
 * 
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
            LoginUpdateRecord record = LoginUpdateRecord.create(deviceId, clientIp, updateTime);
            
            // 添加到缓冲池（自动去重，相同deviceId会被覆盖）
            LoginUpdateRecord previous = bufferPool.put(deviceId, record);
            totalProcessed.incrementAndGet();
            
            if (previous != null) {
                log.debug("AsyncTerminalLoginUpdate - 更新记录覆盖: deviceId={}, 新时间={}, 旧时间={}", 
                        deviceId, updateTime, previous.getUpdateTime());
            }
            
            log.debug("AsyncTerminalLoginUpdate - 提交登录更新: deviceId={}, bufferSize={}", 
                    deviceId, bufferPool.size());
            
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
            for (LoginUpdateRecord record : records) {
                if (record != null && record.getDeviceId() != null) {
                    bufferPool.put(record.getDeviceId(), record);
                    totalProcessed.incrementAndGet();
                }
            }
            
            log.debug("AsyncTerminalLoginUpdate - 批量提交登录更新: count={}, bufferSize={}", 
                    records.size(), bufferPool.size());

            // 检查是否需要紧急刷新
            // checkEmergencyFlush(); // 暂时应该用不上
            
        } catch (Exception e) {
            log.error("AsyncTerminalLoginUpdate - 批量提交登录更新失败: count={}", records.size(), e);
        }
    }
    
    @Override
    public void flushBuffer() {
        if (!flushLock.tryLock()) {
            log.debug("AsyncTerminalLoginUpdate - 刷新操作进行中，跳过本次请求");
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
            
            log.debug("AsyncTerminalLoginUpdate - 开始刷新缓冲池: bufferSize={}, batchSize={}", 
                    bufferSize, batchSize);
            
            // 分批处理缓冲池中的登录更新
            List<LoginUpdateRecord> batch = new ArrayList<>(batchSize);
            
            // 从ConcurrentHashMap中批量取出记录
            var iterator = bufferPool.entrySet().iterator();
            while (iterator.hasNext() && isRunning.get()) {
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
                log.debug("AsyncTerminalLoginUpdate - 定时刷新缓冲池: bufferSize={}", bufferPool.size());
                flushBufferAsync();
            }
        } catch (Exception e) {
            log.error("AsyncTerminalLoginUpdate - 定时刷新失败", e);
        }
    }
    
    /**
     * 异步刷新缓冲池
     */
    @Async("deviceStatusExecutor")
    public void flushBufferAsync() {
        flushBuffer();
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
                
                // 异步执行紧急刷新
                flushBufferAsync();
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
        
        try {
            // 批量更新登录时间
            for (LoginUpdateRecord record : batch) {
                terminalAccountRepository.updateLoginTime(
                        record.getDeviceId(), 
                        record.getClientIp(), 
                        record.getUpdateTime()
                );
            }
            
            log.debug("AsyncTerminalLoginUpdate - 批处理完成: batchSize={}", batch.size());
            
        } catch (Exception e) {
            log.error("AsyncTerminalLoginUpdate - 批处理失败: batchSize={}", batch.size(), e);
            // 这里可以考虑重试机制或者降级处理
        }
    }

}