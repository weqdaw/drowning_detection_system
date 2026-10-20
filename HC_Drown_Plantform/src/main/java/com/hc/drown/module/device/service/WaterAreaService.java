package com.hc.drown.module.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hc.drown.module.device.entity.WaterArea;

import java.util.List;

public interface WaterAreaService extends IService<WaterArea> {
    Page<WaterArea> pageWaterAreas(Integer pageNum, Integer pageSize, String keyword);
    WaterArea getWaterAreaById(Long id);
    boolean createWaterArea(WaterArea area);
    boolean updateWaterArea(WaterArea area);
    List<WaterArea> findContainingPoint(Double lng, Double lat);
}
