package com.colorlight.terminal.infrastructure.async;

import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.colorlight.terminal.application.domain.sensor.GpsReport;
import com.colorlight.terminal.application.port.outbound.repository.GpsRecordRepository;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceGpsHandlePort;
import com.colorlight.terminal.infrastructure.config.properties.TerminalStatsConfigProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步处理GPS记录的服务类，实现了DeviceGpsHandlePort接口。该服务主要用于接收GPS报告并将其存储到数据库中。
 * 通过使用数据缓存队列和定时刷新机制来提高性能和可靠性。
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncGpsRecordService implements DeviceGpsHandlePort {

    private final GpsRecordRepository gpsRepository;
    private final TerminalStatsConfigProperties terminalStatsConfigProperties;

    // 数据缓存队列
    private BlockingQueue<GpsReport> dataBuffer;
    // 刷新锁 - 防止并发刷新
    private final ReentrantLock flushLock = new ReentrantLock();

    @PostConstruct
    private void initializeQueue() {
        int queueSize = terminalStatsConfigProperties.getGps().getMaxQueueSize();
        this.dataBuffer = new LinkedBlockingQueue<>(queueSize);
        log.info("GpsService - GPS缓冲队列初始化完成, 容量: {}", queueSize);
    }

    /**
     * 接收GPS报告并尝试将其添加到数据缓存队列中。
     * 如果队列已满，则触发背压处理机制。
     *
     * @param report 待处理的GPS报告对象
     */
    @Override
    public void receiveGpsRecord(GpsReport report) {
        if (report == null) {
            log.warn("GpsService - 接收到空GPS报告，忽略");
            return;
        }
        
        // 尝试添加到队列
        if (!dataBuffer.offer(report)) {
            // 队列已满，触发背压处理
            handleBackpressure(report);
        }
    }
    
    /**
     * 处理队列满载的背压情况
     */
    private void handleBackpressure(GpsReport report) {
        log.warn("GpsService - GPS数据队列已满，当前大小: {}, 触发紧急刷新", dataBuffer.size());
        
        // 立即尝试刷新一次
        flushBuffer();
        
        // 再次尝试入队
        if (!dataBuffer.offer(report)) {
            // 仍无法入队，记录丢失的数据
            log.error("GpsService - GPS数据丢失，设备ID: {}, 坐标: [{},{}]", 
                    report.getDeviceId(), report.getLatitude(), report.getLongitude());
        }
    }

    /**
     * 将缓存队列中的GPS报告批量保存到数据库中。
     * 该方法会尝试获取刷新锁，如果无法获取（即另一个线程正在执行刷新操作），则跳过本次请求。
     * 获取到锁后，从数据缓存队列中取出指定数量的GPS报告，并调用存储库进行批量保存。
     * 如果在处理过程中发生异常，将记录错误日志。
     * 最后，无论是否成功，都会释放刷新锁。
     */
    @Override
    public void flushBuffer() {
        if (!flushLock.tryLock()) {
            log.debug("GpsService - 刷新入库操作进行中，跳过本次请求");
            return;
        }

        List<GpsReport> batchData = Lists.newArrayList();
        try {
            // 从队列中取出数据
            int drainedCount = dataBuffer.drainTo(batchData, terminalStatsConfigProperties.getGps().getBatchSize());
            if (drainedCount == 0) {
                log.debug("GpsService - 队列为空，无数据需要刷新");
                return;
            }
            
            log.debug("GpsService - 开始批量存储GPS数据: size={}", drainedCount);
            
            // 批量存储
            gpsRepository.batchSaveGpsRecord(batchData);
            
            log.info("GpsService - GPS数据批量刷新成功: size={}, 剩余队列大小: {}", 
                    drainedCount, dataBuffer.size());

        } catch (Exception e) {
            log.error("GpsService - 刷新缓冲队列失败，将数据重新放回队列: size={}", batchData.size(), e);
            
            // 尝试将失败的数据重新放回队列（逆序放入以保持相对顺序）
            if (!batchData.isEmpty()) {
                int reofferedCount = 0;
                for (int i = batchData.size() - 1; i >= 0; i--) {
                    if (dataBuffer.offer(batchData.get(i))) {
                        reofferedCount++;
                    } else {
                        log.warn("GpsService - 队列已满，无法重新放回数据，丢失数据: {}", batchData.size() - reofferedCount);
                        break;
                    }
                }
                log.info("GpsService - 成功重新放回数据: {}/{}", reofferedCount, batchData.size());
            }
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * 定时任务，按照配置的固定延迟时间周期性地刷新数据缓冲队列。
     * 如果数据缓冲队列不为空，则异步执行刷新操作。
     * 刷新过程中如果发生异常，将记录错误日志。
     * 该方法使用了Spring的@Scheduled注解来定时触发，并且初始延迟和后续的执行间隔可以通过配置属性动态设置。
     */
    @Override
    @Scheduled(fixedDelayString = "#{@terminalStatsConfigProperties.gps.flushInterval}",
            initialDelayString = "#{@deviceConfigPort.getTaskBufferPoolDelayMs()}")
    public void scheduledFlush() {

        try {
            if (!dataBuffer.isEmpty()) {
                log.debug("GpsService - 定时刷新缓冲队列: size={}", dataBuffer.size());
                flushBufferAsync();
            }
        } catch (Exception e) {
            log.error("GpsService - 定时刷新失败", e);
        }
    }

    /**
     * 异步刷新缓冲队列
     */
    @Async("statisticsReportExecutor")
    public void flushBufferAsync() {
        flushBuffer();
    }

}
