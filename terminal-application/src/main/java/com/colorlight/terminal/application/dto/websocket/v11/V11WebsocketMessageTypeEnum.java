package com.colorlight.terminal.application.dto.websocket.v11;

import lombok.Getter;

/**
 * V11协议 - 消息类型
 *
 * @author Nan
 */
@Getter
public enum V11WebsocketMessageTypeEnum {

    ERROR(0, "ERROR"),

    /**
     * V1.1协议为省流量 - 心跳使用空上报/回复
     */
    @Deprecated
    HEARTBEAT(1, "HEARTBEAT"),

    /**
     * 指令
     */
    COMMAND(2, "COMMENT"),

    /**
     * 排程
     */
    SCHEDULE(3, "SCHEDULE"),

    /**
     * 节目
     */
    PROGRAMS(4, "PROGRAMS"),

    /**
     * 指令确认
     */
    CONFIRM_COMMAND(5, "CONFIRM_COMMAND"),

    /**
     * led_status上报
     */
    STATUS_REPORT(6, "STATUS_REPORT"),

    /**
     * 下载进度
     */
    DOWNLOAD_STATUS(7, "DOWNLOAD_STATUS"),

    /**
     * 素材播放上报
     */
    MEDIA_RECORD(8, "MEDIA_PLAY_RECORD_REPORT"),

    /**
     * 传感器上报
     */
    MONITOR_REPORT(9, "MONITOR_REPORT"),

    /**
     * 终端日志上报
     */
    LOG_REPORT(10, "LOG_REPORT");

    private final Integer id;

    private final String type;

    V11WebsocketMessageTypeEnum(Integer id, String type) {
        this.id = id;
        this.type = type;
    }

    public static V11WebsocketMessageTypeEnum fromId(Integer id) {
        for (V11WebsocketMessageTypeEnum type : V11WebsocketMessageTypeEnum.values()) {
            if (type.getId().equals(id)) {
                return type;
            }
        }
        return null;
    }

}
