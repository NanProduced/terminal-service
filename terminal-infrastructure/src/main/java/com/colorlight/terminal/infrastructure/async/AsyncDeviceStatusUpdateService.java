package com.colorlight.terminal.infrastructure.async;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncDeviceStatusUpdatePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步设备状态更新服务
 * 
 * 缓冲池机制，提供异步状态更新处理
 * 
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "terminal.device.status-update.async-enabled", havingValue = "true", matchIfMissing = true)
public class AsyncDeviceStatusUpdateService implements AsyncDeviceStatusUpdatePort {
    
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final DeviceConfigPort deviceConfigPort;
    
    // 缓冲池 - 使用线程安全的并发队列
    private final ConcurrentLinkedQueue<DeviceOnlineStatus> bufferPool = new ConcurrentLinkedQueue<>();
    
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
        
        log.info("AsyncDeviceStatusUpdate - 异步状态更新服务启动: windowMs={}, maxSize={}, batchSize={}, emergencyFlushThreshold={}",
                deviceConfigPort.getBufferPoolWindowMs(),
                deviceConfigPort.getBufferPoolMaxSize(),
                deviceConfigPort.getBufferPoolBatchSize(),
                deviceConfigPort.getEmergencyFlushThreshold());
    }
    
    @PreDestroy
    public void destroy() {
        isRunning.set(false);
        
        // 服务关闭时强制刷新缓冲池
        log.info("AsyncDeviceStatusUpdate - 服务关闭，强制刷新缓冲池: bufferSize={}", bufferPool.size());
        flushBuffer();
        
        log.info("AsyncDeviceStatusUpdate - 异步状态更新服务关闭: totalProcessed={}, totalFlushed={}",
                totalProcessed.get(), totalFlushed.get());
    }
    
    @Override
    public void submitStatusUpdate(DeviceOnlineStatus status) {
        if (!isRunning.get() || status == null) {
            return;
        }
        
        try {
            // 添加到缓冲池
            bufferPool.offer(status);
            totalProcessed.incrementAndGet();
            
            log.debug("AsyncDeviceStatusUpdate - 提交状态更新: deviceId={}, bufferSize={}", 
                    status.getDeviceId(), bufferPool.size());
            
            // 检查是否需要紧急刷新
            // checkEmergencyFlush(); // 暂时应该用不上，没这么高并发
            
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 提交状态更新失败: deviceId={}", 
                    status.getDeviceId(), e);
        }
    }
    
    @Override
    public void submitBatchStatusUpdate(List<DeviceOnlineStatus> statusList) {
        if (!isRunning.get() || statusList == null || statusList.isEmpty()) {
            return;
        }
        
        try {
            // 批量添加到缓冲池
            for (DeviceOnlineStatus status : statusList) {
                if (status != null) {
                    bufferPool.offer(status);
                    totalProcessed.incrementAndGet();
                }
            }
            
            log.debug("AsyncDeviceStatusUpdate - 批量提交状态更新: count={}, bufferSize={}", 
                    statusList.size(), bufferPool.size());

            // 检查是否需要紧急刷新
            // checkEmergencyFlush(); // 暂时应该用不上，没这么高并发
            
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 批量提交状态更新失败: count={}", statusList.size(), e);
        }
    }
    
    @Override
    public void flushBuffer() {
        if (!flushLock.tryLock()) {
            log.debug("AsyncDeviceStatusUpdate - 刷新操作进行中，跳过本次请求");
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
            
            log.debug("AsyncDeviceStatusUpdate - 开始刷新缓冲池: bufferSize={}, batchSize={}", 
                    bufferSize, batchSize);
            
            // 分批处理缓冲池中的状态更新
            List<DeviceOnlineStatus> batch = new ArrayList<>(batchSize);
            
            while (!bufferPool.isEmpty() && isRunning.get()) {
                DeviceOnlineStatus status = bufferPool.poll();
                if (status != null) {
                    batch.add(status);
                    
                    // 达到批处理大小或缓冲池已空，执行批量更新
                    if (batch.size() >= batchSize || bufferPool.isEmpty()) {
                        processBatch(new ArrayList<>(batch));
                        processedCount += batch.size();
                        batch.clear();
                    }
                }
            }
            
            lastFlushTime = System.currentTimeMillis();
            totalFlushed.addAndGet(processedCount);
            
            log.info("AsyncDeviceStatusUpdate - 缓冲池刷新完成: processed={}, duration={}ms, remainingBuffer={}", 
                    processedCount, lastFlushTime - startTime, bufferPool.size());
            
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 刷新缓冲池失败", e);
        } finally {
            flushLock.unlock();
        }
    }
    
    @Override
    public BufferPoolStatus getBufferPoolStatus() {
        int currentSize = bufferPool.size();
        int maxSize = deviceConfigPort.getBufferPoolMaxSize();
        double utilizationRate = maxSize > 0 ? (double) currentSize / maxSize : 0.0;
        
        return new BufferPoolStatus(
                currentSize,
                maxSize,
                utilizationRate,
                lastFlushTime,
                totalProcessed.get(),
                totalFlushed.get()
        );
    }
    
    /**
     * 定时刷新缓冲池
     * 根据配置的窗口时间定期刷新
     */
    @Override
    @Scheduled(fixedDelayString = "#{@deviceConfigPort.getBufferPoolWindowMs()}")
    public void scheduledFlush() {
        if (!isRunning.get()) {
            return;
        }

        try {
            if (!bufferPool.isEmpty()) {
                log.debug("AsyncDeviceStatusUpdate - 定时刷新缓冲池: bufferSize={}", bufferPool.size());
                flushBufferAsync();
            }
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 定时刷新失败", e);
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
                log.warn("AsyncDeviceStatusUpdate - 缓冲池使用率达到紧急阈值，触发紧急刷新: " +
                        "currentSize={}, maxSize={}, utilizationRate={}%, threshold={}%",
                        currentSize, maxSize, (int)(utilizationRate * 100), (int)(threshold * 100));
                
                // 异步执行紧急刷新
                flushBufferAsync();
            }
        }
    }
    
    /**
     * 处理一批状态更新
     */
    private void processBatch(List<DeviceOnlineStatus> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        try {
            // 使用现有的Redis服务批量保存状态
            for (DeviceOnlineStatus status : batch) {
                deviceOnlineStatusPort.saveDeviceStatus(status);
            }
            
            log.debug("AsyncDeviceStatusUpdate - 批处理完成: batchSize={}", batch.size());
            
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 批处理失败: batchSize={}", batch.size(), e);
            // 这里可以考虑重试机制或者降级处理
        }
    }
    
    /**
     * 定期输出统计信息
     */
    @Scheduled(fixedRateString = "#{@deviceConfigPort.getBufferPoolStatisticsInterval()}")
    public void logStatistics() {
        if (!isRunning.get()) {
            return;
        }
        
        BufferPoolStatus status = getBufferPoolStatus();
        
        log.info("AsyncDeviceStatusUpdate - 统计信息: {}", JsonUtils.toJsonPretty(status));
        
        // 如果缓冲池长期保持高使用率，记录警告
        if (status.utilizationRate() > 0.7) {
            log.warn("AsyncDeviceStatusUpdate - 缓冲池使用率较高，建议检查处理能力: utilizationRate={}%",
                    (int) (status.utilizationRate() * 100));
        }
    }
}