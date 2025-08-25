package com.colorlight.terminal.infrastructure.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colorlight.terminal.infrastructure.persistence.mysql.entity.TerminalAccountDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TerminalAccountMapper extends BaseMapper<TerminalAccountDO> {
    
    /**
     * 立即更新设备登录时间（用于首次上线）
     * 使用COALESCE确保firstLoginTime只在为null时设置，同时更新lastLoginTime和lastLoginIp
     */
    @Update("UPDATE device_terminal_account SET " +
            "last_login_time = #{loginTime}, " +
            "last_login_ip = #{clientIp}, " +
            "first_login_time = COALESCE(first_login_time, #{loginTime}), " +
            "update_time = NOW() " +
            "WHERE device_id = #{deviceId}")
    int updateLoginTimeImmediate(@Param("deviceId") Long deviceId,
                               @Param("clientIp") String clientIp,
                               @Param("loginTime") LocalDateTime loginTime);
    
    /**
     * 批量更新设备登录时间
     * 利用COALESCE保护firstLoginTime，只在数据库中为null时才设置
     */
    @Update("UPDATE device_terminal_account SET " +
            "last_login_time = #{loginTime}, " +
            "last_login_ip = #{clientIp}, " +
            "first_login_time = COALESCE(first_login_time, #{loginTime}), " +
            "update_time = NOW() " +
            "WHERE device_id = #{deviceId}")
    int updateLoginTime(@Param("deviceId") Long deviceId,
                       @Param("clientIp") String clientIp,
                       @Param("loginTime") LocalDateTime loginTime);
}
