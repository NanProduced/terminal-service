package com.colorlight.terminal.infrastructure.event;

import lombok.*;

/**
 * 异步缓冲区刷新事件
 * 用于触发异步服务的缓冲池刷新操作，解决@Async自调用代理失效问题
 * 
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncBufferFlushEvent {
    
    /**
     * 缓冲区类型
     */
    private BufferType bufferType;
    
    /**
     * 服务实例引用
     */
    private Object serviceInstance;
    
    /**
     * 事件创建时间戳
     */
    private Long eventTime;
    
    /**
     * 缓冲区当前大小（可选）
     */
    private Integer bufferSize;
    
    /**
     * 缓冲区类型枚举
     */
    @Getter
    public enum BufferType {
        /**
         * 设备状态更新缓冲池
         */
        DEVICE_STATUS("设备状态更新"),
        
        /**
         * GPS记录缓冲池
         */
        GPS_RECORD("GPS记录"),
        
        /**
         * 终端登录更新缓冲池
         */
        LOGIN_UPDATE("终端登录更新");
        
        private final String description;
        
        BufferType(String description) {
            this.description = description;
        }

    }
    
    /**
     * 创建设备状态刷新事件
     */
    public static AsyncBufferFlushEvent createDeviceStatusFlushEvent(Object serviceInstance, Integer bufferSize) {
        return AsyncBufferFlushEvent.builder()
                .bufferType(BufferType.DEVICE_STATUS)
                .serviceInstance(serviceInstance)
                .eventTime(System.currentTimeMillis())
                .bufferSize(bufferSize)
                .build();
    }
    
    /**
     * 创建GPS记录刷新事件
     */
    public static AsyncBufferFlushEvent createGpsRecordFlushEvent(Object serviceInstance, Integer bufferSize) {
        return AsyncBufferFlushEvent.builder()
                .bufferType(BufferType.GPS_RECORD)
                .serviceInstance(serviceInstance)
                .eventTime(System.currentTimeMillis())
                .bufferSize(bufferSize)
                .build();
    }
    
    /**
     * 创建登录更新刷新事件
     */
    public static AsyncBufferFlushEvent createLoginUpdateFlushEvent(Object serviceInstance, Integer bufferSize) {
        return AsyncBufferFlushEvent.builder()
                .bufferType(BufferType.LOGIN_UPDATE)
                .serviceInstance(serviceInstance)
                .eventTime(System.currentTimeMillis())
                .bufferSize(bufferSize)
                .build();
    }
}