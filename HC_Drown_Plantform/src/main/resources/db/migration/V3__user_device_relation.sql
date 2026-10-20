-- ============================================================
-- V3__user_device_relation.sql
-- 救生员/人物与监控设备点位关联表 (支持一对多、多对一)
-- ============================================================

CREATE TABLE sys_user_device (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT    NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    device_id BIGINT    NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    UNIQUE(user_id, device_id)
);
