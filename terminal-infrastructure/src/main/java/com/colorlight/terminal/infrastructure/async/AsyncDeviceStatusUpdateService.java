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
    
    // 对象池复用：批处理ArrayList容器，由flushLock保护
    private List<DeviceOnlineStatus> batchContainer;
    
    // 构造器初始化有界队列
    public AsyncDeviceStatusUpdateService(DeviceOnlineStatusPort deviceOnlineStatusPort,
                                         DeviceConfigPort deviceConfigPort,
                                         ApplicationEventPublisher eventPublisher) {
        this.deviceOnlineStatusPort = deviceOnlineStatusPort;
        this.deviceConfigPort = deviceConfigPort;
        this.eventPublisher = eventPublisher;
        // 初始化有界队列（带名称，便于日志追踪）
        this.bufferPool = new BoundedDropOldestQueue<>(deviceConfigPort.getBufferPoolMaxSize(), "DeviceStatusBuffer");
    }

    @PostConstruct
    public void init() {
        isRunning.set(true);
        lastFlushTime = System.currentTimeMillis();
        // 预初始化批处理容器，避免首次创建延迟
        batchContainer = new ArrayList<>(deviceConfigPort.getBufferPoolBatchSize());

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

            // 使用复用的批处理容器，由flushLock保护，无需额外同步
            while (!bufferPool.isEmpty()) {
                DeviceOnlineStatus status = bufferPool.poll();
                if (status != null) {
                    batchContainer.add(status);

                    // 达到批处理大小或缓冲池已空，执行批量更新
                    if (batchContainer.size() >= batchSize || bufferPool.isEmpty()) {
                        processedCount += batchContainer.size();
                        // 传递批次副本，避免因列表清空导致引用失效
                        processBatch(new ArrayList<>(batchContainer));
                        // 清空容器供下一批使用
                        batchContainer.clear();
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
     * 
     * @param batch 设备状态批次列表
     */
    private void processBatch(List<DeviceOnlineStatus> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            deviceOnlineStatusPort.batchSmartDetermined(batch);
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 批处理失败: batchSize={}", batch.size(), e);
            // 这里可以考虑重试机制或者降级处理
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

        // 缓冲池使用率告警：超过80%触发警告
        if (status.utilizationRate() > 0.8) {
            log.warn("AsyncDeviceStatusUpdate - 缓冲池使用率告警（>80%）: utilizationRate={}%, " +
                    "currentSize={}/{}, 建议检查处理能力或增加缓冲池大小",
                    (int) (status.utilizationRate() * 100),
                    status.currentSize(), status.maxSize());
        } else if (status.utilizationRate() > 0.7) {
            // 70-80%之间记录信息级日志，提前预警
            log.info("AsyncDeviceStatusUpdate - 缓冲池使用率较高: utilizationRate={}%, currentSize={}/{}",
                    (int) (status.utilizationRate() * 100),
                    status.currentSize(), status.maxSize());
        }

        // 如果有元素被丢弃，记录警告（数据丢失需要关注）
        if (status.totalDropped() > 0) {
            log.warn("AsyncDeviceStatusUpdate - 检测到数据丢弃: totalDropped={}, " +
                    "totalProcessed={}, dropRate={}%, 建议调整缓冲池大小或优化处理速度",
                    status.totalDropped(),
                    status.totalProcessed(),
                    String.format("%.2f", (double) status.totalDropped() / status.totalProcessed() * 100));
        }
    }
}