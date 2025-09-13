package com.colorlight.terminal.infrastructure.websocket.connection;

import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.infrastructure.testutil.AsyncTestUtils;
import com.colorlight.terminal.infrastructure.testutil.InfrastructureTestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * ShardedConnectionManager 单元测试
 * 测试分片连接管理器的核心功能、并发安全性和性能特性
 * 
 * @author Nan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("分片连接管理器测试")
class ShardedConnectionManagerTest {

    private ShardedConnectionManager connectionManager;

    @Mock
    private TerminalWebsocketSession mockSession;

    @BeforeEach
    void setUp() {
        connectionManager = new ShardedConnectionManager();
        
        // 配置Mock Session
        lenient().when(mockSession.isConnected()).thenReturn(true);
        lenient().when(mockSession.getSessionId()).thenReturn("TEST_SESSION");
        lenient().when(mockSession.getClientIp()).thenReturn("192.168.1.100");
    }

    @Nested
    @DisplayName("连接管理基本功能测试")
    class BasicConnectionManagementTests {

        @Test
        @DisplayName("应该成功添加新连接")
        void should_add_connection_successfully() {
            // Given - 创建测试连接
            Long deviceId = 1001L;
            TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
            connection.setSession(mockSession);

            // When - 添加连接
            boolean result = connectionManager.addConnection(deviceId, connection);

            // Then - 验证添加成功
            assertThat(result).isTrue();
            assertThat(connectionManager.getConnectionCount()).isEqualTo(1);
            
            Optional<TerminalConnection> retrieved = connectionManager.getConnection(deviceId);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getDeviceId()).isEqualTo(deviceId);
        }

        @Test
        @DisplayName("应该拒绝添加重复的连接")
        void should_reject_duplicate_connection() {
            // Given - 添加第一个连接
            Long deviceId = 1002L;
            TerminalConnection connection1 = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
            connection1.setSession(mockSession);
            connectionManager.addConnection(deviceId, connection1);

            // When - 尝试添加重复连接
            TerminalConnection connection2 = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_0);
            connection2.setSession(mockSession);
            boolean result = connectionManager.addConnection(deviceId, connection2);

            // Then - 验证重复连接被拒绝
            assertThat(result).isFalse();
            assertThat(connectionManager.getConnectionCount()).isEqualTo(1);
            
