package com.colorlight.terminal.application.port.outbound.websocket;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;

import java.util.Map;

/**
 * 协议处理器端口接口 - 用于解耦Application层与Infrastructure层
 * 
 * <p>端口职责：</p>
 * <ul>
 *   <li>协议处理器获取：根据协议版本获取对应的处理器</li>
 *   <li>处理器检查：检查协议版本是否有可用处理器</li>
 *   <li>处理器信息：提供处理器注册信息用于监控</li>
 * </ul>
 * 
 * <p>实现注意事项：</p>
 * <ul>
 *   <li>Infrastructure层负责具体实现</li>
 *   <li>支持协议版本的动态配置</li>
 *   <li>保证处理器与协议版本的一对一映射</li>
 * </ul>
 * 
 * @author Nan
 */
public interface ProtocolProcessorPort {
    
    /**
     * 根据协议版本获取对应的处理器
     * 
     * <p>业务约束：</p>
     * <ul>
     *   <li>ProtocolVersion已在连接建立时确定且保证非空</li>
     *   <li>不支持的协议版本应抛出IllegalArgumentException</li>
     *   <li>无降级逻辑，遵循失败快速原则</li>
     * </ul>
     * 
     * @param version 协议版本（保证非空）
     * @return 对应的协议处理器
     * @throws IllegalArgumentException 如果协议版本不支持
     */
    ProtocolMessageProcessor getProcessor(ProtocolVersion version);
    
    /**
     * 检查指定协议版本是否有可用的处理器
     * 
     * @param version 协议版本
     * @return 是否有可用处理器
     */
    boolean hasProcessor(ProtocolVersion version);
    
    /**
     * 获取所有已注册的协议处理器信息（用于监控和调试）
     * 
     * @return 协议版本到处理器类名的映射
     */
    Map<ProtocolVersion, String> getProcessorInfo();
}