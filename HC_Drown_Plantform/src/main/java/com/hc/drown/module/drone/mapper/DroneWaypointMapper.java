package com.hc.drown.module.drone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hc.drown.module.drone.entity.DroneWaypoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DroneWaypointMapper extends BaseMapper<DroneWaypoint> {

    @Select("INSERT INTO drone_waypoint (plan_id, seq_no, location, altitude, hover_sec, action) " +
            "VALUES (#{wp.planId}, #{wp.seqNo}, #{wp.location}, #{wp.altitude}, #{wp.hoverSec}, #{wp.action})")
    int insertWaypoint(@Param("wp") DroneWaypoint waypoint);

    @Select("SELECT id, plan_id, seq_no, location, altitude, hover_sec, action " +
            "FROM drone_waypoint WHERE plan_id = #{planId} ORDER BY seq_no ASC")
    List<DroneWaypoint> selectByPlanId(@Param("planId") Long planId);
}
