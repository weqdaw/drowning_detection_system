package com.hc.drown.module.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.module.device.entity.WaterArea;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WaterAreaMapper extends BaseMapper<WaterArea> {

    /**
     * 自定义分页查询以解析 PostGIS Geometry
     */
    Page<WaterArea> selectWaterAreaPage(Page<WaterArea> page, @Param("keyword") String keyword);

    /**
     * 根据ID查询水域
     */
    WaterArea selectWaterAreaById(@Param("id") Long id);

    /**
     * 自定义插入以使用 ST_GeomFromText
     */
    int insertWaterArea(@Param("area") WaterArea area);

    /**
     * 自定义更新以使用 ST_GeomFromText
     */
    int updateWaterArea(@Param("area") WaterArea area);

    /**
     * 查找包含某个坐标点的水域
     */
    List<WaterArea> findContainingPoint(@Param("lng") Double lng, @Param("lat") Double lat);
}
