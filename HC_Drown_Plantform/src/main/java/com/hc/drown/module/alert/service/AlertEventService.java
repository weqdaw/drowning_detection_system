package com.hc.drown.module.alert.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hc.drown.module.alert.entity.AlertEvent;

public interface AlertEventService extends IService<AlertEvent> {

    /**
     * 分页查询报警事件 (UC01)
     */
    Page<AlertEvent> pageAlertEvents(Integer pageNum, Integer pageSize, String status, Long waterAreaId, String alertType);

    /**
     * 根据ID查询报警详情 (UC01)
     */
    AlertEvent getAlertEventById(Long id);

    /**
     * 接收AI实时推送的疑似报警 (F-05)
     */
    AlertEvent receiveAlert(AlertEvent event);

    /**
     * 值守员人工确认报警及触发联动 (UC02 / F-06)
     * @param id 报警事件ID
     * @param isReal 是否属实
     * @param reason 误报原因
     * @param userId 确认值守员ID
     */
    boolean confirmAlert(Long id, boolean isReal, String reason, Long userId);

    /**
     * 从视频分析平台获取目标监测历史记录
     */
    java.util.List<java.util.Map<String, Object>> getDetectionHistory(Integer limit);

    /**
     * 获取视频录像列表
     */
    java.util.List<java.util.Map<String, Object>> getRecordings(String streamId, Integer limit);

    /**
     * 删除录像记录
     */
    boolean deleteRecording(Integer id);

    /**
     * 清空所有录像记录及物理文件
     */
    boolean clearRecordings();

    /**
     * 删除单条监测历史
     */
    boolean deleteDetectionHistory(Integer id);

    /**
     * 清空监测历史
     */
    boolean clearDetectionHistory();
}
