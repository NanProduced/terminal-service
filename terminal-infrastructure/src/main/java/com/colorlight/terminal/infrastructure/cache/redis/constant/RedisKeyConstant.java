package com.colorlight.terminal.infrastructure.cache.redis.constant;

public class RedisKeyConstant {

    /*===================  终端指令模板 ====================== */

    /**
     * 指令队列 - List
     */
    public static final String COMMAND_QUEUE_KEY = "terminal:commands:%d";

    /**
     * 指令去重索引 - Hash
     */
    public static final String COMMAND_INDEX_KEY = "terminal:command:index:%d";      // 去重索引

    /**
     * 指令详情 - String
     */
    public static final String COMMAND_DETAIL_KEY = "terminal:command:detail:%d";   // 指令详情

    /*===================  设备在线状态模板 ====================== */

    /**
     * 设备在线状态 - Hash
     * 存储设备的当前在线状态信息
     */
    public static final String DEVICE_STATUS_KEY = "device:status:%d";

    /**
     * 设备状态索引 - Set
     * 存储所有设备ID，用于快速遍历
     */
    public static final String DEVICE_STATUS_INDEX_KEY = "device:status:index";

    /**
     * 在线设备计数 - String
     * 存储当前在线设备总数
     */
    public static final String ONLINE_DEVICE_COUNT_KEY = "device:online:count";
    
    /*=================== 设备开机时间 ===================*/

    public static final String DEVICE_SWITCH_ON_RECORD_KEY = "device:switch:%d";

    /*=================== 设备时间及时区信息缓存 ===================*/

    public static final String DEVICE_TIME_ZONE_KEY = "device:timezone:%d";

    /*===================  分布式锁 ====================== */

    /**
     * 终端在线状态更新分布式锁
     */
    public static final String DEVICE_STATUS_UPDATE_LOCK_KEY = "device:update:lock:%d";
}
