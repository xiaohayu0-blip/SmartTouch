package com.smarttouch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smarttouch.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务 Mapper
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /** 根据任务编号查询 */
    @Select("SELECT * FROM task WHERE task_no = #{taskNo}")
    Task selectByTaskNo(@Param("taskNo") String taskNo);

    /** 根据设备ID查询最近N条任务 */
    @Select("SELECT * FROM task WHERE device_id = #{deviceId} ORDER BY create_time DESC LIMIT #{limit}")
    List<Task> selectByDeviceId(@Param("deviceId") Long deviceId, @Param("limit") int limit);

    /** 查询指定状态的任务列表 */
    @Select("SELECT * FROM task WHERE status = #{status} ORDER BY create_time ASC")
    List<Task> selectByStatus(@Param("status") Integer status);
}
