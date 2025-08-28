package com.colorlight.terminal.infrastructure.record;

import com.colorlight.terminal.application.domain.report.TerminalStatusReport;
import com.colorlight.terminal.application.port.outbound.repository.TerminalSwitchOnRecordRepository;
import com.colorlight.terminal.application.port.outbound.status.DeviceSwitchRecordPort;
import com.colorlight.terminal.infrastructure.cache.redis.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 设备开机时间记录处理
 * <p>按照原代码中逻辑进行处理,仅取消Mysql分表</p>
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSwitchOnRecordService implements DeviceSwitchRecordPort {

    private final TerminalSwitchOnRecordRepository terminalSwitchOnRecordRepository;
    private final RedisTemplate<String, String> stringRedisTemplate;


    // 与最近一次开机时间相差两分钟内不记录
    // 不清楚原因，和原逻辑保持一致
    private static final long scopeOfDuplicateRecord = 60L * 2;

    /**
     * 通过盒子上报的初始化状态计算盒子的开机时间戳，并保存到数据库，与最近一条开机时间记录相差两分钟内不保存
     * <P>与原逻辑保持一致/仅取消Mysql分表</P>
     *
     * @param deviceId 设备Id
     * @param info 上报的Info
     */
    @Override
    @Async("deviceStatusExecutor")
    public void asyncHandlerSwitchOnRecord(Long deviceId, TerminalStatusReport.InfoWrapper info) {
        long switchOnUtc = Optional.ofNullable(info)
                .map(TerminalStatusReport.InfoWrapper::getInfo)
                .map(e -> (System.currentTimeMillis() / 1000) - (e.getUp() / 1000))
                .orElse(0L);

        if (switchOnUtc == 0 || switchOnUtc - getLatestSwitchOnTime(deviceId) < scopeOfDuplicateRecord) {
            return;
        }
        // 插入开机时间记录
        terminalSwitchOnRecordRepository.saveSwitchOnRecord(deviceId, switchOnUtc);
        // 更新最近一次开机时间戳缓存
        saveLatestSwitchOnTime(deviceId, switchOnUtc);
        log.info("DeviceSwitchOnRecord - 保存设备开机时间戳记录: deviceId={}, switchOnUtc={}", deviceId, switchOnUtc);

    }

    /**
     * 获取缓存中设备最近一次开机时间戳
     * @param deviceId 设备Id
     * @return 开机时间戳
     */
    @Override
    public Long getLatestSwitchOnTime(Long deviceId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId)))
                .map(Long::parseLong)
                .orElse(0L);
    }

    /**
     * 缓存设备最近一次开机时间戳（缓存10分钟）
     * @param deviceId 设备Id
     * @param timestamp 开机时间戳
     */
    @Override
    public void saveLatestSwitchOnTime(Long deviceId, Long timestamp) {
        stringRedisTemplate.opsForValue().set(String.format(RedisKeyConstant.DEVICE_SWITCH_ON_RECORD_KEY, deviceId),
                String.valueOf(timestamp),
                600,
                TimeUnit.SECONDS);
    }
}
