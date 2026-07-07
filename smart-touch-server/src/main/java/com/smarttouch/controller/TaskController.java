package com.smarttouch.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttouch.common.Result;
import com.smarttouch.entity.Task;
import com.smarttouch.entity.TaskStep;
import com.smarttouch.service.TaskService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务管理 Controller
 * 任务创建/查询/取消/步骤明细
 */
@Validated
@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** 创建任务 */
    @PostMapping("/create")
    public Result<Task> create(@RequestBody @Validated CreateTaskRequest request) {
        Task task = taskService.createTask(request.getDeviceId(), request.getInstruction());
        return Result.success(task);
    }

    /** 分页查询任务列表 */
    @GetMapping("/list")
    public Result<Page<Task>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.success(taskService.listTasks(pageNum, pageSize, status));
    }

    /** 查询任务详情 */
    @GetMapping("/{taskId}")
    public Result<Task> detail(@PathVariable @NotNull Long taskId) {
        return Result.success(taskService.getById(taskId));
    }

    /** 查询任务步骤明细（步骤回放用） */
    @GetMapping("/{taskId}/steps")
    public Result<List<TaskStep>> steps(@PathVariable @NotNull Long taskId) {
        return Result.success(taskService.getTaskSteps(taskId));
    }

    /** 取消任务 */
    @PostMapping("/{taskId}/cancel")
    public Result<?> cancel(@PathVariable @NotNull Long taskId) {
        taskService.cancelTask(taskId);
        return Result.success("任务已取消");
    }

    /** 根据设备ID查询最近任务 */
    @GetMapping("/by-device/{deviceId}")
    public Result<List<Task>> byDevice(
            @PathVariable @NotNull Long deviceId,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(taskService.getTasksByDeviceId(deviceId, limit));
    }

    // ==================== 请求体 ====================

    @Data
    public static class CreateTaskRequest {
        @NotNull(message = "设备ID不能为空")
        private Long deviceId;
        @NotBlank(message = "任务指令不能为空")
        private String instruction;
    }
}
