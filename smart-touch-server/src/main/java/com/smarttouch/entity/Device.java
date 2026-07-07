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
 * 设备信息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("device")
public class Device {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备唯一标识（Android设备ID） */
    private String deviceUuid;

    /** 设备名称/型号 */
    private String deviceName;

    /** 屏幕分辨率（宽x高，如1080x2400） */
    private String resolution;

    /** 状态：0离线 1在线 2执行中 */
    private Integer status;

    /** 最后在线时间 */
    private LocalDateTime lastOnline;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ==================== 状态常量 ====================
    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_BUSY = 2;
}
