package com.colorlight.terminal.application.port.outbound.repository;

import com.colorlight.terminal.application.domain.status.OnlineStatus;

import java.time.LocalDateTime;

/**
 * Mongo 持久化终端在线状态数据的仓储接口。
 */
public interface TerminalOnlineStatusRepository {

    /**
     * 创建或更新设备的在线状态，通常在上线或重连时调用。
     *
     * @param deviceId       设备 ID
     * @param status         在线状态
     * @param onlineStartTime 在线开始时间，可为 null
     */
    void upsertOnlineState(Long deviceId, OnlineStatus status, LocalDateTime onlineStartTime);


    /**
     * 结束一次在线会话，累加总在线时长并标记为离线。
     *
     * @param deviceId            设备 ID
     * @param onlineStartTime     本次会话的上线时间
     * @param sessionDurationSecs 会话持续时长（秒）
     */
    void finalizeOnlineSession(Long deviceId, LocalDateTime onlineStartTime, long sessionDurationSecs);
}
