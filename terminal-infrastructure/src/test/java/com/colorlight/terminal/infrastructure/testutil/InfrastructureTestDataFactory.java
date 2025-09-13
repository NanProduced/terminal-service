package com.colorlight.terminal.infrastructure.testutil;

import com.colorlight.terminal.application.domain.TerminalAccount;
import com.colorlight.terminal.application.domain.connection.ProtocolVersion;
import com.colorlight.terminal.application.domain.connection.TerminalConnection;
import com.colorlight.terminal.application.domain.status.DeviceOnlineStatus;
import com.colorlight.terminal.application.domain.status.OnlineStatus;
import com.colorlight.terminal.application.domain.status.ReportSource;
import com.colorlight.terminal.application.enums.TerminalAccountStatus;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Infrastructure层测试数据工厂
 * 提供统一的测试数据构建方法，确保测试数据的一致性和可维护性
 * 
 * @author Nan
 */
public class InfrastructureTestDataFactory {
    
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000L);
    private static final Random RANDOM = new Random();
    
    // ===================== 设备在线状态相关 =====================
    
    /**
     * 创建标准的设备在线状态对象
     */
    public static DeviceOnlineStatus createDeviceOnlineStatus() {
        return createDeviceOnlineStatus(generateDeviceId(), OnlineStatus.ONLINE);
    }
    
    /**
     * 创建指定状态的设备在线状态对象
     */
    public static DeviceOnlineStatus createDeviceOnlineStatus(Long deviceId, OnlineStatus status) {
        long currentTime = System.currentTimeMillis();
        DeviceOnlineStatus deviceStatus = new DeviceOnlineStatus();
        deviceStatus.setDeviceId(deviceId);
        deviceStatus.setStatus(status);
        deviceStatus.setLastReportTime(currentTime);
        deviceStatus.setLastReportSource(ReportSource.WEBSOCKET);
        deviceStatus.setStatusChangeTime(currentTime - 1000);
        deviceStatus.setOnlineStartTime(currentTime - 60000); // 1分钟前上线
        deviceStatus.setClientIp("192.168.1." + (100 + RANDOM.nextInt(50)));
        return deviceStatus;
    }
    
    /**
     * 创建批量设备在线状态对象
     */
    public static List<DeviceOnlineStatus> createBatchDeviceOnlineStatus(int count) {
        List<DeviceOnlineStatus> statusList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            statusList.add(createDeviceOnlineStatus());
        }
        return statusList;
    }
    
    /**
     * 创建过期的设备状态（用于离线检测测试）
     */
    public static DeviceOnlineStatus createExpiredDeviceStatus(Long deviceId) {
        long expiredTime = System.currentTimeMillis() - 120000; // 2分钟前
        DeviceOnlineStatus deviceStatus = createDeviceOnlineStatus(deviceId, OnlineStatus.ONLINE);
        deviceStatus.setLastReportTime(expiredTime);
        deviceStatus.setStatusChangeTime(expiredTime);
        return deviceStatus;
    }
    
    // ===================== 终端连接相关 =====================
    
    /**
     * 创建模拟的终端连接对象
     */
    public static TerminalConnection createTerminalConnection() {
        return createTerminalConnection(generateDeviceId(), ProtocolVersion.V1_1);
    }
    
    /**
     * 创建指定版本的终端连接对象
     */
    public static TerminalConnection createTerminalConnection(Long deviceId, ProtocolVersion version) {
        // 临时实现 - 避免编译错误
        TerminalConnection connection = new TerminalConnection();
        connection.setDeviceId(deviceId);
        connection.setProtocolVersion(version);
        connection.setClientIp("192.168.1." + (100 + RANDOM.nextInt(50)));
        connection.setConnectTime(LocalDateTime.now());
        connection.setLastActiveTime(LocalDateTime.now());
        return connection;
    }
    
    /**
     * 创建批量终端连接对象
     */
    public static List<TerminalConnection> createBatchTerminalConnections(int count) {
        List<TerminalConnection> connections = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            connections.add(createTerminalConnection());
        }
        return connections;
    }
    
    // ===================== 终端账号相关 =====================
    
    /**
     * 创建终端账号领域对象
     */
    public static TerminalAccount createTerminalAccount() {
        Long deviceId = generateDeviceId();
        return TerminalAccount.builder()
                .deviceId(deviceId)
                .accountName("device_" + deviceId)
                .passwordHash("$2a$10$encrypted_password_hash") // BCrypt加密后的密码
                .status(TerminalAccountStatus.ENABLE)
                .firstLoginTime(LocalDateTime.now().minusDays(7))
                .lastLoginTime(LocalDateTime.now())
                .lastLoginIp("192.168.1." + (100 + RANDOM.nextInt(50)))
                .build();
    }
    
    /**
     * 创建终端账号DO对象
     */
    public static TerminalAccountDO createTerminalAccountDO() {
        Long deviceId = generateDeviceId();
        TerminalAccountDO accountDO = new TerminalAccountDO();
        accountDO.setDeviceId(deviceId);
        accountDO.setAccount("device_" + deviceId);
        accountDO.setPassword("$2a$10$encrypted_password_hash");
        accountDO.setAccountStatus(TerminalAccountStatus.ENABLE.getStatus().byteValue());
        accountDO.setFirstLoginTime(LocalDateTime.now().minusDays(7));
        accountDO.setLastLoginTime(LocalDateTime.now());
        accountDO.setLastLoginIp("192.168.1." + (100 + RANDOM.nextInt(50)));
        accountDO.setCreateTime(LocalDateTime.now().minusDays(30));
        accountDO.setUpdateTime(LocalDateTime.now());
        return accountDO;
    }
    
    // ===================== Redis数据相关 =====================
    
    /**
     * 创建Redis Hash数据Map
     */
    public static Map<String, Object> createRedisHashData(DeviceOnlineStatus status) {
        Map<String, Object> hashData = new HashMap<>();
        hashData.put("deviceId", status.getDeviceId());
        hashData.put("status", status.getStatus().name());
        hashData.put("lastReportTime", status.getLastReportTime());
        hashData.put("lastReportSource", status.getLastReportSource().name());
        hashData.put("statusChangeTime", status.getStatusChangeTime());
        hashData.put("onlineStartTime", status.getOnlineStartTime());
        hashData.put("clientIp", status.getClientIp());
        return hashData;
    }
    
    /**
     * 创建Redis Hash数据Map（Object类型的Key和Value）
     */
    public static Map<Object, Object> createRedisHashObjectData(DeviceOnlineStatus status) {
        Map<Object, Object> hashData = new HashMap<>();
        hashData.put("deviceId", status.getDeviceId());
        hashData.put("status", status.getStatus().name());
        hashData.put("lastReportTime", status.getLastReportTime());
        hashData.put("lastReportSource", status.getLastReportSource().name());
        hashData.put("statusChangeTime", status.getStatusChangeTime());
        hashData.put("onlineStartTime", status.getOnlineStartTime());
        hashData.put("clientIp", status.getClientIp());
        return hashData;
    }
    
    // ===================== 工具方法 =====================
    
    /**
     * 生成唯一的设备ID
     */
    public static Long generateDeviceId() {
        return ID_GENERATOR.incrementAndGet();
    }
    
    /**
     * 生成随机IP地址
     */
    public static String generateRandomIp() {
        return "192.168." + RANDOM.nextInt(256) + "." + (1 + RANDOM.nextInt(254));
    }
    
    /**
     * 生成随机时间戳（过去24小时内）
     */
    public static long generateRandomTimestamp() {
        long now = System.currentTimeMillis();
        long oneDayInMillis = 24 * 60 * 60 * 1000L;
        return now - RANDOM.nextInt((int) oneDayInMillis);
    }
    
    /**
     * 创建设备ID列表
     */
    public static List<Long> createDeviceIdList(int count) {
        List<Long> deviceIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            deviceIds.add(generateDeviceId());
        }
        return deviceIds;
    }
    
    /**
     * 创建设备ID集合
     */
    public static Set<Long> createDeviceIdSet(int count) {
        Set<Long> deviceIds = new HashSet<>();
        for (int i = 0; i < count; i++) {
            deviceIds.add(generateDeviceId());
        }
        return deviceIds;
    }
}