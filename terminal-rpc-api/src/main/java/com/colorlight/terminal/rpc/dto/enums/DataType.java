package com.colorlight.terminal.rpc.dto.enums;

/**
 * 数据类型枚举
 * 
 * @author Nan
 */
public enum DataType {
    
    // MySQL数据
    /**
     * 截图记录 (含MinIO文件)
     */
    SCREENSHOT_RECORD("截图记录", "MySQL", "device_screenshot_record"),
    
    /**
     * 开机记录
     */
    SWITCH_RECORD("开机记录", "MySQL", "device_switch_on_record"),
    
    /**
     * 设备账号信息 (必须删除)
     */
    DEVICE_ACCOUNT("设备账号信息", "MySQL", "terminal_account"),
    
    // MongoDB数据  
    /**
     * GPS记录
     */
    GPS_RECORD("GPS记录", "MongoDB", "gps_record"),
    
    /**
     * 状态上报
     */
    STATUS_REPORT("状态上报", "MongoDB", "terminal_status_report"),
    
    /**
     * 终端日志
     */
    TERMINAL_LOG("终端日志", "MongoDB", "terminal_log"),
    
    /**
     * 素材播放记录
     */
    MEDIA_PLAY_RECORD("素材播放记录", "MongoDB", "media_play_record"),
    
    /**
     * 节目播放记录
     */
    PROGRAM_PLAY_RECORD("节目播放记录", "MongoDB", "program_play_record"),
    
    /**
     * 在线时长
     */
    ONLINE_TIME("在线时长", "MongoDB", "terminal_online_time"),
    
    /**
     * 异常重连记录
     */
    ABNORMAL_RECONNECT("异常重连记录", "MongoDB", "terminal_abnormal_reconnect"),
    
    // 缓存数据
    /**
     * Redis缓存数据
     */
    REDIS_CACHE("Redis缓存数据", "Redis", "device:*");
    
    private final String description;
    private final String storageType;
    private final String target;
    
    DataType(String description, String storageType, String target) {
        this.description = description;
        this.storageType = storageType;
        this.target = target;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getStorageType() {
        return storageType;
    }
    
    public String getTarget() {
        return target;
    }
    
    /**
     * 是否为MySQL数据类型
     */
    public boolean isMysqlType() {
        return "MySQL".equals(this.storageType);
    }
    
    /**
     * 是否为MongoDB数据类型
     */
    public boolean isMongodbType() {
        return "MongoDB".equals(this.storageType);
    }
    
    /**
     * 是否为Redis数据类型
     */
    public boolean isRedisType() {
        return "Redis".equals(this.storageType);
    }
}