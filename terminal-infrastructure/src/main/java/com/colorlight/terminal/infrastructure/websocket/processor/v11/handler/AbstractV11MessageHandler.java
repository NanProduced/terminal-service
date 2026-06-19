package com.colorlight.terminal.infrastructure.websocket.processor.v11.handler;

import com.colorlight.terminal.application.domain.connection.MessageProcessingContext;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketErrorEnum;
import com.colorlight.terminal.application.dto.websocket.v11.V11WebsocketMessage;
import com.colorlight.terminal.infrastructure.websocket.connection.TerminalWebsocketSession;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * V11消息处理器抽象基类
 * 提供通用的异步处理逻辑和工具方法
 *
 * @author Codex
 */
@Slf4j
public abstract class AbstractV11MessageHandler implements V11MessageHandler {

    protected static final String EMPTY_JSON = "{}";

    protected final Executor websocketBusinessExecutor;

    protected AbstractV11MessageHandler(Executor websocketBusinessExecutor) {
        this.websocketBusinessExecutor = websocketBusinessExecutor;
    }

    /**
     * 异步执行业务操作，避免阻塞EventLoop线程
     *
     * @param context       消息处理上下文
     * @param supplier      业务操作提供者
     * @param successAction 成功时的回调
     * @param errorAction   失败时的回调
     * @param <T>           返回类型
     */
    protected <T> void executeAsync(
            MessageProcessingContext context,
            Supplier<T> supplier,
            AsyncSuccessCallback<T> successAction,
            AsyncErrorCallback errorAction) {

        CompletableFuture
                .supplyAsync(supplier, websocketBusinessExecutor)
                .whenComplete((result, throwable) -> {
                    EventLoop eventLoop = getEventLoop(context);
                    eventLoop.execute(() -> {
                        try {
                            if (throwable == null) {
                                successAction.onSuccess(result);
                            } else {
                                errorAction.onError(throwable);
                            }
                        } catch (Exception e) {
                            log.error("AbstractV11MessageHandler - 异步回调执行异常", e);
                        }
                    });
                });
    }

    /**
     * 异步执行无返回值的业务操作
     *
     * @param context       消息处理上下文
     * @param runnable      业务操作
     * @param successAction 成功时的回调
     * @param errorAction   失败时的回调
     */
    protected void executeAsyncVoid(
            MessageProcessingContext context,
            Runnable runnable,
            Runnable successAction,
            AsyncErrorCallback errorAction) {

        CompletableFuture
                .runAsync(runnable, websocketBusinessExecutor)
                .whenComplete((result, throwable) -> {
                    EventLoop eventLoop = getEventLoop(context);
                    eventLoop.execute(() -> {
                        try {
                            if (throwable == null) {
                                successAction.run();
                            } else {
                                errorAction.onError(throwable);
                            }
                        } catch (Exception e) {
                            log.error("AbstractV11MessageHandler - 异步回调执行异常", e);
                        }
                    });
                });
    }

    /**
     * 获取Netty的EventLoop，用于确保回调在正确的线程中执行
     *
     * @param context 消息处理上下文
     * @return EventLoop
     */
    protected EventLoop getEventLoop(MessageProcessingContext context) {
        TerminalWebsocketSession session = (TerminalWebsocketSession) context.getConnection().getSession();
        Channel channel = session.getNettyChannel();
        return channel.eventLoop();
    }

    /**
     * 发送成功响应
     *
     * @param context   消息处理上下文
     * @param messageId 消息ID
     * @param data      响应数据
     */
    protected void sendSuccessResponse(MessageProcessingContext context, Integer messageId, Object data) {
        context.sendMessage(new V11WebsocketMessage(
                getSupportedType().getId(), messageId, data));
    }

    /**
     * 发送成功响应（无数据）
     *
     * @param context   消息处理上下文
     * @param messageId 消息ID
     */
    protected void sendSuccessResponse(MessageProcessingContext context, Integer messageId) {
        context.sendMessage(new V11WebsocketMessage(
                getSupportedType().getId(), messageId));
    }

    /**
     * 发送错误响应
     *
     * @param context   消息处理上下文
     * @param messageId 消息ID
     * @param errorEnum 错误类型
     * @param message   错误消息
     */
    protected void sendErrorResponse(MessageProcessingContext context, Integer messageId,
                                      V11WebsocketErrorEnum errorEnum, String message) {
        context.sendMessage(V11WebsocketMessage.generateErrorContent(
                errorEnum, messageId, message));
    }

    /**
     * 检查数据是否为空
     *
     * @param data 数据对象
     * @return true 如果为空
     */
    protected boolean isEmptyData(Object data) {
        return Objects.isNull(data);
    }

    /**
     * 异步成功回调接口
     *
     * @param <T> 结果类型
     */
    @FunctionalInterface
    protected interface AsyncSuccessCallback<T> {
        void onSuccess(T result);
    }

    /**
     * 异步错误回调接口
     */
    @FunctionalInterface
    protected interface AsyncErrorCallback {
        void onError(Throwable throwable);
    }
}
