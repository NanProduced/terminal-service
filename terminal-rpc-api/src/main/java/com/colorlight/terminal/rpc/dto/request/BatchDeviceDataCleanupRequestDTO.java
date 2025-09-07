package com.colorlight.terminal.rpc.dto.request;

import com.colorlight.terminal.rpc.dto.config.DataCleanupConfigDTO;

import java.io.Serializable;
import java.util.List;

/**
 * 批量设备数据清理请求DTO
 * 
 * @author Nan
 */
public class BatchDeviceDataCleanupRequestDTO implements Serializable {

    private static final long serialVersionUID = -4601831499293703235L;
    /**
     * 设备ID列表
     */
    private List<Long> deviceIds;
    
    /**
     * 清理配置
     */
    private DataCleanupConfigDTO config;
    
    // 默认构造函数
    public BatchDeviceDataCleanupRequestDTO() {}
    
    // 仅设备ID列表构造函数 (使用默认配置)
    public BatchDeviceDataCleanupRequestDTO(List<Long> deviceIds) {
        this.deviceIds = deviceIds;
        this.config = null; // 使用默认配置
    }
    
    // 全参数构造函数
    public BatchDeviceDataCleanupRequestDTO(List<Long> deviceIds, DataCleanupConfigDTO config) {
        this.deviceIds = deviceIds;
        this.config = config;
    }
    
    // Builder模式
    public static BatchDeviceDataCleanupRequestDTOBuilder builder() {
        return new BatchDeviceDataCleanupRequestDTOBuilder();
    }
    
    public static class BatchDeviceDataCleanupRequestDTOBuilder {
        private List<Long> deviceIds;
        private DataCleanupConfigDTO config;
        
        public BatchDeviceDataCleanupRequestDTOBuilder deviceIds(List<Long> deviceIds) {
            this.deviceIds = deviceIds;
            return this;
        }
        
        public BatchDeviceDataCleanupRequestDTOBuilder config(DataCleanupConfigDTO config) {
            this.config = config;
            return this;
        }
        
        public BatchDeviceDataCleanupRequestDTO build() {
            return new BatchDeviceDataCleanupRequestDTO(deviceIds, config);
        }
    }
    
    // Getter和Setter
    public List<Long> getDeviceIds() {
        return deviceIds;
    }
    
    public void setDeviceIds(List<Long> deviceIds) {
        this.deviceIds = deviceIds;
    }
    
    public DataCleanupConfigDTO getConfig() {
        return config;
    }
    
    public void setConfig(DataCleanupConfigDTO config) {
        this.config = config;
    }
    
    @Override
    public String toString() {
        return "BatchDeviceDataCleanupRequestDTO{" +
               "deviceIds=" + deviceIds +
               ", config=" + config +
               '}';
    }
}