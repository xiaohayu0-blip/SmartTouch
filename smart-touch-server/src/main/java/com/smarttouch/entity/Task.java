package com.smarttouch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task")
public class Task {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号（T前缀+雪花ID，如T20240707001） */
    private String taskNo;

    /** 执行设备ID（关联device.id） */
    private Long deviceId;

    /** 用户自然语言指令 */
    private String instruction;

    /** 状态：0待执行 1执行中 2成功 3失败 4超时 5已取消 */
    private Integer status;

    /** 总执行步数 */
    private Integer totalSteps;

    /** 当前执行到第几步 */
    private Integer currentStep;

    /** 任务结果描述或失败原因 */
    private String resultMsg;

    /** 任务开始执行时间 */
    private LocalDateTime startTime;

    /** 任务结束时间 */
    private LocalDateTime endTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ==================== 状态常量 ====================
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_TIMEOUT = 4;
    public static final int STATUS_CANCELLED = 5;
}
