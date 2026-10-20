package com.hc.drown.module.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.drown.module.device.entity.Device;
import com.hc.drown.module.device.mapper.DeviceMapper;
import com.hc.drown.module.device.service.DeviceService;
import com.hc.drown.module.device.service.StreamDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private final StreamDistributionService streamDistributionService;

    @Override
    public Page<Device> pageDevices(Integer pageNum, Integer pageSize, String keyword, String type, Long waterAreaId) {
        Page<Device> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectDevicePage(page, keyword, type, waterAreaId);
    }

    @Override
    public Device getDeviceById(Long id) {
        return baseMapper.selectDeviceById(id);
    }

    @Override
    public boolean createDevice(Device device) {
        if (device != null && device.getDeviceCode() != null && device.getExtraConfig() != null) {
            streamDistributionService.updateRules(device.getDeviceCode(), device.getExtraConfig());
        }
        return baseMapper.insertDevice(device) > 0;
    }

    @Override
    public boolean updateDevice(Device device) {
        if (device != null && device.getDeviceCode() != null && device.getExtraConfig() != null) {
            streamDistributionService.updateRules(device.getDeviceCode(), device.getExtraConfig());
        }
        return baseMapper.updateDevice(device) > 0;
    }

    @Override
    public List<Device> getDevicesByType(String deviceType) {
        return baseMapper.selectByDeviceType(deviceType);
    }

    @Override
    public Device findNearestDevice(Double lng, Double lat, String deviceType) {
        return baseMapper.findNearestDevice(lng, lat, deviceType);
    }

    @Override
    public boolean updateHeartbeat(String deviceCode, String status) {
        Device device = baseMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceCode, deviceCode)
                .eq(Device::getDeleted, 0));
        if (device != null) {
            device.setStatus(status);
            device.setLastHeartbeat(LocalDateTime.now());
            device.setUpdatedAt(LocalDateTime.now());
            return baseMapper.updateById(device) > 0;
        }
        return false;
    }

    @Override
    public boolean switchModel(Long id, String modelName) {
        Device device = baseMapper.selectDeviceById(id);
        if (device == null || !"CAMERA".equalsIgnoreCase(device.getDeviceType())) {
            throw new com.hc.drown.common.exception.BusinessException("该设备不存在或不是摄像头设备");
        }

        // 调用分发系统接口切换模型
        boolean success = streamDistributionService.switchModel(device.getDeviceCode(), modelName);

        if (success) {
            java.util.Map<String, Object> config = device.getExtraConfig();
            if (config == null) {
                config = new java.util.HashMap<>();
            }
            config.put("selectedModel", modelName);
            device.setExtraConfig(config);
            device.setUpdatedAt(LocalDateTime.now());
            return baseMapper.updateDevice(device) > 0;
        }
        return false;
    }

    @Override
    public boolean updateConfidence(Long id, Float confidence) {
        Device device = baseMapper.selectDeviceById(id);
        if (device == null || !"CAMERA".equalsIgnoreCase(device.getDeviceType())) {
            throw new com.hc.drown.common.exception.BusinessException("该设备不存在或不是摄像头设备");
        }

        // 调用分发系统接口热重载灵敏度与阈值
        streamDistributionService.updateConfidence(device.getDeviceCode(), confidence);

        java.util.Map<String, Object> config = device.getExtraConfig();
        if (config == null) {
            config = new java.util.HashMap<>();
        }
        config.put("alert_threshold", confidence);
        config.put("confidence", confidence);
        device.setExtraConfig(config);
        device.setUpdatedAt(LocalDateTime.now());

        // 同时触发规则热更新
        streamDistributionService.updateRules(device.getDeviceCode(), config);

        return baseMapper.updateDevice(device) > 0;
    }

    @Override
    public Device getDeviceByCodeIncludeDeleted(String deviceCode) {
        return baseMapper.selectDeviceByCodeIncludeDeleted(deviceCode);
    }
}
