package com.smarttouch.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smarttouch.common.Result;
import com.smarttouch.entity.Device;
import com.smarttouch.service.DeviceService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理 Controller
 * 设备查询/注册/状态监控
 */
@Validated
@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /** 分页查询设备列表 */
    @GetMapping("/list")
    public Result<Page<Device>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(deviceService.listDevices(pageNum, pageSize));
    }

    /** 查询在线设备列表 */
    @GetMapping("/online")
    public Result<List<Device>> onlineDevices() {
        return Result.success(deviceService.getOnlineDevices());
    }

    /** 根据设备UUID查询详情 */
    @GetMapping("/{deviceUuid}")
    public Result<Device> detail(@PathVariable @NotBlank String deviceUuid) {
        Device device = deviceService.getDeviceDetail(deviceUuid);
        if (device == null) {
            return Result.error(404, "设备不存在");
        }
        return Result.success(device);
    }

    /** 检查设备是否在线 */
    @GetMapping("/{deviceUuid}/online")
    public Result<Boolean> isOnline(@PathVariable @NotBlank String deviceUuid) {
        return Result.success(deviceService.isOnline(deviceUuid));
    }
}
