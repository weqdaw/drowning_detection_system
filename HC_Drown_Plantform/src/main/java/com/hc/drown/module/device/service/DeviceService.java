package com.hc.drown.module.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hc.drown.module.device.entity.Device;

import java.util.List;

public interface DeviceService extends IService<Device> {
    Page<Device> pageDevices(Integer pageNum, Integer pageSize, String keyword, String type, Long waterAreaId);
    Device getDeviceById(Long id);
    boolean createDevice(Device device);
    boolean updateDevice(Device device);
    List<Device> getDevicesByType(String deviceType);
    Device findNearestDevice(Double lng, Double lat, String deviceType);
    boolean updateHeartbeat(String deviceCode, String status);
    boolean switchModel(Long id, String modelName);
    boolean updateConfidence(Long id, Float confidence);
    Device getDeviceByCodeIncludeDeleted(String deviceCode);
}
