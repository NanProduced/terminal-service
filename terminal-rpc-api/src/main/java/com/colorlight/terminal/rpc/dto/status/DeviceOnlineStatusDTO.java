package com.colorlight.terminal.rpc.dto.status;


import java.io.Serializable;

/**
 * 设备在线状态RPC传输对象
 * 
 * @author Nan
 */
public class DeviceOnlineStatusDTO implements Serializable {

    private static final long serialVersionUID = 4599058877559350625L;

    /**
     * 设备ID
     */
    private Long deviceId;
    
    /**
     * 最后上报时间戳(毫秒)
     */
    private Long lastReportTime;
    
    /**
     * 最后上报来源
     */
    private String lastReportSource;
    
    /**
     * 在线状态
     */
    private String status;
    
    /**
     * 状态变更时间戳(毫秒)
     */
    private Long statusChangeTime;
    
    /**
     * 本次上线开始时间戳(毫秒)
     */
    private Long onlineStartTime;
    
    /**
     * 客户端IP地址
     */
    private String clientIp;
    
    /**
     * 当前在线时长(毫秒)
     */
    private Long currentOnlineDuration;
    
    /**
     * 是否在线
     */
    private Boolean online;
    
    // Default constructor
    public DeviceOnlineStatusDTO() {
    }
    
    // Full constructor
    public DeviceOnlineStatusDTO(Long deviceId, Long lastReportTime, String lastReportSource, 
                                String status, Long statusChangeTime, Long onlineStartTime, 
                                String clientIp, Long currentOnlineDuration, Boolean online) {
        this.deviceId = deviceId;
        this.lastReportTime = lastReportTime;
        this.lastReportSource = lastReportSource;
        this.status = status;
        this.statusChangeTime = statusChangeTime;
        this.onlineStartTime = onlineStartTime;
        this.clientIp = clientIp;
        this.currentOnlineDuration = currentOnlineDuration;
        this.online = online;
    }
    
    // Getters and Setters
    public Long getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }
    
    public Long getLastReportTime() {
        return lastReportTime;
    }
    
    public void setLastReportTime(Long lastReportTime) {
        this.lastReportTime = lastReportTime;
    }
    
    public String getLastReportSource() {
        return lastReportSource;
    }
    
    public void setLastReportSource(String lastReportSource) {
        this.lastReportSource = lastReportSource;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Long getStatusChangeTime() {
        return statusChangeTime;
    }
    
    public void setStatusChangeTime(Long statusChangeTime) {
        this.statusChangeTime = statusChangeTime;
    }
    
    public Long getOnlineStartTime() {
        return onlineStartTime;
    }
    
    public void setOnlineStartTime(Long onlineStartTime) {
        this.onlineStartTime = onlineStartTime;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public Long getCurrentOnlineDuration() {
        return currentOnlineDuration;
    }
    
    public void setCurrentOnlineDuration(Long currentOnlineDuration) {
        this.currentOnlineDuration = currentOnlineDuration;
    }
    
    public Boolean getOnline() {
        return online;
    }
    
    public void setOnline(Boolean online) {
        this.online = online;
    }
    
    // Builder pattern for convenience
    public static DeviceOnlineStatusDTOBuilder builder() {
        return new DeviceOnlineStatusDTOBuilder();
    }
    
    public static class DeviceOnlineStatusDTOBuilder {
        private Long deviceId;
        private Long lastReportTime;
        private String lastReportSource;
        private String status;
        private Long statusChangeTime;
        private Long onlineStartTime;
        private String clientIp;
        private Long currentOnlineDuration;
        private Boolean online;
        
        public DeviceOnlineStatusDTOBuilder deviceId(Long deviceId) {
            this.deviceId = deviceId;
            return this;
        }
        
        public DeviceOnlineStatusDTOBuilder lastReportTime(Long lastReportTime) {
            this.lastReportTime = lastReportTime;
            return this;
        }
        
        public DeviceOnlineStatusDTOBuilder lastReportSource(String lastReportSource) {
            this.lastReportSource = lastReportSource;
            return this;
        }
        
        public DeviceOnlineStatusDTOBuilder status(String status) {
            this.status = status;
            return this;
        }
        
        public DeviceOnlineStatusDTOBuilder statusChangeTime(Long statusChangeTime) {
            this.statusChangeTime = statusChangeTime;
            return this;
        }
        
        public DeviceOnlineStatusDTOBuilder onlineStartTime(Long onlineStartTime) {
            this.onlineStartTime = onlineStartTime;
            return this;
        }
        
        public DeviceOnlineStatusDTOBuilder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }
        
        public DeviceOnlineStatusDTOBuilder currentOnlineDuration(Long currentOnlineDuration) {
            this.currentOnlineDuration = currentOnlineDuration;
            return this;
        }
        
        public DeviceOnlineStatusDTOBuilder online(Boolean online) {
            this.online = online;
            return this;
        }
        
        public DeviceOnlineStatusDTO build() {
            return new DeviceOnlineStatusDTO(deviceId, lastReportTime, lastReportSource, 
                                           status, statusChangeTime, onlineStartTime, 
                                           clientIp, currentOnlineDuration, online);
        }
    }
}