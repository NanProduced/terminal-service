package com.colorlight.terminal.infrastructure.websocket.connection;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.port.outbound.connection.ConnectionManagerPort;
import com.colorlight.terminal.commons.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 高性能分片连接管理器
 * 
 * @author Nan
 */
@Slf4j
@Component
public class ShardedConnectionManager implements ConnectionManagerPort, DisposableBean {
    
    // ============== 配置常量 ==============

    /**
     * 分片数量 - 基于CPU核心数优化
     * <p>8核以下按核心数乘2</p>
     */
    private static final int SHARD_COUNT = Math.min(16, Runtime.getRuntime().availableProcessors() * 2);
    
    /** 每个分片的初始容量 */
    private static final int INITIAL_SHARD_CAPACITY = 1024;
    
    // ============== 核心数据结构 ==============
    
    /** 分片存储 - 每个分片独立管理一部分连接 */
    private final ConnectionShard[] shards;
    
    /** 全局连接计数器 - 原子操作保证一致性 */
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    /** 版本协议连接数统计 */
    private final Map<ProtocolVersion, AtomicInteger> versionCounter = new ConcurrentHashMap<>();

    /** 全局读写锁 - 保护整体状态变更 */
    private final ReadWriteLock globalLock = new ReentrantReadWriteLock();
    
    /** 管理器状态 */
    private volatile boolean running = true;
    
    // ============== 构造函数 ==============
    
    public ShardedConnectionManager() {
        this.shards = new ConnectionShard[SHARD_COUNT];
        
        // 初始化所有分片
        for (int i = 0; i < SHARD_COUNT; i++) {
            shards[i] = new ConnectionShard(i, INITIAL_SHARD_CAPACITY);
        }
        
        log.info("ShardedConnectionManager - 分片连接管理初始化完成, 分片: {}, 初始化总容量: {}",
                SHARD_COUNT, SHARD_COUNT * INITIAL_SHARD_CAPACITY);
    }
    
    // ============== 公开接口实现 ==============
    
    @Override
    public boolean addConnection(Long deviceId, TerminalConnection connection) {
        if (!running || deviceId == null || connection == null) {
            log.warn("ShardedConnectionManager - 无法添加连接 - manager={}, deviceId={}, connection={}",
                    running, deviceId, connection != null);
            return false;
        }
        
        try {
            ConnectionShard shard = getShardForDevice(deviceId);
            boolean added = shard.addConnection(deviceId, connection);
            
            if (added) {
                // 增加连接计数
                totalConnections.incrementAndGet();
                // 增加协议计数
                versionCounter.compute(connection.getProtocolVersion(), (key, value) -> {
                    if (value == null) {
                        return new AtomicInteger(1);
                    }
                    else {
                        value.incrementAndGet();
                        return value;
                    }
                });
                log.debug("ShardedConnectionManager - 添加终端连接: {}, version: {}, total: {}", deviceId, connection.getProtocolVersion().getVersion(), totalConnections.get());
            }
            
            return added;
            
        } catch (Exception e) {
            log.error("ShardedConnectionManager - 添加终端连接失败: {}", deviceId, e);
            return false;
        }
    }
    
    @Override
    public TerminalConnection removeConnection(Long deviceId) {
        if (!running || deviceId == null) {
            return null;
        }
        
        try {
            ConnectionShard shard = getShardForDevice(deviceId);
            TerminalConnection removed = shard.removeConnection(deviceId);
            
            if (removed != null) {
                // 减少连接计数
                totalConnections.decrementAndGet();
                // 减少协议版本计数
                versionCounter.get(removed.getProtocolVersion()).decrementAndGet();
                log.debug("ShardedConnectionManager - 移除终端连接: {}, total: {}", deviceId, totalConnections.get());
            }
            
            return removed;
            
        } catch (Exception e) {
            log.error("ShardedConnectionManager - 移除终端连接失败: {}", deviceId, e);
            return null;
        }
    }
    
