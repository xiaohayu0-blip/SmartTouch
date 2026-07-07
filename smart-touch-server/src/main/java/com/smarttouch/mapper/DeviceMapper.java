package com.smarttouch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smarttouch.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备 Mapper
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /** 根据设备UUID查询（唯一约束，返回单条） */
    @Select("SELECT * FROM device WHERE device_uuid = #{deviceUuid}")
    Device selectByDeviceUuid(@Param("deviceUuid") String deviceUuid);

    /** 查询所有在线设备 */
    @Select("SELECT * FROM device WHERE status IN (1, 2)")
    List<Device> selectOnlineDevices();

    /** 查询空闲设备（在线且未执行任务） */
    @Select("SELECT * FROM device WHERE status = 1")
    List<Device> selectIdleDevices();
}
