package com.hc.drown.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.module.task.entity.RescueTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RescueTaskMapper extends BaseMapper<RescueTask> {

    /**
     * 自定义分页查询以解析 PostGIS Geometry 且支持关联数据
     */
    Page<RescueTask> selectRescueTaskPage(Page<RescueTask> page,
                                          @Param("status") String status,
                                          @Param("assigneeId") Long assigneeId,
                                          @Param("eventId") Long eventId);

    /**
     * 根据ID查询救援任务
     */
    RescueTask selectRescueTaskById(@Param("id") Long id);

    /**
     * 自定义插入以使用 ST_GeomFromText
     */
    int insertRescueTask(@Param("task") RescueTask task);

    /**
     * 自定义更新以使用 ST_GeomFromText
     */
    int updateRescueTask(@Param("task") RescueTask task);
}
