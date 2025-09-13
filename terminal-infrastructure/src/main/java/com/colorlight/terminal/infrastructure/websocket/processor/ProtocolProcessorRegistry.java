package com.colorlight.terminal.infrastructure.websocket.processor;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolMessageProcessor;
import com.colorlight.terminal.application.port.outbound.websocket.ProtocolProcessorPort;
import com.colorlight.terminal.infrastructure.config.properties.WebSocketConfigProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 协议处理器注册表 - Infrastructure层实现ProtocolProcessorPort
 *
 * <ul>
 *   <li>每个ProtocolVersion对应唯一处理器，无降级逻辑</li>
 *   <li>基于枚举中的processorClassName进行装配</li>
 *   <li>不支持的协议版本直接抛出异常</li>
 *   <li>支持动态启用/禁用协议版本</li>
 * </ul>
 * 
 * @author Nan
 */
@Slf4j
@Component
public class ProtocolProcessorRegistry implements ProtocolProcessorPort {
    
    /**
     * 协议版本到处理器的映射
     */
    private final Map<ProtocolVersion, ProtocolMessageProcessor> processorMap = new EnumMap<>(ProtocolVersion.class);
    
    /**
     * 允许的处理器包名白名单 - 反射安全检查
     */
    private static final Set<String> ALLOWED_PROCESSOR_PACKAGES = Set.of(
            "com.colorlight.terminal.infrastructure.websocket.processor.v10",
            "com.colorlight.terminal.infrastructure.websocket.processor.v11"
    );
    
    private final ApplicationContext applicationContext;
    private final WebSocketConfigProperties webSocketConfigProperties;
    
    public ProtocolProcessorRegistry(ApplicationContext applicationContext, WebSocketConfigProperties webSocketConfigProperties) {
        this.applicationContext = applicationContext;
        this.webSocketConfigProperties = webSocketConfigProperties;
    }
    
    /**
     * 初始化协议处理器映射 - 基于枚举定义自动装配
     */
    @PostConstruct
    public void initializeProcessors() {
        log.info("ProtocolProcessorRegistry - 开始初始化协议处理器");
        
        for (ProtocolVersion version : ProtocolVersion.values()) {
            // 检查配置是否支持该版本（优先使用配置，后备使用枚举默认值）
            boolean supported = webSocketConfigProperties.getProtocol().isVersionSupported(version.getVersion(), version.isSupported());

            
            if (!supported) {
                log.info("ProtocolProcessorRegistry - 跳过禁用协议版本: {} ",
                        version.name());
                continue;
            }
            
            try {
                // 根据枚举中的类名获取处理器实例
                String className = version.getProcessorClassName();
                
                // 反射安全检查：验证类名在允许的包中
                if (!isProcessorClassAllowed(className)) {
                    throw new SecurityException("不允许的处理器类，安全检查失败: " + className);
                }
                
                Class<?> processorClass = Class.forName(className);
                
                // 从Spring容器中获取处理器bean
                ProtocolMessageProcessor processor = (ProtocolMessageProcessor) applicationContext.getBean(processorClass);
                
                // 验证处理器版本匹配
                if (!version.equals(processor.getSupportedVersion())) {
                    throw new IllegalStateException(String.format(
                            "协议处理器版本不匹配: enum=%s, processor=%s", 
                            version, processor.getSupportedVersion()));
                }
                
                processorMap.put(version, processor);
                log.info("ProtocolProcessorRegistry - 成功注册协议处理器: version={}, processor={}", 
                        version.getVersion(), processor.getClass().getSimpleName());
                        
            } catch (Exception e) {
                log.error("ProtocolProcessorRegistry - 协议处理器注册失败: version={}, className={}", 
                         version.getVersion(), version.getProcessorClassName(), e);
                throw new IllegalStateException("协议处理器初始化失败: " + version.getVersion(), e);
            }
        }
        
        log.info("ProtocolProcessorRegistry - 协议处理器初始化完成，共注册{}个处理器", processorMap.size());
        
        // 验证至少有一个处理器可用
        if (processorMap.isEmpty()) {
            throw new IllegalStateException("没有可用的协议处理器，系统无法启动");
        }
    }
    
    /**
     * 根据协议版本获取对应的处理器
     * 
     * <p>业务约束：</p>
     * <ul>
     *   <li>ProtocolVersion已在连接建立时确定且保证非空</li>
     *   <li>不支持的协议版本不应该到达此方法</li>
     *   <li>无降级逻辑，失败快速原则</li>
     * </ul>
     * 
     * @param version 协议版本（保证非空）
     * @return 对应的协议处理器
     * @throws IllegalArgumentException 如果协议版本不支持
     */
    public ProtocolMessageProcessor getProcessor(ProtocolVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("协议版本不能为空");
        }
        
        ProtocolMessageProcessor processor = processorMap.get(version);
        if (processor == null) {
            throw new IllegalArgumentException("不支持的协议版本: " + version.getVersion());
        }
        
        return processor;
    }
    
    /**
     * 检查指定协议版本是否有可用的处理器
     * 
     * @param version 协议版本
     * @return 是否有可用处理器
     */
    public boolean hasProcessor(ProtocolVersion version) {
        return version != null && processorMap.containsKey(version);
    }
    
    /**
     * 获取所有已注册的协议处理器信息（用于监控和调试）
     * 
     * @return 协议版本到处理器类名的映射
     */
    public Map<ProtocolVersion, String> getProcessorInfo() {
        Map<ProtocolVersion, String> info = new EnumMap<>(ProtocolVersion.class);
        processorMap.forEach((version, processor) -> 
                info.put(version, processor.getClass().getSimpleName()));
        return info;
    }
    
    /**
     * 检查处理器类名是否在允许的安全包名列表中
     * 
     * <p>安全策略：</p>
     * <ul>
     *   <li>只允许预定义包中的处理器类</li>
     *   <li>防止恶意类名注入导致的安全漏洞</li>
     *   <li>实现白名单机制而非黑名单</li>
     * </ul>
     * 
     * @param className 完整的类名
     * @return 如果类名属于允许的包则返回true，否则返回false
     */
    private boolean isProcessorClassAllowed(String className) {
        if (className == null || className.trim().isEmpty()) {
            log.warn("ProtocolProcessorRegistry - 处理器类名为空，安全检查失败");
            return false;
        }
        
        // 验证类名格式：必须包含包路径
        if (!className.contains(".")) {
            log.warn("ProtocolProcessorRegistry - 处理器类名格式无效: {}", className);
            return false;
        }
        
        // 获取包名（去除类名部分）
        String packageName = className.substring(0, className.lastIndexOf('.'));
        
        // 检查是否在白名单中
        boolean allowed = ALLOWED_PROCESSOR_PACKAGES.contains(packageName);
        
        if (!allowed) {
            log.warn("ProtocolProcessorRegistry - 处理器类不在允许的包中: className={}, package={}", 
                    className, packageName);
        }
        
        return allowed;
    }
}