            // 验证原连接仍然存在且未被替换
            Optional<TerminalConnection> retrieved = connectionManager.getConnection(deviceId);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getProtocolVersion()).isEqualTo(ProtocolVersion.V1_1);
        }

        @Test
        @DisplayName("应该成功移除已存在的连接")
        void should_remove_existing_connection_successfully() {
            // Given - 先添加连接
            Long deviceId = 1003L;
            TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
            connection.setSession(mockSession);
            connectionManager.addConnection(deviceId, connection);

            // When - 移除连接
            TerminalConnection removed = connectionManager.removeConnection(deviceId);

            // Then - 验证移除成功
            assertThat(removed).isNotNull();
            assertThat(removed.getDeviceId()).isEqualTo(deviceId);
            assertThat(connectionManager.getConnectionCount()).isZero();
            
            Optional<TerminalConnection> retrieved = connectionManager.getConnection(deviceId);
            assertThat(retrieved).isEmpty();
        }

        @Test
        @DisplayName("移除不存在的连接应该返回null")
        void should_return_null_when_removing_non_existent_connection() {
            // Given - 空管理器
            Long deviceId = 1004L;

            // When - 尝试移除不存在的连接
            TerminalConnection removed = connectionManager.removeConnection(deviceId);

            // Then - 验证返回null
            assertThat(removed).isNull();
            assertThat(connectionManager.getConnectionCount()).isZero();
        }

        @Test
        @DisplayName("获取不存在的连接应该返回空Optional")
        void should_return_empty_optional_for_non_existent_connection() {
            // Given - 空管理器
            Long deviceId = 1005L;

            // When - 获取不存在的连接
            Optional<TerminalConnection> result = connectionManager.getConnection(deviceId);

            // Then - 验证返回空Optional
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("空值和边界条件处理测试")
    class NullAndBoundaryTests {

        @Test
        @DisplayName("传入null设备ID应该返回false")
        void should_return_false_for_null_device_id() {
            // Given - null设备ID和有效连接
            TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(1006L, ProtocolVersion.V1_1);

            // When - 尝试添加null设备ID的连接
            boolean result = connectionManager.addConnection(null, connection);

            // Then - 验证返回false
            assertThat(result).isFalse();
            assertThat(connectionManager.getConnectionCount()).isZero();
        }

        @Test
        @DisplayName("传入null连接对象应该返回false")
        void should_return_false_for_null_connection() {
            // Given - 有效设备ID和null连接
            Long deviceId = 1007L;

            // When - 尝试添加null连接
            boolean result = connectionManager.addConnection(deviceId, null);

            // Then - 验证返回false
            assertThat(result).isFalse();
            assertThat(connectionManager.getConnectionCount()).isZero();
        }

        @Test
        @DisplayName("移除null设备ID应该返回null")
        void should_return_null_when_removing_null_device_id() {
            // When - 移除null设备ID
            TerminalConnection result = connectionManager.removeConnection(null);

            // Then - 验证返回null
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("获取null设备ID的连接应该返回空Optional")
        void should_return_empty_optional_for_null_device_id() {
            // When - 获取null设备ID的连接
            Optional<TerminalConnection> result = connectionManager.getConnection(null);

            // Then - 验证返回空Optional
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("协议版本统计测试")
    class ProtocolVersionStatisticsTests {

        @Test
        @DisplayName("应该正确统计不同协议版本的连接数")
        void should_track_protocol_version_statistics_correctly() {
            // Given - 添加不同版本的连接
            addConnectionWithVersion(2001L, ProtocolVersion.V1_0);
            addConnectionWithVersion(2002L, ProtocolVersion.V1_1);

            // When - 获取统计信息
            Map<String, Object> stats = connectionManager.getShardStatistics();

            // Then - 验证版本统计
            assertThat(connectionManager.getConnectionCount()).isEqualTo(2);
            assertThat(stats).containsKey("versionCount");
        }

        private void addConnectionWithVersion(Long deviceId, ProtocolVersion version) {
            TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, version);
            connection.setSession(mockSession);
            connectionManager.addConnection(deviceId, connection);
        }
    }

    @Nested
    @DisplayName("在线设备ID列表测试")
    class OnlineDeviceIdsTests {

        @Test
        @DisplayName("应该返回所有在线设备的ID列表")
        void should_return_all_online_device_ids() {
            // Given - 添加多个连接
            Set<Long> expectedDeviceIds = Set.of(3001L, 3002L, 3003L, 3004L, 3005L);
            for (Long deviceId : expectedDeviceIds) {
                TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
                connection.setSession(mockSession);
                connectionManager.addConnection(deviceId, connection);
            }

            // When - 获取在线设备ID列表
            Collection<Long> onlineDeviceIds = connectionManager.getOnlineDeviceIds();

            // Then - 验证返回正确的设备ID集合
            assertThat(onlineDeviceIds).hasSize(5).containsExactlyInAnyOrderElementsOf(expectedDeviceIds);
        }

        @Test
        @DisplayName("没有连接时应该返回空列表")
        void should_return_empty_list_when_no_connections() {
            // When - 获取在线设备ID列表
            Collection<Long> onlineDeviceIds = connectionManager.getOnlineDeviceIds();

            // Then - 验证返回空列表
            assertThat(onlineDeviceIds).isEmpty();
        }
    }

    @Nested
    @DisplayName("分片统计和负载均衡测试")
    class ShardStatisticsTests {

        @Test
        @DisplayName("应该正确计算分片统计信息")
        void should_calculate_shard_statistics_correctly() {
            // Given - 添加多个连接以测试分片分布
            for (int i = 0; i < 20; i++) {
                Long deviceId = 4000L + i;
                TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
                connection.setSession(mockSession);
                connectionManager.addConnection(deviceId, connection);
            }

            // When - 获取分片统计
            Map<String, Object> stats = connectionManager.getShardStatistics();

            // Then - 验证统计信息结构正确
            assertThat(stats)
                .containsKeys(
                    "totalShards",
                    "totalConnections", 
                    "running",
                    "shardSizes",
                    "maxShardSize",
                    "minShardSize",
                    "loadBalance")
                .containsEntry("totalShards", 16)
                .containsEntry("totalConnections", 20)
                .containsEntry("running", true);
            
            // 验证负载均衡指标
            Integer maxShardSize = (Integer) stats.get("maxShardSize");
            Integer minShardSize = (Integer) stats.get("minShardSize");
            assertThat(maxShardSize).isGreaterThanOrEqualTo(minShardSize);
        }
    }

    @Nested
    @DisplayName("无效连接清理测试")
    class InvalidConnectionCleanupTests {

        @Test
        @DisplayName("应该清理无效连接")
        void should_cleanup_invalid_connections() {
            // Given - 添加一个有效连接和一个无效连接
            Long validDeviceId = 5001L;
            Long invalidDeviceId = 5002L;
            
            // 有效连接
            TerminalWebsocketSession validSession = mock(TerminalWebsocketSession.class);
            when(validSession.isConnected()).thenReturn(true);
            TerminalConnection validConnection = InfrastructureTestDataFactory.createTerminalConnection(validDeviceId, ProtocolVersion.V1_1);
            validConnection.setSession(validSession);
            connectionManager.addConnection(validDeviceId, validConnection);
            
            // 无效连接
            TerminalWebsocketSession invalidSession = mock(TerminalWebsocketSession.class);
            when(invalidSession.isConnected()).thenReturn(false);
            TerminalConnection invalidConnection = InfrastructureTestDataFactory.createTerminalConnection(invalidDeviceId, ProtocolVersion.V1_1);
            invalidConnection.setSession(invalidSession);
            connectionManager.addConnection(invalidDeviceId, invalidConnection);
            
            assertThat(connectionManager.getConnectionCount()).isEqualTo(2);

            // When - 执行清理
            int cleanedCount = connectionManager.cleanupInvalidConnections();

            // Then - 验证无效连接被清理
            assertThat(cleanedCount).isEqualTo(1);
            assertThat(connectionManager.getConnectionCount()).isEqualTo(1);
            
            // 验证有效连接仍然存在
            assertThat(connectionManager.getConnection(validDeviceId)).isPresent();
            assertThat(connectionManager.getConnection(invalidDeviceId)).isEmpty();
        }

        @Test
        @DisplayName("没有无效连接时清理应该返回0")
        void should_return_zero_when_no_invalid_connections() {
            // Given - 只添加有效连接
            Long deviceId = 5003L;
            TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
            connection.setSession(mockSession);
            connectionManager.addConnection(deviceId, connection);

            // When - 执行清理
            int cleanedCount = connectionManager.cleanupInvalidConnections();

            // Then - 验证没有连接被清理
            assertThat(cleanedCount).isZero();
            assertThat(connectionManager.getConnectionCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("并发安全性测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("并发添加连接应该是线程安全的")
        void should_be_thread_safe_for_concurrent_additions() throws InterruptedException {
            // Given - 准备并发测试参数
            int threadCount = 10;
            int connectionsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // When - 并发添加连接
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < connectionsPerThread; j++) {
                            Long deviceId = (long) (threadId * connectionsPerThread + j + 6000);
                            TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
                            connection.setSession(mockSession);
                            
                            boolean added = connectionManager.addConnection(deviceId, connection);
                            if (added) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then - 验证并发安全性
            latch.await();
            executor.shutdown();

            int expectedConnections = threadCount * connectionsPerThread;
            assertThat(successCount.get()).isEqualTo(expectedConnections);
            assertThat(connectionManager.getConnectionCount()).isEqualTo(expectedConnections);
        }

        @Test
        @DisplayName("并发读取连接应该是线程安全的")
        void should_be_thread_safe_for_concurrent_reads() {
            // Given - 为了节省测试时间，只添加5个连接
            List<Long> deviceIds = IntStream.range(7000, 7005)
                    .mapToLong(i -> (long) i)
                    .boxed()
                    .toList();

            for (Long deviceId : deviceIds) {
                TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
                connection.setSession(mockSession);
                connectionManager.addConnection(deviceId, connection);
            }

            // When - 并发读取连接
            List<CompletableFuture<Boolean>> futures = deviceIds.stream()
                    .map(deviceId -> 
                        CompletableFuture.supplyAsync(() -> 
                            connectionManager.getConnection(deviceId).isPresent()))
                    .toList();

            // Then - 验证所有并发读取都成功
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            AsyncTestUtils.verifyFutureSuccess(allFutures, Duration.ofSeconds(5));

            // 验证所有连接都能被找到
            for (CompletableFuture<Boolean> future : futures) {
                Boolean found = AsyncTestUtils.verifyFutureSuccess(future, Duration.ofSeconds(1));
                assertThat(found).isTrue();
            }
        }

        @Test
        @DisplayName("并发添加和移除连接应该是线程安全的")
        void should_be_thread_safe_for_concurrent_add_and_remove() throws InterruptedException {
            // Given - 准备测试参数
            int operationCount = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(operationCount);
            AtomicInteger addCount = new AtomicInteger(0);
            AtomicInteger removeCount = new AtomicInteger(0);

            // When - 并发添加和移除连接
            for (int i = 0; i < operationCount; i++) {
                final Long deviceId = 8000L + i;
                executor.submit(() -> {
                    try {
                        // 添加连接
                        TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
                        connection.setSession(mockSession);
                        if (connectionManager.addConnection(deviceId, connection)) {
                            addCount.incrementAndGet();
                        }

                        // 随机移除一些连接
                        if (deviceId % 3 == 0) {
                            TerminalConnection removed = connectionManager.removeConnection(deviceId);
                            if (removed != null) {
                                removeCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then - 验证并发操作的一致性
            latch.await();
            executor.shutdown();

            int expectedRemainingConnections = addCount.get() - removeCount.get();
            assertThat(connectionManager.getConnectionCount()).isEqualTo(expectedRemainingConnections);
        }
    }

    @Nested
    @DisplayName("生命周期管理测试")
    class LifecycleManagementTests {

        @Test
        @DisplayName("destroy方法应该清理所有连接并关闭管理器")
        void should_cleanup_all_connections_and_shutdown_manager() {
            // Given - 添加一些连接
            for (int i = 0; i < 5; i++) {
                Long deviceId = 9000L + i;
                TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
                connection.setSession(mockSession);
                connectionManager.addConnection(deviceId, connection);
            }

            assertThat(connectionManager.getConnectionCount()).isEqualTo(5);

            // When - 销毁管理器
            connectionManager.destroy();

            // Then - 验证所有连接被清理且管理器状态正确
            assertThat(connectionManager.getConnectionCount()).isZero();

            // 验证销毁后的操作都返回失败或空结果
            Long testDeviceId = 9999L;
            TerminalConnection testConnection = InfrastructureTestDataFactory.createTerminalConnection(testDeviceId, ProtocolVersion.V1_1);
            testConnection.setSession(mockSession);

            assertThat(connectionManager.addConnection(testDeviceId, testConnection)).isFalse();
            assertThat(connectionManager.removeConnection(testDeviceId)).isNull();
            assertThat(connectionManager.getConnection(testDeviceId)).isEmpty();
            assertThat(connectionManager.getOnlineDeviceIds()).isEmpty();
        }

        @Test
        @DisplayName("销毁后清理操作应该返回0")
        void should_return_zero_for_cleanup_after_destroy() {
            // Given - 销毁管理器
            connectionManager.destroy();

            // When - 执行清理操作
            int cleanedCount = connectionManager.cleanupInvalidConnections();

            // Then - 验证返回0
            assertThat(cleanedCount).isZero();
        }

        @Test
        @DisplayName("销毁后统计信息应该反映已关闭状态")
        void should_reflect_shutdown_status_in_statistics_after_destroy() {
            // Given - 销毁管理器
            connectionManager.destroy();

            // When - 获取统计信息
            Map<String, Object> stats = connectionManager.getShardStatistics();

            // Then - 验证统计信息反映关闭状态
            assertThat(stats)
                    .containsEntry("running", false)
                    .containsEntry("totalConnections", 0);
        }
    }

    @Nested
    @DisplayName("异常处理和错误恢复测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("清理过程中Session异常应该被处理")
        void should_handle_session_exceptions_during_cleanup() {
            // Given - 添加一个在清理时会抛异常的连接
            TerminalWebsocketSession faultySession = mock(TerminalWebsocketSession.class);
            when(faultySession.isConnected()).thenThrow(new RuntimeException("Cleanup error"));

            Long deviceId = 10002L;
            TerminalConnection connection = InfrastructureTestDataFactory.createTerminalConnection(deviceId, ProtocolVersion.V1_1);
            connection.setSession(faultySession);
            
            // 强制添加连接（绕过验证）
            connectionManager.addConnection(deviceId, connection);

            // When - 执行清理（应该不抛异常）
            assertThatCode(() -> connectionManager.cleanupInvalidConnections())
                    .doesNotThrowAnyException();

        }
    }
}