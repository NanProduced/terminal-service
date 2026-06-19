package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessageTypeEnum;
import com.colorlight.terminal.commons.exception.CommonErrorCode;
import com.colorlight.terminal.commons.exception.business.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * V11消息处理器工厂
 * 管理所有消息处理器，根据消息类型获取对应的处理器
 *
 * @author Codex
 */
@Slf4j
@Component
public class V11MessageHandlerFactory {

    private final Map<V11WebsocketMessageTypeEnum, V11MessageHandler> handlers = new EnumMap<>(V11WebsocketMessageTypeEnum.class);

    /**
     * 构造函数，注入所有实现了 V11MessageHandler 接口的处理器
     *
     * @param handlerList 处理器列表
     */
    public V11MessageHandlerFactory(List<V11MessageHandler> handlerList) {
        for (V11MessageHandler handler : handlerList) {
            V11WebsocketMessageTypeEnum supportedType = handler.getSupportedType();
            if (handlers.containsKey(supportedType)) {
                log.warn("V11MessageHandlerFactory - 发现重复的消息类型处理器: type={}, existing={}, new={}",
                        supportedType, handlers.get(supportedType).getClass().getName(), handler.getClass().getName());
            }
            handlers.put(supportedType, handler);
            log.debug("V11MessageHandlerFactory - 注册消息处理器: type={}, handler={}",
                    supportedType, handler.getClass().getSimpleName());
        }
        log.info("V11MessageHandlerFactory - 共注册 {} 个消息处理器", handlers.size());
    }

    /**
     * 根据消息类型获取对应的处理器
     *
     * @param messageType 消息类型枚举
     * @return 对应的消息处理器
     * @throws BusinessException 如果没有找到对应的处理器
     */
    public V11MessageHandler getHandler(V11WebsocketMessageTypeEnum messageType) {
        V11MessageHandler handler = handlers.get(messageType);
        if (handler == null) {
            log.error("V11MessageHandlerFactory - 未找到消息类型对应的处理器: type={}", messageType);
            throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_TYPE);
        }
        return handler;
    }

    /**
     * 获取指令获取处理器（用于连接建立时主动推送指令）
     *
     * @return CommandGetHandler 实例
     */
    public CommandGetHandler getCommandGetHandler() {
        V11MessageHandler handler = handlers.get(V11WebsocketMessageTypeEnum.COMMAND);
        if (handler instanceof CommandGetHandler) {
            return (CommandGetHandler) handler;
        }
        log.error("V11MessageHandlerFactory - COMMAND类型处理器不是CommandGetHandler类型");
        throw new BusinessException(CommonErrorCode.WS_INVALID_MESSAGE_TYPE);
    }

    /**
     * 检查是否有对应消息类型的处理器
     *
     * @param messageType 消息类型枚举
     * @return true 如果有对应的处理器
     */
    public boolean hasHandler(V11WebsocketMessageTypeEnum messageType) {
        return handlers.containsKey(messageType);
    }

    /**
     * 获取所有已注册的消息类型
     *
     * @return 已注册的消息类型集合
     */
    public java.util.Set<V11WebsocketMessageTypeEnum> getRegisteredTypes() {
        return handlers.keySet();
    }
}
