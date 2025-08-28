package com.colorlight.terminal.infrastructure.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceSwitchOnRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceSwitchOnRecordMapper extends BaseMapper<DeviceSwitchOnRecordDO> {
}
