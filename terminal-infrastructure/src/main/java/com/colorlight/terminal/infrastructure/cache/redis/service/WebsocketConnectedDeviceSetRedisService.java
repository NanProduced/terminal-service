package com.colorlight.terminal.infrastructure.cache.redis.service;

import com.colorlight.terminal.application.port.outbound.cache.WebsocketConnectedDeviceSetPort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * WebSocket 已连接设备集合 Redis 实现
 *
 * <p>注意：Redis 操作为阻塞 I/O，必须避免在 Netty EventLoop 线程直接执行。</p>
 * <p>此实现内部使用 websocketConnectionExecutor 异步执行 SADD/SREM，避免影响 WebSocket 性能。</p>
 *
 * @author Nan
 */
@Slf4j
@Service
public class WebsocketConnectedDeviceSetRedisService implements WebsocketConnectedDeviceSetPort {

    /**
     * 复用 Spring Boot 提供的 StringRedisTemplate（其父类类型为 RedisTemplate<String, String>）
     * 以保证 Set 成员存储为直观的字符串（避免 Object 序列化导致可读性/兼容性问题）。
     */
    private final RedisTemplate<String, String> stringRedisTemplate;

    /**
     * WebSocket 连接处理线程池（连接建立/清理等包含 Redis 写操作的场景）
     */
    private final Executor websocketConnectionExecutor;

    public WebsocketConnectedDeviceSetRedisService(
            RedisTemplate<String, String> stringRedisTemplate,
            @Qualifier("websocketConnectionExecutor") Executor websocketConnectionExecutor) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.websocketConnectionExecutor = websocketConnectionExecutor;
    }

    @Override
    public void add(Long deviceId) {
        if (deviceId == null) {
            return;
        }
        submitRedisTask("SADD", deviceId, () ->
                stringRedisTemplate.opsForSet().add(RedisKeyConstant.DEVICE_WEBSOCKET_SET_KEY, deviceId.toString())
        );
    }

    @Override
    public void remove(Long deviceId) {
        if (deviceId == null) {
            return;
        }
        submitRedisTask("SREM", deviceId, () ->
                stringRedisTemplate.opsForSet().remove(RedisKeyConstant.DEVICE_WEBSOCKET_SET_KEY, deviceId.toString())
        );
    }

    private void submitRedisTask(String op, Long deviceId, Runnable action) {
        try {
            websocketConnectionExecutor.execute(() -> {
                try {
                    action.run();
                } catch (DataAccessException e) {
                    log.warn("WebsocketConnectedDeviceSetRedisService - Redis {} 失败: deviceId={}", op, deviceId, e);
                } catch (Exception e) {
                    log.warn("WebsocketConnectedDeviceSetRedisService - 执行 {} 异常: deviceId={}", op, deviceId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("WebsocketConnectedDeviceSetRedisService - 线程池拒绝任务，跳过 Redis {}: deviceId={}", op, deviceId, e);
        } catch (Exception e) {
            log.error("WebsocketConnectedDeviceSetRedisService - 提交 Redis {} 任务失败: deviceId={}", op, deviceId, e);
        }
    }
}
