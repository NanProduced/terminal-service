package com.colorlight.terminal.application.dto.cache;

import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import org.springframework.lang.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 设备级状态更新上下文
 * 负责串行化更新、合并心跳与触发写入
 *
 * @author Nan
 */
public final class DeviceUpdateContext {

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<DeviceOnlineStatus> pendingStatus = new AtomicReference<>();
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    /**
     * 获取锁
     */
    public void lock() {
        lock.lock();
    }

    /**
     * 释放锁
     */
    public void unlock() {
        lock.unlock();
    }

    /**
     * 保存待flush的设备状态
     *
     * @param status 设备在线状态
     */
    public void savePendingStatus(DeviceOnlineStatus status) {
        pendingStatus.set(status);
    }

    /**
     * 尝试触发flush操作
     * 使用CAS确保只有一个线程能成功调度flush
     *
     * @return true 表示成功调度（应该执行flush），false 表示已有flush在进行
     */
    public boolean tryScheduleFlush() {
        return flushing.compareAndSet(false, true);
    }

    /**
     * 取出并清空最新的待处理状态
     *
     * @return 最新的设备状态，如果没有待处理状态则返回 null
     */
    @Nullable
    public DeviceOnlineStatus drainLatest() {
        return pendingStatus.getAndSet(null);
    }

    /**
     * 检查是否还有待处理的状态
     *
     * @return true 表示有待处理状态，false 表示无
     */
    public boolean hasPending() {
        return pendingStatus.get() != null;
    }

    /**
     * 标记flush完成
     * 允许下一次的flush操作
     */
    public void finishFlush() {
        flushing.set(false);
    }
}
