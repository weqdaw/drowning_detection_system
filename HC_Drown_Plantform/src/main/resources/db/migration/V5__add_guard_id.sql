-- ============================================================
-- V5__add_guard_id.sql
-- 为 sys_user 表增加救生员/人员编号 guard_id 字段
-- ============================================================

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS guard_id VARCHAR(50);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_user_guard_id ON sys_user(guard_id) WHERE deleted = 0;
