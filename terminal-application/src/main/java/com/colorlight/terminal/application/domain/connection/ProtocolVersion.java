package com.colorlight.terminal.application.domain.connection;

import lombok.Getter;

import java.util.Arrays;

/**
 * 协议版本枚举
 *
 * <p>定义终端设备支持的协议版本，用于版本感知的连接管理和消息处理</p>
 *
 * <h3>版本定义与路径映射</h3>
 * <ul>
 *   <li>v1.0: 现有播放盒协议，路径 /ColorWebSocket/websocket/chat</li>
 *   <li>v1.1: 新终端协议，路径 /ColorWebSocket/terminal</li>
 * </ul>
 *
 * <h3>URL示例</h3>
 * <ul>
 *   <li>v1.0: ws://ip:8443/ColorWebSocket/websocket/chat?username=xxx&password=xxx</li>
 *   <li>v1.1: ws://ip:8443/ColorWebSocket/terminal?username=xxx&protocol_version=1.1</li>
 * </ul>
 *
 * @author Nan
 */
@Getter
public enum ProtocolVersion {

    /**
     * 原先的协议版本
     */
    V1_0("1.0", "基础协议版本", "/ColorWebSocket/websocket/chat", true, 
         "com.colorlight.terminal.infrastructure.websocket.processor.v10.V10ProtocolMessageProcessor"),

    /**
     * 新定义的协议版本
     */
    V1_1("1.1", "新Websocket协议", "/ColorWebSocket/terminal", false,
         "com.colorlight.terminal.infrastructure.websocket.processor.v11.V11ProtocolMessageProcessor");

    /**
     * 版本号
     */
    private final String version;

    /**
     * 描述
     */
    private final String description;

    /**
     * 连接端点
     */
    private final String path;

    /**
     * 当前服务器是否支持（默认值，可被配置覆盖）
     */
    private final boolean defaultSupported;
    
    /**
     * 协议处理器类名（用于工厂类查找对应处理器）
     */
    private final String processorClassName;

    ProtocolVersion(String version, String description, String path, boolean defaultSupported, String processorClassName) {
        this.version = version;
        this.description = description;
        this.path = path;
        this.defaultSupported = defaultSupported;
        this.processorClassName = processorClassName;
    }
    
    /**
     * 检查协议是否被支持（考虑配置覆盖）
     * 
     * @return 是否支持（优先使用配置值，否则使用默认值）
     */
    public boolean isSupported() {
        return defaultSupported; // 后续可通过配置服务增强
    }

    /**
     * 根据版本号字符串获取协议版本
     * @param versionStr 版本号字符串
     * @return 协议版本，不支持则返回默认V1.0
     */
    public static ProtocolVersion fromVersion(String versionStr) {
        if (versionStr == null || versionStr.trim().isEmpty()) {
            return V1_0;
        }

        return Arrays.stream(values())
                .filter(v -> v.getVersion().equalsIgnoreCase(versionStr.trim()))
                .findFirst()
                .orElse(V1_0);
    }
}
