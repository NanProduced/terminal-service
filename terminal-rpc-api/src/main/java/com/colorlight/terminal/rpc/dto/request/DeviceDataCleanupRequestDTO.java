package com.colorlight.terminal.rpc.dto.request;

import com.colorlight.terminal.rpc.dto.config.DataCleanupConfigDTO;

import java.io.Serializable;

/**
 * 设备数据清理请求DTO
 * 
 * @author Nan
 */
public class DeviceDataCleanupRequestDTO implements Serializable {

    private static final long serialVersionUID = 3515492847003424931L;
    /**
     * 设备ID
     */
    private Long deviceId;
    
    /**
     * 清理配置 (可选，使用默认配置)
     */
    private DataCleanupConfigDTO config;
    
    // 默认构造函数
    public DeviceDataCleanupRequestDTO() {}
    
    // 仅设备ID构造函数 (使用默认配置)
    public DeviceDataCleanupRequestDTO(Long deviceId) {
        this.deviceId = deviceId;
        this.config = null; // 使用默认配置
    }
    
    // 全参数构造函数
    public DeviceDataCleanupRequestDTO(Long deviceId, DataCleanupConfigDTO config) {
        this.deviceId = deviceId;
        this.config = config;
    }
    
    // Builder模式
    public static DeviceDataCleanupRequestDTOBuilder builder() {
        return new DeviceDataCleanupRequestDTOBuilder();
    }
    
    public static class DeviceDataCleanupRequestDTOBuilder {
        private Long deviceId;
        private DataCleanupConfigDTO config;
        
        public DeviceDataCleanupRequestDTOBuilder deviceId(Long deviceId) {
            this.deviceId = deviceId;
            return this;
        }
        
        public DeviceDataCleanupRequestDTOBuilder config(DataCleanupConfigDTO config) {
            this.config = config;
            return this;
        }
        
        public DeviceDataCleanupRequestDTO build() {
            return new DeviceDataCleanupRequestDTO(deviceId, config);
        }
    }
    
    // Getter和Setter
    public Long getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }
    
    public DataCleanupConfigDTO getConfig() {
        return config;
    }
    
    public void setConfig(DataCleanupConfigDTO config) {
        this.config = config;
    }
    
    @Override
    public String toString() {
        return "DeviceDataCleanupRequestDTO{" +
               "deviceId=" + deviceId +
               ", config=" + config +
               '}';
    }
}