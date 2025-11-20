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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步设备状态更新服务
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

    // 去重缓冲池 - 使用ConcurrentHashMap自动去重，每设备仅保留最新状态
    private final ConcurrentHashMap<Long, DeviceOnlineStatus> bufferPool = new ConcurrentHashMap<>();

    // 刷新锁 - 防止并发刷新
    private final ReentrantLock flushLock = new ReentrantLock();

    // 服务状态
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // 统计指标
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFlushed = new AtomicLong(0);
    private volatile long lastFlushTime = 0;
    private volatile long peakSize = 0;  // 缓冲池峰值大小
    private volatile long totalDropped = 0;  // 数据丢弃统计（用于兼容性，ConcurrentHashMap无损失）

    // 构造器
    public AsyncDeviceStatusUpdateService(DeviceOnlineStatusPort deviceOnlineStatusPort,
                                         DeviceConfigPort deviceConfigPort,
                                         ApplicationEventPublisher eventPublisher) {
        this.deviceOnlineStatusPort = deviceOnlineStatusPort;
        this.deviceConfigPort = deviceConfigPort;
        this.eventPublisher = eventPublisher;
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
            ensureCapacityAndAdd(status.getDeviceId(), status);
            checkEmergencyFlush();

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
            // 批量添加到缓冲池（自动去重，并进行容量管理）
            for (DeviceOnlineStatus status : statusList) {
                if (status != null && status.getDeviceId() != null) {
                    ensureCapacityAndAdd(status.getDeviceId(), status);
                }
            }

            checkEmergencyFlush();

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
            if (bufferPool.isEmpty()) {
                return;
            }

            long startTime = System.currentTimeMillis();
            int batchSize = deviceConfigPort.getBufferPoolBatchSize();
            int processedCount = 0;

            // 分批处理缓冲池中的状态更新
            List<DeviceOnlineStatus> batch = new ArrayList<>(batchSize);

            // 从ConcurrentHashMap中批量取出记录
            var iterator = bufferPool.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                batch.add(entry.getValue());
                iterator.remove();  // 从缓冲池中移除

                // 达到批处理大小或已处理完所有记录，执行批量更新
                if (batch.size() >= batchSize || !iterator.hasNext()) {
                    processBatch(new ArrayList<>(batch));
                    processedCount += batch.size();
                    batch.clear();
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
                totalFlushed.get(),
                totalDropped  // ConcurrentHashMap无损失，总是0
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
        int maxSize = deviceConfigPort.getBufferPoolMaxSize();
        double threshold = deviceConfigPort.getEmergencyFlushThreshold();

        if (currentSize > 0 && maxSize > 0) {
            double utilizationRate = (double) currentSize / maxSize;

            if (utilizationRate >= threshold) {
                log.warn("AsyncDeviceStatusUpdate - 缓冲池使用率达到紧急阈值，触发紧急刷新: " +
                        "currentSize={}, maxSize={}, utilizationRate={}%, threshold={}%",
                        currentSize, maxSize, (int)(utilizationRate * 100), (int)(threshold * 100));

                // 发布紧急刷新事件
                eventPublisher.publishEvent(AsyncBufferFlushEvent.createDeviceStatusFlushEvent(this, currentSize));
            }
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
     * 容量管理：检查缓冲池是否接近上限，接近则同步触发 flush 以腾出空间
     * 使用软上限策略，不主动丢弃数据，而是通过同步 flush 来清空缓冲池
     *
     * @param deviceId 设备ID
     * @param status 设备状态
     */
    private void ensureCapacityAndAdd(Long deviceId, DeviceOnlineStatus status) {
        int currentSize = bufferPool.size();
        int maxSize = deviceConfigPort.getBufferPoolMaxSize();

        // 容量检查：若接近上限，同步触发 flush 以腾出空间（不丢弃数据）
        if (currentSize >= maxSize) {
            log.warn("AsyncDeviceStatusUpdate - 缓冲池接近上限，触发同步flush以腾出空间: " +
                    "currentSize={}, maxSize={}", currentSize, maxSize);
            flushBuffer();  // 同步刷新，清空缓冲池
        }

        // 添加新值到缓冲池
        bufferPool.put(deviceId, status);
        totalProcessed.incrementAndGet();

        // 更新峰值大小
        long newSize = bufferPool.size();
        if (newSize > peakSize) {
            peakSize = newSize;
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

        // 缓冲池使用率告警：超过80%触发警告，表示 flush 可能跟不上写入速度
        if (status.utilizationRate() > 0.8) {
            log.warn("AsyncDeviceStatusUpdate - 缓冲池使用率告警（>80%）: utilizationRate={}%, " +
                    "currentSize={}/{}, 表示 flush 处理速度接近上限，建议检查 Redis 性能或增加缓冲池大小",
                    (int) (status.utilizationRate() * 100),
                    status.currentSize(), status.maxSize());
        } else if (status.utilizationRate() > 0.7) {
            // 70-80%之间记录信息级日志，提前预警
            log.info("AsyncDeviceStatusUpdate - 缓冲池使用率较高: utilizationRate={}%, currentSize={}/{}",
                    (int) (status.utilizationRate() * 100),
                    status.currentSize(), status.maxSize());
        }
    }
}