-- ============================================================
-- V1__init_schema.sql
-- 黄村乡AI防溺水平台 初始数据库结构
-- ============================================================

-- 仅启用 UUID 扩展（已移除 PostGIS 依赖）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- 1. 用户角色体系
-- ============================================================

CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50)  NOT NULL UNIQUE,   -- ROLE_ADMIN / ROLE_OPERATOR / ROLE_LEADER / ROLE_RESCUER
    role_name   VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP  NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE sys_user (
    id              BIGSERIAL    PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    real_name       VARCHAR(64)  NOT NULL,
    phone           VARCHAR(20),
    avatar_url      VARCHAR(500),
    status          SMALLINT     NOT NULL DEFAULT 1,  -- 1:启用 0:禁用
    duty_status     SMALLINT     NOT NULL DEFAULT 0,  -- 0:离岗 1:在岗 (救生员专用)
    last_login_at   TIMESTAMP,
    password_reset  BOOLEAN      NOT NULL DEFAULT TRUE, -- 首次登录强制修改
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE sys_user_role (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    role_id BIGINT NOT NULL REFERENCES sys_role(id),
    UNIQUE(user_id, role_id)
);

CREATE TABLE sys_permission (
    id          BIGSERIAL    PRIMARY KEY,
    perm_code   VARCHAR(100) NOT NULL UNIQUE,  -- device:read / alert:confirm ...
    perm_name   VARCHAR(100) NOT NULL,
    module      VARCHAR(50),
    created_at  TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE TABLE sys_role_permission (
    id            BIGSERIAL PRIMARY KEY,
    role_id       BIGINT NOT NULL REFERENCES sys_role(id),
    permission_id BIGINT NOT NULL REFERENCES sys_permission(id),
    UNIQUE(role_id, permission_id)
);

-- ============================================================
-- 2. 水域管理
-- ============================================================

CREATE TABLE water_area (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    area_code   VARCHAR(50)  UNIQUE,
    level       SMALLINT     NOT NULL DEFAULT 1,  -- 1:重点 2:普通
    boundary    TEXT,                              -- WKT 文本格式的 POLYGON
    center_point VARCHAR(100),                     -- WKT 文本格式的 POINT
    description TEXT,
    created_at  TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP  NOT NULL DEFAULT NOW(),
    deleted     SMALLINT     NOT NULL DEFAULT 0
);

-- ============================================================
-- 3. 设备管理
-- ============================================================

CREATE TABLE device (
    id              BIGSERIAL    PRIMARY KEY,
    device_code     VARCHAR(100) NOT NULL UNIQUE,  -- 设备唯一编码
    device_name     VARCHAR(100) NOT NULL,
    device_type     VARCHAR(30)  NOT NULL,          -- CAMERA/SPEAKER/DRONE_HANGAR/DRONE
    manufacturer    VARCHAR(100),
    model           VARCHAR(100),
    ip_address      VARCHAR(45),
    port            INTEGER,
    location        VARCHAR(100),                   -- 安装位置 WKT Point
    location_desc   VARCHAR(255),
    water_area_id   BIGINT REFERENCES water_area(id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE', -- ONLINE/OFFLINE/FAULT/MAINTENANCE
    last_heartbeat  TIMESTAMP,
    extra_config    JSONB,                           -- 设备特有配置（云台参数等）
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    deleted         SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_device_type ON device(device_type);
CREATE INDEX idx_device_status ON device(status);

CREATE TABLE device_preset (
    id          BIGSERIAL    PRIMARY KEY,
    device_id   BIGINT       NOT NULL REFERENCES device(id),
    preset_no   INTEGER      NOT NULL,
    preset_name VARCHAR(100),
    description VARCHAR(255),
    UNIQUE(device_id, preset_no)
);

-- ============================================================
-- 4. 报警事件
-- ============================================================

CREATE TABLE alert_event (
    id              BIGSERIAL    PRIMARY KEY,
    event_no        VARCHAR(50)  NOT NULL UNIQUE,   -- 事件编号 AE2024xxxx
    source_device_id BIGINT      REFERENCES device(id),
    water_area_id   BIGINT       REFERENCES water_area(id),
    alert_location  VARCHAR(100),                   -- 警报位置 WKT Point
    confidence      DECIMAL(5,2),                   -- AI置信度 0-100
    alert_type      VARCHAR(30)  NOT NULL DEFAULT 'DROWNING', -- DROWNING/ABNORMAL
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING/CONFIRMED/FALSE_ALARM/RESOLVED
    snapshot_url    VARCHAR(500),                   -- 截图URL (MinIO)
    video_clip_url  VARCHAR(500),                   -- 短视频URL
    confirmed_by    BIGINT       REFERENCES sys_user(id),
    confirmed_at    TIMESTAMP,
    false_alarm_reason TEXT,
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    deleted         SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_alert_status ON alert_event(status);
CREATE INDEX idx_alert_created ON alert_event(created_at DESC);

CREATE TABLE alert_action_log (
    id          BIGSERIAL   PRIMARY KEY,
    event_id    BIGINT      NOT NULL REFERENCES alert_event(id),
    action_type VARCHAR(50) NOT NULL,  -- UAV_DISPATCH/SPEAKER_BROADCAST/CAMERA_PRESET/TASK_CREATE
    target_id   BIGINT,               -- 关联设备ID或任务ID
    status      VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    detail      TEXT,
    executed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 5. 救援任务
-- ============================================================

CREATE TABLE rescue_task (
    id              BIGSERIAL    PRIMARY KEY,
    task_no         VARCHAR(50)  NOT NULL UNIQUE,   -- RT2024xxxx
    event_id        BIGINT       NOT NULL REFERENCES alert_event(id),
    assignee_id     BIGINT       NOT NULL REFERENCES sys_user(id),  -- 被派发救生员
    dispatcher_id   BIGINT       REFERENCES sys_user(id),           -- 调度人（系统自动=null）
    status          VARCHAR(20)  NOT NULL DEFAULT 'DISPATCHED',
    -- DISPATCHED/ACCEPTED/ARRIVED/PROCESSING/COMPLETED/CANCELLED
    task_location   VARCHAR(100),                  -- 任务位置 WKT Point
    priority        SMALLINT     NOT NULL DEFAULT 1, -- 1:普通 2:紧急 3:特级
    dispatch_at     TIMESTAMP  NOT NULL DEFAULT NOW(),
    accept_at       TIMESTAMP,
    arrive_at       TIMESTAMP,
    complete_at     TIMESTAMP,
    result_type     VARCHAR(30),  -- RESCUED/PERSUADED/FALSE_ALARM/NEED_SUPPORT
    result_note     TEXT,
    result_photo_urls TEXT[],     -- 现场照片URLs
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    deleted         SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_task_assignee ON rescue_task(assignee_id);
CREATE INDEX idx_task_event ON rescue_task(event_id);
CREATE INDEX idx_task_status ON rescue_task(status);

CREATE TABLE rescue_task_log (
    id          BIGSERIAL   PRIMARY KEY,
    task_id     BIGINT      NOT NULL REFERENCES rescue_task(id),
    operator_id BIGINT      REFERENCES sys_user(id),
    action      VARCHAR(50) NOT NULL,  -- DISPATCH/ACCEPT/ARRIVE/COMPLETE/CANCEL
    note        TEXT,
    location    VARCHAR(100),               -- 记录时的位置 WKT Point
    logged_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 6. 救生员位置轨迹
-- ============================================================

CREATE TABLE lifeguard_location (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES sys_user(id),
    location    VARCHAR(100) NOT NULL,          -- 救生员经纬度 WKT Point
    accuracy    DECIMAL(8,2),  -- GPS精度(米)
    duty_status SMALLINT    NOT NULL DEFAULT 1, -- 1:在岗 0:离岗
    recorded_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_location_user ON lifeguard_location(user_id, recorded_at DESC);

-- ============================================================
-- 7. 无人机巡检
-- ============================================================

CREATE TABLE drone_plan (
    id              BIGSERIAL    PRIMARY KEY,
    plan_name       VARCHAR(100) NOT NULL,
    hangar_id       BIGINT       NOT NULL REFERENCES device(id), -- 机库设备
    water_area_id   BIGINT       REFERENCES water_area(id),
    schedule_type   VARCHAR(20)  NOT NULL DEFAULT 'ONCE', -- ONCE/DAILY/WEEKLY
    cron_expression VARCHAR(50),                          -- 定时cron
    next_exec_at    TIMESTAMP,
    flight_altitude INTEGER      NOT NULL DEFAULT 30,     -- 飞行高度（米）
    flight_speed    DECIMAL(5,2) NOT NULL DEFAULT 5.0,    -- 飞行速度（m/s）
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/PAUSED/DELETED
    created_by      BIGINT       REFERENCES sys_user(id),
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE drone_waypoint (
    id          BIGSERIAL PRIMARY KEY,
    plan_id     BIGINT    NOT NULL REFERENCES drone_plan(id),
    seq_no      INTEGER   NOT NULL,
    location    VARCHAR(100) NOT NULL,          -- 无人机航点经纬度 WKT Point
    altitude    INTEGER,
    hover_sec   INTEGER   NOT NULL DEFAULT 0, -- 悬停秒数
    action      VARCHAR(50),                  -- HOVER/PHOTO/VIDEO
    UNIQUE(plan_id, seq_no)
);

CREATE TABLE drone_mission (
    id              BIGSERIAL    PRIMARY KEY,
    mission_no      VARCHAR(50)  NOT NULL UNIQUE,   -- DM2024xxxx
    plan_id         BIGINT       REFERENCES drone_plan(id),
    drone_id        BIGINT       NOT NULL REFERENCES device(id),
    trigger_type    VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED/MANUAL/EMERGENCY
    trigger_event_id BIGINT      REFERENCES alert_event(id),   -- 应急触发关联的报警
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING/FLYING/RETURNING/COMPLETED/ABORTED
    start_at        TIMESTAMP,
    end_at          TIMESTAMP,
    flight_distance DECIMAL(10,2), -- 飞行距离（米）
    report_url      VARCHAR(500),  -- 巡检报告URL
    operator_id     BIGINT       REFERENCES sys_user(id),
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP  NOT NULL DEFAULT NOW(),
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE drone_flight_log (
    id          BIGSERIAL   PRIMARY KEY,
    mission_id  BIGINT      NOT NULL REFERENCES drone_mission(id),
    location    VARCHAR(100),               -- 飞行记录位置 WKT Point
    altitude    DECIMAL(8,2),
    speed       DECIMAL(8,2),
    battery_pct SMALLINT,
    signal_dbm  INTEGER,
    logged_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_flight_log_mission ON drone_flight_log(mission_id, logged_at);

-- ============================================================
-- 8. 审计日志
-- ============================================================

CREATE TABLE audit_log (
    id          BIGSERIAL,
    user_id     BIGINT       REFERENCES sys_user(id),
    username    VARCHAR(64),
    module      VARCHAR(50)  NOT NULL,   -- ALERT/DEVICE/USER/TASK/DRONE/ADMIN
    action      VARCHAR(100) NOT NULL,
    target_id   VARCHAR(50),
    detail      TEXT,
    ip_address  VARCHAR(45),
    result      VARCHAR(10)  NOT NULL DEFAULT 'SUCCESS',
    created_at  TIMESTAMP  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 创建默认分区（后续可按月分区）
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;
CREATE INDEX idx_audit_user ON audit_log(user_id, created_at DESC);
CREATE INDEX idx_audit_module ON audit_log(module, created_at DESC);

-- ============================================================
-- 9. 存储策略配置
-- ============================================================

CREATE TABLE storage_config (
    id                      BIGSERIAL    PRIMARY KEY,
    water_area_id           BIGINT       REFERENCES water_area(id), -- NULL=全局默认
    retention_days_video    INTEGER      NOT NULL DEFAULT 30,
    retention_days_snapshot INTEGER      NOT NULL DEFAULT 90,
    backup_enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    backup_target           VARCHAR(50),  -- NAS/OSS
    backup_cron             VARCHAR(50),
    storage_warn_threshold  INTEGER      NOT NULL DEFAULT 85, -- 容量预警阈值%
    updated_by              BIGINT       REFERENCES sys_user(id),
    updated_at              TIMESTAMP  NOT NULL DEFAULT NOW()
);
