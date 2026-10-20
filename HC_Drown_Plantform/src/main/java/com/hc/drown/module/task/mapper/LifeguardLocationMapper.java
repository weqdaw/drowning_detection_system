package com.hc.drown.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hc.drown.module.task.entity.LifeguardLocation;
import com.hc.drown.module.user.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LifeguardLocationMapper extends BaseMapper<LifeguardLocation> {

    @Insert("INSERT INTO lifeguard_location (user_id, location, accuracy, duty_status, recorded_at) " +
            "VALUES (#{loc.userId}, #{loc.location}, #{loc.accuracy}, #{loc.dutyStatus}, NOW())")
    int insertLocation(@Param("loc") LifeguardLocation location);

    /**
     * 查询所有救生员及其最新位置（在岗与下岗）
     */
    @Select("SELECT DISTINCT u.*, l.location as last_wkt_location " +
            "FROM sys_user u " +
            "LEFT JOIN ( " +
            "   SELECT DISTINCT ON (user_id) user_id, location, recorded_at " +
            "   FROM lifeguard_location " +
            "   ORDER BY user_id, recorded_at DESC " +
            ") l ON u.id = l.user_id " +
            "LEFT JOIN sys_user_role ur ON u.id = ur.user_id " +
            "LEFT JOIN sys_role r ON ur.role_id = r.id AND r.deleted = 0 " +
            "WHERE u.deleted = 0 AND (r.role_code = 'ROLE_RESCUER' OR l.location IS NOT NULL)")
    List<SysUser> findActiveLifeguards();
}
