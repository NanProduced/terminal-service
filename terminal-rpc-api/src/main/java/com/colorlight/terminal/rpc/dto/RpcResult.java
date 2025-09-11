package com.colorlight.terminal.rpc.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RPC统一响应结果 (纯Java实现，无Lombok依赖)
 * 
 * @param <T> 数据类型
 * @author Nan
 */
public class RpcResult<T> implements Serializable {

    private static final long serialVersionUID = 4003729994395502714L;
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误代码
     */
    private String errorCode;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 响应时间戳
     */
    private Long timestamp;
    
    /**
     * 扩展上下文
     */
    private Map<String, Object> context;
    
    // 默认构造函数
    public RpcResult() {
        this.timestamp = System.currentTimeMillis();
        this.context = new HashMap<>();
    }
    
    // 全参数构造函数
    public RpcResult(boolean success, String errorCode, String errorMessage, T data, Long timestamp, Map<String, Object> context) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.data = data;
        this.timestamp = timestamp != null ? timestamp : System.currentTimeMillis();
        this.context = context != null ? context : new HashMap<>();
    }
    
    // 成功结果的静态工厂方法
    public static <T> RpcResult<T> success(T data) {
        RpcResult<T> result = new RpcResult<>();
        result.success = true;
        result.data = data;
        return result;
    }
    
    public static <T> RpcResult<T> success() {
        return success(null);
    }
    
    // 失败结果的静态工厂方法
    public static <T> RpcResult<T> error(String errorCode, String errorMessage) {
        RpcResult<T> result = new RpcResult<>();
        result.success = false;
        result.errorCode = errorCode;
        result.errorMessage = errorMessage;
        return result;
    }
    
    public static <T> RpcResult<T> error(String errorMessage) {
        return error("SYSTEM_ERROR", errorMessage);
    }
    
    // Getter 和 Setter 方法
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    // 上下文操作方法
    public RpcResult<T> addContext(String key, Object value) {
        if (this.context == null) {
            this.context = new HashMap<>();
        }
        this.context.put(key, value);
        return this;
    }
    
    public Object getContext(String key) {
        return this.context != null ? this.context.get(key) : null;
    }
    
    // equals, hashCode, toString
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RpcResult<?> rpcResult = (RpcResult<?>) o;
        return success == rpcResult.success &&
               Objects.equals(errorCode, rpcResult.errorCode) &&
               Objects.equals(errorMessage, rpcResult.errorMessage) &&
               Objects.equals(data, rpcResult.data) &&
               Objects.equals(timestamp, rpcResult.timestamp) &&
               Objects.equals(context, rpcResult.context);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, errorCode, errorMessage, data, timestamp, context);
    }
    
    @Override
    public String toString() {
        return "RpcResult{" +
               "success=" + success +
               ", errorCode='" + errorCode + '\'' +
               ", errorMessage='" + errorMessage + '\'' +
               ", data=" + data +
               ", timestamp=" + timestamp +
               ", context=" + context +
               '}';
    }
}