package com.colorlight.terminal.infrastructure.async;

import lombok.Getter;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 有界队列 - 满时丢弃最旧元素
 * <p>当队列达到最大容量时，自动移除最旧的元素来为新元素腾出空间</p>
 * <p>适用于状态更新等时效性场景，新数据比旧数据更重要</p>
 *
 * @param <E> 队列元素类型
 * @author Nan
 */
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

    /**
     * 创建有界队列
     *
     * @param maxCapacity 最大容量
     */
    public BoundedDropOldestQueue(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("最大容量必须大于0: " + maxCapacity);
        }

        this.maxCapacity = maxCapacity;
        this.queue = new LinkedBlockingQueue<>(maxCapacity);
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
            while (queue.size() >= maxCapacity) {
                E droppedElement = queue.poll();
                if (droppedElement != null) {
                    droppedCount.incrementAndGet();
                }
            }

            // 添加新元素
            return queue.offer(element);

        } finally {
            offerLock.unlock();
        }
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