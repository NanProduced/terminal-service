package com.colorlight.terminal.application.domain.status;

import lombok.Getter;

/**
 * 设备在线状态枚举
 * 
 * @author Nan
 */
@Getter
public enum OnlineStatus {

    /**
     * 设备首次上报（终端的状态缓存不存在，一段时间内的首次上报）
     */
    GO_LIVE("上线"),

    /**
     * 在线状态
     */
    ONLINE("在线"),

    /**
     *
     * 用于区分短时间内设备超出上报时间阈值被判离线，然后重新连接
     * <p>
     * <l><b>技术上判断为：</b>
     * <l>1.定时任务检测到设备离线（超出上报时间阈值），标记状态为离线</l>
     * <l>2.这时状态缓存还未过期，还存在状态索引</l>
     * <l>3.终端重新上报（在状态过期前上报）</l>
     * </p>
     */
    RECONNECT("重连"),
    
    /**
     * 离线状态
     */
    OFFLINE("离线"),
    
    /**
     * 未知状态（Redis故障时的降级状态）
     */
    UNKNOWN("未知");
    
    private final String description;
    
    OnlineStatus(String description) {
        this.description = description;
    }

}