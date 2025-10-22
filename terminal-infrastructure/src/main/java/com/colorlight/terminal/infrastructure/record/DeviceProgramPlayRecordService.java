package com.colorlight.terminal.infrastructure.record;

import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.colorlight.terminal.application.domain.report.ProgramPlayRecordReport;
import com.colorlight.terminal.application.port.outbound.repository.ProgramPlayRecordRepository;
import com.colorlight.terminal.application.port.outbound.statistics.DeviceProgramPlayRecordPort;
import com.colorlight.terminal.infrastructure.persistence.mysql.repository.MysqlProgramRepositoryAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 设备节目播放记录服务实现类，负责处理并保存来自设备的节目播放记录报告。
 * 该服务通过过滤和处理有效的节目播放报告，并填充必要的业务字段后，将数据保存到数据库中。
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProgramPlayRecordService implements DeviceProgramPlayRecordPort {

    private final MysqlProgramRepositoryAdapter programRepositoryAdapter;
    private final ProgramPlayRecordRepository programPlayRecordRepository;

    /**
     * 处理并保存节目播放记录报告。
     * 该方法首先过滤出支持统计的云节目（通过尝试将programIdStr转换为整数来判断是否为云节目），
     * 然后对每个有效的节目播放报告进行处理，包括填充必要的业务字段如设备ID等。最后，将处理后的报告保存到数据库中。
     *
     * @param deviceId 设备标识符
     * @param reports 节目播放记录报告列表
     */
    @Override
    public void handleProgramPlayRecordReport(Long deviceId, List<ProgramPlayRecordReport> reports) {
        List<ProgramPlayRecordReport> reportToSave = Lists.newArrayList();
        reports.stream()
                // 通过格式化programIdStr来判断是否为Lan节目
                .filter(report -> {
                    try {
                        // 云节目的programIdStr字段实际是节目的作者Id(Integer)
                        report.setAuthorId(Integer.parseInt(report.getProgramIdStr()));
                        return true;
                    } catch (NumberFormatException e) {
                        log.info("ProgramStats - LAN节目不支持统计: {}", report.getProgramIdStr());
                        return false;
                    }
                })
                .forEach(report -> {
                    // 上报数据处理（填充业务字段）
                    processProgramReport(deviceId, report);
                    reportToSave.add(report);
                });

        if (reportToSave.isEmpty()) {
            log.info("ProgramStats - 无需保存节目播放记录,deviceId={}", deviceId);
            return;
        }
        programPlayRecordRepository.saveProgramPlayRecords(reportToSave);
        log.info("ProgramStats - 存储{}条节目播放记录,deviceId={}", reportToSave.size(), deviceId);
    }

    /**
     * 处理单个节目播放上报（填充业务逻辑字段）
     * @param deviceId 设备Id
     * @param report 上报数据
     */
    private void processProgramReport(Long deviceId, ProgramPlayRecordReport report) {
        // 填充设备Id
        report.setDeviceId(deviceId);

        // 解析Vsn名
        String programVsn = report.getProgramVsn();
        String programVsnName = null;
        if (programVsn.endsWith(".vsn")) programVsnName = programVsn.substring(0, programVsn.length() - 4);
        else log.warn("ProgramStats - 节目VSN名称解析失败: deviceId={}, programVsn={}", deviceId, programVsn);

        // 查主服务的program表，尝试填充programId字段
        Integer programId = programRepositoryAdapter.findProgramIdByNameAndAuthor(report.getProgramName(),
                Objects.isNull(report.getAuthorId()) ? 0 : report.getAuthorId(),
                programVsnName);
        if (Objects.nonNull(programId)) report.setProgramId(programId);

    }
}