    @Override
    public Optional<TerminalConnection> getConnection(Long deviceId) {
        if (!running || deviceId == null) {
            return Optional.empty();
        }
        
        try {
            ConnectionShard shard = getShardForDevice(deviceId);
            return shard.getConnection(deviceId);
            
        } catch (Exception e) {
            log.error("ShardedConnectionManager - 获取终端连接失败: {}", deviceId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public int getConnectionCount() {
        return totalConnections.get();
    }
    
    @Override
    public Collection<Long> getOnlineDeviceIds() {
        if (!running) {
            return Collections.emptyList();
        }
        
        globalLock.readLock().lock();
        try {
            Set<Long> allDeviceIds = new HashSet<>();
            
            // 并行收集所有分片的设备ID
            for (ConnectionShard shard : shards) {
                allDeviceIds.addAll(shard.getDeviceIds());
            }
            
            return allDeviceIds;
            
        } finally {
            globalLock.readLock().unlock();
        }
    }
    
    // ============== 性能优化方法 ==============
    
    /**
     * 根据设备ID计算分片索引
     * 使用一致性哈希确保分布均匀
     */
    private ConnectionShard getShardForDevice(Long deviceId) {
        int shardIndex = (deviceId.hashCode() & Integer.MAX_VALUE) % SHARD_COUNT;
        return shards[shardIndex];
    }
    
    /**
     * 获取分片统计信息 - 用于监控和调优
     */
    public Map<String, Object> getShardStatistics() {
        globalLock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalShards", SHARD_COUNT);
            stats.put("totalConnections", totalConnections.get());
            stats.put("running", running);
            
            // 分片详细统计
            Map<Integer, Integer> shardSizes = new HashMap<>();
            int maxShardSize = 0;
            int minShardSize = Integer.MAX_VALUE;
            
            for (int i = 0; i < shards.length; i++) {
                int size = shards[i].size();
                shardSizes.put(i, size);
                maxShardSize = Math.max(maxShardSize, size);
                minShardSize = Math.min(minShardSize, size);
            }
            
            stats.put("shardSizes", shardSizes);
            stats.put("maxShardSize", maxShardSize);
            stats.put("minShardSize", minShardSize);
            stats.put("loadBalance", (double) maxShardSize / Math.max(1, minShardSize));
            stats.put("versionCount", JsonUtils.toJson(versionCounter));
            
            return stats;
            
        } finally {
            globalLock.readLock().unlock();
        }
    }
    
    /**
     * 清理无效连接 - 定期维护任务
     */
    public int cleanupInvalidConnections() {
        if (!running) {
            return 0;
        }
        
        globalLock.writeLock().lock();
        try {
            int cleanedCount = 0;
            
            for (ConnectionShard shard : shards) {
                cleanedCount += shard.cleanupInvalidConnections();
            }
            
            // 更新总计数
            totalConnections.addAndGet(-cleanedCount);
            
            if (cleanedCount > 0) {
                log.info("ShardedConnectionManager - 清除 {} 个无效连接, 当前连接数: {}",
                        cleanedCount, totalConnections.get());
            }
            
            return cleanedCount;
            
        } finally {
            globalLock.writeLock().unlock();
        }
    }
    
    // ============== 生命周期管理 ==============
    
    @Override
    public void destroy() {
        log.info("ShardedConnectionManager - Shutting down ShardedConnectionManager...");
        
        globalLock.writeLock().lock();
        try {
            running = false;
            
            // 关闭所有分片
            for (ConnectionShard shard : shards) {
                shard.shutdown();
            }
            
            totalConnections.set(0);
            log.info("ShardedConnectionManager - ShardedConnectionManager shutdown completed");
            
        } finally {
            globalLock.writeLock().unlock();
        }
    }
    
    // ============== 内部分片类 ==============
    
    /**
     * 连接分片 - 管理部分连接的独立单元
     * 每个分片使用独立的锁，减少竞争
     */
    private static class ConnectionShard {
        private final int shardId;
        private final ConcurrentHashMap<Long, TerminalConnection> connections;
        private final ReadWriteLock shardLock = new ReentrantReadWriteLock();
        
        public ConnectionShard(int shardId, int initialCapacity) {
            this.shardId = shardId;
            this.connections = new ConcurrentHashMap<>(initialCapacity);
        }
        
        public boolean addConnection(Long deviceId, TerminalConnection connection) {
            TerminalConnection existing = connections.putIfAbsent(deviceId, connection);
            boolean added = (existing == null);
            
            if (!added) {
                log.debug("ConnectionShard - 设备 {} 连接已经存在 {}", deviceId, shardId);
            }
            
            return added;
        }
        
        public TerminalConnection removeConnection(Long deviceId) {
            return connections.remove(deviceId);
        }
        
        public Optional<TerminalConnection> getConnection(Long deviceId) {
            TerminalConnection connection = connections.get(deviceId);
            return Optional.ofNullable(connection);
        }
        
        public Set<Long> getDeviceIds() {
            return new HashSet<>(connections.keySet());
        }
        
        public int size() {
            return connections.size();
        }
        
        /**
         * 清理无效连接
         * 实现连接健康检查逻辑
         */
        public int cleanupInvalidConnections() {
            shardLock.writeLock().lock();
            try {
                Iterator<Map.Entry<Long, TerminalConnection>> iterator = connections.entrySet().iterator();
                int cleanedCount = 0;
                
                while (iterator.hasNext()) {
                    Map.Entry<Long, TerminalConnection> entry = iterator.next();
                    TerminalConnection connection = entry.getValue();
                    
                    // 检查连接是否有效
                    if (!isSessionValid(connection)) {
                        iterator.remove();
                        cleanedCount++;
                        log.debug("ConnectionShard - 清除无效连接: {} - 分片编号: {}",
                                entry.getKey(), shardId);
                    }
                }
                
                return cleanedCount;
                
            } finally {
                shardLock.writeLock().unlock();
            }
        }
        
        /**
         * 检查会话是否有效
         * 具体实现依赖于会话类型
         */
        private boolean isSessionValid(TerminalConnection connection) {
            try {
                if (connection.getSession() instanceof TerminalWebsocketSession session) {
                    return session.isConnected();
                }
                return false;
            } catch (Exception e) {
                log.debug("检查会话有效性时发生异常: {}", e.getMessage());
                return false;
            }
        }
        
        public void shutdown() {
            shardLock.writeLock().lock();
            try {
                log.debug("ConnectionShard - Shutting down shard {} with {} connections", shardId, connections.size());
                connections.clear();
            } finally {
                shardLock.writeLock().unlock();
            }
        }
    }
}
