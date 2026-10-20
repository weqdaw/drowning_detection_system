package com.hc.drown.module.drone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hc.drown.module.drone.entity.DroneFlightLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DroneFlightLogMapper extends BaseMapper<DroneFlightLog> {

    @Select("INSERT INTO drone_flight_log (mission_id, location, altitude, speed, battery_pct, signal_dbm, logged_at) " +
            "VALUES (#{log.missionId}, #{log.location}, #{log.altitude}, #{log.speed}, #{log.batteryPct}, #{log.signalDbm}, NOW())")
    int insertFlightLog(@Param("log") DroneFlightLog log);

    @Select("SELECT id, mission_id, location, altitude, speed, battery_pct, signal_dbm, logged_at " +
            "FROM drone_flight_log WHERE mission_id = #{missionId} ORDER BY logged_at ASC")
    List<DroneFlightLog> selectByMissionId(@Param("missionId") Long missionId);
}
