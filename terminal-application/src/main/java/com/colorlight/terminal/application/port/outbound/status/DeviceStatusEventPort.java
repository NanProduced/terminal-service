package com.colorlight.terminal.application.port.outbound.status;

import com.colorlight.terminal.application.domain.status.DeviceStatusEvent;

/**
 * 设备状态事件发布端口接口
 * 预留扩展机制，用于通知其他系统
 * 
 * @author Nan
 */
public interface DeviceStatusEventPort {
    
    /**
     * 发布设备状态变更事件
     * 
     * @param event 状态变更事件
     */
    void publishStatusEvent(DeviceStatusEvent event);
    
    /**
     * 批量发布状态变更事件
     * 用于批量离线处理场景
     * 
     * @param events 事件列表
     */
    void batchPublishStatusEvents(java.util.List<DeviceStatusEvent> events);
}