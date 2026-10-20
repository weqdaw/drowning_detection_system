package com.hc.drown.module.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.drown.module.device.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 自定义分页查询以解析 PostGIS Geometry
     */
    Page<Device> selectDevicePage(Page<Device> page,
                                  @Param("keyword") String keyword,
                                  @Param("type") String type,
                                  @Param("waterAreaId") Long waterAreaId);

    /**
     * 根据ID查询设备
     */
    Device selectDeviceById(@Param("id") Long id);

    /**
     * 自定义插入以使用 ST_GeomFromText
     */
    int insertDevice(@Param("device") Device device);

    /**
     * 自定义更新以使用 ST_GeomFromText
     */
    int updateDevice(@Param("device") Device device);

    /**
     * 根据设备类型查询设备
     */
    List<Device> selectByDeviceType(@Param("deviceType") String deviceType);

    /**
     * 查询离指定坐标最近的特定类型设备 (例如音柱、机库)
     */
    Device findNearestDevice(@Param("lng") Double lng,
                             @Param("lat") Double lat,
                             @Param("deviceType") String deviceType);

    Device selectDeviceByCodeIncludeDeleted(@Param("deviceCode") String deviceCode);
}
