-- ============================================================
-- V2__init_data.sql
-- 初始化基础数据（角色、权限、管理员账号）
-- ============================================================

-- 1. 角色
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ROLE_ADMIN',    '系统管理员', '负责平台运维、设备注册、用户权限管理'),
('ROLE_OPERATOR', '值守员',     '监控中心值机，处理报警、调度资源'),
('ROLE_LEADER',   '乡镇领导',   '查看全域态势和统计分析报表'),
('ROLE_RESCUER',  '救生员',     '现场救援，接收任务，上报处置结果')
ON CONFLICT (role_code) DO NOTHING;

-- 2. 权限定义
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
-- 用户管理
('user:read',        '查看用户',     'USER'),
('user:write',       '新增/修改用户', 'USER'),
('user:delete',      '删除用户',     'USER'),
-- 设备管理
('device:read',      '查看设备',     'DEVICE'),
('device:write',     '注册/修改设备', 'DEVICE'),
('device:delete',    '删除设备',     'DEVICE'),
('device:control',   '控制设备',     'DEVICE'),
-- 报警管理
('alert:read',       '查看报警',     'ALERT'),
('alert:confirm',    '确认报警',     'ALERT'),
('alert:close',      '关闭报警',     'ALERT'),
-- 救援任务
('task:read',        '查看任务',     'TASK'),
('task:dispatch',    '派发任务',     'TASK'),
('task:update',      '更新任务状态', 'TASK'),
-- 无人机
('drone:read',       '查看无人机',   'DRONE'),
('drone:plan',       '制定巡检计划', 'DRONE'),
('drone:control',    '控制无人机',   'DRONE'),
-- 统计报表
('stats:read',       '查看统计报表', 'STATS'),
-- 审计日志
('audit:read',       '查看审计日志', 'ADMIN'),
-- 存储配置
('storage:config',   '存储策略配置', 'ADMIN'),
-- 水域管理
('waterarea:read',   '查看水域',     'WATERAREA'),
('waterarea:write',  '新增/修改水域', 'WATERAREA')
ON CONFLICT (perm_code) DO NOTHING;

-- 3. 角色-权限绑定
-- 系统管理员：全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- 值守员权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_OPERATOR'
  AND p.perm_code IN (
    'device:read','device:control',
    'alert:read','alert:confirm','alert:close',
    'task:read','task:dispatch',
    'drone:read','drone:plan','drone:control',
    'stats:read','waterarea:read'
  )
ON CONFLICT DO NOTHING;

-- 乡镇领导权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_LEADER'
  AND p.perm_code IN (
    'device:read','alert:read','task:read',
    'drone:read','stats:read','waterarea:read'
  )
ON CONFLICT DO NOTHING;

-- 救生员权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_RESCUER'
  AND p.perm_code IN (
    'task:read','task:update','alert:read'
  )
ON CONFLICT DO NOTHING;

-- 4. 初始管理员账号（密码: Admin@2024，BCrypt加密）
INSERT INTO sys_user (username, password, real_name, phone, status, password_reset)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800000000', 1, FALSE)
ON CONFLICT (username) DO NOTHING;

-- 分配管理员角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- 5. 示例水域数据
INSERT INTO water_area (name, area_code, level, center_point, description) VALUES
('黄村水库', 'WA001', 1, 'POINT(113.2644 23.1291)', '重点防控水域，水深较大'),
('南河段', 'WA002', 1, 'POINT(113.2700 23.1350)', '水流湍急，重点监控'),
('东湖游泳区', 'WA003', 2, 'POINT(113.2550 23.1200)', '开放游泳区域')
ON CONFLICT (area_code) DO NOTHING;

-- 6. 全局存储策略
INSERT INTO storage_config (water_area_id, retention_days_video, retention_days_snapshot, backup_enabled, storage_warn_threshold)
VALUES (NULL, 30, 90, FALSE, 85)
ON CONFLICT DO NOTHING;
