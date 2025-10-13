package com.colorlight.terminal.application.port.outbound.websocket;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;

/**
 * 协议消息处理器接口 - 协议特定的消息解析和业务逻辑处理
 *
 * <ul>
 *   <li>每个协议版本有独立的处理器实现</li>
 *   <li>仅处理协议相关的消息解析和业务逻辑</li>
 *   <li>统计更新由MessageProcessingContext协调</li>
 *   <li>错误处理由上层UseCase协调</li>
 * </ul>
 * 
 * @author Nan
 */
public interface ProtocolMessageProcessor {
    
    /**
     * 获取支持的协议版本
     * 
     * @return 协议版本
     */
    ProtocolVersion getSupportedVersion();
    
    /**
     * 处理文本消息 - 协议特定的消息解析和业务逻辑
     *
     * <p>处理范围：</p>
     * <ul>
     *   <li>协议特定的消息格式解析</li>
     *   <li>消息验证和转换</li>
     *   <li>业务逻辑处理</li>
     * </ul>
     *
     * <p>不包含：</p>
     * <ul>
     *   <li>统计更新（由MessageProcessingContext处理）</li>
     *   <li>连接状态更新（由上层UseCase处理）</li>
     * </ul>
     *
     * @param context 消息处理上下文
     * @return 处理结果
     */
    TextMessageProcessResult processTextMessage(MessageProcessingContext context);

    /**
     * 连接建立后的回调处理 - 协议特定的连接初始化逻辑
     *
     * <p>用途：</p>
     * <ul>
     *   <li>V1.1协议：主动推送待执行指令列表</li>
     *   <li>V1.0协议：无需特殊处理（默认空实现）</li>
     *   <li>未来协议：可实现特定的连接初始化逻辑</li>
     * </ul>
     *
     * <p>设计说明：</p>
     * <ul>
     *   <li>默认空实现，子类按需覆盖</li>
     *   <li>在WebSocket握手完成后调用</li>
     *   <li>主动推送消息时，messageId/receiptId应设为null，表示服务器主动推送</li>
     * </ul>
     *
     * @param context 消息处理上下文，包含设备连接信息
     */
    default void onConnectionEstablished(MessageProcessingContext context) {
        // 默认空实现，V1.0等协议无需特殊处理
    }

    /**
     * 文本消息处理结果
     */
    record TextMessageProcessResult(
            boolean success,
            boolean heartbeat,
            String errorMessage
    ) {
        public static TextMessageProcessResult ofSuccess(boolean heartbeat) {
            return new TextMessageProcessResult(true, heartbeat, null);
        }

        public static TextMessageProcessResult ofFailure(String errorMessage) {
            return new TextMessageProcessResult(false, false, errorMessage);
        }
    }
}