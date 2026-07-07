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
 * 会话记录实体
 * 保存Agent与LLM的完整交互日志，用于调试分析和步骤回放
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("session_record")
public class SessionRecord {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务ID */
    private Long taskId;

    /** 关联步骤序号（0表示任务级别的记录，如系统提示词） */
    private Integer stepNo;

    /** 角色：system/user/assistant/tool */
    private String role;

    /** 消息内容（prompt/LLM返回/tool执行结果） */
    private String content;

    /** 该条消息的token估算数 */
    private Integer tokenCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    // ==================== 角色常量 ====================
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";
}
