package com.colorlight.terminal.rpc.dto.result;

import com.colorlight.terminal.rpc.dto.status.DeviceOnlineStatusDTO;

import java.io.Serializable;
import java.util.Map;

/**
 * 批量设备状态查询结果DTO
 * 
 * @author Nan
 */
public class BatchDeviceStatusResultDTO implements Serializable {

    private static final long serialVersionUID = -1495473951255381700L;

    /**
     * 设备在线状态映射 (deviceId -> online)
     */
    private Map<Long, Boolean> onlineStatusMap;
    
    /**
     * 设备详细状态映射 (deviceId -> status)
     * 仅当请求包含详细信息时填充
     */
    private Map<Long, DeviceOnlineStatusDTO> detailStatusMap;
    
    /**
     * 查询统计信息
     */
    private QueryStatistics statistics;
    
    // Default constructor
    public BatchDeviceStatusResultDTO() {
    }
    
    // Constructor
    public BatchDeviceStatusResultDTO(Map<Long, Boolean> onlineStatusMap, 
                                     Map<Long, DeviceOnlineStatusDTO> detailStatusMap, 
                                     QueryStatistics statistics) {
        this.onlineStatusMap = onlineStatusMap;
        this.detailStatusMap = detailStatusMap;
        this.statistics = statistics;
    }
    
    // Getters and Setters
    public Map<Long, Boolean> getOnlineStatusMap() {
        return onlineStatusMap;
    }
    
    public void setOnlineStatusMap(Map<Long, Boolean> onlineStatusMap) {
        this.onlineStatusMap = onlineStatusMap;
    }
    
    public Map<Long, DeviceOnlineStatusDTO> getDetailStatusMap() {
        return detailStatusMap;
    }
    
    public void setDetailStatusMap(Map<Long, DeviceOnlineStatusDTO> detailStatusMap) {
        this.detailStatusMap = detailStatusMap;
    }
    
    public QueryStatistics getStatistics() {
        return statistics;
    }
    
    public void setStatistics(QueryStatistics statistics) {
        this.statistics = statistics;
    }
    
    // Builder pattern
    public static BatchDeviceStatusResultDTOBuilder builder() {
        return new BatchDeviceStatusResultDTOBuilder();
    }
    
    public static class BatchDeviceStatusResultDTOBuilder {
        private Map<Long, Boolean> onlineStatusMap;
        private Map<Long, DeviceOnlineStatusDTO> detailStatusMap;
        private QueryStatistics statistics;
        
        public BatchDeviceStatusResultDTOBuilder onlineStatusMap(Map<Long, Boolean> onlineStatusMap) {
            this.onlineStatusMap = onlineStatusMap;
            return this;
        }
        
        public BatchDeviceStatusResultDTOBuilder detailStatusMap(Map<Long, DeviceOnlineStatusDTO> detailStatusMap) {
            this.detailStatusMap = detailStatusMap;
            return this;
        }
        
        public BatchDeviceStatusResultDTOBuilder statistics(QueryStatistics statistics) {
            this.statistics = statistics;
            return this;
        }
        
        public BatchDeviceStatusResultDTO build() {
            return new BatchDeviceStatusResultDTO(onlineStatusMap, detailStatusMap, statistics);
        }
    }
    
    /**
     * 查询统计信息内部类
     */
    public static class QueryStatistics implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        /**
         * 请求设备数量
         */
        private Integer requestedCount;
        
        /**
         * 在线设备数量
         */
        private Integer onlineCount;
        
        /**
         * 离线设备数量
         */
        private Integer offlineCount;
        
        /**
         * 查询耗时(毫秒)
         */
        private Long queryTimeMs;
        
        // Default constructor
        public QueryStatistics() {
        }
        
        // Constructor
        public QueryStatistics(Integer requestedCount, Integer onlineCount, Integer offlineCount, Long queryTimeMs) {
            this.requestedCount = requestedCount;
            this.onlineCount = onlineCount;
            this.offlineCount = offlineCount;
            this.queryTimeMs = queryTimeMs;
        }
        
        // Getters and Setters
        public Integer getRequestedCount() {
            return requestedCount;
        }
        
        public void setRequestedCount(Integer requestedCount) {
            this.requestedCount = requestedCount;
        }
        
        public Integer getOnlineCount() {
            return onlineCount;
        }
        
        public void setOnlineCount(Integer onlineCount) {
            this.onlineCount = onlineCount;
        }
        
        public Integer getOfflineCount() {
            return offlineCount;
        }
        
        public void setOfflineCount(Integer offlineCount) {
            this.offlineCount = offlineCount;
        }
        
        public Long getQueryTimeMs() {
            return queryTimeMs;
        }
        
        public void setQueryTimeMs(Long queryTimeMs) {
            this.queryTimeMs = queryTimeMs;
        }
        
        // Builder pattern
        public static QueryStatisticsBuilder builder() {
            return new QueryStatisticsBuilder();
        }
        
        public static class QueryStatisticsBuilder {
            private Integer requestedCount;
            private Integer onlineCount;
            private Integer offlineCount;
            private Long queryTimeMs;
            
            public QueryStatisticsBuilder requestedCount(Integer requestedCount) {
                this.requestedCount = requestedCount;
                return this;
            }
            
            public QueryStatisticsBuilder onlineCount(Integer onlineCount) {
                this.onlineCount = onlineCount;
                return this;
            }
            
            public QueryStatisticsBuilder offlineCount(Integer offlineCount) {
                this.offlineCount = offlineCount;
                return this;
            }
            
            public QueryStatisticsBuilder queryTimeMs(Long queryTimeMs) {
                this.queryTimeMs = queryTimeMs;
                return this;
            }
            
            public QueryStatistics build() {
                return new QueryStatistics(requestedCount, onlineCount, offlineCount, queryTimeMs);
            }
        }
    }
}