package com.hc.drown.module.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.module.alert.entity.AlertEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    /**
     * 自定义分页查询以解析 PostGIS Geometry 且支持关联数据
     */
    Page<AlertEvent> selectAlertEventPage(Page<AlertEvent> page,
                                          @Param("status") String status,
                                          @Param("waterAreaId") Long waterAreaId,
                                          @Param("alertType") String alertType);

    /**
     * 根据ID查询报警事件
     */
    AlertEvent selectAlertEventById(@Param("id") Long id);

    /**
     * 自定义插入以使用 ST_GeomFromText
     */
    int insertAlertEvent(@Param("event") AlertEvent event);

    /**
     * 自定义更新以使用 ST_GeomFromText
     */
    int updateAlertEvent(@Param("event") AlertEvent event);
}
