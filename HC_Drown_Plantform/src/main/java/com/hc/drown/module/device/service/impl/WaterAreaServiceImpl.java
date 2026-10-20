package com.hc.drown.module.device.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.drown.module.device.entity.WaterArea;
import com.hc.drown.module.device.mapper.WaterAreaMapper;
import com.hc.drown.module.device.service.WaterAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WaterAreaServiceImpl extends ServiceImpl<WaterAreaMapper, WaterArea> implements WaterAreaService {

    @Override
    public Page<WaterArea> pageWaterAreas(Integer pageNum, Integer pageSize, String keyword) {
        Page<WaterArea> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectWaterAreaPage(page, keyword);
    }

    @Override
    public WaterArea getWaterAreaById(Long id) {
        return baseMapper.selectWaterAreaById(id);
    }

    @Override
    public boolean createWaterArea(WaterArea area) {
        return baseMapper.insertWaterArea(area) > 0;
    }

    @Override
    public boolean updateWaterArea(WaterArea area) {
        return baseMapper.updateWaterArea(area) > 0;
    }

    @Override
    public List<WaterArea> findContainingPoint(Double lng, Double lat) {
        List<WaterArea> allAreas = list();
        List<WaterArea> containingAreas = new java.util.ArrayList<>();
        for (WaterArea area : allAreas) {
            if (area.getBoundary() != null) {
                List<double[]> polygon = com.hc.drown.common.util.GeoUtils.parsePolygon(area.getBoundary());
                if (com.hc.drown.common.util.GeoUtils.isPointInPolygon(lng, lat, polygon)) {
                    containingAreas.add(area);
                }
            }
        }
        return containingAreas;
    }
}
