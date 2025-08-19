package com.colorlight.terminal.rpc.dto.request;


import java.io.Serializable;
import java.util.List;

/**
 * 批量设备状态查询请求DTO
 * 
 * @author Nan
 */
public class BatchDeviceStatusRequestDTO implements Serializable {

    private static final long serialVersionUID = 3016113406080329891L;

    /**
     * 设备ID列表
     */
    private List<Long> deviceIds;
    
    /**
     * 是否包含详细信息
     */
    private Boolean includeDetails;
    
    // Default constructor
    public BatchDeviceStatusRequestDTO() {
    }
    
    // Constructor
    public BatchDeviceStatusRequestDTO(List<Long> deviceIds, Boolean includeDetails) {
        this.deviceIds = deviceIds;
        this.includeDetails = includeDetails;
    }
    
    // Getters and Setters
    public List<Long> getDeviceIds() {
        return deviceIds;
    }
    
    public void setDeviceIds(List<Long> deviceIds) {
        this.deviceIds = deviceIds;
    }
    
    public Boolean getIncludeDetails() {
        return includeDetails;
    }
    
    public void setIncludeDetails(Boolean includeDetails) {
        this.includeDetails = includeDetails;
    }
}