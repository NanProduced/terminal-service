package com.colorlight.terminal.rpc.dto.config;

import com.colorlight.terminal.rpc.dto.enums.CleanupMode;
import com.colorlight.terminal.rpc.dto.enums.DataType;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据清理配置DTO
 * 
 * @author Nan
 */
public class DataCleanupConfigDTO implements Serializable {

    private static final long serialVersionUID = 1619090341925739033L;
    /**
     * 清理模式
     */
    private CleanupMode mode = CleanupMode.ALL;
    
    /**
     * 数据类型列表 (mode为INCLUDE/EXCLUDE时使用)
     */
    private Set<DataType> dataTypes = new HashSet<>();
    
    // 默认构造函数
    public DataCleanupConfigDTO() {}
    
    // 全参数构造函数
    public DataCleanupConfigDTO(CleanupMode mode, Set<DataType> dataTypes) {
        this.mode = mode;
        this.dataTypes = dataTypes != null ? dataTypes : new HashSet<>();
    }
    
    // Builder模式
    public static DataCleanupConfigDTOBuilder builder() {
        return new DataCleanupConfigDTOBuilder();
    }
    
    public static class DataCleanupConfigDTOBuilder {
        private CleanupMode mode = CleanupMode.ALL;
        private Set<DataType> dataTypes = new HashSet<>();
        
        public DataCleanupConfigDTOBuilder mode(CleanupMode mode) {
            this.mode = mode;
            return this;
        }
        
        public DataCleanupConfigDTOBuilder dataTypes(Set<DataType> dataTypes) {
            this.dataTypes = dataTypes;
            return this;
        }
        
        public DataCleanupConfigDTOBuilder addDataType(DataType dataType) {
            if (this.dataTypes == null) {
                this.dataTypes = new HashSet<>();
            }
            this.dataTypes.add(dataType);
            return this;
        }
        
        public DataCleanupConfigDTO build() {
            return new DataCleanupConfigDTO(mode, dataTypes);
        }
    }
    
    // Getter和Setter
    public CleanupMode getMode() {
        return mode;
    }
    
    public void setMode(CleanupMode mode) {
        this.mode = mode;
    }
    
    public Set<DataType> getDataTypes() {
        return dataTypes;
    }
    
    public void setDataTypes(Set<DataType> dataTypes) {
        this.dataTypes = dataTypes;
    }
    
    @Override
    public String toString() {
        return "DataCleanupConfigDTO{" +
               "mode=" + mode +
               ", dataTypes=" + dataTypes +
               '}';
    }
}