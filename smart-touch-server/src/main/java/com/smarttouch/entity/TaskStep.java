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
 * 任务步骤明细实体
 * 记录每一步的动作、参数、结果，支持任务回放
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_step")
public class TaskStep {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务ID（关联task.id） */
    private Long taskId;

    /** 步骤序号（从1开始递增） */
    private Integer stepNo;

    /** 动作类型：click/swipe/type/screenshot/wait/finish */
    private String action;

    /** 动作参数JSON（坐标、文本等） */
    private String actionJson;

    /** 执行前截图URL */
    private String screenshotUrl;

    /** 执行结果：success/fail/timeout */
    private String result;

    /** LLM决策理由 */
    private String llmReason;

    /** 该步耗时（毫秒） */
    private Integer costMs;

    /** 创建时间 */
    private LocalDateTime createTime;

    // ==================== 结果常量 ====================
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAIL = "fail";
    public static final String RESULT_TIMEOUT = "timeout";
}
