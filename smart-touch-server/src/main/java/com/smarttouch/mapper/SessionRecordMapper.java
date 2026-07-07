package com.smarttouch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smarttouch.entity.SessionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话记录 Mapper
 */
@Mapper
public interface SessionRecordMapper extends BaseMapper<SessionRecord> {

    /** 根据任务ID查询所有会话记录（按时间排序） */
    @Select("SELECT * FROM session_record WHERE task_id = #{taskId} ORDER BY create_time ASC")
    List<SessionRecord> selectByTaskId(@Param("taskId") Long taskId);

    /** 根据任务ID和步骤序号查询 */
    @Select("SELECT * FROM session_record WHERE task_id = #{taskId} AND step_no = #{stepNo} ORDER BY create_time ASC")
    List<SessionRecord> selectByTaskIdAndStepNo(@Param("taskId") Long taskId, @Param("stepNo") Integer stepNo);
}
