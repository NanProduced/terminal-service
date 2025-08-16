package com.colorlight.terminal.rpc.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * Rpc调用响应封装
 * <p>基于异常传递涉及序列化兼容性/JDK11兼容性要求</p>
 * <p>使用简单数据类型</p>
 * @param <T>
 */
public class RpcResult <T> implements Serializable {
    private boolean success;
    private String errorCode;
    private String errorMessage;
    private T data;
    private Long timestamp;
    private Map<String, Object> context;


}

