-- =====================================================
-- SmartTouch 数据库初始化脚本
-- 首次部署时由 docker-compose 自动执行
-- 手动执行：mysql -u root -p < init.sql
-- =====================================================

-- 创建数据库（如不存在）
CREATE DATABASE IF NOT EXISTS `smart_touch`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `smart_touch`;

-- =====================================================
-- 1. 设备信息表
-- =====================================================
CREATE TABLE IF NOT EXISTS `device` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `device_uuid`  VARCHAR(64) NOT NULL COMMENT '设备唯一标识（Android设备ID）',
    `device_name`  VARCHAR(128) DEFAULT '' COMMENT '设备名称/型号（如Xiaomi 14）',
    `resolution`   VARCHAR(32) DEFAULT '' COMMENT '屏幕分辨率（宽x高，如1080x2400）',
    `status`       TINYINT DEFAULT 0 COMMENT '状态：0离线 1在线 2执行中',
    `last_online`  DATETIME COMMENT '最后在线时间',
    `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_device_uuid` (`device_uuid`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备信息表';

-- =====================================================
-- 2. 任务表
-- =====================================================
CREATE TABLE IF NOT EXISTS `task` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `task_no`       VARCHAR(32) NOT NULL COMMENT '任务编号（T前缀+雪花ID，如T20240707001）',
    `device_id`     BIGINT NOT NULL COMMENT '执行设备ID（关联device.id）',
    `instruction`   TEXT NOT NULL COMMENT '用户自然语言指令（如"打开微信给张三发你好"）',
    `status`        TINYINT DEFAULT 0 COMMENT '状态：0待执行 1执行中 2成功 3失败 4超时 5已取消',
    `total_steps`   INT DEFAULT 0 COMMENT '总执行步数',
    `current_step`  INT DEFAULT 0 COMMENT '当前执行到第几步',
    `result_msg`    VARCHAR(512) DEFAULT '' COMMENT '任务结果描述或失败原因',
    `start_time`    DATETIME COMMENT '任务开始执行时间',
    `end_time`      DATETIME COMMENT '任务结束时间',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_device_id` (`device_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- =====================================================
-- 3. 任务步骤明细表
-- =====================================================
CREATE TABLE IF NOT EXISTS `task_step` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `task_id`        BIGINT NOT NULL COMMENT '任务ID（关联task.id）',
    `step_no`        INT NOT NULL COMMENT '步骤序号（从1开始递增）',
    `action`         VARCHAR(32) NOT NULL COMMENT '动作类型：click/swipe/type/screenshot/wait/finish',
    `action_json`    JSON COMMENT '动作参数JSON（坐标、文本等）',
    `screenshot_url` VARCHAR(512) DEFAULT '' COMMENT '执行前截图URL（本地文件路径或OSS链接）',
    `result`         VARCHAR(16) DEFAULT '' COMMENT '执行结果：success/fail/timeout',
    `llm_reason`     VARCHAR(512) DEFAULT '' COMMENT 'LLM决策理由（模型输出的reason字段）',
    `cost_ms`        INT DEFAULT 0 COMMENT '该步耗时（毫秒）',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_task_id` (`task_id`),
    KEY `idx_task_step` (`task_id`, `step_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务步骤明细表';

-- =====================================================
-- 4. 会话记录表（Agent交互日志，用于调试和回放）
-- =====================================================
CREATE TABLE IF NOT EXISTS `session_record` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `task_id`     BIGINT NOT NULL COMMENT '关联任务ID',
    `step_no`     INT DEFAULT 0 COMMENT '关联步骤序号（0表示任务级别的记录）',
    `role`        VARCHAR(16) NOT NULL COMMENT '角色：system/user/assistant/tool',
    `content`     MEDIUMTEXT COMMENT '消息内容（prompt/LLM返回/tool结果）',
    `token_count` INT DEFAULT 0 COMMENT '该条消息的token估算数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_task_id` (`task_id`),
    KEY `idx_task_step` (`task_id`, `step_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话记录表（Agent交互日志）';
