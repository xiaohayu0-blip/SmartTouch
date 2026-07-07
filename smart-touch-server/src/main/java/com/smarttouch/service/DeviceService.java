package com.smarttouch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttouch.common.BusinessException;
import com.smarttouch.entity.Device;
import com.smarttouch.gateway.DeviceSession;
import com.smarttouch.gateway.WebSocketGateway;
import com.smarttouch.mapper.DeviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备管理 Service
 * 设备注册、上下线、查询
 */
@Slf4j
@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final WebSocketGateway webSocketGateway;

    public DeviceService(DeviceMapper deviceMapper, WebSocketGateway webSocketGateway) {
        this.deviceMapper = deviceMapper;
        this.webSocketGateway = webSocketGateway;
    }

    /** 分页查询设备列表 */
    public Page<Device> listDevices(int pageNum, int pageSize) {
        Page<Device> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>()
                .orderByDesc(Device::getLastOnline);
        return deviceMapper.selectPage(page, wrapper);
    }

    /** 根据ID查询设备 */
    public Device getById(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        return device;
    }

    /** 根据UUID查询设备 */
    public Device getByUuid(String deviceUuid) {
        return deviceMapper.selectByDeviceUuid(deviceUuid);
    }

    /** 查询所有在线设备 */
    public List<Device> getOnlineDevices() {
        return deviceMapper.selectOnlineDevices();
    }

    /** 设备注册（首次连接时自动注册或更新） */
    @Transactional
    public Device registerDevice(String deviceUuid, String deviceName, String resolution) {
        Device device = deviceMapper.selectByDeviceUuid(deviceUuid);
        LocalDateTime now = LocalDateTime.now();

        if (device == null) {
            // 新设备注册
            device = Device.builder()
                    .deviceUuid(deviceUuid)
                    .deviceName(deviceName)
                    .resolution(resolution)
                    .status(Device.STATUS_ONLINE)
                    .lastOnline(now)
                    .build();
            deviceMapper.insert(device);
            log.info("新设备注册: deviceUuid={}, deviceName={}", deviceUuid, deviceName);
        } else {
            // 已有设备更新信息
            device.setDeviceName(deviceName);
            device.setResolution(resolution);
            device.setStatus(Device.STATUS_ONLINE);
            device.setLastOnline(now);
            deviceMapper.updateById(device);
            log.info("设备信息更新: deviceUuid={}", deviceUuid);
        }
        return device;
    }

    /** 获取设备在线状态（综合WebSocket+数据库） */
    public boolean isOnline(String deviceUuid) {
        return webSocketGateway.isOnline(deviceUuid);
    }

    /** 获取当前设备详情（含WebSocket实时状态） */
    public Device getDeviceDetail(String deviceUuid) {
        Device device = deviceMapper.selectByDeviceUuid(deviceUuid);
        if (device == null) return null;

        DeviceSession session = webSocketGateway.getSession(deviceUuid);
        if (session != null) {
            device.setStatus(session.getStatus());
            device.setLastOnline(session.getLastHeartbeat());
        } else {
            device.setStatus(Device.STATUS_OFFLINE);
        }
        return device;
    }
}
