package com.colorlight.terminal.infrastructure.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.ProgramDO;
import com.colorlight.terminal.infrastructure.persistence.mysql.mapper.ProgramMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * 主服务的节目表查询接口
 * <p>本服务只查询</p>
 * <p>仅在infrastructure中使用</p>
 *
 * @author Nan
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MysqlProgramRepositoryAdapter {

    private final ProgramMapper programMapper;

    /**
     * 根据节目名和作者名查询节目Id
     * <P>节目播放上报处理流程中使用，用于在记录中填充节目Id</P>
     *
     * @see com.colorlight.terminal.infrastructure.record.DeviceProgramPlayRecordService
     * @param programName 节目名称
     * @param authorId 节目作者Id
     * @param vsnName 节目Vsn名称
     * @return 节目Id（如果查询成功）
     */
    public Integer findProgramIdByNameAndAuthor(String programName,
                                                Integer authorId,
                                                String vsnName) {
        LambdaQueryWrapper<ProgramDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ProgramDO::getProgramId)
                .eq(ProgramDO::getProgramName, programName)
                .eq(ProgramDO::getStatus, "publish")
                .eq(ProgramDO::getIsDelete, 0);

        if (authorId != null && authorId > 0) {
            wrapper.eq(ProgramDO::getAuthorId, authorId);
        }
        if (StringUtils.isNotBlank(vsnName)) {
            wrapper.eq(ProgramDO::getVsnName, vsnName);
        }

        try {
            ProgramDO programDO = programMapper.selectOne(wrapper);
            if (Objects.isNull(programDO) || Objects.isNull(programDO.getProgramId())) {
                log.warn("Program - 节目Id查询失败: programName={}, authorId={}, vsnName={}", programName, authorId, vsnName);
                return null;
            }
            return programDO.getProgramId();
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.MYSQL_ERROR, e);
        }

    }
}
