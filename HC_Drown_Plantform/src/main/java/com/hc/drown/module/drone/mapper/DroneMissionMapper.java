package com.hc.drown.module.drone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.module.drone.entity.DroneMission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DroneMissionMapper extends BaseMapper<DroneMission> {

    @Select("<script>" +
            "SELECT dm.*, d.device_name AS drone_name, dp.plan_name, u.real_name AS operator_name " +
            "FROM drone_mission dm " +
            "LEFT JOIN device d ON dm.drone_id = d.id " +
            "LEFT JOIN drone_plan dp ON dm.plan_id = dp.id " +
            "LEFT JOIN sys_user u ON dm.operator_id = u.id " +
            "WHERE dm.deleted = 0 " +
            "<if test='status != null and status != \"\"'> AND dm.status = #{status} </if> " +
            "<if test='droneId != null'> AND dm.drone_id = #{droneId} </if> " +
            "ORDER BY dm.created_at DESC" +
            "</script>")
    Page<DroneMission> selectMissionPage(Page<DroneMission> page,
                                         @Param("status") String status,
                                         @Param("droneId") Long droneId);
}
