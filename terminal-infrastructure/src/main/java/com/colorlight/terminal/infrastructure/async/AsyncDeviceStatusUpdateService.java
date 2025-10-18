package com.colorlight.terminal.infrastructure.async;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.port.outbound.config.DeviceConfigPort;
import com.colorlight.terminal.application.port.outbound.status.AsyncDeviceStatusUpdatePort;
import com.colorlight.terminal.application.port.outbound.status.DeviceOnlineStatusPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import com.colorlight.terminal.infrastructure.event.AsyncBufferFlushEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步设备状态更新服务
 * <p>缓冲池机制，提供异步状态更新处理</p>
 * 
 * @author Nan
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "terminal.device.status-update.async-enabled", havingValue = "true", matchIfMissing = true)
public class AsyncDeviceStatusUpdateService implements AsyncDeviceStatusUpdatePort {
    
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final DeviceConfigPort deviceConfigPort;
    private final ApplicationEventPublisher eventPublisher;
    
    // 缓冲池 - 使用有界队列，满时丢弃最旧元素
    private final BoundedDropOldestQueue<DeviceOnlineStatus> bufferPool;
    
    // 刷新锁 - 防止并发刷新
    private final ReentrantLock flushLock = new ReentrantLock();
    
    // 服务状态
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 统计指标
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFlushed = new AtomicLong(0);
    private volatile long lastFlushTime = 0;
    
    
    // 构造器初始化有界队列
    public AsyncDeviceStatusUpdateService(DeviceOnlineStatusPort deviceOnlineStatusPort,
                                         DeviceConfigPort deviceConfigPort,
                                         ApplicationEventPublisher eventPublisher) {
        this.deviceOnlineStatusPort = deviceOnlineStatusPort;
        this.deviceConfigPort = deviceConfigPort;
        this.eventPublisher = eventPublisher;
        this.bufferPool = new BoundedDropOldestQueue<>(deviceConfigPort.getBufferPoolMaxSize());
    }

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

            // 检查是否需要紧急刷新（暂不需要）
            // checkEmergencyFlush();
            
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

            // 检查是否需要紧急刷新（暂不需要）
            // checkEmergencyFlush();
            
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 批量提交状态更新失败: count={}", statusList.size(), e);
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

            // 分批处理缓冲池中的状态更新
            List<DeviceOnlineStatus> batch = new ArrayList<>(batchSize);
            
            while (!bufferPool.isEmpty()) {
                DeviceOnlineStatus status = bufferPool.poll();
                if (status != null) {
                    batch.add(status);
                    
                    // 达到批处理大小或缓冲池已空，执行批量更新
                    if (batch.size() >= batchSize || bufferPool.isEmpty()) {
                        processedCount += batch.size();
                        processBatch(batch);
                        // 重新创建batch容器，避免clear()操作
                        batch = new ArrayList<>(batchSize);
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
        int maxSize = bufferPool.getMaxCapacity();
        double utilizationRate = bufferPool.getUtilizationRate();

        return new BufferPoolStatus(
                currentSize,
                maxSize,
                utilizationRate,
                lastFlushTime,
                totalProcessed.get(),
                totalFlushed.get(),
                bufferPool.getDroppedCount()
        );
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
                eventPublisher.publishEvent(AsyncBufferFlushEvent.createDeviceStatusFlushEvent(this, bufferPool.size()));
            }
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 定时刷新失败", e);
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
        double utilizationRate = bufferPool.getUtilizationRate();
        double threshold = deviceConfigPort.getEmergencyFlushThreshold();

        if (currentSize > 0 && utilizationRate >= threshold) {
            log.warn("AsyncDeviceStatusUpdate - 缓冲池使用率达到紧急阈值，触发紧急刷新: " +
                    "currentSize={}, maxSize={}, utilizationRate={}%, threshold={}%, droppedCount={}",
                    currentSize, bufferPool.getMaxCapacity(), (int)(utilizationRate * 100),
                    (int)(threshold * 100), bufferPool.getDroppedCount());

            // 发布紧急刷新事件
            eventPublisher.publishEvent(AsyncBufferFlushEvent.createDeviceStatusFlushEvent(this, currentSize));
        }
    }
    
    /**
     * 处理一批状态更新
     */
    private void processBatch(List<DeviceOnlineStatus> batch) {
        if (batch.isEmpty()) {
            return;
        }

        for (DeviceOnlineStatus status : batch) {
            try {
                // 使用现有的Redis服务批量更新状态
                deviceOnlineStatusPort.smartDetermined(status);
            } catch (Exception e) {
                log.error("AsyncDeviceStatusUpdate - 批处理失败: batchSize={}", batch.size(), e);
                // 这里可以考虑重试机制或者降级处理
            }
        }
    }
    
    /**
     * 定期输出统计信息
     * 配置延迟启动，避免系统启动时资源竞争
     */
    @Scheduled(fixedRateString = "#{@deviceConfigPort.getBufferPoolStatisticsInterval()}",
               initialDelayString = "#{@deviceConfigPort.getTaskStatisticsDelayMs()}")
    public void logStatistics() {
        if (!isRunning.get()) {
            return;
        }
        
        BufferPoolStatus status = getBufferPoolStatus();
        
        log.info("AsyncDeviceStatusUpdate - 统计信息:\n{}", JsonUtils.toJsonPretty(status));
        
        // 如果缓冲池长期保持高使用率，记录警告
        if (status.utilizationRate() > 0.7) {
            log.warn("AsyncDeviceStatusUpdate - 缓冲池使用率较高，建议检查处理能力: utilizationRate={}%",
                    (int) (status.utilizationRate() * 100));
        }

        // 如果有元素被丢弃，记录警告
        if (status.totalDropped() > 0) {
            log.warn("AsyncDeviceStatusUpdate - 检测到元素丢弃，可能需要调整缓冲池大小或处理速度: totalDropped={}",
                    status.totalDropped());
        }
    }
}