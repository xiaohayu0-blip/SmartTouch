package com.smarttouch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smarttouch.entity.TaskStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务步骤明细 Mapper
 */
@Mapper
public interface TaskStepMapper extends BaseMapper<TaskStep> {

    /** 根据任务ID查询所有步骤（按步骤序号排序，用于步骤回放） */
    @Select("SELECT * FROM task_step WHERE task_id = #{taskId} ORDER BY step_no ASC")
    List<TaskStep> selectByTaskId(@Param("taskId") Long taskId);

    /** 查询某任务的最后一步 */
    @Select("SELECT * FROM task_step WHERE task_id = #{taskId} ORDER BY step_no DESC LIMIT 1")
    TaskStep selectLatestByTaskId(@Param("taskId") Long taskId);

    /** 统计某任务的步骤数 */
    @Select("SELECT COUNT(*) FROM task_step WHERE task_id = #{taskId}")
    int countByTaskId(@Param("taskId") Long taskId);
}
