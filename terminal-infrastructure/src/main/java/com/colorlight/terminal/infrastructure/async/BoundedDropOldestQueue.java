package com.colorlight.terminal.infrastructure.async;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 有界队列 - 满时丢弃最旧元素
 * <p>当队列达到最大容量时，自动移除最旧的元素来为新元素腾出空间</p>
 * <p>适用于状态更新等时效性场景，新数据比旧数据更重要</p>
 * <p>增强功能：数据丢弃监控，当队列满时记录警告日志</p>
 *
 * @param <E> 队列元素类型
 * @author Nan
 */
@Slf4j
public class BoundedDropOldestQueue<E> {

    private final LinkedBlockingQueue<E> queue;
    /**
     * -- GETTER --
     *  获取最大容量
     *
     * @return 最大容量
     */
    @Getter
    private final int maxCapacity;
    private final ReentrantLock offerLock = new ReentrantLock();

    // 统计指标
    private final AtomicLong droppedCount = new AtomicLong(0);

    // 队列名称（用于日志标识）
    private final String queueName;

    /**
     * 创建有界队列
     *
     * @param maxCapacity 最大容量
     */
    public BoundedDropOldestQueue(int maxCapacity) {
        this(maxCapacity, "UnnamedQueue");
    }

    /**
     * 创建有界队列（带名称）
     *
     * @param maxCapacity 最大容量
     * @param queueName 队列名称（用于日志标识）
     */
    public BoundedDropOldestQueue(int maxCapacity, String queueName) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("最大容量必须大于0: " + maxCapacity);
        }

        this.maxCapacity = maxCapacity;
        this.queueName = queueName;
        this.queue = new LinkedBlockingQueue<>(maxCapacity);

        log.info("BoundedQueue - 有界队列初始化完成: name={}, maxCapacity={}", queueName, maxCapacity);
    }

    /**
     * 添加元素 - 满时丢弃最旧元素
     *
     * @param element 要添加的元素
     * @return 是否成功添加
     */
    public boolean offer(E element) {
        if (element == null) {
            return false;
        }

        offerLock.lock();
        try {
            // 如果队列已满，先移除最旧的元素
            boolean hasDropped = false;
            while (queue.size() >= maxCapacity) {
                E droppedElement = queue.poll();
                if (droppedElement != null) {
                    long currentDropCount = droppedCount.incrementAndGet();
                    hasDropped = true;

                    // 记录丢弃警告（包含丢弃的数据信息）
                    if (log.isWarnEnabled()) {
                        log.warn("BoundedQueue - 队列满，丢弃最旧数据: queue={}, currentSize={}, maxCapacity={}, " +
                                "droppedElement={}, totalDropped={}",
                                queueName, queue.size(), maxCapacity,
                                formatElement(droppedElement), currentDropCount);
                    }
                }
            }

            // 如果发生了丢弃，记录当前队列使用率（用于诊断）
            if (hasDropped && log.isWarnEnabled()) {
                double utilizationRate = getUtilizationRate();
                log.warn("BoundedQueue - 队列使用率: queue={}, utilizationRate={}%, currentSize={}/{}",
                        queueName, String.format("%.1f", utilizationRate * 100), queue.size(), maxCapacity);
            }

            // 添加新元素
            return queue.offer(element);

        } finally {
            offerLock.unlock();
        }
    }

    /**
     * 格式化元素用于日志输出（避免敏感信息泄露）
     */
    private String formatElement(E element) {
        if (element == null) {
            return "null";
        }

        // 只输出类型和部分标识信息，不输出完整对象
        String className = element.getClass().getSimpleName();
        String elementInfo = element.toString();

        // 限制日志长度，避免超长日志
        if (elementInfo.length() > 100) {
            elementInfo = elementInfo.substring(0, 100) + "...";
        }

        return className + "[" + elementInfo + "]";
    }

    /**
     * 批量添加元素
     *
     * @param elements 要添加的元素集合
     * @return 成功添加的元素数量
     */
    public int offerAll(Collection<? extends E> elements) {
        if (elements == null || elements.isEmpty()) {
            return 0;
        }

        int addedCount = 0;
        for (E element : elements) {
            if (offer(element)) {
                addedCount++;
            }
        }

        return addedCount;
    }

    /**
     * 移除并返回队列头部元素
     *
     * @return 队列头部元素，如果队列为空则返回null
     */
    public E poll() {
        return queue.poll();
    }

    /**
     * 获取队列当前大小
     *
     * @return 当前队列大小
     */
    public int size() {
        return queue.size();
    }

    /**
     * 检查队列是否为空
     *
     * @return 队列是否为空
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * 获取已丢弃的元素总数
     *
     * @return 丢弃计数
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 获取队列使用率
     *
     * @return 使用率 (0.0 - 1.0)
     */
    public double getUtilizationRate() {
        return (double) size() / maxCapacity;
    }

    /**
     * 清空队列
     */
    public void clear() {
        offerLock.lock();
        try {
            queue.clear();
        } finally {
            offerLock.unlock();
        }
    }

    /**
     * 获取队列状态信息
     */
    public QueueStatus getStatus() {
        return new QueueStatus(
                size(),
                maxCapacity,
                getUtilizationRate(),
                droppedCount.get()
        );
    }

    /**
     * 队列状态记录
     */
    public record QueueStatus(
            int currentSize,
            int maxCapacity,
            double utilizationRate,
            long droppedCount
    ) {}
}