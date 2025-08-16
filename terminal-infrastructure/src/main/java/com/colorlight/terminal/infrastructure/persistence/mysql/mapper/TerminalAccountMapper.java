package com.colorlight.terminal.infrastructure.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TerminalAccountMapper extends BaseMapper<TerminalAccountDO> {
}
