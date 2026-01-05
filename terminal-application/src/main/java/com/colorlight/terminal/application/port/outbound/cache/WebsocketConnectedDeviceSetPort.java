package com.colorlight.terminal.application.port.outbound.cache;

/**
 * WebSocket 已连接设备集合存储接口
 *
 * <p>用于维护 Redis Set：device:websocket:set</p>
 * <ul>
 *   <li>连接建立：SADD device:websocket:set {deviceId}</li>
 *   <li>连接断开：SREM device:websocket:set {deviceId}</li>
 * </ul>
 *
 * @author Nan
 */
public interface WebsocketConnectedDeviceSetPort {

    /**
     * 记录设备已建立 WebSocket 连接
     *
     * @param deviceId 设备ID
     */
    void add(Long deviceId);

    /**
     * 移除设备 WebSocket 连接记录
     *
     * @param deviceId 设备ID
     */
    void remove(Long deviceId);
}

