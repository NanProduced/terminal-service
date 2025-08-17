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

}
