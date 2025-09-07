package com.colorlight.terminal.infrastructure.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.DeviceDeletionRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备删除记录Mapper
 * 仅用于记录删除操作
 * 
 * @author Nan
 */
@Mapper
public interface DeviceDeletionRecordMapper extends BaseMapper<DeviceDeletionRecordDO> {

}