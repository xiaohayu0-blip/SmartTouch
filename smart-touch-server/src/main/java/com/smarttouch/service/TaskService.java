package com.smarttouch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttouch.common.BusinessException;
import com.smarttouch.entity.Device;
import com.smarttouch.entity.Task;
import com.smarttouch.entity.TaskStep;
import com.smarttouch.mapper.DeviceMapper;
import com.smarttouch.mapper.TaskMapper;
import com.smarttouch.mapper.TaskStepMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 任务管理 Service
 * 任务创建/查询/取消 + 状态机流转
 */
@Slf4j
@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final DeviceMapper deviceMapper;

    public TaskService(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                       DeviceMapper deviceMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.deviceMapper = deviceMapper;
    }

    /** 创建任务 */
    @Transactional
    public Task createTask(Long deviceId, String instruction) {
        // 校验设备存在且在线
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        if (device.getStatus() == Device.STATUS_OFFLINE) {
            throw new BusinessException(400, "设备离线，无法创建任务");
        }
        if (device.getStatus() == Device.STATUS_BUSY) {
            throw new BusinessException(400, "设备正在执行任务，请稍后再试");
        }

        // 生成任务编号
        String taskNo = generateTaskNo();

        Task task = Task.builder()
                .taskNo(taskNo)
                .deviceId(deviceId)
                .instruction(instruction)
                .status(Task.STATUS_PENDING)
                .totalSteps(0)
                .currentStep(0)
                .build();
        taskMapper.insert(task);

        // 标记设备为执行中
        device.setStatus(Device.STATUS_BUSY);
        deviceMapper.updateById(device);

        log.info("任务创建成功: taskNo={}, deviceId={}, instruction={}", taskNo, deviceId, instruction);
        return task;
    }

    /** 分页查询任务列表 */
    public Page<Task> listTasks(int pageNum, int pageSize, Integer status) {
        Page<Task> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(status != null, Task::getStatus, status)
                .orderByDesc(Task::getCreateTime);
        return taskMapper.selectPage(page, wrapper);
    }

    /** 根据ID查询任务 */
    public Task getById(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return task;
    }

    /** 根据任务编号查询 */
    public Task getByTaskNo(String taskNo) {
        Task task = taskMapper.selectByTaskNo(taskNo);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return task;
    }

    /** 查询某设备最近N条任务 */
    public List<Task> getTasksByDeviceId(Long deviceId, int limit) {
        return taskMapper.selectByDeviceId(deviceId, limit);
    }

    /** 取消任务（仅待执行/执行中状态可取消） */
    @Transactional
    public void cancelTask(Long taskId) {
        Task task = getById(taskId);
        if (task.getStatus() != Task.STATUS_PENDING && task.getStatus() != Task.STATUS_RUNNING) {
            throw new BusinessException(400, "当前状态不允许取消");
        }

        task.setStatus(Task.STATUS_CANCELLED);
        task.setEndTime(LocalDateTime.now());
        task.setResultMsg("用户取消");
        taskMapper.updateById(task);

        // 释放设备
        releaseDevice(task.getDeviceId());

        log.info("任务已取消: taskNo={}", task.getTaskNo());
    }

    /** 更新任务状态为执行中 */
    public void markRunning(Long taskId) {
        Task task = getById(taskId);
        task.setStatus(Task.STATUS_RUNNING);
        task.setStartTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /** 更新任务状态为成功 */
    @Transactional
    public void markSuccess(Long taskId, String msg) {
        Task task = getById(taskId);
        task.setStatus(Task.STATUS_SUCCESS);
        task.setEndTime(LocalDateTime.now());
        task.setResultMsg(msg);
        taskMapper.updateById(task);
        releaseDevice(task.getDeviceId());
    }

    /** 更新任务状态为失败 */
    @Transactional
    public void markFailed(Long taskId, String reason) {
        Task task = getById(taskId);
        task.setStatus(Task.STATUS_FAILED);
        task.setEndTime(LocalDateTime.now());
        task.setResultMsg(reason);
        taskMapper.updateById(task);
        releaseDevice(task.getDeviceId());
    }

    /** 更新任务状态为超时 */
    @Transactional
    public void markTimeout(Long taskId) {
        Task task = getById(taskId);
        task.setStatus(Task.STATUS_TIMEOUT);
        task.setEndTime(LocalDateTime.now());
        task.setResultMsg("任务执行超时");
        taskMapper.updateById(task);
        releaseDevice(task.getDeviceId());
    }

    /** 更新当前执行步数 */
    public void updateCurrentStep(Long taskId, int stepNo) {
        Task task = getById(taskId);
        task.setCurrentStep(stepNo);
        task.setTotalSteps(Math.max(task.getTotalSteps(), stepNo));
        taskMapper.updateById(task);
    }

    /** 查询任务的步骤明细（按步骤排序） */
    public List<TaskStep> getTaskSteps(Long taskId) {
        return taskStepMapper.selectByTaskId(taskId);
    }

    // ==================== 内部方法 ====================

    /** 生成任务编号：T + 日期 + 短UUID */
    private String generateTaskNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String shortUuid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "T" + date + shortUuid;
    }

    /** 释放设备（将设备状态从执行中恢复为在线） */
    private void releaseDevice(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device != null && device.getStatus() == Device.STATUS_BUSY) {
            device.setStatus(Device.STATUS_ONLINE);
            deviceMapper.updateById(device);
        }
    }
}
