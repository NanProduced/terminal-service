package com.colorlight.terminal.application.port.outbound.connection;

import com.colorlight.terminal.application.domain.connection.TerminalConnection;

import java.util.Collection;
import java.util.Optional;

/**
 * 连接管理器接口
 *
 * <p>定义WebSocket连接管理的核心抽象</p>
 *
 * @author Nan
 */
public interface ConnectionManagerPort {

    /**
     * 添加WebSocket连接
     *
     * @param deviceId 设备ID
     * @param session WebSocket会话
     * @return 是否添加成功
     */
    boolean addConnection(Long deviceId, TerminalConnection session);

    /**
     * 移除WebSocket连接
     *
     * @param deviceId 设备ID
     * @return 被移除的会话，如果不存在返回null
     */
    TerminalConnection removeConnection(Long deviceId);

    /**
     * 获取WebSocket连接
     *
     * @param deviceId 设备ID
     * @return WebSocket会话的Optional包装
     */
    Optional<TerminalConnection> getConnection(Long deviceId);

    /**
     * 获取当前连接总数
     *
     * @return 连接数量
     */
    int getConnectionCount();

    /**
     * 获取所有在线设备ID
     *
     * @return 设备ID集合
     */
    Collection<Long> getOnlineDeviceIds();

}
