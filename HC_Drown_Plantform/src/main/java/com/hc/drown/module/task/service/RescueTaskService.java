package com.hc.drown.module.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hc.drown.module.task.entity.LifeguardLocation;
import com.hc.drown.module.task.entity.RescueTask;

import java.util.List;

public interface RescueTaskService extends IService<RescueTask> {

    /**
     * 分页查询救援任务 (UC11)
     */
    Page<RescueTask> pageRescueTasks(Integer pageNum, Integer pageSize, String status, Long assigneeId, Long eventId);

    /**
     * 根据ID获取任务详情 (UC09)
     */
    RescueTask getRescueTaskById(Long id);

    /**
     * 自动或人工派发任务 (F-07)
     */
    RescueTask dispatchTask(Long eventId, Long dispatcherId);

    /**
     * 接受任务 (UC09)
     */
    boolean acceptTask(Long taskId, Long userId);

    /**
     * 上报到达 (UC10)
     */
    boolean arriveTask(Long taskId, Long userId, Double lng, Double lat);

    /**
     * 完成任务并填写结果 (UC10)
     */
    boolean completeTask(Long taskId, Long userId, String resultType, String note, List<String> photoUrls);

    /**
     * 上报救生员实时GPS位置 (UC12 / F-19)
     */
    boolean uploadLocation(LifeguardLocation location, Double lng, Double lat);

    /**
     * 手工标注更新任务状态 (PROCESSED, RECOGNITION_ERROR 等)
     */
    boolean updateTaskStatus(Long taskId, String status, Long userId, String note);
}
